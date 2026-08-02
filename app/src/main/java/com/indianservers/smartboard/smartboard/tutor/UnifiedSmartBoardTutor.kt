package com.indianservers.smartboard.smartboard.tutor

import com.indianservers.smartboard.biology.data.BundledBiologyCatalogue
import com.indianservers.smartboard.biology.model.BiologyLearningLevel
import com.indianservers.smartboard.biology.repository.BiologyRepository
import com.indianservers.smartboard.biology.repository.OfflineBiologyRepository
import com.indianservers.smartboard.chemistry.data.BundledElementData
import com.indianservers.smartboard.smartboard.integration.SmartBoardWorkVerificationAdapter
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardActionHistoryEntry
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardAmbiguity
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardCapability
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardEngineReference
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardIntelligenceLevel
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardLearnerContext
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardServiceAvailability
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsVerificationStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SolutionStepStatus
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.physics.PhysicsTutorEngine
import com.indianservers.smartboard.smartboard.physics.PhysicsMisconceptionDetector
import com.indianservers.smartboard.smartboard.physics.PhysicsWorkVerifier
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.withTimeout

enum class UnifiedTutorMode {
    ASK,
    HINT,
    NEXT_STEP,
    CHECK_MY_WORK,
    FIND_MY_MISTAKE,
    FULL_SOLUTION,
    EXPLAIN_CONCEPT,
    EXPLAIN_VISUALLY,
    ALTERNATIVE_METHOD,
    PRACTICE,
    EXAM_STYLE,
    CONCISE,
    DETAILED,
}

enum class SmartBoardTutorVerificationStatus {
    VERIFIED,
    VERIFIED_WITH_CONDITIONS,
    NUMERICALLY_VERIFIED,
    RULE_VERIFIED,
    MODEL_REFERENCE_VERIFIED,
    PARTIALLY_VERIFIED,
    AI_ONLY,
    INCONCLUSIVE,
    UNSUPPORTED,
    FAILED,
}

enum class SmartBoardWorkStatus { CORRECT, INCORRECT, PARTIAL, INCONCLUSIVE, UNSUPPORTED }
enum class SmartBoardFinalAnswerStatus { CORRECT, INCORRECT, MISSING, NOT_CHECKED }
enum class SmartBoardVerifiedStepStatus { VALID, INVALID, UNCERTAIN, BLOCKED_BY_EARLIER_STEP }
enum class SmartBoardSequenceType {
    MATHEMATICAL_DERIVATION,
    PHYSICS_NUMERICAL_SOLUTION,
    CHEMISTRY_BALANCING,
    CHEMISTRY_STOICHIOMETRY,
    ENGLISH_CORRECTION,
    BIOLOGY_LABELLING,
    BIOLOGY_GENETICS,
    GENERAL_EXPLANATION,
}
enum class SmartBoardSequenceStatus { ACTIVE, NEEDS_CORRECTION, COMPLETE, BLOCKED, UNSUPPORTED }
enum class SmartBoardHintType { CONCEPT_REMINDER, DIRECTIONAL_CUE, RULE, PARTIAL_SETUP, NEXT_ACTION, INTERMEDIATE_RESULT, WORKED_GUIDANCE }
enum class SmartBoardTutorContentKind {
    TEXT, FORMULA, CHEMICAL_EQUATION, CORRECTION_COMPARISON, DIAGRAM_LABEL, TABLE, HINT, STEP,
    WARNING, VISUALIZATION_REFERENCE, PRACTICE_QUESTION,
}
enum class EnglishFindingType { ERROR, LIKELY_ERROR, STYLE_SUGGESTION, ALTERNATIVE_WORDING, AMBIGUOUS, ACCEPTABLE_VARIANT }
enum class SmartBoardTutorToolPermission { READ_ONLY, REVERSIBLE_WRITE, SENSITIVE_CONFIRMATION }

data class SmartBoardTutorElement(
    val id: String,
    val subject: SmartBoardSubject,
    val kind: String,
    val content: String,
    val userConfirmed: Boolean,
    val recognitionConfidence: Float?,
    val deterministicResult: Boolean,
)

data class SmartBoardTutorMessage(
    val id: String,
    val role: String,
    val text: String,
    val subject: SmartBoardSubject,
    val verificationStatus: SmartBoardTutorVerificationStatus?,
    val referencedElementIds: List<String>,
    val createdAt: Long,
) {
    init {
        require(role in setOf("user", "tutor"))
        require(text.length <= 4_000)
        require(referencedElementIds.size <= 32)
    }
}

data class SmartBoardTutorContext(
    val boardId: String,
    val boardSubjectMode: SmartBoardSubject,
    val primarySubject: SmartBoardSubject?,
    val supportingSubjects: Set<SmartBoardSubject>,
    val selectedElementIds: List<String>,
    val activeProblemId: String?,
    val activeConceptId: String?,
    val selectedElements: List<SmartBoardTutorElement>,
    val relationships: List<SmartBoardRelationship>,
    val recentActions: List<SmartBoardActionHistoryEntry>,
    val priorTutorMessages: List<SmartBoardTutorMessage>,
    val learnerContext: SmartBoardLearnerContext?,
    val availableCapabilities: Set<SmartBoardCapability>,
    val unresolvedAmbiguities: List<SmartBoardAmbiguity>,
    val serviceAvailability: SmartBoardServiceAvailability,
    val contextFingerprint: String,
)

data class SmartBoardSubjectTutorContext(
    val subject: SmartBoardSubject,
    val supportingSubjects: Set<SmartBoardSubject>,
    val elements: List<SmartBoardTutorElement>,
    val conceptId: String?,
    val capabilities: Set<SmartBoardCapability>,
    val deterministicOnly: Boolean,
)

data class SmartBoardWorkSequence(
    val id: String,
    val primarySubject: SmartBoardSubject,
    val supportingSubjects: Set<SmartBoardSubject>,
    val problemElementIds: List<String>,
    val orderedStepIds: List<String>,
    val currentStepId: String?,
    val finalAnswerElementId: String?,
    val conceptId: String?,
    val sequenceType: SmartBoardSequenceType,
    val status: SmartBoardSequenceStatus,
)

data class SmartBoardVerifiedStep(
    val stepId: String,
    val status: SmartBoardVerifiedStepStatus,
    val explanation: String,
    val evidence: String?,
    val engine: SmartBoardEngineReference?,
    val confidence: Float?,
    val recognitionIssue: String?,
    val conditions: List<String>,
    val suggestedCorrectionType: String?,
)

data class SmartBoardMisconception(
    val id: String,
    val subject: SmartBoardSubject,
    val code: String,
    val title: String,
    val description: String,
    val confidence: Float?,
    val evidenceElementIds: List<String>,
    val conceptId: String?,
    val correctiveHint: String?,
    val persistentCandidate: Boolean,
)

