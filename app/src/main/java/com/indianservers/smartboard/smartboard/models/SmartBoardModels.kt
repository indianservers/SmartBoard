package com.indianservers.smartboard.smartboard.models

enum class SmartBoardSubject { AUTO, MATHEMATICS, PHYSICS, CHEMISTRY, ENGLISH, BIOLOGY, GENERAL }

object SmartBoardClassroomSubjects {
    val selectable: List<SmartBoardSubject> = listOf(
        SmartBoardSubject.AUTO,
        SmartBoardSubject.MATHEMATICS,
        SmartBoardSubject.PHYSICS,
        SmartBoardSubject.CHEMISTRY,
        SmartBoardSubject.BIOLOGY,
    )
    val academic: Set<SmartBoardSubject> = selectable.filterNot { it == SmartBoardSubject.AUTO }.toSet()

    fun supports(subject: SmartBoardSubject): Boolean = subject in selectable
}

enum class SmartBoardRecognitionTarget { CONTENT, GRAPH_2D, GRAPH_3D }

enum class SubjectConfidenceLevel { HIGH, MEDIUM, LOW, UNRESOLVED }
enum class SubjectClassificationSource {
    USER_SELECTION, BOARD_MODE, LOCAL_RULES, RECOGNITION_PROVIDER, AI_CLASSIFIER,
    CURRICULUM_CONTEXT, IMPORTED_METADATA, UNKNOWN,
}

sealed interface SubjectEvidence {
    val description: String
    data class SymbolPattern(override val description: String) : SubjectEvidence
    data class RecognizedTerm(val term: String) : SubjectEvidence { override val description = "Recognized term: $term" }
    data class DiagramType(val type: String) : SubjectEvidence { override val description = "Diagram type: $type" }
    data class FormulaMatch(val formulaId: String) : SubjectEvidence { override val description = "Formula match: $formulaId" }
    data class UnitMatch(val unit: String) : SubjectEvidence { override val description = "Unit match: $unit" }
    data class LanguagePattern(val language: String) : SubjectEvidence { override val description = "Language pattern: $language" }
    data class ConceptMatch(val conceptId: String) : SubjectEvidence { override val description = "Concept match: $conceptId" }
    data class UserContext(override val description: String) : SubjectEvidence
}

data class SubjectCandidate(
    val subject: SmartBoardSubject,
    val confidence: Float?,
    val evidence: List<SubjectEvidence>,
) {
    init {
        require(subject !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL))
        require(confidence == null || confidence in 0f..1f)
        require(evidence.size <= 24)
    }
}

data class SmartBoardSubjectClassification(
    val primarySubject: SmartBoardSubject?,
    val alternateSubjects: List<SubjectCandidate>,
    val confidence: Float?,
    val source: SubjectClassificationSource,
    val userConfirmed: Boolean,
    val inheritedFromBoardMode: Boolean,
    val warnings: List<String>,
) {
    init {
        require(primarySubject != SmartBoardSubject.AUTO)
        require(confidence == null || confidence in 0f..1f)
        require(alternateSubjects.size <= 5 && warnings.size <= 20)
    }
    val confidenceLevel: SubjectConfidenceLevel get() = when {
        primarySubject == null -> SubjectConfidenceLevel.UNRESOLVED
        confidence == null || confidence < .45f -> SubjectConfidenceLevel.LOW
        confidence < .80f -> SubjectConfidenceLevel.MEDIUM
        else -> SubjectConfidenceLevel.HIGH
    }
}

data class SmartBoardConceptCandidate(
    val id: String,
    val subject: SmartBoardSubject,
    val conceptId: String?,
    val displayName: String,
    val confidence: Float?,
    val evidence: List<String>,
    val parentConceptId: String? = null,
    val engineCapabilityIds: List<String> = emptyList(),
)

data class SmartBoardSubjectMode(
    val selection: SmartBoardSubject,
    val locked: Boolean,
    val userSelected: Boolean,
    val lastChangedAt: Long,
)

enum class SmartBoardBackground { PLAIN, GRID, DOTS, RULED }

enum class StrokeTool { PEN, PENCIL, HIGHLIGHTER, ERASER }

enum class SmartBoardTool {
    PEN,
    PENCIL,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    RECTANGLE_SELECT,
    PAN,
    LASER_POINTER,
    SPOTLIGHT,
}

