package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyResultElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.ChemistryResultElement
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.EnglishResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import java.security.MessageDigest
import java.util.Locale

interface SmartBoardContextBuilder {
    suspend fun build(
        document: SmartBoardDocument,
        selection: Set<String>,
        activeProblemId: String?,
    ): SmartBoardIntelligenceContext
}

fun interface SmartBoardLearnerContextProvider {
    fun contextFor(subject: SmartBoardSubject, elements: List<SmartBoardContextElement>): SmartBoardLearnerContext?
}

class DefaultSmartBoardContextBuilder(
    private val memoryProvider: (String) -> SmartBoardSessionMemory? = { null },
    private val learnerProvider: SmartBoardLearnerContextProvider = SmartBoardLearnerContextProvider { _, _ -> null },
    private val serviceAvailability: () -> SmartBoardServiceAvailability = {
        SmartBoardServiceAvailability(SmartBoardIntelligenceLevel.DETERMINISTIC, true, false)
    },
    private val deviceContext: () -> SmartBoardDeviceContext = {
        SmartBoardDeviceContext("unknown", reducedMotion = false, highContrast = false, networkAvailable = false)
    },
    private val intelligenceMode: () -> SmartBoardIntelligenceMode = { SmartBoardIntelligenceMode.ASSISTIVE },
    private val maximumElements: Int = 24,
    private val maximumCharacters: Int = 8_000,
) : SmartBoardContextBuilder {
    init {
        require(maximumElements in 1..100)
        require(maximumCharacters in 1_000..32_000)
    }

    override suspend fun build(
        document: SmartBoardDocument,
        selection: Set<String>,
        activeProblemId: String?,
    ): SmartBoardIntelligenceContext {
        val memory = memoryProvider(document.id)
        val seed = selection.ifEmpty {
            memory?.activeProblemId?.let { problemId ->
                memory.activeWorkflow?.steps?.flatMap(SmartBoardWorkflowStep::inputElementIds)?.toSet()
                    ?.takeIf(Set<String>::isNotEmpty)
                    ?: setOf(problemId)
            }.orEmpty()
        }.ifEmpty {
            document.elements.asReversed().firstOrNull { it !is StrokeElement && !it.hidden }?.let { setOf(it.id) }.orEmpty()
        }
        val adjacency = adjacency(document)
        val relevant = linkedSetOf<String>()
        val queue = ArrayDeque(seed.filter { id -> document.elements.any { it.id == id } })
        while (queue.isNotEmpty() && relevant.size < maximumElements * 2) {
            val id = queue.removeFirst()
            if (!relevant.add(id)) continue
            adjacency[id].orEmpty().forEach { if (it !in relevant) queue.addLast(it) }
        }
        val candidates = document.elements.filter { it.id in relevant && !it.hidden }
        var characters = 0
        val contextElements = buildList {
            candidates.forEachIndexed { index, element ->
                if (size >= maximumElements) return@forEachIndexed
                val summary = element.contextSummary().take(1_200)
                if (characters + summary.length > maximumCharacters) return@forEachIndexed
                characters += summary.length
                add(
                    SmartBoardContextElement(
                        id = element.id,
                        kind = element::class.simpleName ?: "Board element",
                        summary = SmartBoardUntrustedContentPolicy.mark(summary),
                        sourceTrust = element.sourceTrust(),
                        confidence = element.contextConfidence(),
                        sourceElementIds = element.sourceIds(),
                        readingOrder = index,
                    ),
                )
            }
        }
        val includedIds = contextElements.mapTo(linkedSetOf(), SmartBoardContextElement::id)
        val relationships = document.relationships.filter { relation -> relation.elementIds.any(includedIds::contains) }
            .map { relation -> relation.copy(elementIds = relation.elementIds.filter(includedIds::contains)) }
            .filter { it.elementIds.isNotEmpty() }
        val ambiguities = contextElements.flatMap { element ->
            val source = document.elements.firstOrNull { it.id == element.id }
            when (source) {
                is PhysicsExpressionElement -> source.ambiguities.mapIndexed { index, message ->
                    SmartBoardAmbiguity("${source.id}:$index", source.id, message.substringBefore(' '), emptyList(), message)
                }
                else -> emptyList()
            }
        }.filterNot { ambiguity -> memory?.resolvedAmbiguities?.containsKey(ambiguity.id) == true }
        val availability = serviceAvailability()
        val capabilities = defaultCapabilities(document.subject) - availability.unavailableCapabilities
        return SmartBoardIntelligenceContext(
            boardId = document.id,
            subject = document.subject,
            mode = intelligenceMode(),
            selectedElementIds = seed.filter(includedIds::contains),
            activeProblemId = activeProblemId ?: memory?.activeProblemId,
            activeWorkflowId = memory?.activeWorkflow?.id,
            currentGoal = memory?.activeWorkflow?.goal,
            elements = contextElements,
            relationships = relationships,
            recentActions = memory?.recentActions.orEmpty().takeLast(20),
            pendingAmbiguities = ambiguities,
            learnerContext = learnerProvider.contextFor(document.subject, contextElements),
            lessonContext = null,
            availableCapabilities = capabilities,
            deviceContext = deviceContext(),
            serviceAvailability = availability,
            metrics = SmartBoardContextMetrics(
                candidateElementCount = candidates.size,
                includedElementCount = contextElements.size,
                includedCharacters = characters,
                truncatedElementCount = (candidates.size - contextElements.size).coerceAtLeast(0),
                fullBoardIncluded = document.elements.isNotEmpty() && contextElements.size == document.elements.count { !it.hidden },
            ),
        )
    }

    private fun adjacency(document: SmartBoardDocument): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        fun link(first: String, second: String) {
            result.getOrPut(first, ::linkedSetOf) += second
            result.getOrPut(second, ::linkedSetOf) += first
        }
        document.relationships.forEach { relationship ->
            relationship.elementIds.forEach { first ->
                relationship.elementIds.filterNot { it == first }.forEach { second -> link(first, second) }
            }
        }
        document.elements.forEach { element -> element.sourceIds().forEach { source -> link(element.id, source) } }
        return result
    }
}

