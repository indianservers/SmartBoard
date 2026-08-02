package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.models.SmartBoardAction
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject

enum class SmartBoardIntelligenceLevel { FULL, DETERMINISTIC, BASIC }
enum class SmartBoardGoalType {
    RECOGNIZE, UNDERSTAND, SOLVE, SIMPLIFY, VERIFY, LEARN, PRACTICE, VISUALIZE, GRAPH,
    COMPARE, DERIVE, EXPLAIN, CORRECT, COMPLETE_WORK, ANALYZE_DATA, CONVERT_UNITS,
    CHECK_DIMENSIONS, CREATE_EXAMPLE, PREPARE_EXAM_ANSWER, UNKNOWN,
}
enum class GoalEvidence { EXPLICIT_COMMAND, EXPLICIT_ACTION, SELECTION, CONTENT_TYPE, RECENT_ACTION, ACTIVE_WORKFLOW, LESSON }
enum class SmartBoardRecommendationCategory { CONTINUE_WORKING, SOLVE, VERIFY, LEARN, VISUALIZE, EXPLORE, CORRECT, PRACTICE, CONVERT, COMPARE }
enum class SmartBoardCapability {
    RECOGNITION, CAS, SOLVER, GRAPH_2D, GRAPH_3D, GEOMETRY_2D, GEOMETRY_3D, STATISTICS,
    WORK_VERIFICATION, LOCAL_TUTOR, REMOTE_ASSISTANT, PHYSICS_FORMULAS, PHYSICS_UNITS,
    PHYSICS_DIMENSIONS, PHYSICS_NUMERICAL, PHYSICS_VISUALIZATION, PHOTO_RECOGNITION,
}
enum class SmartBoardSourceTrust { USER_CONFIRMED, RECOGNIZED_UNTRUSTED, IMPORTED_UNTRUSTED, ENGINE_DERIVED, INFERRED }
enum class SmartBoardPermissionClass { SAFE_READ_ONLY, REVERSIBLE_WRITE, SENSITIVE }
enum class SmartBoardVerificationStatus { VERIFIED, VERIFIED_WITH_CONDITIONS, NUMERICALLY_VERIFIED, PARTIALLY_VERIFIED, INCONCLUSIVE, UNSUPPORTED, FAILED }
enum class ConfidenceDisplayLevel { HIGH_CONFIDENCE, REVIEW_RECOMMENDED, NEEDS_CONFIRMATION }
enum class SmartBoardProblemStage { CAPTURING, RECOGNIZING, UNDERSTANDING, PLANNING, SOLVING, VERIFYING, VISUALIZING, EXPLAINING, COMPLETED, BLOCKED }
enum class SmartBoardCompletionStatus { NOT_STARTED, IN_PROGRESS, COMPLETE, BLOCKED }
enum class SmartBoardWorkflowStepType { CONFIRM_INPUT, ENGINE_ACTION, VERIFY_RESULT, OPEN_VISUALIZATION, INSERT_EXPLANATION }
enum class WorkflowStepStatus { PENDING, APPROVED, RUNNING, COMPLETED, FAILED, SKIPPED, CANCELLED }
enum class SmartBoardExplanationMode { ONE_LINE, BRIEF, STANDARD, DETAILED, VISUAL_FIRST, FORMULA_FIRST, EXAM_STYLE, CONCEPTUAL, STEP_BY_STEP }

data class MissingInformation(val id: String, val prompt: String, val blocking: Boolean)
data class SmartBoardAmbiguity(val id: String, val elementId: String?, val token: String, val choices: List<String>, val prompt: String)
data class SmartBoardKnownFact(val label: String, val value: String, val sourceElementId: String?)
data class SmartBoardUnknownFact(val label: String, val reason: String, val sourceElementId: String?)

data class SmartBoardGoal(
    val type: SmartBoardGoalType,
    val subject: SmartBoardSubject,
    val targetElementIds: List<String>,
    val confidence: Float,
    val inferredFrom: Set<GoalEvidence>,
    val requiredInformation: List<MissingInformation> = emptyList(),
    val userConfirmed: Boolean = false,
) {
    init { require(confidence in 0f..1f) }
}

data class SmartBoardContextElement(
    val id: String,
    val kind: String,
    val summary: String,
    val sourceTrust: SmartBoardSourceTrust,
    val confidence: Float?,
    val sourceElementIds: List<String>,
    val readingOrder: Int,
) {
    init { require(summary.length <= 1_200 && (confidence == null || confidence in 0f..1f)) }
}