data class SmartBoardWorkVerificationResult(
    val sequenceId: String,
    val primarySubject: SmartBoardSubject,
    val overallStatus: SmartBoardWorkStatus,
    val stepResults: List<SmartBoardVerifiedStep>,
    val firstInvalidStepId: String?,
    val finalAnswerStatus: SmartBoardFinalAnswerStatus,
    val verificationSource: List<SmartBoardEngineReference>,
    val misconceptions: List<SmartBoardMisconception>,
    val assumptions: List<String>,
    val warnings: List<String>,
)

data class SmartBoardHint(
    val id: String,
    val subject: SmartBoardSubject,
    val problemId: String,
    val level: Int,
    val type: SmartBoardHintType,
    val content: String,
    val referencedElementIds: List<String>,
    val verificationStatus: SmartBoardTutorVerificationStatus,
    val revealsFinalAnswer: Boolean,
) {
    init {
        require(level in 1..7)
        require(level >= 7 || !revealsFinalAnswer)
    }
}

data class SmartBoardVisualRecommendation(
    val id: String,
    val subject: SmartBoardSubject,
    val title: String,
    val reason: String,
    val capability: SmartBoardCapability,
    val sourceElementIds: List<String>,
    val targetModule: String?,
    val confidence: Float?,
    val userConfirmationRequired: Boolean,
)

data class SmartBoardTutorContentBlock(
    val kind: SmartBoardTutorContentKind,
    val title: String?,
    val content: String,
)

data class SmartBoardSuggestedPrompt(val id: String, val label: String, val mode: UnifiedTutorMode)

data class SmartBoardTutorToolDefinition(
    val id: String,
    val title: String,
    val subject: SmartBoardSubject?,
    val capability: SmartBoardCapability?,
    val permission: SmartBoardTutorToolPermission,
    val requiresSelection: Boolean,
    val engine: SmartBoardEngineReference?,
)

data class SmartBoardTutorToolCall(
    val id: String,
    val toolId: String,
    val boardId: String,
    val subject: SmartBoardSubject,
    val sourceElementIds: List<String>,
    val arguments: Map<String, String>,
    val explicitUserApproval: Boolean,
)

data class SmartBoardTutorToolResult(
    val callId: String,
    val toolId: String,
    val success: Boolean,
    val message: String,
    val verificationStatus: SmartBoardTutorVerificationStatus,
    val referencedElementIds: List<String> = emptyList(),
    val moduleRoute: String? = null,
)

data class UnifiedTutorRequest(
    val context: SmartBoardTutorContext,
    val mode: UnifiedTutorMode,
    val message: String,
    val hintLevel: Int = 1,
)

data class UnifiedTutorResponse(
    val id: String,
    val subject: SmartBoardSubject,
    val supportingSubjects: Set<SmartBoardSubject>,
    val mode: UnifiedTutorMode,
    val message: String,
    val structuredContent: List<SmartBoardTutorContentBlock>,
    val referencedElementIds: List<String>,
    val toolResults: List<SmartBoardTutorToolResult>,
    val verificationStatus: SmartBoardTutorVerificationStatus,
    val warnings: List<String>,
    val suggestedFollowUpActions: List<SmartBoardSuggestedPrompt>,
    val createdAt: Long,
    val verification: SmartBoardWorkVerificationResult? = null,
    val hint: SmartBoardHint? = null,
    val visuals: List<SmartBoardVisualRecommendation> = emptyList(),
)

data class SmartBoardTutorConversation(
    val boardId: String,
    val activeSubject: SmartBoardSubject?,
    val activeMode: UnifiedTutorMode,
    val activeProblemId: String?,
    val messages: List<SmartBoardTutorMessage>,
    val shownHintLevels: Map<String, Int>,
    val updatedAt: Long,
) {
    init {
        require(messages.size <= 100)
        require(shownHintLevels.values.all { it in 1..7 })
    }

    companion object {
        fun empty(boardId: String, now: Long = 0L) = SmartBoardTutorConversation(
            boardId, null, UnifiedTutorMode.ASK, null, emptyList(), emptyMap(), now,
        )
    }
}

interface UnifiedSmartBoardTutor {
    suspend fun respond(request: UnifiedTutorRequest): UnifiedTutorResponse
    suspend fun suggestPrompts(context: SmartBoardTutorContext): List<SmartBoardSuggestedPrompt>
    suspend fun verifyResponse(response: UnifiedTutorResponse): SmartBoardTutorVerificationStatus
}

interface SmartBoardSubjectTutorHandler {
    val subject: SmartBoardSubject
    suspend fun buildSubjectContext(context: SmartBoardTutorContext): SmartBoardSubjectTutorContext
    suspend fun suggestedModes(context: SmartBoardSubjectTutorContext): List<UnifiedTutorMode>
    suspend fun availableTools(context: SmartBoardSubjectTutorContext): List<SmartBoardTutorToolDefinition>
    suspend fun verifyWork(context: SmartBoardSubjectTutorContext): SmartBoardWorkVerificationResult
    suspend fun detectMisconceptions(
        context: SmartBoardSubjectTutorContext,
        verification: SmartBoardWorkVerificationResult,
    ): List<SmartBoardMisconception>
    suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext): List<SmartBoardVisualRecommendation>
    suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse
}

interface SmartBoardTutorToolRegistry {
    fun availableTools(context: SmartBoardTutorContext): List<SmartBoardTutorToolDefinition>
    suspend fun execute(call: SmartBoardTutorToolCall, context: SmartBoardTutorContext): SmartBoardTutorToolResult
}

object SmartBoardTutorSecurity {
    private val instructionLike = Regex(
        """(?i)\b(ignore previous|reveal (?:api|secret)|enable hidden|send all|delete (?:the )?board|system prompt)\b""",
    )

    fun safeMessage(raw: String): String = raw.trim().take(2_000)

    fun containsInstructionLikeBoardContent(value: String): Boolean = instructionLike.containsMatchIn(value)

    fun validateToolCall(call: SmartBoardTutorToolCall, context: SmartBoardTutorContext, definition: SmartBoardTutorToolDefinition): String? {
        if (call.boardId != context.boardId) return "Tool call does not belong to this Board."
        if (definition.subject != null && definition.subject != call.subject) return "Tool is not available for this subject."
        if (call.sourceElementIds.any { it !in context.selectedElementIds }) return "Tool access is restricted to selected content."
        if (definition.requiresSelection && call.sourceElementIds.isEmpty()) return "Select content before using this tool."
        if (definition.permission != SmartBoardTutorToolPermission.READ_ONLY && !call.explicitUserApproval) {
            return "This action requires explicit confirmation."
        }
        return null
    }
}