fun SmartBoardTool.usesDirectPointerInput(): Boolean = when (this) {
    SmartBoardTool.PEN,
    SmartBoardTool.PENCIL,
    SmartBoardTool.HIGHLIGHTER,
    SmartBoardTool.ERASER,
    SmartBoardTool.LASSO,
    SmartBoardTool.RECTANGLE_SELECT,
    -> true
    SmartBoardTool.PAN,
    SmartBoardTool.LASER_POINTER,
    SmartBoardTool.SPOTLIGHT,
    -> false
}

enum class SmartBoardInputMode { DRAW_WITH_FINGER, STYLUS_ONLY, FINGER_PANS }

fun SmartBoardInputMode.afterSelecting(tool: SmartBoardTool): SmartBoardInputMode =
    if (this == SmartBoardInputMode.FINGER_PANS && tool.usesDirectPointerInput()) {
        SmartBoardInputMode.DRAW_WITH_FINGER
    } else {
        this
    }

enum class MathExpressionType {
    NUMBER,
    ARITHMETIC,
    ALGEBRAIC_EXPRESSION,
    EQUATION,
    INEQUALITY,
    FUNCTION,
    CALCULUS,
    MATRIX,
    VECTOR,
    COORDINATE,
    STATISTICAL,
    SYSTEM,
    DERIVATIVE,
    INTEGRAL,
    LIMIT,
    DATASET,
    UNKNOWN,
}

enum class SmartBoardRecognitionMode { MANUAL_ONLY, SUGGEST_AFTER_PAUSE, AUTOMATIC }
enum class SmartBoardIntelligenceMode { MANUAL, ASSISTIVE, GUIDED_LEARNING, FAST_SOLVE, EXPLORATION }

enum class SmartBoardResultKind { CAS, STATISTICS, VERIFICATION, TUTOR, VISUAL_EXPLANATION }

enum class SmartBoardGraphKind { EXPLICIT_2D, IMPLICIT_2D, PARAMETRIC_2D, POLAR_2D, DATA_2D, SURFACE_3D }

enum class SolutionStepStatus { UNCHECKED, VALID, INVALID, UNCERTAIN }

enum class SmartBoardShapeType {
    LINE, HORIZONTAL_LINE, VERTICAL_LINE, DIAGONAL_LINE, LINE_SEGMENT, RAY,
    ARROW, DOUBLE_HEADED_ARROW, VECTOR_ARROW, FORCE_ARROW,
    CIRCLE, ELLIPSE, SQUARE, RECTANGLE, ROUNDED_RECTANGLE,
    TRIANGLE, RIGHT_TRIANGLE, EQUILATERAL_TRIANGLE,
    POLYGON, PENTAGON, HEXAGON, STAR,
    ARC, SEMICIRCLE, CURVE, CLOSED_REGION,
    COORDINATE_AXES, NUMBER_LINE, ANGLE, RIGHT_ANGLE_MARKER,
    PARALLEL_LINES, PERPENDICULAR_LINES,
    SPRING, RESISTOR, CIRCUIT_WIRE, NODE,
    FLOWCHART_CONNECTOR, TEXT_BOX, BRACKET, BRACE,
    TABLE_BOUNDARY, GRAPH_GRID, LAB_CONTAINER,
    CUBE, CUBOID, CYLINDER, CONE, SPHERE, PYRAMID,
}

data class SmartBoardPoint(val x: Float, val y: Float) {
    operator fun plus(other: SmartBoardPoint) = SmartBoardPoint(x + other.x, y + other.y)
    operator fun minus(other: SmartBoardPoint) = SmartBoardPoint(x - other.x, y - other.y)
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timestampMillis: Long,
) {
    init {
        require(x.isFinite() && y.isFinite())
        require(pressure.isFinite() && pressure >= 0f)
        require(timestampMillis >= 0L)
    }
    val position get() = SmartBoardPoint(x, y)
}

data class SmartBoardBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(left <= right && top <= bottom)
    }
    val width get() = right - left
    val height get() = bottom - top
    val center get() = SmartBoardPoint((left + right) / 2f, (top + bottom) / 2f)
    fun contains(point: SmartBoardPoint) = point.x in left..right && point.y in top..bottom
    fun intersects(other: SmartBoardBounds) =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
    fun translate(delta: SmartBoardPoint) = SmartBoardBounds(left + delta.x, top + delta.y, right + delta.x, bottom + delta.y)
    fun expand(padding: Float) = SmartBoardBounds(left - padding, top - padding, right + padding, bottom + padding)

    companion object {
        val Empty = SmartBoardBounds(0f, 0f, 0f, 0f)
        fun from(points: List<SmartBoardPoint>, padding: Float = 0f): SmartBoardBounds {
            if (points.isEmpty()) return Empty
            return SmartBoardBounds(
                points.minOf { it.x } - padding,
                points.minOf { it.y } - padding,
                points.maxOf { it.x } + padding,
                points.maxOf { it.y } + padding,
            )
        }
    }
}

