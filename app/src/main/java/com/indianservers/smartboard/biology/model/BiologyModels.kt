package com.indianservers.smartboard.biology.model

import com.indianservers.smartboard.biology.future3d.Future3DObjectMetadata

enum class BiologyLearningLevel(val label: String, val rank: Int) {
    FOUNDATION("Foundation", 0), CLASS_7("Class 7", 1), CLASS_8("Class 8", 2), CLASS_9("Class 9", 3),
    CLASS_10("Class 10", 4), CLASS_11("Class 11", 5), CLASS_12("Class 12", 6),
    UNDERGRADUATE("Undergraduate", 7), POSTGRADUATE("Postgraduate", 8),
}

enum class BiologyContentStatus { Complete, OverviewReady }
enum class BiologyProgressStatus { NotStarted, InProgress, Completed }
enum class BiologyNodeType { Domain, Unit, Chapter, Topic, Concept, Glossary }

data class BiologyDomain(
    val id: String,
    val title: String,
    val description: String,
    val unitIds: List<String>,
    val minimumLevel: BiologyLearningLevel,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val keywords: Set<String>,
    val iconText: String,
)

data class BiologyUnit(
    val id: String,
    val domainId: String,
    val title: String,
    val description: String,
    val chapterIds: List<String>,
    val minimumLevel: BiologyLearningLevel,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val keywords: Set<String>,
)

data class BiologyChapter(
    val id: String,
    val unitId: String,
    val title: String,
    val description: String,
    val topicIds: List<String>,
    val minimumLevel: BiologyLearningLevel,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val keywords: Set<String>,
)

data class BiologyTopic(
    val id: String,
    val chapterId: String,
    val title: String,
    val description: String,
    val conceptIds: List<String>,
    val minimumLevel: BiologyLearningLevel,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val keywords: Set<String>,
    val future3DObjectId: String?,
)

data class BiologyConcept(
    val id: String,
    val topicId: String,
    val title: String,
    val summary: String,
    val minimumLevel: BiologyLearningLevel,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val keywords: Set<String>,
    val status: BiologyContentStatus,
    val learningObjectives: List<String>,
    val blocks: List<BiologyContentBlock>,
    val plannedSections: List<String>,
    val relatedConceptIds: List<String>,
    val diagramIds: List<String>,
    val future3DObjectId: String?,
    val quizQuestionIds: List<String>,
)

sealed interface BiologyContentBlock {
    val minimumLevel: BiologyLearningLevel
    data class Paragraph(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Heading(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class BulletGroup(val items: List<String>, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class NumberedSteps(val steps: List<String>, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class KeyFact(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Definition(val term: String, val definition: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Diagram(val diagramId: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Comparison(val title: String, val columns: List<String>, val rows: List<List<String>>, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class DataTable(val title: String, val headers: List<String>, val rows: List<List<String>>, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Formula(val expression: String, val explanation: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Misconception(val claim: String, val correction: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class ClinicalContext(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class ResearchInsight(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
    data class Warning(val text: String, override val minimumLevel: BiologyLearningLevel) : BiologyContentBlock
}

data class DiagramLabel(val id: String, val text: String, val function: String, val normalizedX: Float, val normalizedY: Float)
data class BiologyDiagram(val id: String, val title: String, val description: String, val labels: List<DiagramLabel>, val accessibleAlternative: String)
data class GlossaryTerm(val id: String, val term: String, val schoolDefinition: String, val advancedDefinition: String, val domainId: String, val conceptId: String?, val synonyms: List<String>)

enum class BiologyQuestionType { MultipleChoice, MultipleSelect, TrueFalse, MatchTerms, LabelDiagram, OrderProcess, ShortAnswerSelfCheck }
data class BiologyQuizQuestion(val id: String, val conceptId: String, val type: BiologyQuestionType, val prompt: String, val options: List<String>, val correctAnswers: Set<Int>, val explanation: String, val hint: String, val level: BiologyLearningLevel)

data class BiologySearchResult(
    val id: String,
    val title: String,
    val type: BiologyNodeType,
    val domainId: String,
    val minimumLevel: BiologyLearningLevel,
    val context: String,
    val hasDiagram: Boolean,
    val future3DReady: Boolean,
)

data class BiologyCatalogue(
    val schemaVersion: Int,
    val domains: List<BiologyDomain>,
    val units: List<BiologyUnit>,
    val chapters: List<BiologyChapter>,
    val topics: List<BiologyTopic>,
    val concepts: List<BiologyConcept>,
    val diagrams: List<BiologyDiagram>,
    val glossary: List<GlossaryTerm>,
    val quizzes: List<BiologyQuizQuestion>,
    val future3D: List<Future3DObjectMetadata>,
)