object SmartBoardGoalDetector {
    fun detect(context: SmartBoardIntelligenceContext, command: String?, explicitAction: Boolean = false): SmartBoardGoal {
        val normalized = command.orEmpty().trim().lowercase(Locale.ROOT)
        val commandType = when {
            Regex("""\b(solve|find roots?|calculate)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.SOLVE
            Regex("""\b(graph|plot)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.GRAPH
            Regex("""\b(verify|check my|check answer)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.VERIFY
            Regex("""\b(simplify)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.SIMPLIFY
            Regex("""\b(explain|why)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.EXPLAIN
            Regex("""\b(hint|continue|next step)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.LEARN
            Regex("""\b(convert).*(unit|si)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.CONVERT_UNITS
            Regex("""\b(dimension)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.CHECK_DIMENSIONS
            Regex("""\b(visual|show this)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.VISUALIZE
            Regex("""\b(compare)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.COMPARE
            Regex("""\b(practice|similar question)\b""").containsMatchIn(normalized) -> SmartBoardGoalType.PRACTICE
            else -> SmartBoardGoalType.UNKNOWN
        }
        val inferredType = if (commandType != SmartBoardGoalType.UNKNOWN) commandType else when {
            context.pendingAmbiguities.isNotEmpty() -> SmartBoardGoalType.UNDERSTAND
            context.elements.any { "SolutionSequence" in it.kind } -> SmartBoardGoalType.VERIFY
            context.elements.any { "PhysicsExpression" in it.kind } -> SmartBoardGoalType.UNDERSTAND
            context.elements.any { "MathExpression" in it.kind } -> SmartBoardGoalType.UNDERSTAND
            else -> SmartBoardGoalType.UNKNOWN
        }
        val unrelatedSelection = context.selectedElementIds.size > 1 &&
            context.relationships.none { relation -> context.selectedElementIds.count(relation.elementIds::contains) > 1 }
        val required = buildList {
            if (context.selectedElementIds.isEmpty()) add(MissingInformation("selection", "Select the content to work with.", true))
            if (unrelatedSelection && Regex("""\b(this|it)\b""").containsMatchIn(normalized)) {
                add(MissingInformation("ambiguous-selection", "Choose which selected item you mean.", true))
            }
            if (context.pendingAmbiguities.isNotEmpty()) add(MissingInformation("ambiguity", context.pendingAmbiguities.first().prompt, true))
        }
        val confidence = when {
            required.any(MissingInformation::blocking) -> .35f
            explicitAction && commandType != SmartBoardGoalType.UNKNOWN -> .98f
            normalized.isNotBlank() && commandType != SmartBoardGoalType.UNKNOWN -> .88f
            inferredType != SmartBoardGoalType.UNKNOWN -> .68f
            else -> .25f
        }
        return SmartBoardGoal(
            inferredType,
            context.subject,
            context.selectedElementIds,
            confidence,
            buildSet {
                if (normalized.isNotBlank()) add(GoalEvidence.EXPLICIT_COMMAND)
                if (explicitAction) add(GoalEvidence.EXPLICIT_ACTION)
                if (context.selectedElementIds.isNotEmpty()) add(GoalEvidence.SELECTION)
                if (commandType == SmartBoardGoalType.UNKNOWN && inferredType != SmartBoardGoalType.UNKNOWN) add(GoalEvidence.CONTENT_TYPE)
            },
            required,
            userConfirmed = explicitAction,
        )
    }
}

object SmartBoardUntrustedContentPolicy {
    private val injectionSignals = listOf(
        Regex("""ignore\s+(?:all\s+)?(?:prior|previous|system)\s+(?:rules|instructions)""", RegexOption.IGNORE_CASE),
        Regex("""(?:delete|clear|share|upload)\s+(?:the\s+)?board""", RegexOption.IGNORE_CASE),
        Regex("""reveal\s+(?:secrets?|keys?|system prompt)""", RegexOption.IGNORE_CASE),
        Regex("""enable\s+(?:tools?|permissions?)""", RegexOption.IGNORE_CASE),
    )

    fun containsInjectionSignal(value: String) = injectionSignals.any { it.containsMatchIn(value) }
    fun mark(value: String) = "[UNTRUSTED_BOARD_CONTENT] ${value.replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), " ")}"
    fun maySelectTool(value: String) = false
}

object SmartBoardPermissionPolicy {
    fun authorize(definition: SmartBoardToolDefinition, explicitApproval: Boolean): Result<Unit> = runCatching {
        if (definition.permission != SmartBoardPermissionClass.SAFE_READ_ONLY) {
            require(explicitApproval) { "This action requires explicit approval." }
        }
    }
}

class SmartBoardSessionMemoryManager {
    private val memories = linkedMapOf<String, SmartBoardSessionMemory>()
    fun get(boardId: String) = memories[boardId]
    fun put(memory: SmartBoardSessionMemory) {
        memories[memory.boardId] = memory
        while (memories.size > 12) memories.remove(memories.keys.first())
    }
    fun resolveAmbiguity(boardId: String, ambiguityId: String, value: String, now: Long): SmartBoardSessionMemory {
        val current = get(boardId) ?: SmartBoardSessionMemory.empty(boardId, now)
        return current.copy(resolvedAmbiguities = current.resolvedAmbiguities + (ambiguityId to value), lastUpdatedAt = now).also(::put)
    }
    fun dismiss(boardId: String, recommendationId: String, now: Long): SmartBoardSessionMemory {
        val current = get(boardId) ?: SmartBoardSessionMemory.empty(boardId, now)
        return current.copy(dismissedRecommendationIds = current.dismissedRecommendationIds + recommendationId, lastUpdatedAt = now).also(::put)
    }
    fun record(boardId: String, action: SmartBoardActionHistoryEntry, now: Long): SmartBoardSessionMemory {
        val current = get(boardId) ?: SmartBoardSessionMemory.empty(boardId, now)
        return current.copy(
            completedActionIds = if (action.succeeded) current.completedActionIds + action.actionId else current.completedActionIds,
            recentActions = (current.recentActions + action).takeLast(50),
            lastUpdatedAt = now,
        ).also(::put)
    }
}

fun recommendationId(toolId: String, sources: List<String>) = buildString {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$toolId|${sources.sorted().joinToString(",")}".toByteArray())
    append(toolId)
    append(':')
    append(digest.take(6).joinToString("") { "%02x".format(it) })
}

private fun SmartBoardElement.contextSummary(): String = when (this) {
    is StrokeElement -> "Unrecognized ${tool.name.lowercase()} stroke with ${points.size} points"
    is ShapeElement -> "Recognized ${shapeType.name.lowercase().replace('_', ' ')} with ${points.size} vector points"
    is MathExpressionElement -> displayLatex
    is PhysicsExpressionElement -> displaySource
    is TextElement -> text
    is TableElement -> "Table columns ${columnHeaders.joinToString()}; ${rows.take(8).joinToString("; ") { it.joinToString(", ") }}"
    is ImageElement -> "Imported image ${pixelWidth} by ${pixelHeight}; pixels omitted from structured context"
    is ActionResultElement -> "$title: ${exact ?: approximate.orEmpty()} ${details.take(3).joinToString("; ")}"
    is PhysicsResultElement -> "$title: ${numericalResult?.toString().orEmpty()} ${resultUnitSymbol.orEmpty()} ${steps.take(3).joinToString("; ") { it.expression }}"
    is GraphConfigurationElement -> "Graph ${graphKind.name}: ${expressions.joinToString()}"
    is SolutionSequenceElement -> "Problem $problemExpression; ${steps.joinToString("; ") { "${it.expression} [${it.status}]" }}"
    is PhysicsDiagramElement -> "${diagramType.name} diagram; ${inferredRelations.joinToString("; ") { it.description }}"
    is ChemistryExpressionElement -> normalizedChemicalNotation ?: rawText
    is EnglishTextElement -> correctedText ?: rawText
    is BiologyContentElement -> recognizedText.orEmpty() + " ${detectedLabels.joinToString { it.text }}"
    is ChemistryResultElement -> "$title: ${balancedEquation ?: numericalResult?.toString().orEmpty()}"
    is EnglishResultElement -> "$title: ${suggestedText ?: originalText}"
    is BiologyResultElement -> "$title: ${explanation.orEmpty()} ${studySummary.take(3).joinToString()}"
}

private fun SmartBoardElement.sourceIds(): List<String> = when (this) {
    is MathExpressionElement -> sourceStrokeIds
    is ShapeElement -> sourceStrokeIds
    is PhysicsExpressionElement -> sourceStrokeIds
    is ActionResultElement -> sourceElementIds
    is PhysicsResultElement -> sourceElementIds
    is GraphConfigurationElement -> sourceElementIds
    is SolutionSequenceElement -> sourceRegionIds
    is PhysicsDiagramElement -> sourceStrokeIds
    is ChemistryExpressionElement -> sourceStrokeIds
    is EnglishTextElement -> sourceStrokeIds
    is BiologyContentElement -> sourceStrokeIds
    is ChemistryResultElement -> sourceElementIds
    is EnglishResultElement -> sourceElementIds
    is BiologyResultElement -> sourceElementIds
    is TableElement -> sourceElementIds
    else -> emptyList()
}

private fun SmartBoardElement.sourceTrust() = when (this) {
    is ImageElement -> SmartBoardSourceTrust.IMPORTED_UNTRUSTED
    is MathExpressionElement, is PhysicsExpressionElement, is TextElement, is StrokeElement, is ShapeElement -> SmartBoardSourceTrust.RECOGNIZED_UNTRUSTED
    is TableElement -> if (sourceElementIds.isEmpty()) SmartBoardSourceTrust.USER_CONFIRMED else SmartBoardSourceTrust.ENGINE_DERIVED
    is ActionResultElement, is PhysicsResultElement, is GraphConfigurationElement, is SolutionSequenceElement,
    is ChemistryResultElement, is EnglishResultElement, is BiologyResultElement -> SmartBoardSourceTrust.ENGINE_DERIVED
    is PhysicsDiagramElement -> SmartBoardSourceTrust.INFERRED
    is ChemistryExpressionElement, is EnglishTextElement, is BiologyContentElement -> SmartBoardSourceTrust.RECOGNIZED_UNTRUSTED
}

private fun SmartBoardElement.contextConfidence(): Float? = when (this) {
    is MathExpressionElement -> recognitionConfidence
    is ShapeElement -> recognitionConfidence
    is PhysicsExpressionElement -> recognitionConfidence
    is PhysicsDiagramElement -> confidence
    is ChemistryExpressionElement -> subjectClassification.confidence
    is EnglishTextElement -> subjectClassification.confidence
    is BiologyContentElement -> subjectClassification.confidence
    else -> null
}

private fun defaultCapabilities(subject: SmartBoardSubject): Set<SmartBoardCapability> = buildSet {
    addAll(
        setOf(
            SmartBoardCapability.RECOGNITION, SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D,
            SmartBoardCapability.GEOMETRY_2D, SmartBoardCapability.GEOMETRY_3D,
            SmartBoardCapability.WORK_VERIFICATION, SmartBoardCapability.LOCAL_TUTOR,
            SmartBoardCapability.PHOTO_RECOGNITION,
        ),
    )
    when (subject) {
        SmartBoardSubject.MATHEMATICS -> addAll(setOf(SmartBoardCapability.CAS, SmartBoardCapability.SOLVER, SmartBoardCapability.STATISTICS))
        SmartBoardSubject.PHYSICS -> addAll(
            setOf(
                SmartBoardCapability.CAS, SmartBoardCapability.SOLVER, SmartBoardCapability.STATISTICS,
                SmartBoardCapability.PHYSICS_FORMULAS, SmartBoardCapability.PHYSICS_UNITS,
                SmartBoardCapability.PHYSICS_DIMENSIONS, SmartBoardCapability.PHYSICS_NUMERICAL,
                SmartBoardCapability.PHYSICS_VISUALIZATION,
            ),
        )
        else -> Unit
    }
}