data class SmartBoardViewport(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
) {
    init {
        require(panX.isFinite() && panY.isFinite())
        require(zoom.isFinite() && zoom in 0.1f..12f)
    }
}

sealed interface SmartBoardElement {
    val id: String
    val bounds: SmartBoardBounds
    val createdAt: Long
    val hidden: Boolean
    val subjectClassification: SmartBoardSubjectClassification? get() = null
}

enum class ChemistryExpressionType { ELEMENT_SYMBOL, FORMULA, ION, REACTION, ORGANIC_GROUP, CHEMICAL_NAME, UNKNOWN }
enum class EnglishTextType { WORD, SENTENCE, PARAGRAPH, HEADING, LIST, FILL_IN_BLANK, GRAMMAR_EXERCISE, VOCABULARY_NOTE, UNKNOWN }
enum class BiologyContentType { TERM, SHORT_ANSWER, LABELLED_DIAGRAM, CELL_DIAGRAM, ORGAN_DIAGRAM, PLANT_DIAGRAM, GENETICS, TAXONOMY, PROCESS_FLOW, TABLE, UNKNOWN }

data class BiologyLabelCandidate(val text: String, val confidence: Float?, val confirmed: Boolean = false)

data class ChemistryExpressionElement(
    override val id: String,
    val rawText: String,
    val normalizedChemicalNotation: String?,
    val expressionType: ChemistryExpressionType,
    val sourceStrokeIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val subjectClassification: SmartBoardSubjectClassification,
    override val hidden: Boolean = false,
) : SmartBoardElement

data class EnglishTextElement(
    override val id: String,
    val rawText: String,
    val correctedText: String?,
    val languageCode: String?,
    val textType: EnglishTextType,
    val sourceStrokeIds: List<String>,
    val lineBreaks: List<Int> = emptyList(),
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val subjectClassification: SmartBoardSubjectClassification,
    override val hidden: Boolean = false,
) : SmartBoardElement

data class BiologyContentElement(
    override val id: String,
    val recognizedText: String?,
    val contentType: BiologyContentType,
    val detectedLabels: List<BiologyLabelCandidate>,
    val sourceStrokeIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val subjectClassification: SmartBoardSubjectClassification,
    override val hidden: Boolean = false,
) : SmartBoardElement

data class StrokeElement(
    override val id: String,
    val points: List<StrokePoint>,
    val tool: StrokeTool,
    val width: Float,
    val opacity: Float,
    val argbColor: Long,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank())
        require(points.size >= 2)
        require(width > 0f && width.isFinite())
        require(opacity in 0f..1f)
    }
}

/**
 * A fitted vector object. [points] are document-space construction/path points and source ink is
 * retained by the conversion history command so recognition can always be undone.
 */
data class ShapeElement(
    override val id: String,
    val shapeType: SmartBoardShapeType,
    val points: List<SmartBoardPoint>,
    val sourceStrokeIds: List<String>,
    val recognitionConfidence: Float,
    val strokeWidth: Float,
    val argbColor: Long,
    val opacity: Float = 1f,
    val fillArgbColor: Long? = null,
    val rotationDegrees: Float = 0f,
    val locked: Boolean = false,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank())
        require(points.size >= 2)
        require(recognitionConfidence in 0f..1f)
        require(strokeWidth.isFinite() && strokeWidth > 0f)
        require(opacity in 0f..1f)
        require(rotationDegrees.isFinite())
    }
}

enum class SemanticMathNodeKind {
    NUMBER, VARIABLE, NEGATION, SUM, PRODUCT, POWER, FUNCTION,
    EQUATION, INEQUALITY, MATRIX, MATRIX_ROW, PIECEWISE, PIECEWISE_BRANCH,
    VECTOR, COORDINATE, UNKNOWN,
}

/**
 * A renderer-independent mathematical node. Node ids are stable paths inside the expression,
 * allowing the UI to target a subexpression without reparsing display LaTeX.
 */
