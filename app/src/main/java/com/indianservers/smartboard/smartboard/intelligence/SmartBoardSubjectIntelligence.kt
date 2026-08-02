package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.integration.SmartBoardCasAdapter
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardGraphAdapter
import com.indianservers.smartboard.smartboard.integration.SmartBoardMathAction
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsContentType
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardAction
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.physics.PhysicsBoardAnalyzer
import com.indianservers.smartboard.smartboard.physics.PhysicsSmartBoardIntelligenceHandler
import java.util.UUID

interface SmartBoardSubjectIntelligenceHandler {
    val subject: SmartBoardSubject
    suspend fun understand(context: SmartBoardIntelligenceContext, goal: SmartBoardGoal): SmartBoardSubjectUnderstanding
    suspend fun recommend(understanding: SmartBoardSubjectUnderstanding, context: SmartBoardIntelligenceContext): List<SmartBoardRecommendation>
    fun buildTools(context: SmartBoardIntelligenceContext): List<SmartBoardToolDefinition>
    suspend fun verify(result: SmartBoardToolResult): SmartBoardVerificationResult
}

class MathematicsSmartBoardIntelligenceHandler : SmartBoardSubjectIntelligenceHandler {
    override val subject = SmartBoardSubject.MATHEMATICS

    override suspend fun understand(context: SmartBoardIntelligenceContext, goal: SmartBoardGoal): SmartBoardSubjectUnderstanding {
        val sources = context.elements.filter { "MathExpression" in it.kind || "SolutionSequence" in it.kind }
        val primary = sources.firstOrNull()?.summary?.untrustedValue().orEmpty()
        val analysis = SmartBoardExpressionAnalyzer.analyze(primary)
        val recognition = sources.mapNotNull(SmartBoardContextElement::confidence).minOrNull()
        val classification = if (analysis.type.name == "UNKNOWN") .35f else .9f
        return SmartBoardSubjectUnderstanding(
            subject,
            goal,
            if (sources.isEmpty()) "Select one mathematical expression." else "Detected ${analysis.type.name.lowercase().replace('_', ' ')}.",
            setOf(analysis.type.name),
            buildList {
                addAll(analysis.warnings)
                if (sources.size > 1 && goal.requiredInformation.any { it.id == "ambiguous-selection" }) add("Multiple unrelated expressions need a target choice.")
                if (primary.contains("int", true) && !primary.contains("+ C", true) && context.elements.any { "Result" in it.kind }) {
                    add("An indefinite integral result may be missing + C.")
                }
            },
            SmartBoardConfidenceProfile(
                recognition, classification, goal.confidence, recommendation = null, verification = null,
                overallDisplayLevel = displayLevel(minOf(classification, goal.confidence)),
            ),
        )
    }

