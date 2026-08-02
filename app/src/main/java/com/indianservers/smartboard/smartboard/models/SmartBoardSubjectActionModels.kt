package com.indianservers.smartboard.smartboard.models

enum class SmartBoardResultStatus { LOADING, VERIFIED, VERIFIED_WITH_CONDITIONS, PARTIAL, NEEDS_CONFIRMATION, UNSUPPORTED, FAILED, CANCELLED }
enum class SmartBoardSubjectErrorCode { INVALID_INPUT, CAPABILITY_UNAVAILABLE, ENGINE_FAILURE, AMBIGUOUS_INPUT, CANCELLED, UNSUPPORTED }

data class SmartBoardSubjectError(
    val subject: SmartBoardSubject,
    val code: SmartBoardSubjectErrorCode,
    val userMessage: String,
    val recoverable: Boolean,
    val suggestedAction: String? = null,
    val details: Map<String, String> = emptyMap(),
)

interface SmartBoardSubjectResult {
    val resultId: String
    val resultSubject: SmartBoardSubject
    val resultSources: List<String>
    val resultActionId: String
    val resultStatus: SmartBoardResultStatus
    val resultTitle: String
    val resultWarnings: List<String>
    val resultCreatedAt: Long
}

data class ChemistryFormulaComponent(
    val symbol: String,
    val atomCount: Int,
    val atomicMass: Double,
    val contribution: Double,
)
data class ChemistrySolutionStep(val expression: String, val explanation: String, val verified: Boolean)

data class ChemistryResultElement(
    override val id: String,
    val sourceElementIds: List<String>,
    val actionId: String,
    val status: SmartBoardResultStatus,
    val title: String,
    val normalizedNotation: String?,
    val balancedEquation: String?,
    val formulaBreakdown: List<ChemistryFormulaComponent>,
    val numericalResult: Double?,
    val resultUnit: String?,
    val steps: List<ChemistrySolutionStep>,
    val identifiedConcepts: List<String>,
    val visualizationReference: String?,
    val assumptions: List<String>,
    val warnings: List<String>,
    val engineIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement, SmartBoardSubjectResult {
    override val resultId get() = id
    override val resultSubject = SmartBoardSubject.CHEMISTRY
    override val resultSources get() = sourceElementIds
    override val resultActionId get() = actionId
    override val resultStatus get() = status
    override val resultTitle get() = title
    override val resultWarnings get() = warnings
    override val resultCreatedAt get() = createdAt
}

enum class EnglishIssueType { SPELLING, SUBJECT_VERB_AGREEMENT, TENSE, ARTICLE, PREPOSITION, PUNCTUATION, CAPITALIZATION, PRONOUN, WORD_CHOICE, FRAGMENT, RUN_ON, STYLE }
data class SmartBoardTextRange(val start: Int, val endExclusive: Int)
data class EnglishCorrectionSuggestion(
    val id: String,
    val issueType: EnglishIssueType,
    val originalText: String,
    val suggestedText: String,
    val range: SmartBoardTextRange,
    val explanation: String?,
    val confidence: Float?,
    val engineSource: String?,
    val accepted: Boolean = false,
)
data class PartOfSpeechToken(val token: String, val role: String, val confidence: Float?)
data class EnglishReadabilityResult(val wordCount: Int, val sentenceCount: Int, val averageWordsPerSentence: Double)
data class EnglishVocabularyResult(val word: String, val definition: String?, val synonyms: List<String>)

data class EnglishResultElement(
    override val id: String,
    val sourceElementIds: List<String>,
    val actionId: String,
    val status: SmartBoardResultStatus,
    val title: String,
    val originalText: String,
    val suggestedText: String?,
    val corrections: List<EnglishCorrectionSuggestion>,
    val partsOfSpeech: List<PartOfSpeechToken>,
    val explanation: String?,
    val readability: EnglishReadabilityResult?,
    val vocabularyResults: List<EnglishVocabularyResult>,
    val warnings: List<String>,
    val engineIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement, SmartBoardSubjectResult {
    override val resultId get() = id
    override val resultSubject = SmartBoardSubject.ENGLISH
    override val resultSources get() = sourceElementIds
    override val resultActionId get() = actionId
    override val resultStatus get() = status
    override val resultTitle get() = title
    override val resultWarnings get() = warnings
    override val resultCreatedAt get() = createdAt
}

data class BiologyConfirmedLabel(val text: String, val structureId: String?, val function: String?, val confirmedByUser: Boolean)
data class BiologyProcessStep(val order: Int, val text: String)
data class BiologyGeneticsResult(val genotypeRatio: String?, val phenotypeRatio: String?, val verified: Boolean)

data class BiologyResultElement(
    override val id: String,
    val sourceElementIds: List<String>,
    val actionId: String,
    val status: SmartBoardResultStatus,
    val title: String,
    val conceptId: String?,
    val explanation: String?,
    val confirmedLabels: List<BiologyConfirmedLabel>,
    val processSteps: List<BiologyProcessStep>,
    val geneticsResult: BiologyGeneticsResult?,
    val modelReference: String?,
    val studySummary: List<String>,
    val warnings: List<String>,
    val engineIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement, SmartBoardSubjectResult {
    override val resultId get() = id
    override val resultSubject = SmartBoardSubject.BIOLOGY
    override val resultSources get() = sourceElementIds
    override val resultActionId get() = actionId
    override val resultStatus get() = status
    override val resultTitle get() = title
    override val resultWarnings get() = warnings
    override val resultCreatedAt get() = createdAt
}

data class SmartBoardProblemOwnership(
    val primarySubject: SmartBoardSubject,
    val supportingSubjects: Set<SmartBoardSubject>,
    val sourceElementIds: List<String>,
    val confidence: Float?,
    val userConfirmed: Boolean,
)

data class SmartBoardActionConfiguration(
    val actionId: String,
    val parameters: Map<String, String>,
    val createdAt: Long,
)