class SmartBoardTutorContextBuilder(
    private val maximumElements: Int = 16,
    private val maximumCharacters: Int = 6_000,
) {
    init {
        require(maximumElements in 1..32)
        require(maximumCharacters in 1_000..12_000)
    }

    fun build(
        document: SmartBoardDocument,
        selection: Set<String>,
        messages: List<SmartBoardTutorMessage> = emptyList(),
        activeProblemId: String? = null,
        recentActions: List<SmartBoardActionHistoryEntry> = emptyList(),
        learnerContext: SmartBoardLearnerContext? = null,
        availability: SmartBoardServiceAvailability = SmartBoardServiceAvailability(
            SmartBoardIntelligenceLevel.DETERMINISTIC,
            recognitionAvailable = true,
            aiAvailable = false,
        ),
    ): SmartBoardTutorContext {
        val selected = document.elements.filter { it.id in selection && !it.hidden }.take(maximumElements)
        var used = 0
        val elements = selected.mapNotNull { element ->
            val raw = element.tutorContent().take(1_200)
            if (used + raw.length > maximumCharacters) return@mapNotNull null
            used += raw.length
            val classification = document.elementSubjectClassifications[element.id] ?: element.subjectClassification
            SmartBoardTutorElement(
                element.id,
                classification?.primarySubject ?: element.intrinsicSubject(),
                element::class.simpleName ?: "Board element",
                raw,
                classification?.userConfirmed == true || element.intrinsicSubject() != SmartBoardSubject.GENERAL,
                element.recognitionConfidence(),
                element.isDeterministicResult(),
            )
        }
        val ownership = determineOwnership(
            document.subjectMode.selection,
            document.subjectMode.locked || document.subjectMode.userSelected,
            elements,
        )
        val ids = elements.map(SmartBoardTutorElement::id)
        val relationships = document.relationships.filter { relationship ->
            relationship.elementIds.any(ids::contains)
        }.map { it.copy(elementIds = it.elementIds.filter(ids::contains)) }.filter { it.elementIds.isNotEmpty() }
        val capabilities = defaultTutorCapabilities(ownership.first, availability)
        val conceptId = ids.asSequence().mapNotNull(document.elementConcepts::get).firstOrNull()?.conceptId
        val fingerprint = fingerprint(
            document.id,
            ownership.first?.name.orEmpty(),
            elements.joinToString("\u001f") { "${it.id}:${it.content}:${it.userConfirmed}" },
        )
        return SmartBoardTutorContext(
            boardId = document.id,
            boardSubjectMode = document.subjectMode.selection,
            primarySubject = ownership.first,
            supportingSubjects = ownership.second,
            selectedElementIds = ids,
            activeProblemId = activeProblemId,
            activeConceptId = conceptId,
            selectedElements = elements,
            relationships = relationships,
            recentActions = recentActions.takeLast(20),
            priorTutorMessages = messages.takeLast(20),
            learnerContext = learnerContext,
            availableCapabilities = capabilities,
            unresolvedAmbiguities = emptyList(),
            serviceAvailability = availability,
            contextFingerprint = fingerprint,
        )
    }

    private fun determineOwnership(
        boardMode: SmartBoardSubject,
        boardModeConfirmed: Boolean,
        elements: List<SmartBoardTutorElement>,
    ): Pair<SmartBoardSubject?, Set<SmartBoardSubject>> {
        val subjects = elements.map { it.subject }.filterNot { it in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL) }
        if (subjects.isEmpty()) {
            return boardMode.takeUnless { it in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL) } to emptySet()
        }
        val counts = subjects.groupingBy { it }.eachCount()
        val domainPriority = listOf(
            SmartBoardSubject.PHYSICS,
            SmartBoardSubject.CHEMISTRY,
            SmartBoardSubject.BIOLOGY,
            SmartBoardSubject.MATHEMATICS,
            SmartBoardSubject.ENGLISH,
        )
        val locked = boardMode.takeIf { boardModeConfirmed }
            ?.takeUnless { it in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL) }
        val primary = locked?.takeIf(counts::containsKey)
            ?: domainPriority.filter(counts::containsKey).maxByOrNull { counts.getValue(it) }
            ?: counts.maxByOrNull(Map.Entry<SmartBoardSubject, Int>::value)?.key
        return primary to subjects.filterNot { it == primary }.toSet()
    }

    private fun fingerprint(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u001e").toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

class DefaultSmartBoardTutorToolRegistry(
    private val handlers: Map<SmartBoardSubject, SmartBoardSubjectTutorHandler>,
) : SmartBoardTutorToolRegistry {
    private val generic = listOf(
        SmartBoardTutorToolDefinition("inspect-selection", "Inspect selected content", null, null, SmartBoardTutorToolPermission.READ_ONLY, true, null),
        SmartBoardTutorToolDefinition("highlight-selection", "Highlight referenced content", null, null, SmartBoardTutorToolPermission.REVERSIBLE_WRITE, true, null),
        SmartBoardTutorToolDefinition("insert-tutor-card", "Insert tutor card", null, null, SmartBoardTutorToolPermission.REVERSIBLE_WRITE, true, null),
    )

    override fun availableTools(context: SmartBoardTutorContext): List<SmartBoardTutorToolDefinition> {
        val handler = context.primarySubject?.let(handlers::get) ?: return generic
        val subjectContext = SmartBoardSubjectTutorContext(
            handler.subject,
            context.supportingSubjects,
            context.selectedElements,
            context.activeConceptId,
            context.availableCapabilities,
            !context.serviceAvailability.aiAvailable,
        )
        return generic + runBlockingAvailableTools(handler, subjectContext).filter { definition ->
            definition.capability == null || definition.capability in context.availableCapabilities
        }
    }

    override suspend fun execute(call: SmartBoardTutorToolCall, context: SmartBoardTutorContext): SmartBoardTutorToolResult {
        val definition = availableTools(context).firstOrNull { it.id == call.toolId }
            ?: return failure(call, "Tool is not allowlisted.")
        SmartBoardTutorSecurity.validateToolCall(call, context, definition)?.let { return failure(call, it) }
        return runCatching {
            withTimeout(8_000) {
                when (call.toolId) {
                    "inspect-selection" -> SmartBoardTutorToolResult(
                        call.id, call.toolId, true, "${call.sourceElementIds.size} selected element(s) inspected.",
                        SmartBoardTutorVerificationStatus.RULE_VERIFIED, call.sourceElementIds,
                    )
                    "highlight-selection", "insert-tutor-card" -> SmartBoardTutorToolResult(
                        call.id, call.toolId, true, "Action approved for the selected content.",
                        SmartBoardTutorVerificationStatus.RULE_VERIFIED, call.sourceElementIds,
                    )
                    "math-graph", "physics-graph" -> SmartBoardTutorToolResult(
                        call.id, call.toolId, true, "Opening the existing graph workspace.",
                        SmartBoardTutorVerificationStatus.VERIFIED_WITH_CONDITIONS, call.sourceElementIds, "graph2d",
                    )
                    "math-verify", "physics-verify", "biology-labels" -> verificationTool(call, context)
                    "chemistry-elements", "english-inspect", "biology-catalogue" -> subjectInspectionTool(call, context)
                    else -> failure(call, "Tool is registered but has no production executor.")
                }
            }
        }.getOrElse { failure(call, "The tool could not complete safely.") }
    }

    private suspend fun verificationTool(
        call: SmartBoardTutorToolCall,
        context: SmartBoardTutorContext,
    ): SmartBoardTutorToolResult {
        val handler = handlers[call.subject] ?: return failure(call, "Subject handler is unavailable.")
        val verification = handler.verifyWork(handler.buildSubjectContext(context))
        return SmartBoardTutorToolResult(
            call.id,
            call.toolId,
            verification.overallStatus != SmartBoardWorkStatus.UNSUPPORTED,
            verification.firstInvalidStepId?.let { "First invalid step: $it." }
                ?: verification.warnings.firstOrNull()
                ?: "Selected work was checked by the registered subject verifier.",
            statusFor(verification),
            call.sourceElementIds,
        )
    }

    private suspend fun subjectInspectionTool(
        call: SmartBoardTutorToolCall,
        context: SmartBoardTutorContext,
    ): SmartBoardTutorToolResult {
        val handler = handlers[call.subject] ?: return failure(call, "Subject handler is unavailable.")
        val mode = if (call.toolId == "biology-catalogue") UnifiedTutorMode.EXPLAIN_CONCEPT else UnifiedTutorMode.HINT
        val response = handler.respond(
            UnifiedTutorRequest(context, mode, call.arguments["query"].orEmpty(), 1),
            handler.buildSubjectContext(context),
        )
        return SmartBoardTutorToolResult(
            call.id,
            call.toolId,
            response.verificationStatus !in setOf(SmartBoardTutorVerificationStatus.FAILED, SmartBoardTutorVerificationStatus.UNSUPPORTED),
            response.message,
            response.verificationStatus,
            call.sourceElementIds,
        )
    }

    private fun runBlockingAvailableTools(
        handler: SmartBoardSubjectTutorHandler,
        context: SmartBoardSubjectTutorContext,
    ): List<SmartBoardTutorToolDefinition> = when (handler) {
        is BaseSmartBoardTutorHandler -> handler.availableToolsNow(context)
        else -> emptyList()
    }

    private fun failure(call: SmartBoardTutorToolCall, message: String) = SmartBoardTutorToolResult(
        call.id, call.toolId, false, message, SmartBoardTutorVerificationStatus.FAILED,
    )
}

abstract class BaseSmartBoardTutorHandler : SmartBoardSubjectTutorHandler {
    protected val localEngine = SmartBoardEngineReference("smart-board-local-rules", true, false, true)

    override suspend fun buildSubjectContext(context: SmartBoardTutorContext) = SmartBoardSubjectTutorContext(
        subject, context.supportingSubjects, context.selectedElements, context.activeConceptId,
        context.availableCapabilities, !context.serviceAvailability.aiAvailable,
    )

    final override suspend fun availableTools(context: SmartBoardSubjectTutorContext) = availableToolsNow(context)
    open fun availableToolsNow(context: SmartBoardSubjectTutorContext): List<SmartBoardTutorToolDefinition> = emptyList()
    override suspend fun detectMisconceptions(
        context: SmartBoardSubjectTutorContext,
        verification: SmartBoardWorkVerificationResult,
    ) = verification.misconceptions
    override suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext) = emptyList<SmartBoardVisualRecommendation>()

    protected fun response(
        request: UnifiedTutorRequest,
        status: SmartBoardTutorVerificationStatus,
        message: String,
        blocks: List<SmartBoardTutorContentBlock> = listOf(SmartBoardTutorContentBlock(SmartBoardTutorContentKind.TEXT, null, message)),
        warnings: List<String> = emptyList(),
        verification: SmartBoardWorkVerificationResult? = null,
        hint: SmartBoardHint? = null,
        visuals: List<SmartBoardVisualRecommendation> = emptyList(),
    ) = UnifiedTutorResponse(
        UUID.randomUUID().toString(), subject, request.context.supportingSubjects, request.mode, message, blocks,
        request.context.selectedElementIds, emptyList(), status, warnings,
        listOf(
            SmartBoardSuggestedPrompt("next", "Next step only", UnifiedTutorMode.NEXT_STEP),
            SmartBoardSuggestedPrompt("check", "Check my work", UnifiedTutorMode.CHECK_MY_WORK),
            SmartBoardSuggestedPrompt("visual", "Explain visually", UnifiedTutorMode.EXPLAIN_VISUALLY),
        ),
        System.currentTimeMillis(), verification, hint, visuals,
    )

    protected fun unsupported(request: UnifiedTutorRequest, capability: String) = response(
        request,
        SmartBoardTutorVerificationStatus.UNSUPPORTED,
        "$capability is not available from a verified local engine.",
        warnings = listOf("The tutor did not invent or independently calculate this result."),
    )

    protected fun hint(
        request: UnifiedTutorRequest,
        content: String,
        verified: SmartBoardTutorVerificationStatus,
    ): SmartBoardHint {
        val level = request.hintLevel.coerceIn(1, 7)
        return SmartBoardHint(
            UUID.randomUUID().toString(), subject, request.context.activeProblemId ?: request.context.contextFingerprint,
            level, SmartBoardHintType.entries[level - 1], content, request.context.selectedElementIds,
            verified, revealsFinalAnswer = level == 7,
        )
    }
}