    override suspend fun recommend(
        understanding: SmartBoardSubjectUnderstanding,
        context: SmartBoardIntelligenceContext,
    ): List<SmartBoardRecommendation> {
        val source = context.elements.firstOrNull { "MathExpression" in it.kind }?.summary?.untrustedValue().orEmpty()
        if (source.isBlank()) return emptyList()
        val analysis = SmartBoardExpressionAnalyzer.analyze(source)
        val completed = context.recentActions.filter(SmartBoardActionHistoryEntry::succeeded).mapTo(hashSetOf(), SmartBoardActionHistoryEntry::actionId)
        val actions = buildList {
            if (Regex("""(?:\^2|²).*[=]|[=].*(?:\^2|²)""").containsMatchIn(source)) {
                add(SmartBoardMathAction.FACTOR)
                add(SmartBoardMathAction.SOLVE)
                add(SmartBoardMathAction.PLOT_2D)
            }
            addAll(analysis.actions)
            if (analysis.type.name in setOf("EQUATION", "SYSTEM", "INEQUALITY")) add(SmartBoardMathAction.VERIFY_WORK)
        }.distinct()
        return actions.mapIndexed { index, action ->
            val toolId = action.toolId()
            val reason = when (action) {
                SmartBoardMathAction.FACTOR -> "Factoring is useful because the selected expression contains a polynomial structure."
                SmartBoardMathAction.SOLVE -> "Solve is relevant because the selection contains an equation or unknown."
                SmartBoardMathAction.PLOT_2D -> "Graphing can connect symbolic behavior with intercepts and shape."
                SmartBoardMathAction.VERIFY_WORK -> "Verification checks whether the current transformation preserves the mathematics."
                SmartBoardMathAction.DIFFERENTIATE -> "The selected function can be analyzed through its rate of change."
                SmartBoardMathAction.INTEGRATE -> "The selected function supports an antiderivative or area interpretation."
                SmartBoardMathAction.STATISTICS -> "The selected values form a dataset suitable for statistical analysis."
                else -> "${action.name.lowercase().replace('_', ' ')} is supported by the existing deterministic mathematics engine."
            }
            recommendation(
                toolId,
                context,
                action.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                reason,
                categoryFor(action),
                priority = (96 - index * 7).coerceAtLeast(40),
                disabled = when {
                    toolId in completed -> "Already completed for this active context."
                    requiredCapability(action) !in context.availableCapabilities -> "Required engine is unavailable."
                    understanding.goal.requiredInformation.any(MissingInformation::blocking) -> understanding.goal.requiredInformation.first().prompt
                    else -> null
                },
                engine = engineFor(action),
            )
        }
    }

    override fun buildTools(context: SmartBoardIntelligenceContext): List<SmartBoardToolDefinition> =
        SmartBoardMathAction.entries.mapNotNull { action ->
            val capability = requiredCapability(action)
            if (capability !in context.availableCapabilities) null else tool(action.toolId(), action.name, subject, capability, engineFor(action))
        }

    override suspend fun verify(result: SmartBoardToolResult) = verificationFor(result)
}

class PhysicsSubjectIntelligenceAdapter(
    private val analyzer: PhysicsBoardAnalyzer = PhysicsBoardAnalyzer(),
) : SmartBoardSubjectIntelligenceHandler {
    override val subject = SmartBoardSubject.PHYSICS

    override suspend fun understand(context: SmartBoardIntelligenceContext, goal: SmartBoardGoal): SmartBoardSubjectUnderstanding {
        val element = context.elements.firstOrNull { "PhysicsExpression" in it.kind }
        val analysis = element?.summary?.untrustedValue()?.let(analyzer::analyze)
        val classification = if (analysis == null || analysis.contentType == PhysicsContentType.UNKNOWN) .35f else .88f
        return SmartBoardSubjectUnderstanding(
            subject,
            goal,
            analysis?.let { "Detected ${it.contentType.name.lowercase().replace('_', ' ')}${it.topic?.let { topic -> " in ${topic.name.lowercase().replace('_', ' ')}" }.orEmpty()}." }
                ?: "Select one Physics expression or diagram.",
            listOfNotNull(analysis?.contentType?.name, analysis?.topic?.name).toSet(),
            analysis?.warnings.orEmpty() + analysis?.ambiguities.orEmpty().map { it.message },
            SmartBoardConfidenceProfile(
                element?.confidence, classification, goal.confidence,
                formulaMatch = if (analysis?.equations?.isNotEmpty() == true) .9f else null,
                diagramInterpretation = analysis?.diagrams?.firstOrNull()?.confidence,
                recommendation = null, verification = null,
                overallDisplayLevel = displayLevel(minOf(classification, goal.confidence)),
            ),
        )
    }

    override suspend fun recommend(
        understanding: SmartBoardSubjectUnderstanding,
        context: SmartBoardIntelligenceContext,
    ): List<SmartBoardRecommendation> {
        val source = context.elements.firstOrNull { "PhysicsExpression" in it.kind }?.summary?.untrustedValue() ?: return emptyList()
        val analysis = analyzer.analyze(source)
        val actions = (analysis.suggestedActions + listOf(PhysicsActionType.VERIFY_WORK, PhysicsActionType.TUTOR_HINT)).distinct()
        return actions.mapIndexed { index, action ->
            val toolId = "physics.${action.name.lowercase()}"
            recommendation(
                toolId, context,
                action.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                physicsReason(action, analysis.contentType),
                physicsCategory(action),
                (96 - index * 6).coerceAtLeast(40),
                disabled = when {
                    physicsCapability(action) !in context.availableCapabilities -> "Required Physics engine is unavailable."
                    understanding.goal.requiredInformation.any(MissingInformation::blocking) -> understanding.goal.requiredInformation.first().prompt
                    else -> null
                },
                engine = SmartBoardEngineReference(physicsEngine(action), true, true, true),
            )
        }
    }

    override fun buildTools(context: SmartBoardIntelligenceContext): List<SmartBoardToolDefinition> =
        PhysicsActionType.entries.mapNotNull { action ->
            val capability = physicsCapability(action)
            if (capability !in context.availableCapabilities) null else tool(
                "physics.${action.name.lowercase()}", action.name, subject, capability,
                SmartBoardEngineReference(physicsEngine(action), true, true, true),
            )
        }

    override suspend fun verify(result: SmartBoardToolResult) = verificationFor(result)
}