data class SemanticMathNode(
    val id: String,
    val kind: SemanticMathNodeKind,
    val value: String? = null,
    val children: List<SemanticMathNode> = emptyList(),
    val sourceStrokeIds: List<String> = emptyList(),
    val confidence: Float? = null,
    val spokenForm: String = "",
) {
    init {
        require(id.isNotBlank())
        require(children.size <= 256)
        require(sourceStrokeIds.size <= 2_048)
        require(confidence == null || confidence in 0f..1f)
    }
}

data class SemanticExpressionTree(
    val root: SemanticMathNode,
    val authoredLatex: String,
    val engineExpression: String,
    val mathMl: String,
    val spokenForm: String,
    val parserVerified: Boolean,
    /** False means leaf-to-stroke associations are conservative spatial approximations. */
    val exactStrokeMapping: Boolean = false,
)

data class MathExpressionElement(
    override val id: String,
    val rawLatex: String,
    val correctedLatex: String?,
    val normalizedExpression: String?,
    val sourceStrokeIds: List<String>,
    val recognitionConfidence: Float?,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
    val semanticTree: SemanticExpressionTree? = null,
) : SmartBoardElement {
    init {
        require(id.isNotBlank())
        require(rawLatex.isNotBlank())
        require(recognitionConfidence == null || recognitionConfidence in 0f..1f)
    }
    val displayLatex get() = correctedLatex?.takeIf(String::isNotBlank) ?: rawLatex
}

data class TextElement(
    override val id: String,
    val text: String,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init { require(id.isNotBlank() && text.isNotBlank()) }
}

data class TableElement(
    override val id: String,
    val columnHeaders: List<String>,
    val rows: List<List<String>>,
    val sourceElementIds: List<String>,
    val firstRowIsHeader: Boolean = true,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank())
        require(columnHeaders.size in 1..64)
        require(rows.size <= 10_000)
        require(rows.all { it.size == columnHeaders.size })
        require(columnHeaders.all { it.length <= 512 })
        require(rows.all { row -> row.all { it.length <= 2_000 } })
        require(sourceElementIds.size <= 2_048)
    }
}

/**
 * Imported images are persisted by a private app-file reference. Pixel data is never embedded
 * in the Board document.
 */
data class ImageElement(
    override val id: String,
    val assetId: String,
    val relativePath: String,
    val mimeType: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val rotationDegrees: Int = 0,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && assetId.isNotBlank())
        require(relativePath.isNotBlank() && !relativePath.startsWith("/") && !relativePath.contains(".."))
        require(pixelWidth in 1..8192 && pixelHeight in 1..8192)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}

data class ActionResultElement(
    override val id: String,
    val kind: SmartBoardResultKind,
    val title: String,
    val exact: String?,
    val approximate: String?,
    val details: List<String>,
    val assumptions: List<String>,
    val sourceElementIds: List<String>,
    val verified: Boolean,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(details.size <= 100 && assumptions.size <= 50)
    }
}

data class GraphConfigurationElement(
    override val id: String,
    val graphKind: SmartBoardGraphKind,
    val expressions: List<String>,
    val sourceElementIds: List<String>,
    val moduleRoute: String,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
    val parameterValues: Map<String, Double> = emptyMap(),
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && expressions.isNotEmpty())
        require(expressions.size <= 32 && expressions.all { it.length <= 4_000 })
        require(moduleRoute.isNotBlank())
        require(parameterValues.size <= 32)
        require(parameterValues.all { (name, value) -> name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,31}")) && value.isFinite() })
    }
}

data class SolutionStep(
    val id: String,
    val expression: String,
    val sourceStrokeIds: List<String>,
    val confidence: Float?,
    val status: SolutionStepStatus = SolutionStepStatus.UNCHECKED,
    val feedback: String? = null,
) {
    init {
        require(id.isNotBlank() && expression.isNotBlank())
        require(confidence == null || confidence in 0f..1f)
    }
}

data class SolutionSequenceElement(
    override val id: String,
    val problemExpression: String,
    val steps: List<SolutionStep>,
    val firstInvalidStepIndex: Int?,
    val sourceRegionIds: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && problemExpression.isNotBlank())
        require(firstInvalidStepIndex == null || firstInvalidStepIndex in steps.indices)
    }
}

data class RecognitionRegion(
    val id: String,
    val bounds: SmartBoardBounds,
    val order: Int,
    val sourceElementIds: List<String>,
    val excluded: Boolean = false,
) {
    init { require(id.isNotBlank() && order >= 0) }
}