class MathematicsSmartBoardTutorHandler(
    private val tutor: SmartBoardTutorEngine = SmartBoardTutorEngine(),
    private val verifier: SmartBoardWorkVerificationAdapter = SmartBoardWorkVerificationAdapter(),
) : BaseSmartBoardTutorHandler() {
    override val subject = SmartBoardSubject.MATHEMATICS
    private val engine = SmartBoardEngineReference("trusted-math-kernel", true, true, true)

    override suspend fun suggestedModes(context: SmartBoardSubjectTutorContext) = UnifiedTutorMode.entries

    override fun availableToolsNow(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardTutorToolDefinition("math-verify", "Verify mathematical work", subject, SmartBoardCapability.WORK_VERIFICATION, SmartBoardTutorToolPermission.READ_ONLY, true, engine),
        SmartBoardTutorToolDefinition("math-graph", "Open graph", subject, SmartBoardCapability.GRAPH_2D, SmartBoardTutorToolPermission.SENSITIVE_CONFIRMATION, true, engine),
    )

    override suspend fun verifyWork(context: SmartBoardSubjectTutorContext): SmartBoardWorkVerificationResult {
        val lines = context.elements.flatMap { it.content.lines() }.map(String::trim).filter(String::isNotBlank)
        if (lines.isEmpty()) return unsupportedVerification(subject, "No mathematical steps were selected.")
        val checked = verifier.verify(lines.map { it to 1f })
        var blocked = false
        val steps = checked.steps.map { step ->
            val mapped = when {
                blocked -> SmartBoardVerifiedStepStatus.BLOCKED_BY_EARLIER_STEP
                step.status == SolutionStepStatus.INVALID -> SmartBoardVerifiedStepStatus.INVALID
                step.status == SolutionStepStatus.VALID -> SmartBoardVerifiedStepStatus.VALID
                else -> SmartBoardVerifiedStepStatus.UNCERTAIN
            }
            if (mapped == SmartBoardVerifiedStepStatus.INVALID) blocked = true
            SmartBoardVerifiedStep(
                step.id, mapped, step.feedback.orEmpty(), step.expression, engine, step.confidence, null,
                emptyList(), if (mapped == SmartBoardVerifiedStepStatus.INVALID) "equivalent transformation" else null,
            )
        }
        val invalid = steps.firstOrNull { it.status == SmartBoardVerifiedStepStatus.INVALID }
        val misconceptionEvidence = invalid?.let { bad ->
            val index = steps.indexOf(bad)
            if (index > 0) SmartBoardMisconceptionAnalyzer.assess(
                lines[index - 1], lines[index], SolutionStepStatus.INVALID, 1f,
            ) else emptyList()
        }.orEmpty()
        return SmartBoardWorkVerificationResult(
            "math-${context.elements.joinToString { it.id }}", subject,
            when {
                invalid != null -> SmartBoardWorkStatus.INCORRECT
                steps.any { it.status == SmartBoardVerifiedStepStatus.UNCERTAIN } -> SmartBoardWorkStatus.INCONCLUSIVE
                else -> SmartBoardWorkStatus.CORRECT
            },
            steps, invalid?.stepId,
            if (invalid != null) SmartBoardFinalAnswerStatus.INCORRECT else SmartBoardFinalAnswerStatus.CORRECT,
            listOf(engine),
            misconceptionEvidence.map {
                SmartBoardMisconception(
                    "math-${it.kind.name.lowercase()}", subject, it.kind.name, it.kind.name.lowercase().replace('_', ' '),
                    it.explanation, .8f, context.elements.map(SmartBoardTutorElement::id), context.conceptId,
                    it.explanation, persistentCandidate = false,
                )
            },
            emptyList(), emptyList(),
        )
    }

    override suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardVisualRecommendation(
            "math-graph", subject, "Function graph", "Inspect the selected relationship visually.",
            SmartBoardCapability.GRAPH_2D, context.elements.map { it.id }, "graph2d", .85f, true,
        ),
    )

    override suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse {
        val source = context.elements.joinToString("\n", transform = SmartBoardTutorElement::content)
        if (source.isBlank()) return unsupported(request, "Mathematics tutoring")
        if (request.mode in setOf(UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE)) {
            val verification = verifyWork(context)
            val first = verification.stepResults.firstOrNull { it.status == SmartBoardVerifiedStepStatus.INVALID }
            val message = first?.let { "First unsupported step: ${it.evidence}. ${it.explanation}" } ?: "No invalid supported step was found."
            return response(request, statusFor(verification), message, verification = verification)
        }
        if (request.mode == UnifiedTutorMode.EXPLAIN_VISUALLY) {
            return response(
                request, SmartBoardTutorVerificationStatus.VERIFIED_WITH_CONDITIONS,
                "Use the linked graph to test how the expression changes.", visuals = recommendVisuals(context),
            )
        }
        val oldMode = when (request.mode) {
            UnifiedTutorMode.NEXT_STEP -> SmartBoardTutorMode.NEXT_STEP
            UnifiedTutorMode.FULL_SOLUTION -> SmartBoardTutorMode.FULL_SOLUTION
            UnifiedTutorMode.ALTERNATIVE_METHOD -> SmartBoardTutorMode.ALTERNATIVE_METHOD
            UnifiedTutorMode.HINT -> SmartBoardTutorMode.HINT
            else -> SmartBoardTutorMode.CONCEPT
        }
        val existing = tutor.respond(SmartBoardTutorRequest(source, mode = oldMode, hintLevel = request.hintLevel))
        val hint = if (request.mode in setOf(UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP)) {
            hint(request, existing.content.firstOrNull().orEmpty(), if (existing.verified) SmartBoardTutorVerificationStatus.VERIFIED else SmartBoardTutorVerificationStatus.INCONCLUSIVE)
        } else null
        return response(
            request,
            if (existing.verified) SmartBoardTutorVerificationStatus.VERIFIED else SmartBoardTutorVerificationStatus.INCONCLUSIVE,
            existing.content.firstOrNull() ?: existing.title,
            existing.content.map { SmartBoardTutorContentBlock(SmartBoardTutorContentKind.STEP, existing.title, it) },
            existing.warnings,
            hint = hint,
        )
    }
}