class SmartBoardSubjectIntelligenceRegistry(handlers: List<SmartBoardSubjectIntelligenceHandler>) {
    private val bySubject = handlers.associateBy(SmartBoardSubjectIntelligenceHandler::subject)
    init { require(bySubject.size == handlers.size) }
    fun handler(subject: SmartBoardSubject) = bySubject[subject]
        ?: error("${subject.name.lowercase()} intelligence is not installed.")
    fun supportedSubjects() = bySubject.keys
}

interface SmartBoardCapabilityResolver {
    fun resolve(goal: SmartBoardGoal, context: SmartBoardIntelligenceContext): SmartBoardCapabilityResolution
}

class DefaultSmartBoardCapabilityResolver : SmartBoardCapabilityResolver {
    override fun resolve(goal: SmartBoardGoal, context: SmartBoardIntelligenceContext): SmartBoardCapabilityResolution {
        val capability = when (goal.type) {
            SmartBoardGoalType.SOLVE -> if (context.subject == SmartBoardSubject.PHYSICS) SmartBoardCapability.PHYSICS_NUMERICAL else SmartBoardCapability.SOLVER
            SmartBoardGoalType.SIMPLIFY, SmartBoardGoalType.DERIVE -> SmartBoardCapability.CAS
            SmartBoardGoalType.GRAPH, SmartBoardGoalType.VISUALIZE -> SmartBoardCapability.GRAPH_2D
            SmartBoardGoalType.VERIFY, SmartBoardGoalType.CORRECT -> SmartBoardCapability.WORK_VERIFICATION
            SmartBoardGoalType.ANALYZE_DATA -> SmartBoardCapability.STATISTICS
            SmartBoardGoalType.CONVERT_UNITS -> SmartBoardCapability.PHYSICS_UNITS
            SmartBoardGoalType.CHECK_DIMENSIONS -> SmartBoardCapability.PHYSICS_DIMENSIONS
            SmartBoardGoalType.EXPLAIN, SmartBoardGoalType.LEARN -> SmartBoardCapability.LOCAL_TUTOR
            else -> null
        }
        val available = capability == null || capability in context.availableCapabilities
        val missing = goal.requiredInformation
        return SmartBoardCapabilityResolution(
            capability,
            capability?.let { SmartBoardEngineReference(engineId(it), true, it !in setOf(SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D), true) },
            missing,
            available && missing.none(MissingInformation::blocking),
            confirmationRequired = !goal.userConfirmed || goal.type in setOf(SmartBoardGoalType.COMPLETE_WORK),
            reason = when {
                missing.isNotEmpty() -> missing.first().prompt
                !available -> "The required capability is unavailable in the current service mode."
                capability == null -> "No deterministic engine is required yet."
                else -> "Routes to ${engineId(capability)} because it is the existing deterministic capability for ${goal.type.name.lowercase()}."
            },
        )
    }
}