data class SmartBoardActionHistoryEntry(
    val id: String,
    val actionId: String,
    val targetElementIds: List<String>,
    val succeeded: Boolean,
    val occurredAt: Long,
)

data class SmartBoardLearnerContext(
    val level: String?,
    val masteryState: String?,
    val recentErrorKinds: List<String>,
    val hintDependence: Float?,
    val preferredRepresentation: String?,
)

data class SmartBoardLessonContext(val curriculumNodeId: String?, val conceptId: String?, val lessonTitle: String?)
data class SmartBoardDeviceContext(val formFactor: String, val reducedMotion: Boolean, val highContrast: Boolean, val networkAvailable: Boolean)
data class SmartBoardServiceAvailability(
    val intelligenceLevel: SmartBoardIntelligenceLevel,
    val recognitionAvailable: Boolean,
    val aiAvailable: Boolean,
    val unavailableCapabilities: Set<SmartBoardCapability> = emptySet(),
)
data class SmartBoardContextMetrics(
    val candidateElementCount: Int,
    val includedElementCount: Int,
    val includedCharacters: Int,
    val truncatedElementCount: Int,
    val fullBoardIncluded: Boolean,
)

data class SmartBoardIntelligenceContext(
    val boardId: String,
    val subject: SmartBoardSubject,
    val mode: SmartBoardIntelligenceMode,
    val selectedElementIds: List<String>,
    val activeProblemId: String?,
    val activeWorkflowId: String?,
    val currentGoal: SmartBoardGoal?,
    val elements: List<SmartBoardContextElement>,
    val relationships: List<SmartBoardRelationship>,
    val recentActions: List<SmartBoardActionHistoryEntry>,
    val pendingAmbiguities: List<SmartBoardAmbiguity>,
    val learnerContext: SmartBoardLearnerContext?,
    val lessonContext: SmartBoardLessonContext?,
    val availableCapabilities: Set<SmartBoardCapability>,
    val deviceContext: SmartBoardDeviceContext,
    val serviceAvailability: SmartBoardServiceAvailability,
    val metrics: SmartBoardContextMetrics,
)

data class SmartBoardConfidenceProfile(
    val recognition: Float?,
    val classification: Float?,
    val intent: Float?,
    val formulaMatch: Float? = null,
    val diagramInterpretation: Float? = null,
    val calculation: Float? = null,
    val recommendation: Float?,
    val verification: Float?,
    val overallDisplayLevel: ConfidenceDisplayLevel,
)

data class SmartBoardEngineReference(
    val id: String,
    val local: Boolean,
    val exactResultSupported: Boolean,
    val offlineCapable: Boolean,
)

data class SmartBoardRecommendation(
    val id: String,
    val action: SmartBoardAction,
    val toolId: String?,
    val category: SmartBoardRecommendationCategory,
    val title: String,
    val reason: String,
    val priority: Int,
    val confidence: Float,
    val sourceElementIds: List<String>,
    val requiredConfirmation: Boolean,
    val expectedOutcome: String?,
    val engine: SmartBoardEngineReference?,
    val learningValue: Float?,
    val disabledReason: String?,
) {
    init { require(priority in 0..100 && confidence in 0f..1f) }
}

data class SmartBoardProblemState(
    val id: String,
    val subject: SmartBoardSubject,
    val problemElementIds: List<String>,
    val goal: SmartBoardGoal?,
    val knownInformation: List<SmartBoardKnownFact>,
    val unknownInformation: List<SmartBoardUnknownFact>,
    val attemptedSteps: List<String>,
    val verifiedSteps: List<String>,
    val invalidStepId: String?,
    val currentStage: SmartBoardProblemStage,
    val selectedMethod: String?,
    val assumptions: List<String>,
    val warnings: List<String>,
    val completionStatus: SmartBoardCompletionStatus,
)

data class SmartBoardToolReference(val id: String)
data class SmartBoardWorkflowStep(
    val id: String,
    val order: Int,
    val type: SmartBoardWorkflowStepType,
    val title: String,
    val tool: SmartBoardToolReference?,
    val inputElementIds: List<String>,
    val dependsOnStepIds: List<String>,
    val status: WorkflowStepStatus,
    val requiresConfirmation: Boolean,
    val canRetry: Boolean,
    val canSkip: Boolean,
)
data class SmartBoardWorkflowPlan(
    val id: String,
    val title: String,
    val goal: SmartBoardGoal,
    val steps: List<SmartBoardWorkflowStep>,
    val requiresUserApproval: Boolean,
    val estimatedCapabilities: Set<SmartBoardCapability>,
    val warnings: List<String>,
)