class PhysicsSmartBoardTutorHandler(
    private val tutor: PhysicsTutorEngine = PhysicsTutorEngine(),
    private val verifier: PhysicsWorkVerifier = PhysicsWorkVerifier(),
) : BaseSmartBoardTutorHandler() {
    override val subject = SmartBoardSubject.PHYSICS
    private val engine = SmartBoardEngineReference("physics-smart-board-engine", true, true, true)

    override suspend fun suggestedModes(context: SmartBoardSubjectTutorContext) = listOf(
        UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP, UnifiedTutorMode.CHECK_MY_WORK,
        UnifiedTutorMode.FIND_MY_MISTAKE, UnifiedTutorMode.EXPLAIN_CONCEPT, UnifiedTutorMode.EXPLAIN_VISUALLY,
    )

    override fun availableToolsNow(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardTutorToolDefinition("physics-verify", "Verify units and dimensions", subject, SmartBoardCapability.PHYSICS_DIMENSIONS, SmartBoardTutorToolPermission.READ_ONLY, true, engine),
        SmartBoardTutorToolDefinition("physics-graph", "Open motion graph", subject, SmartBoardCapability.GRAPH_2D, SmartBoardTutorToolPermission.SENSITIVE_CONFIRMATION, true, engine),
    )

    override suspend fun verifyWork(context: SmartBoardSubjectTutorContext): SmartBoardWorkVerificationResult {
        val result = verifier.verify(context.elements.joinToString("\n", transform = SmartBoardTutorElement::content))
        var blocked = false
        val steps = result.steps.mapIndexed { index, step ->
            val mapped = when {
                blocked -> SmartBoardVerifiedStepStatus.BLOCKED_BY_EARLIER_STEP
                step.status == PhysicsVerificationStatus.INVALID -> SmartBoardVerifiedStepStatus.INVALID
                step.status == PhysicsVerificationStatus.VALID -> SmartBoardVerifiedStepStatus.VALID
                else -> SmartBoardVerifiedStepStatus.UNCERTAIN
            }
            if (mapped == SmartBoardVerifiedStepStatus.INVALID) blocked = true
            SmartBoardVerifiedStep(
                "physics-step-$index", mapped, step.feedback, step.line, engine, null, null, emptyList(),
                if (mapped == SmartBoardVerifiedStepStatus.INVALID) "unit or dimensional correction" else null,
            )
        }
        val invalid = steps.firstOrNull { it.status == SmartBoardVerifiedStepStatus.INVALID }
        val misconceptionEvidence = PhysicsMisconceptionDetector.detect(
            context.elements.joinToString("\n", transform = SmartBoardTutorElement::content),
        )
        return SmartBoardWorkVerificationResult(
            "physics-${context.elements.joinToString { it.id }}", subject,
            when {
                invalid != null -> SmartBoardWorkStatus.INCORRECT
                steps.isEmpty() || steps.any { it.status == SmartBoardVerifiedStepStatus.UNCERTAIN } -> SmartBoardWorkStatus.INCONCLUSIVE
                else -> SmartBoardWorkStatus.CORRECT
            },
            steps, invalid?.stepId,
            if (invalid != null) SmartBoardFinalAnswerStatus.INCORRECT else SmartBoardFinalAnswerStatus.NOT_CHECKED,
            listOf(engine),
            misconceptionEvidence.map {
                SmartBoardMisconception(
                    "physics-${it.id}", subject, it.id, it.id.replace('-', ' '), it.message, .9f,
                    context.elements.map(SmartBoardTutorElement::id), context.conceptId, it.message,
                    persistentCandidate = false,
                )
            },
            listOf("Dimensional consistency does not by itself prove physical applicability."), emptyList(),
        )
    }

    override suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardVisualRecommendation(
            "physics-motion-graph", subject, "Motion graph", "Relate the quantities to slopes and areas.",
            SmartBoardCapability.GRAPH_2D, context.elements.map { it.id }, "graph2d", .8f, true,
        ),
    )

    override suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse {
        if (request.mode in setOf(UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE)) {
            val verification = verifyWork(context)
            val first = verification.stepResults.firstOrNull { it.status == SmartBoardVerifiedStepStatus.INVALID }
            return response(
                request, statusFor(verification),
                first?.let { "First invalid Physics step: ${it.evidence}. ${it.explanation}" }
                    ?: "No dimensionally invalid supported step was found.",
                verification = verification,
                visuals = recommendVisuals(context),
            )
        }
        val result = tutor.hint(
            context.elements.joinToString("\n", transform = SmartBoardTutorElement::content),
            request.mode == UnifiedTutorMode.NEXT_STEP,
        )
        val content = if (request.mode == UnifiedTutorMode.NEXT_STEP) result.guidance.take(1) else result.guidance
        val hint = hint(
            request,
            content.firstOrNull().orEmpty(),
            if (result.verified) SmartBoardTutorVerificationStatus.VERIFIED_WITH_CONDITIONS else SmartBoardTutorVerificationStatus.INCONCLUSIVE,
        )
        return response(
            request,
            hint.verificationStatus,
            content.firstOrNull() ?: result.title,
            content.map { SmartBoardTutorContentBlock(SmartBoardTutorContentKind.HINT, result.title, it) },
            result.warnings,
            hint = hint,
            visuals = recommendVisuals(context),
        )
    }
}