interface SmartBoardToolRegistry {
    fun availableTools(context: SmartBoardIntelligenceContext): List<SmartBoardToolDefinition>
    suspend fun execute(call: SmartBoardToolCall, context: SmartBoardIntelligenceContext): SmartBoardToolResult
}

class DefaultSmartBoardToolRegistry(
    private val subjects: SmartBoardSubjectIntelligenceRegistry,
    private val cas: SmartBoardCasAdapter = SmartBoardCasAdapter(),
    private val physics: PhysicsSmartBoardIntelligenceHandler = PhysicsSmartBoardIntelligenceHandler(),
) : SmartBoardToolRegistry {
    override fun availableTools(context: SmartBoardIntelligenceContext) = subjects.handler(context.subject).buildTools(context)

    override suspend fun execute(call: SmartBoardToolCall, context: SmartBoardIntelligenceContext): SmartBoardToolResult {
        require(call.boardId == context.boardId && call.subject == context.subject)
        val definition = availableTools(context).singleOrNull { it.id == call.toolId }
            ?: return failed(call, "The requested tool is not allowlisted for this Board.")
        SmartBoardPermissionPolicy.authorize(definition, call.explicitUserApproval).getOrElse {
            return failed(call, it.message ?: "Approval is required.")
        }
        val missing = definition.requiredArguments - call.arguments.keys
        if (missing.isNotEmpty()) return failed(call, "Missing required parameter: ${missing.first()}.")
        if (call.sourceElementIds.isEmpty() || call.sourceElementIds.any { it !in context.elements.map(SmartBoardContextElement::id) }) {
            return failed(call, "Tool input must stay within the selected intelligence context.")
        }
        val source = call.arguments.getValue("source")
        return if (call.subject == SmartBoardSubject.MATHEMATICS) executeMath(call, source) else executePhysics(call, source)
    }

    private suspend fun executeMath(call: SmartBoardToolCall, source: String): SmartBoardToolResult {
        val action = SmartBoardMathAction.entries.firstOrNull { it.toolId() == call.toolId } ?: return failed(call, "Unknown mathematics tool.")
        if (action in setOf(SmartBoardMathAction.PLOT_2D, SmartBoardMathAction.PLOT_3D)) {
            val graph = SmartBoardGraphAdapter.prepare(source, action == SmartBoardMathAction.PLOT_3D).getOrElse {
                return failed(call, it.message ?: "Graph preview is unavailable.")
            }
            return SmartBoardToolResult(
                call.id, call.toolId, true, "Open ${graph.route}", null, null, listOf("Validated by the existing typed graph parser."),
                emptyList(), SmartBoardVerificationStatus.VERIFIED, moduleRoute = graph.route, modulePayload = graph.expression,
            )
        }
        if (action in setOf(SmartBoardMathAction.OPEN_GEOMETRY_2D, SmartBoardMathAction.OPEN_GEOMETRY_3D)) {
            val route = if (action == SmartBoardMathAction.OPEN_GEOMETRY_2D) "geometry2d" else "geometry3d"
            return SmartBoardToolResult(call.id, call.toolId, true, "Open geometry", null, null, emptyList(), emptyList(),
                SmartBoardVerificationStatus.INCONCLUSIVE, moduleRoute = route, modulePayload = source)
        }
        if (action == SmartBoardMathAction.VERIFY_WORK) {
            return SmartBoardToolResult(call.id, call.toolId, true, "Verification requested", null, null,
                listOf("Use the existing line-by-line work verifier on the selected sequence."), emptyList(),
                SmartBoardVerificationStatus.PARTIALLY_VERIFIED)
        }
        val result = cas.execute(source, action)
        return SmartBoardToolResult(
            call.id, call.toolId, result.supported, result.title, result.exact, result.approximate,
            result.steps, result.assumptions,
            if (result.verified) SmartBoardVerificationStatus.VERIFIED else if (result.supported) SmartBoardVerificationStatus.INCONCLUSIVE else SmartBoardVerificationStatus.UNSUPPORTED,
            safeMessage = if (result.supported) null else "The existing mathematics engine does not support this request.",
        )
    }

    private suspend fun executePhysics(call: SmartBoardToolCall, source: String): SmartBoardToolResult {
        val action = PhysicsActionType.entries.firstOrNull { "physics.${it.name.lowercase()}" == call.toolId }
            ?: return failed(call, "Unknown Physics tool.")
        val analysis = PhysicsBoardAnalyzer().analyze(source)
        val element = PhysicsExpressionElement(
            "phase4-${UUID.randomUUID()}", source, null, analysis.contentType, analysis.topic,
            analysis.equations.firstOrNull()?.formulaId, emptyList(), null,
            analysis.ambiguities.map { it.message }, analysis.warnings, SmartBoardBounds.Empty, 0L,
        )
        val outcome = physics.execute(element, action, System.currentTimeMillis())
        val result = outcome.result
        return SmartBoardToolResult(
            call.id, call.toolId, result != null || outcome.handoffRoute != null, result?.title ?: outcome.message,
            result?.exactResultLatex, result?.numericalResult?.toString(),
            result?.steps?.map { "${it.title}: ${it.expression} — ${it.explanation}" }.orEmpty(),
            result?.assumptions.orEmpty(),
            when (result?.status) {
                PhysicsResultStatus.VERIFIED -> SmartBoardVerificationStatus.VERIFIED
                PhysicsResultStatus.PARTIALLY_VERIFIED -> SmartBoardVerificationStatus.PARTIALLY_VERIFIED
                PhysicsResultStatus.NEEDS_CONFIRMATION, null -> SmartBoardVerificationStatus.INCONCLUSIVE
                PhysicsResultStatus.UNSUPPORTED -> SmartBoardVerificationStatus.UNSUPPORTED
            },
            producedElement = result,
            moduleRoute = outcome.handoffRoute,
            modulePayload = outcome.handoffPayload,
        )
    }

    private fun failed(call: SmartBoardToolCall, message: String) = SmartBoardToolResult(
        call.id, call.toolId, false, "Tool unavailable", null, null, emptyList(), emptyList(),
        SmartBoardVerificationStatus.FAILED, safeMessage = message.take(240),
    )
}