data class SmartBoardSessionPreferences(
    val outputStyle: SmartBoardExplanationMode = SmartBoardExplanationMode.STANDARD,
    val suggestionSnoozedUntil: Long? = null,
    val suggestionsDisabledForBoard: Boolean = false,
    val lastGraphRange: String? = null,
    val lastSelectedSubjectTool: String? = null,
)
data class SmartBoardSessionMemory(
    val boardId: String,
    val activeProblemId: String?,
    val activeWorkflow: SmartBoardWorkflowPlan?,
    val resolvedAmbiguities: Map<String, String>,
    val completedActionIds: Set<String>,
    val dismissedRecommendationIds: Set<String>,
    val shownHintLevels: Map<String, Int>,
    val recentActions: List<SmartBoardActionHistoryEntry>,
    val userPreferences: SmartBoardSessionPreferences,
    val lastUpdatedAt: Long,
) {
    companion object {
        fun empty(boardId: String, now: Long = 0L) = SmartBoardSessionMemory(
            boardId, null, null, emptyMap(), emptySet(), emptySet(), emptyMap(), emptyList(),
            SmartBoardSessionPreferences(), now,
        )
    }
}

data class SmartBoardToolDefinition(
    val id: String,
    val title: String,
    val subject: SmartBoardSubject?,
    val capability: SmartBoardCapability,
    val permission: SmartBoardPermissionClass,
    val requiredArguments: Set<String>,
    val engine: SmartBoardEngineReference,
)
data class SmartBoardToolCall(
    val id: String,
    val toolId: String,
    val boardId: String,
    val subject: SmartBoardSubject,
    val sourceElementIds: List<String>,
    val arguments: Map<String, String>,
    val explicitUserApproval: Boolean,
)
data class SmartBoardToolResult(
    val callId: String,
    val toolId: String,
    val success: Boolean,
    val title: String,
    val exact: String?,
    val approximate: String?,
    val details: List<String>,
    val assumptions: List<String>,
    val verificationStatus: SmartBoardVerificationStatus,
    val producedElement: SmartBoardElement? = null,
    val moduleRoute: String? = null,
    val modulePayload: String? = null,
    val safeMessage: String? = null,
)
data class SmartBoardCapabilityResolution(
    val capability: SmartBoardCapability?,
    val engine: SmartBoardEngineReference?,
    val missingInformation: List<MissingInformation>,
    val available: Boolean,
    val confirmationRequired: Boolean,
    val reason: String,
)

data class SmartBoardUnderstandingRequest(
    val context: SmartBoardIntelligenceContext,
    val command: String? = null,
    val explicitAction: Boolean = false,
)
data class SmartBoardSubjectUnderstanding(
    val subject: SmartBoardSubject,
    val goal: SmartBoardGoal,
    val summary: String,
    val detectedKinds: Set<String>,
    val warnings: List<String>,
    val confidence: SmartBoardConfidenceProfile,
)
data class SmartBoardUnderstandingResult(
    val context: SmartBoardIntelligenceContext,
    val subjectUnderstanding: SmartBoardSubjectUnderstanding,
    val problemState: SmartBoardProblemState,
    val clarification: String?,
)
data class SmartBoardWorkflowRequest(val context: SmartBoardIntelligenceContext, val command: String, val goal: SmartBoardGoal? = null)
data class SmartBoardWorkflowStepRequest(
    val context: SmartBoardIntelligenceContext,
    val plan: SmartBoardWorkflowPlan,
    val stepId: String,
    val explicitApproval: Boolean,
    val arguments: Map<String, String> = emptyMap(),
)
data class SmartBoardWorkflowStepResult(
    val plan: SmartBoardWorkflowPlan,
    val step: SmartBoardWorkflowStep,
    val toolResult: SmartBoardToolResult?,
    val verification: SmartBoardVerificationResult,
)
data class SmartBoardExplanationRequest(
    val context: SmartBoardIntelligenceContext,
    val targetElementIds: List<String>,
    val explanationMode: SmartBoardExplanationMode,
    val learnerLevel: String?,
    val maxSteps: Int?,
    val includeVisualization: Boolean,
    val includeVerification: Boolean,
)
data class SmartBoardExplanationResult(
    val title: String,
    val content: List<String>,
    val verification: SmartBoardVerificationResult,
    val degraded: Boolean,
)
data class SmartBoardVerificationInput(val toolResult: SmartBoardToolResult, val context: SmartBoardIntelligenceContext)
data class SmartBoardVerificationResult(val status: SmartBoardVerificationStatus, val explanation: String, val confidence: Float?)