class ChemistrySmartBoardTutorHandler : BaseSmartBoardTutorHandler() {
    override val subject = SmartBoardSubject.CHEMISTRY
    private val elementEngine = SmartBoardEngineReference(BundledElementData.DATASET_VERSION, true, true, true)

    override suspend fun suggestedModes(context: SmartBoardSubjectTutorContext) = listOf(
        UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP, UnifiedTutorMode.EXPLAIN_CONCEPT,
        UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.EXPLAIN_VISUALLY,
    )

    override fun availableToolsNow(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardTutorToolDefinition("chemistry-elements", "Validate element symbols", subject, null, SmartBoardTutorToolPermission.READ_ONLY, true, elementEngine),
    )

    override suspend fun verifyWork(context: SmartBoardSubjectTutorContext) =
        unsupportedVerification(subject, "No reusable production equation-balancing or stoichiometry engine is installed.")

    override suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardVisualRecommendation(
            "chemistry-periodic-table", subject, "Periodic table", "Inspect recognized element symbols.",
            SmartBoardCapability.RECOGNITION, context.elements.map { it.id }, "chemistry:periodic-table", .9f, true,
        ),
    )

    override suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse {
        val source = context.elements.joinToString(" ", transform = SmartBoardTutorElement::content)
        val symbols = Regex("""[A-Z][a-z]?""").findAll(source).map { it.value }.distinct().toList()
        val known = BundledElementData.elements.associateBy { it.symbol }
        val unknown = symbols.filterNot(known::containsKey)
        if (unknown.isNotEmpty()) {
            return response(
                request, SmartBoardTutorVerificationStatus.RULE_VERIFIED,
                "Review the unrecognized element symbol${if (unknown.size > 1) "s" else ""}: ${unknown.joinToString()}.",
                warnings = listOf("Only element-symbol validation used the verified periodic-table dataset."),
            )
        }
        if (request.mode in setOf(UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE, UnifiedTutorMode.FULL_SOLUTION)) {
            return unsupported(request, "Chemical equation and stoichiometry verification")
        }
        val cue = if ('→' in source || "->" in source) {
            "Start by counting each element on the reactant and product sides. A verified balancer is not installed, so the tutor will not supply coefficients."
        } else {
            "Identify each verified element symbol, then confirm subscripts and any charge before calculating."
        }
        val hint = hint(request, cue, SmartBoardTutorVerificationStatus.RULE_VERIFIED)
        return response(
            request, SmartBoardTutorVerificationStatus.RULE_VERIFIED, cue,
            hint = hint, visuals = recommendVisuals(context),
            warnings = listOf("Equation balancing remains disabled because no reusable verified engine was found."),
        )
    }
}

class EnglishSmartBoardTutorHandler : BaseSmartBoardTutorHandler() {
    override val subject = SmartBoardSubject.ENGLISH

    override suspend fun suggestedModes(context: SmartBoardSubjectTutorContext) = listOf(
        UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP, UnifiedTutorMode.EXPLAIN_CONCEPT,
        UnifiedTutorMode.CONCISE, UnifiedTutorMode.DETAILED, UnifiedTutorMode.FIND_MY_MISTAKE,
    )

    override fun availableToolsNow(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardTutorToolDefinition("english-inspect", "Inspect confirmed text", subject, SmartBoardCapability.RECOGNITION, SmartBoardTutorToolPermission.READ_ONLY, true, localEngine),
    )

    override suspend fun verifyWork(context: SmartBoardSubjectTutorContext) =
        unsupportedVerification(subject, "No production grammar, spelling, or sentence-analysis engine is installed.")