interface SmartBoardVerificationGate {
    suspend fun verify(input: SmartBoardVerificationInput): SmartBoardVerificationResult
}

class DeterministicSmartBoardVerificationGate(
    private val subjects: SmartBoardSubjectIntelligenceRegistry,
) : SmartBoardVerificationGate {
    override suspend fun verify(input: SmartBoardVerificationInput): SmartBoardVerificationResult =
        subjects.handler(input.context.subject).verify(input.toolResult)
}

object SmartBoardLearnerAdaptation {
    fun adjust(
        recommendations: List<SmartBoardRecommendation>,
        learner: SmartBoardLearnerContext?,
        mode: SmartBoardIntelligenceMode,
    ): List<SmartBoardRecommendation> {
        return recommendations.map { recommendation ->
            val delta = when {
                mode == SmartBoardIntelligenceMode.GUIDED_LEARNING && recommendation.category == SmartBoardRecommendationCategory.LEARN -> 12
                mode == SmartBoardIntelligenceMode.FAST_SOLVE &&
                    recommendation.category in setOf(SmartBoardRecommendationCategory.SOLVE, SmartBoardRecommendationCategory.VERIFY) -> 12
                mode == SmartBoardIntelligenceMode.EXPLORATION &&
                    recommendation.category in setOf(SmartBoardRecommendationCategory.VISUALIZE, SmartBoardRecommendationCategory.EXPLORE) -> 12
                learner?.masteryState in setOf("NOT_STARTED", "INTRODUCED", "LEARNING") && recommendation.category == SmartBoardRecommendationCategory.LEARN -> 10
                learner?.masteryState == "MASTERED" && recommendation.category == SmartBoardRecommendationCategory.EXPLORE -> 8
                learner?.hintDependence?.let { it > .6f } == true && recommendation.title.contains("full", true) -> -12
                else -> 0
            }
            recommendation.copy(priority = (recommendation.priority + delta).coerceIn(0, 100))
        }.sortedByDescending(SmartBoardRecommendation::priority)
    }
}

