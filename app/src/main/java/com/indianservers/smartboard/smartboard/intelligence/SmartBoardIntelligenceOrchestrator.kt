package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import java.util.UUID

interface SmartBoardIntelligenceOrchestrator {
    suspend fun understand(request: SmartBoardUnderstandingRequest): SmartBoardUnderstandingResult
    suspend fun recommendActions(context: SmartBoardIntelligenceContext): List<SmartBoardRecommendation>
    suspend fun planWorkflow(request: SmartBoardWorkflowRequest): SmartBoardWorkflowPlan
    suspend fun executeApprovedStep(request: SmartBoardWorkflowStepRequest): SmartBoardWorkflowStepResult
    suspend fun explain(request: SmartBoardExplanationRequest): SmartBoardExplanationResult
}

class DefaultSmartBoardIntelligenceOrchestrator(
    private val subjects: SmartBoardSubjectIntelligenceRegistry,
    private val tools: SmartBoardToolRegistry,
    private val capabilityResolver: SmartBoardCapabilityResolver = DefaultSmartBoardCapabilityResolver(),
    private val verificationGate: SmartBoardVerificationGate = DeterministicSmartBoardVerificationGate(subjects),
    private val memory: SmartBoardSessionMemoryManager = SmartBoardSessionMemoryManager(),
) : SmartBoardIntelligenceOrchestrator {
    override suspend fun understand(request: SmartBoardUnderstandingRequest): SmartBoardUnderstandingResult {
        val goal = SmartBoardGoalDetector.detect(request.context, request.command, request.explicitAction)
        val understanding = subjects.handler(request.context.subject).understand(request.context, goal)
        val known = request.context.elements.filter { element ->
            element.sourceTrust in setOf(SmartBoardSourceTrust.USER_CONFIRMED, SmartBoardSourceTrust.ENGINE_DERIVED)
        }.map { SmartBoardKnownFact(it.kind, it.summary.untrustedValue(), it.id) }
        val unknown = buildList {
            goal.requiredInformation.forEach { add(SmartBoardUnknownFact(it.id, it.prompt, null)) }
            request.context.pendingAmbiguities.forEach { add(SmartBoardUnknownFact(it.token, it.prompt, it.elementId)) }
        }.distinctBy { it.label to it.sourceElementId }
        val state = SmartBoardProblemState(
            id = request.context.activeProblemId ?: "problem-${UUID.randomUUID()}",
            subject = request.context.subject,
            problemElementIds = goal.targetElementIds,
            goal = goal,
            knownInformation = known,
            unknownInformation = unknown,
            attemptedSteps = request.context.recentActions.map(SmartBoardActionHistoryEntry::actionId),
            verifiedSteps = request.context.recentActions.filter { it.succeeded }.map(SmartBoardActionHistoryEntry::actionId),
            invalidStepId = null,
            currentStage = when {
                goal.requiredInformation.any(MissingInformation::blocking) -> SmartBoardProblemStage.BLOCKED
                request.context.elements.isEmpty() -> SmartBoardProblemStage.CAPTURING
                else -> SmartBoardProblemStage.UNDERSTANDING
            },
            selectedMethod = null,
            assumptions = emptyList(),
            warnings = understanding.warnings,
            completionStatus = if (goal.requiredInformation.any(MissingInformation::blocking)) SmartBoardCompletionStatus.BLOCKED else SmartBoardCompletionStatus.IN_PROGRESS,
        )
        val clarification = when {
            goal.requiredInformation.any(MissingInformation::blocking) -> goal.requiredInformation.first { it.blocking }.prompt
            goal.confidence < .55f -> "What would you like to do with the selected content?"
            else -> null
        }
        return SmartBoardUnderstandingResult(request.context.copy(currentGoal = goal), understanding, state, clarification)
    }

    override suspend fun recommendActions(context: SmartBoardIntelligenceContext): List<SmartBoardRecommendation> {
        val goal = context.currentGoal ?: SmartBoardGoalDetector.detect(context, null)
        val understanding = subjects.handler(context.subject).understand(context, goal)
        val dismissed = memory.get(context.boardId)?.dismissedRecommendationIds.orEmpty()
        val completed = memory.get(context.boardId)?.completedActionIds.orEmpty()
        val raw = subjects.handler(context.subject).recommend(understanding, context)
        val unique = raw
            .filterNot { it.id in dismissed }
            .distinctBy { it.toolId to it.sourceElementIds.sorted() }
            .map { recommendation ->
                if (recommendation.toolId in completed && recommendation.disabledReason == null) {
                    recommendation.copy(disabledReason = "Already completed for this active context.", priority = recommendation.priority - 30)
                } else recommendation
            }
        return SmartBoardLearnerAdaptation.adjust(unique, context.learnerContext, context.mode)
            .sortedWith(compareByDescending<SmartBoardRecommendation> { it.disabledReason == null }.thenByDescending { it.priority })
            .take(12)
    }

    override suspend fun planWorkflow(request: SmartBoardWorkflowRequest): SmartBoardWorkflowPlan {
        val goal = request.goal ?: SmartBoardGoalDetector.detect(request.context, request.command, explicitAction = true)
        val resolution = capabilityResolver.resolve(goal, request.context)
        val lower = request.command.lowercase()
        val targets = goal.targetElementIds
        val specifications = buildList {
            add(StepSpec(SmartBoardWorkflowStepType.CONFIRM_INPUT, "Confirm selected interpretation", null, confirmation = true, optional = false))
            if (request.context.subject == SmartBoardSubject.MATHEMATICS) {
                if (Regex("""(?:quadratic|\^2|²|factor)""").containsMatchIn(lower) && ("solve" in lower || "factor" in lower)) {
                    add(StepSpec(SmartBoardWorkflowStepType.ENGINE_ACTION, "Factor with the existing CAS", "math.factor", true, false))
                }
                if ("solve" in lower || goal.type == SmartBoardGoalType.SOLVE) {
                    add(StepSpec(SmartBoardWorkflowStepType.ENGINE_ACTION, "Solve with the existing equation solver", "math.solve", true, false))
                    add(StepSpec(SmartBoardWorkflowStepType.VERIFY_RESULT, "Verify deterministic result", null, false, false))
                }
                if ("graph" in lower || "plot" in lower || goal.type == SmartBoardGoalType.GRAPH) {
                    add(StepSpec(SmartBoardWorkflowStepType.OPEN_VISUALIZATION, "Validate and open the existing graph", "math.plot_2d", true, true))
                }
            } else if (request.context.subject == SmartBoardSubject.PHYSICS) {
                if ("unit" in lower || "si" in lower) add(StepSpec(SmartBoardWorkflowStepType.ENGINE_ACTION, "Convert confirmed units", "physics.convert_to_si", true, true))
                if ("solve" in lower || goal.type == SmartBoardGoalType.SOLVE) {
                    add(StepSpec(SmartBoardWorkflowStepType.ENGINE_ACTION, "Solve with existing Physics formula and mathematics engines", "physics.solve_numerical", true, false))
                    add(StepSpec(SmartBoardWorkflowStepType.VERIFY_RESULT, "Verify units and result", null, false, false))
                }
                if ("graph" in lower || "plot" in lower) add(StepSpec(SmartBoardWorkflowStepType.OPEN_VISUALIZATION, "Open the existing graph workspace", "physics.draw_graph", true, true))
            }
        }
        val steps = specifications.mapIndexed { index, spec ->
            val previous = if (index == 0) emptyList() else listOf("workflow-step-${index - 1}")
            SmartBoardWorkflowStep(
                id = "workflow-step-$index",
                order = index,
                type = spec.type,
                title = spec.title,
                tool = spec.toolId?.let(::SmartBoardToolReference),
                inputElementIds = targets,
                dependsOnStepIds = previous,
                status = WorkflowStepStatus.PENDING,
                requiresConfirmation = spec.confirmation,
                canRetry = spec.type != SmartBoardWorkflowStepType.CONFIRM_INPUT,
                canSkip = spec.optional,
            )
        }
        val warning = buildList {
            if (!resolution.available) add(resolution.reason)
            addAll(goal.requiredInformation.map(MissingInformation::prompt))
            if (steps.size == 1) add("Choose a supported solve, verify, graph or explain action before execution.")
        }
        return SmartBoardWorkflowPlan(
            "workflow-${UUID.randomUUID()}",
            request.command.trim().take(80).ifBlank { "Smart Board workflow" },
            goal,
            steps,
            requiresUserApproval = true,
            estimatedCapabilities = steps.mapNotNull { step ->
                step.tool?.id?.let { id -> tools.availableTools(request.context).firstOrNull { it.id == id }?.capability }
            }.toSet(),
            warnings = warning,
        )
    }

    override suspend fun executeApprovedStep(request: SmartBoardWorkflowStepRequest): SmartBoardWorkflowStepResult {
        val step = request.plan.steps.single { it.id == request.stepId }
        require(step.status in setOf(WorkflowStepStatus.PENDING, WorkflowStepStatus.APPROVED, WorkflowStepStatus.FAILED)) {
            "Only pending or retryable workflow steps can run."
        }
        val dependenciesComplete = step.dependsOnStepIds.all { dependency ->
            request.plan.steps.firstOrNull { it.id == dependency }?.status in setOf(WorkflowStepStatus.COMPLETED, WorkflowStepStatus.SKIPPED)
        }
        require(dependenciesComplete) { "Complete prerequisite workflow steps first." }
        if (step.requiresConfirmation) require(request.explicitApproval) { "Approve this workflow step before execution." }
        val source = request.context.elements.filter { it.id in step.inputElementIds }.joinToString("\n") { it.summary.untrustedValue() }
        val toolResult = when {
            step.type == SmartBoardWorkflowStepType.CONFIRM_INPUT -> null
            step.type == SmartBoardWorkflowStepType.VERIFY_RESULT -> null
            step.tool != null -> tools.execute(
                SmartBoardToolCall(
                    "call-${UUID.randomUUID()}", step.tool.id, request.context.boardId, request.context.subject,
                    step.inputElementIds, mapOf("source" to source) + request.arguments, request.explicitApproval,
                ),
                request.context,
            )
            else -> null
        }
        val verification = when {
            step.type == SmartBoardWorkflowStepType.CONFIRM_INPUT ->
                SmartBoardVerificationResult(SmartBoardVerificationStatus.INCONCLUSIVE, "The user confirmed the selected interpretation.", null)
            step.type == SmartBoardWorkflowStepType.VERIFY_RESULT ->
                SmartBoardVerificationResult(SmartBoardVerificationStatus.VERIFIED_WITH_CONDITIONS, "The prior deterministic engine result remains linked to its assumptions.", .85f)
            toolResult != null -> verificationGate.verify(SmartBoardVerificationInput(toolResult, request.context))
            else -> SmartBoardVerificationResult(SmartBoardVerificationStatus.FAILED, "No executable tool was defined.", 0f)
        }
        val completed = toolResult?.success != false && verification.status != SmartBoardVerificationStatus.FAILED
        val updatedStep = step.copy(status = if (completed) WorkflowStepStatus.COMPLETED else WorkflowStepStatus.FAILED)
        val plan = request.plan.copy(steps = request.plan.steps.map { if (it.id == step.id) updatedStep else it })
        return SmartBoardWorkflowStepResult(plan, updatedStep, toolResult, verification)
    }

    override suspend fun explain(request: SmartBoardExplanationRequest): SmartBoardExplanationResult {
        val selected = request.context.elements.filter { it.id in request.targetElementIds }
        if (selected.isEmpty()) {
            return SmartBoardExplanationResult(
                "Clarification needed", listOf("Select the result or expression to explain."),
                SmartBoardVerificationResult(SmartBoardVerificationStatus.INCONCLUSIVE, "No target was selected.", null), true,
            )
        }
        val style = when (request.explanationMode) {
            SmartBoardExplanationMode.ONE_LINE -> "In one line"
            SmartBoardExplanationMode.BRIEF -> "Briefly"
            SmartBoardExplanationMode.EXAM_STYLE -> "For an exam answer"
            SmartBoardExplanationMode.VISUAL_FIRST -> "Start with the visual relationship"
            SmartBoardExplanationMode.FORMULA_FIRST -> "Start from the governing formula"
            SmartBoardExplanationMode.STEP_BY_STEP -> "Step by step"
            else -> "Explain"
        }
        val content = buildList {
            add("$style: ${selected.first().summary.untrustedValue().take(500)}")
            if (request.includeVerification) add("Verification status is reported separately; explanation wording does not verify a calculation.")
            if (request.includeVisualization) add("Use an existing interactive graph or subject visualization when one is recommended.")
        }.take(request.maxSteps?.coerceIn(1, 12) ?: 12)
        return SmartBoardExplanationResult(
            "Contextual explanation",
            content,
            SmartBoardVerificationResult(SmartBoardVerificationStatus.INCONCLUSIVE, "Explanation is grounded in selected structured content; calculations require their deterministic tool result.", .65f),
            degraded = !request.context.serviceAvailability.aiAvailable,
        )
    }

    fun sessionMemory() = memory

    private data class StepSpec(
        val type: SmartBoardWorkflowStepType,
        val title: String,
        val toolId: String?,
        val confirmation: Boolean,
        val optional: Boolean,
    )
}

private fun String.untrustedValue() = removePrefix("[UNTRUSTED_BOARD_CONTENT] ")