enum class SmartBoardRelationshipType {
    GROUP, RECOGNIZED_FROM, RECOGNIZED_AS, DESCRIBES, LABELS, DERIVED_FROM, SOLVES,
    EXPLAINS, REPRESENTS, PART_OF_PROBLEM, PART_OF_DIAGRAM, USES_FORMULA, USES_DATA,
    CROSS_SUBJECT_CONTEXT,
}

data class SmartBoardRelationship(
    val id: String,
    val type: SmartBoardRelationshipType,
    val elementIds: List<String>,
    val createdAt: Long,
) {
    init {
        require(id.isNotBlank())
        require(elementIds.isNotEmpty())
    }
}

data class SmartBoardDocument(
    val id: String,
    val title: String,
    val subject: SmartBoardSubject,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val viewport: SmartBoardViewport,
    val background: SmartBoardBackground,
    val elements: List<SmartBoardElement>,
    val relationships: List<SmartBoardRelationship> = emptyList(),
    val recognitionRegions: List<RecognitionRegion> = emptyList(),
    val subjectMode: SmartBoardSubjectMode = SmartBoardSubjectMode(subject, false, false, createdAt),
    val elementSubjectClassifications: Map<String, SmartBoardSubjectClassification> = emptyMap(),
    val elementConcepts: Map<String, SmartBoardConceptCandidate> = emptyMap(),
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(schemaVersion > 0)
        require(createdAt >= 0 && updatedAt >= createdAt)
        require(elements.map { it.id }.distinct().size == elements.size)
    }

    companion object {
        const val CurrentSchemaVersion = 8
        fun new(
            id: String,
            now: Long,
            title: String = "Untitled Board",
            subject: SmartBoardSubject = SmartBoardSubject.MATHEMATICS,
        ) = SmartBoardDocument(
            id = id,
            title = title,
            subject = subject,
            schemaVersion = CurrentSchemaVersion,
            createdAt = now,
            updatedAt = now,
            viewport = SmartBoardViewport(),
            background = SmartBoardBackground.GRID,
            elements = emptyList(),
        )
    }
}

data class SmartBoardPreferences(
    val inputMode: SmartBoardInputMode = SmartBoardInputMode.DRAW_WITH_FINGER,
    val pressureSensitivity: Boolean = true,
    val smoothingLevel: Int = 2,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val recognitionMode: SmartBoardRecognitionMode = SmartBoardRecognitionMode.SUGGEST_AFTER_PAUSE,
    val autoShapeEnabled: Boolean = true,
    val autoShapeDelayMillis: Int = 700,
    val intelligenceMode: SmartBoardIntelligenceMode = SmartBoardIntelligenceMode.ASSISTIVE,
    val intelligenceSuggestionsEnabled: Boolean = true,
    val recognitionPersonalizationEnabled: Boolean = false,
    val recognitionDiagnosticsEnabled: Boolean = false,
    val recognitionQualityTier: RecognitionQualityTier = RecognitionQualityTier.BALANCED,
) {
    init {
        require(smoothingLevel in 0..4)
        require(autoShapeDelayMillis in 300..3_000)
    }
}

enum class RecognitionQualityTier { FAST, BALANCED, ACCURATE }

sealed interface SmartBoardAction {
    data object InsertExpression : SmartBoardAction
    data object RetryRecognition : SmartBoardAction
    data object EditLatex : SmartBoardAction
    data class SubjectAction(val id: String, val label: String) : SmartBoardAction
}

data class SmartBoardRecognitionInput(
    val documentId: String,
    val subject: SmartBoardSubject,
    val strokeIds: List<String>,
    val strokes: List<StrokeElement>,
    val bounds: SmartBoardBounds,
    val rasterPng: ByteArray = byteArrayOf(),
    val requestedAt: Long,
)

data class SmartBoardSubjectAnalysis(
    val subject: SmartBoardSubject,
    val summary: String,
    val recognition: MathRecognitionResult?,
    val attributes: Map<String, String> = emptyMap(),
)

interface SmartBoardSubjectHandler {
    val subject: SmartBoardSubject
    suspend fun analyze(input: SmartBoardRecognitionInput): SmartBoardSubjectAnalysis
    fun supportedActions(analysis: SmartBoardSubjectAnalysis): List<SmartBoardAction>
}

data class MathRecognitionAlternative(val latex: String, val confidence: Float?)

data class MathRecognitionResult(
    val latex: String,
    val normalizedExpression: String?,
    val plainText: String?,
    val confidence: Float?,
    val alternatives: List<MathRecognitionAlternative>,
    val detectedType: MathExpressionType,
    val warnings: List<String>,
)