private fun recommendation(
    toolId: String,
    context: SmartBoardIntelligenceContext,
    title: String,
    reason: String,
    category: SmartBoardRecommendationCategory,
    priority: Int,
    disabled: String?,
    engine: SmartBoardEngineReference,
) = SmartBoardRecommendation(
    recommendationId(toolId, context.selectedElementIds), SmartBoardAction.SubjectAction(toolId, title), toolId,
    category, title, reason, priority, if (disabled == null) .88f else .45f, context.selectedElementIds,
    requiredConfirmation = true, expectedOutcome = title, engine = engine,
    learningValue = if (category in setOf(SmartBoardRecommendationCategory.LEARN, SmartBoardRecommendationCategory.EXPLORE)) .8f else .5f,
    disabledReason = disabled,
)

private fun tool(
    id: String,
    title: String,
    subject: SmartBoardSubject,
    capability: SmartBoardCapability,
    engine: SmartBoardEngineReference,
) = SmartBoardToolDefinition(
    id, title.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase), subject, capability,
    if (id.contains("plot") || id.contains("open")) SmartBoardPermissionClass.REVERSIBLE_WRITE else SmartBoardPermissionClass.SAFE_READ_ONLY,
    setOf("source"), engine,
)

private fun SmartBoardMathAction.toolId() = "math.${name.lowercase()}"
private fun requiredCapability(action: SmartBoardMathAction) = when (action) {
    SmartBoardMathAction.PLOT_2D -> SmartBoardCapability.GRAPH_2D
    SmartBoardMathAction.PLOT_3D -> SmartBoardCapability.GRAPH_3D
    SmartBoardMathAction.OPEN_GEOMETRY_2D -> SmartBoardCapability.GEOMETRY_2D
    SmartBoardMathAction.OPEN_GEOMETRY_3D -> SmartBoardCapability.GEOMETRY_3D
    SmartBoardMathAction.STATISTICS -> SmartBoardCapability.STATISTICS
    SmartBoardMathAction.VERIFY_WORK -> SmartBoardCapability.WORK_VERIFICATION
    SmartBoardMathAction.SOLVE -> SmartBoardCapability.SOLVER
    else -> SmartBoardCapability.CAS
}
private fun engineFor(action: SmartBoardMathAction) = SmartBoardEngineReference(
    when (requiredCapability(action)) {
        SmartBoardCapability.SOLVER -> "MathProblemSolver"
        SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D -> "TypedGraphExpressionParser"
        SmartBoardCapability.STATISTICS -> "AdvancedStatisticsEngine"
        SmartBoardCapability.WORK_VERIFICATION -> "TrustedMathKernel"
        SmartBoardCapability.GEOMETRY_2D -> "Geometry2D"
        SmartBoardCapability.GEOMETRY_3D -> "Geometry3D"
        else -> "SymbolicCasEngine"
    },
    local = true, exactResultSupported = requiredCapability(action) !in setOf(SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D),
    offlineCapable = true,
)
private fun categoryFor(action: SmartBoardMathAction) = when (action) {
    SmartBoardMathAction.PLOT_2D, SmartBoardMathAction.PLOT_3D, SmartBoardMathAction.OPEN_GEOMETRY_2D, SmartBoardMathAction.OPEN_GEOMETRY_3D -> SmartBoardRecommendationCategory.VISUALIZE
    SmartBoardMathAction.VERIFY_WORK -> SmartBoardRecommendationCategory.VERIFY
    SmartBoardMathAction.SOLVE, SmartBoardMathAction.EVALUATE, SmartBoardMathAction.SIMPLIFY, SmartBoardMathAction.FACTOR, SmartBoardMathAction.EXPAND -> SmartBoardRecommendationCategory.SOLVE
    SmartBoardMathAction.STATISTICS -> SmartBoardRecommendationCategory.EXPLORE
    else -> SmartBoardRecommendationCategory.LEARN
}