    override suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse {
        val source = context.elements.joinToString(" ", transform = SmartBoardTutorElement::content)
        if (source.isBlank()) return unsupported(request, "English tutoring")
        if (request.mode in setOf(UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE)) {
            return response(
                request, SmartBoardTutorVerificationStatus.UNSUPPORTED,
                "I can inspect the confirmed text, but this build has no verified grammar engine to classify an error.",
                blocks = listOf(
                    SmartBoardTutorContentBlock(SmartBoardTutorContentKind.CORRECTION_COMPARISON, "Original preserved", source),
                    SmartBoardTutorContentBlock(SmartBoardTutorContentKind.WARNING, "Verification unavailable", "No correction was applied."),
                ),
                warnings = listOf("Style preferences are never presented as grammar errors."),
            )
        }
        val cue = "Identify the subject, main verb, and time expression; then check whether they agree. Keep the original until you approve a correction."
        return response(
            request, SmartBoardTutorVerificationStatus.INCONCLUSIVE, cue,
            hint = hint(request, cue, SmartBoardTutorVerificationStatus.INCONCLUSIVE),
            warnings = listOf("This is deterministic study guidance, not a verified grammar judgment."),
        )
    }
}

class BiologySmartBoardTutorHandler(
    private val repository: BiologyRepository = OfflineBiologyRepository(),
) : BaseSmartBoardTutorHandler() {
    override val subject = SmartBoardSubject.BIOLOGY
    private val modelEngine = SmartBoardEngineReference("bundled-biology-catalogue", true, true, true)

    override suspend fun suggestedModes(context: SmartBoardSubjectTutorContext) = listOf(
        UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP, UnifiedTutorMode.CHECK_MY_WORK,
        UnifiedTutorMode.EXPLAIN_CONCEPT, UnifiedTutorMode.EXPLAIN_VISUALLY,
    )

    override fun availableToolsNow(context: SmartBoardSubjectTutorContext) = listOf(
        SmartBoardTutorToolDefinition("biology-catalogue", "Look up Biology concept", subject, SmartBoardCapability.RECOGNITION, SmartBoardTutorToolPermission.READ_ONLY, true, modelEngine),
        SmartBoardTutorToolDefinition("biology-labels", "Validate model-backed labels", subject, SmartBoardCapability.WORK_VERIFICATION, SmartBoardTutorToolPermission.READ_ONLY, true, modelEngine),
    )

    override suspend fun verifyWork(context: SmartBoardSubjectTutorContext): SmartBoardWorkVerificationResult {
        val knownLabels = BundledBiologyCatalogue.catalogue.diagrams.flatMap { diagram -> diagram.labels.map { it.text.lowercase() } }.toSet()
        val candidateLabels = context.elements.flatMap { element ->
            element.content.split(',', '\n').map(String::trim).filter(String::isNotBlank)
        }
        if (candidateLabels.isEmpty()) return unsupportedVerification(subject, "No Biology labels were selected.")
        val steps = candidateLabels.mapIndexed { index, label ->
            val valid = label.lowercase() in knownLabels
            val status = if (valid) SmartBoardVerifiedStepStatus.VALID else SmartBoardVerifiedStepStatus.UNCERTAIN
            SmartBoardVerifiedStep(
                "biology-label-$index", status,
                if (valid) "Label matches a bundled reviewed diagram." else "Label was not found in the selected model catalogue; confirm the diagram and spelling.",
                label, modelEngine, if (valid) 1f else null, null, emptyList(), if (valid) null else "model-backed label review",
            )
        }
        return SmartBoardWorkVerificationResult(
            "biology-${context.elements.joinToString { it.id }}", subject,
            if (steps.all { it.status == SmartBoardVerifiedStepStatus.VALID }) SmartBoardWorkStatus.CORRECT else SmartBoardWorkStatus.INCONCLUSIVE,
            steps, null, SmartBoardFinalAnswerStatus.NOT_CHECKED, listOf(modelEngine), emptyList(), emptyList(),
            listOf("A missing catalogue match is not proof that a biological label is wrong."),
        )
    }

    override suspend fun recommendVisuals(context: SmartBoardSubjectTutorContext): List<SmartBoardVisualRecommendation> {
        val query = context.elements.firstOrNull()?.content.orEmpty()
        val match = repository.search(query, BiologyLearningLevel.POSTGRADUATE).firstOrNull()
        return match?.let {
            listOf(
                SmartBoardVisualRecommendation(
                    "biology-model-${it.id}", subject, it.title, "Open the existing Biology learning reference.",
                    SmartBoardCapability.RECOGNITION, context.elements.map { element -> element.id }, "biology:${it.id}", .75f, true,
                ),
            )
        }.orEmpty()
    }

    override suspend fun respond(request: UnifiedTutorRequest, context: SmartBoardSubjectTutorContext): UnifiedTutorResponse {
        val source = context.elements.joinToString(" ", transform = SmartBoardTutorElement::content)
        if (request.mode in setOf(UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE)) {
            val verification = verifyWork(context)
            return response(
                request, statusFor(verification),
                verification.stepResults.firstOrNull { it.status != SmartBoardVerifiedStepStatus.VALID }?.explanation
                    ?: "Selected labels match reviewed Biology model references.",
                verification = verification, visuals = recommendVisuals(context),
            )
        }
        val match = repository.search(source, BiologyLearningLevel.POSTGRADUATE).firstOrNull()
        if (match == null) return unsupported(request, "Model-backed Biology explanation")
        val cue = if (request.mode in setOf(UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP)) {
            "Use structure and function as your clue: ${match.context.take(180)}"
        } else match.context
        return response(
            request, SmartBoardTutorVerificationStatus.MODEL_REFERENCE_VERIFIED, cue,
            hint = if (request.mode in setOf(UnifiedTutorMode.HINT, UnifiedTutorMode.NEXT_STEP)) {
                hint(request, cue, SmartBoardTutorVerificationStatus.MODEL_REFERENCE_VERIFIED)
            } else null,
            visuals = recommendVisuals(context),
        )
    }
}