private fun physicsCapability(action: PhysicsActionType) = when (action) {
    PhysicsActionType.CONVERT_UNITS, PhysicsActionType.CONVERT_TO_SI -> SmartBoardCapability.PHYSICS_UNITS
    PhysicsActionType.CHECK_DIMENSIONS -> SmartBoardCapability.PHYSICS_DIMENSIONS
    PhysicsActionType.SOLVE_NUMERICAL, PhysicsActionType.SUBSTITUTE_VALUES, PhysicsActionType.REARRANGE_FORMULA -> SmartBoardCapability.PHYSICS_NUMERICAL
    PhysicsActionType.DRAW_GRAPH -> SmartBoardCapability.GRAPH_2D
    PhysicsActionType.OPEN_2D -> SmartBoardCapability.GEOMETRY_2D
    PhysicsActionType.OPEN_3D -> SmartBoardCapability.GEOMETRY_3D
    PhysicsActionType.OPEN_CIRCUIT, PhysicsActionType.OPEN_WAVE, PhysicsActionType.OPEN_OPTICS -> SmartBoardCapability.PHYSICS_VISUALIZATION
    PhysicsActionType.VERIFY_WORK -> SmartBoardCapability.WORK_VERIFICATION
    PhysicsActionType.TUTOR_HINT, PhysicsActionType.NEXT_STEP, PhysicsActionType.EXPLAIN_FORMULA -> SmartBoardCapability.LOCAL_TUTOR
    PhysicsActionType.ANALYZE_EXPERIMENT, PhysicsActionType.ANALYZE_UNCERTAINTY -> SmartBoardCapability.STATISTICS
    else -> SmartBoardCapability.PHYSICS_FORMULAS
}
private fun physicsEngine(action: PhysicsActionType) = when (physicsCapability(action)) {
    SmartBoardCapability.PHYSICS_UNITS -> "PhysicsUnitSystem"
    SmartBoardCapability.PHYSICS_DIMENSIONS -> "SymbolicCasEngine + Physics dimensions"
    SmartBoardCapability.PHYSICS_NUMERICAL -> "OfflinePhysicsFormulaRepository + MathProblemSolver"
    SmartBoardCapability.STATISTICS -> "AdvancedStatisticsEngine"
    SmartBoardCapability.GRAPH_2D -> "TypedGraphExpressionParser"
    SmartBoardCapability.WORK_VERIFICATION -> "PhysicsWorkVerifier"
    SmartBoardCapability.LOCAL_TUTOR -> "PhysicsTutorEngine"
    else -> "Existing Physics workspace"
}
private fun physicsCategory(action: PhysicsActionType) = when (action) {
    PhysicsActionType.CONVERT_UNITS, PhysicsActionType.CONVERT_TO_SI -> SmartBoardRecommendationCategory.CONVERT
    PhysicsActionType.CHECK_DIMENSIONS, PhysicsActionType.VERIFY_WORK -> SmartBoardRecommendationCategory.VERIFY
    PhysicsActionType.DRAW_GRAPH, PhysicsActionType.OPEN_2D, PhysicsActionType.OPEN_3D,
    PhysicsActionType.OPEN_CIRCUIT, PhysicsActionType.OPEN_WAVE, PhysicsActionType.OPEN_OPTICS -> SmartBoardRecommendationCategory.VISUALIZE
    PhysicsActionType.TUTOR_HINT, PhysicsActionType.NEXT_STEP, PhysicsActionType.EXPLAIN_FORMULA -> SmartBoardRecommendationCategory.LEARN
    PhysicsActionType.REVIEW_DIAGRAM -> SmartBoardRecommendationCategory.CORRECT
    else -> SmartBoardRecommendationCategory.SOLVE
}
private fun physicsReason(action: PhysicsActionType, content: PhysicsContentType) = when (action) {
    PhysicsActionType.SOLVE_NUMERICAL -> "The selected $content contains known values and an explicit unknown."
    PhysicsActionType.CHECK_DIMENSIONS -> "A dimensional check can detect incompatible terms without changing the formula."
    PhysicsActionType.CONVERT_UNITS, PhysicsActionType.CONVERT_TO_SI -> "Confirmed compatible units should be normalized before substitution."
    PhysicsActionType.DRAW_GRAPH -> "A graph can expose how the physical quantities vary."
    PhysicsActionType.TUTOR_HINT -> "One hint preserves learner participation while using the identified Physics context."
    else -> "${action.name.lowercase().replace('_', ' ')} is supported for the detected ${content.name.lowercase().replace('_', ' ')}."
}
private fun verificationFor(result: SmartBoardToolResult) = SmartBoardVerificationResult(
    result.verificationStatus,
    when (result.verificationStatus) {
        SmartBoardVerificationStatus.VERIFIED -> "The deterministic engine returned a verified result."
        SmartBoardVerificationStatus.VERIFIED_WITH_CONDITIONS -> "Verified under the listed assumptions."
        SmartBoardVerificationStatus.NUMERICALLY_VERIFIED -> "Verified numerically within engine tolerance."
        SmartBoardVerificationStatus.PARTIALLY_VERIFIED -> "Only part of the result could be verified."
        SmartBoardVerificationStatus.INCONCLUSIVE -> "The result needs further evidence."
        SmartBoardVerificationStatus.UNSUPPORTED -> "No installed deterministic verifier supports this result."
        SmartBoardVerificationStatus.FAILED -> "Execution or verification failed."
    },
    when (result.verificationStatus) {
        SmartBoardVerificationStatus.VERIFIED -> .98f
        SmartBoardVerificationStatus.VERIFIED_WITH_CONDITIONS, SmartBoardVerificationStatus.NUMERICALLY_VERIFIED -> .9f
        SmartBoardVerificationStatus.PARTIALLY_VERIFIED -> .65f
        SmartBoardVerificationStatus.INCONCLUSIVE, SmartBoardVerificationStatus.UNSUPPORTED -> .4f
        SmartBoardVerificationStatus.FAILED -> .1f
    },
)
private fun displayLevel(value: Float) = when {
    value >= .8f -> ConfidenceDisplayLevel.HIGH_CONFIDENCE
    value >= .55f -> ConfidenceDisplayLevel.REVIEW_RECOMMENDED
    else -> ConfidenceDisplayLevel.NEEDS_CONFIRMATION
}
private fun engineId(capability: SmartBoardCapability) = when (capability) {
    SmartBoardCapability.CAS -> "SymbolicCasEngine"
    SmartBoardCapability.SOLVER -> "MathProblemSolver"
    SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D -> "TypedGraphExpressionParser"
    SmartBoardCapability.STATISTICS -> "AdvancedStatisticsEngine"
    SmartBoardCapability.WORK_VERIFICATION -> "TrustedMathKernel"
    SmartBoardCapability.PHYSICS_UNITS -> "PhysicsUnitSystem"
    SmartBoardCapability.PHYSICS_DIMENSIONS -> "PhysicsDimensionalAnalyzer"
    SmartBoardCapability.PHYSICS_NUMERICAL -> "PhysicsNumericalSolver"
    else -> capability.name
}
private fun String.untrustedValue() = removePrefix("[UNTRUSTED_BOARD_CONTENT] ")