class DefaultUnifiedSmartBoardTutor(
    handlers: List<SmartBoardSubjectTutorHandler> = listOf(
        MathematicsSmartBoardTutorHandler(),
        PhysicsSmartBoardTutorHandler(),
        ChemistrySmartBoardTutorHandler(),
        EnglishSmartBoardTutorHandler(),
        BiologySmartBoardTutorHandler(),
    ),
) : UnifiedSmartBoardTutor {
    init {
        require(handlers.map(SmartBoardSubjectTutorHandler::subject).distinct().size == handlers.size) {
            "Only one tutor handler may be registered per subject."
        }
    }
    private val handlers = handlers.associateBy(SmartBoardSubjectTutorHandler::subject)
    val toolRegistry: SmartBoardTutorToolRegistry = DefaultSmartBoardTutorToolRegistry(this.handlers)

    override suspend fun respond(request: UnifiedTutorRequest): UnifiedTutorResponse {
        val subject = request.context.primarySubject
            ?: return unavailable(request, SmartBoardSubject.GENERAL, "Confirm a primary subject for the selected content.")
        val handler = handlers[subject]
            ?: return unavailable(request, subject, "No tutor handler is installed for ${subject.name.lowercase()}.")
        val cleanRequest = request.copy(message = SmartBoardTutorSecurity.safeMessage(request.message))
        val subjectContext = handler.buildSubjectContext(request.context)
        if (cleanRequest.mode !in handler.suggestedModes(subjectContext) && cleanRequest.mode !in setOf(UnifiedTutorMode.ASK, UnifiedTutorMode.CONCISE, UnifiedTutorMode.DETAILED)) {
            return unavailable(cleanRequest, subject, "This tutor mode is unavailable for the verified ${subject.name.lowercase()} tools.")
        }
        return handler.respond(cleanRequest, subjectContext)
    }

    override suspend fun suggestPrompts(context: SmartBoardTutorContext): List<SmartBoardSuggestedPrompt> {
        val handler = context.primarySubject?.let(handlers::get) ?: return listOf(
            SmartBoardSuggestedPrompt("clarify", "Confirm the subject", UnifiedTutorMode.ASK),
        )
        val subjectContext = handler.buildSubjectContext(context)
        return handler.suggestedModes(subjectContext).take(6).map { mode ->
            SmartBoardSuggestedPrompt(mode.name.lowercase(), mode.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase), mode)
        }
    }

    override suspend fun verifyResponse(response: UnifiedTutorResponse): SmartBoardTutorVerificationStatus {
        if (response.verificationStatus in setOf(SmartBoardTutorVerificationStatus.VERIFIED, SmartBoardTutorVerificationStatus.NUMERICALLY_VERIFIED) &&
            response.structuredContent.isEmpty()
        ) return SmartBoardTutorVerificationStatus.FAILED
        if (response.verificationStatus == SmartBoardTutorVerificationStatus.AI_ONLY && response.warnings.none { it.contains("not", true) }) {
            return SmartBoardTutorVerificationStatus.FAILED
        }
        return response.verificationStatus
    }

    private fun unavailable(request: UnifiedTutorRequest, subject: SmartBoardSubject, message: String) = UnifiedTutorResponse(
        UUID.randomUUID().toString(), subject, request.context.supportingSubjects, request.mode, message,
        listOf(SmartBoardTutorContentBlock(SmartBoardTutorContentKind.WARNING, "Unavailable", message)),
        request.context.selectedElementIds, emptyList(), SmartBoardTutorVerificationStatus.UNSUPPORTED,
        listOf("Drawing, editing, saving, and available local tools remain usable."), emptyList(),
        System.currentTimeMillis(),
    )
}

private fun defaultTutorCapabilities(
    subject: SmartBoardSubject?,
    availability: SmartBoardServiceAvailability,
): Set<SmartBoardCapability> {
    val base = mutableSetOf(SmartBoardCapability.RECOGNITION, SmartBoardCapability.LOCAL_TUTOR)
    when (subject) {
        SmartBoardSubject.MATHEMATICS -> base += setOf(
            SmartBoardCapability.CAS, SmartBoardCapability.SOLVER, SmartBoardCapability.WORK_VERIFICATION,
            SmartBoardCapability.GRAPH_2D, SmartBoardCapability.GRAPH_3D,
        )
        SmartBoardSubject.PHYSICS -> base += setOf(
            SmartBoardCapability.PHYSICS_FORMULAS, SmartBoardCapability.PHYSICS_UNITS,
            SmartBoardCapability.PHYSICS_DIMENSIONS, SmartBoardCapability.PHYSICS_NUMERICAL,
            SmartBoardCapability.WORK_VERIFICATION, SmartBoardCapability.GRAPH_2D,
        )
        SmartBoardSubject.BIOLOGY -> base += SmartBoardCapability.WORK_VERIFICATION
        else -> Unit
    }
    if (availability.aiAvailable) base += SmartBoardCapability.REMOTE_ASSISTANT
    return base - availability.unavailableCapabilities
}

private fun SmartBoardElement.intrinsicSubject() = when (this) {
    is MathExpressionElement, is SolutionSequenceElement, is TableElement -> SmartBoardSubject.MATHEMATICS
    is PhysicsExpressionElement, is PhysicsDiagramElement -> SmartBoardSubject.PHYSICS
    is ChemistryExpressionElement -> SmartBoardSubject.CHEMISTRY
    is EnglishTextElement -> SmartBoardSubject.ENGLISH
    is BiologyContentElement -> SmartBoardSubject.BIOLOGY
    else -> SmartBoardSubject.GENERAL
}

private fun SmartBoardElement.tutorContent(): String = when (this) {
    is MathExpressionElement -> displayLatex
    is PhysicsExpressionElement -> displaySource
    is PhysicsDiagramElement -> detectedObjects.joinToString { it.label ?: it.kind }
    is ChemistryExpressionElement -> normalizedChemicalNotation ?: rawText
    is EnglishTextElement -> correctedText ?: rawText
    is BiologyContentElement -> recognizedText.orEmpty() + detectedLabels.joinToString { it.text }
    is ShapeElement -> "Recognized ${shapeType.name.lowercase().replace('_', ' ')}"
    is SolutionSequenceElement -> listOf(problemExpression).plus(steps.map { it.expression }).joinToString("\n")
    is TableElement -> buildString {
        appendLine(columnHeaders.joinToString("\t"))
        append(rows.take(20).joinToString("\n") { it.joinToString("\t") })
    }
    else -> ""
}

private fun SmartBoardElement.recognitionConfidence(): Float? = when (this) {
    is MathExpressionElement -> recognitionConfidence
    is PhysicsExpressionElement -> recognitionConfidence
    is ShapeElement -> recognitionConfidence
    else -> null
}

private fun SmartBoardElement.isDeterministicResult() =
    this is com.indianservers.smartboard.smartboard.models.ActionResultElement && verified ||
        this is com.indianservers.smartboard.smartboard.models.PhysicsResultElement &&
        status.name.startsWith("VERIFIED")

private fun unsupportedVerification(subject: SmartBoardSubject, warning: String) = SmartBoardWorkVerificationResult(
    "unsupported-${UUID.randomUUID()}", subject, SmartBoardWorkStatus.UNSUPPORTED, emptyList(), null,
    SmartBoardFinalAnswerStatus.NOT_CHECKED, emptyList(), emptyList(), emptyList(), listOf(warning),
)

private fun statusFor(result: SmartBoardWorkVerificationResult) = when (result.overallStatus) {
    SmartBoardWorkStatus.CORRECT -> SmartBoardTutorVerificationStatus.VERIFIED
    SmartBoardWorkStatus.INCORRECT -> SmartBoardTutorVerificationStatus.VERIFIED_WITH_CONDITIONS
    SmartBoardWorkStatus.PARTIAL -> SmartBoardTutorVerificationStatus.PARTIALLY_VERIFIED
    SmartBoardWorkStatus.INCONCLUSIVE -> SmartBoardTutorVerificationStatus.INCONCLUSIVE
    SmartBoardWorkStatus.UNSUPPORTED -> SmartBoardTutorVerificationStatus.UNSUPPORTED
}
