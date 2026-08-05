package com.indianservers.smartboard.smartboard.audit

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement

enum class AuditCategory {
    BASIC_ARITHMETIC,
    ALGEBRAIC_EXPRESSIONS,
    EQUATIONS_INEQUALITIES,
    POWERS_SUBSCRIPTS_ROOTS,
    FRACTIONS_RATIONAL,
    COMPLEX_NUMBERS,
    LOG_EXP_SPECIAL,
    TRIGONOMETRY,
    CALCULUS,
    MATRICES_VECTORS,
    GRAPHS,
    GEOMETRY_DIAGRAMS,
    PROBABILITY_STATISTICS,
    SETS_LOGIC,
}

enum class AuditDifficulty { EASY, MEDIUM, HARD, EXTREME }

enum class HandwritingProfile {
    CLEAN_STUDENT,
    FAST_CLASSROOM,
    SMALL_COMPACT,
    LARGE_BOARD,
    RIGHT_SLANTED,
    LEFT_SLANTED,
    UNEVEN_BASELINE,
    VARIABLE_SPACING,
    HEAVY_PRESSURE,
    LIGHT_BROKEN,
    ROUNDED,
    ANGULAR,
    CROWDED,
    WIDELY_SPACED,
    OVERWRITTEN_CORRECTION,
    MIXED_CASE,
    UNUSUAL_STROKE_ORDER,
    SLIGHTLY_SHAKY,
}

enum class CanvasRegion {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    NEAR_BOUNDARY, NEAR_TOOLBAR,
}

enum class AuditStatus {
    PASS,
    PASS_WITH_NORMALIZATION,
    PARTIAL,
    WRONG_SYMBOL,
    WRONG_STRUCTURE,
    WRONG_LAYOUT,
    MISGROUPED,
    FALSE_POSITIVE,
    NOT_DETECTED,
    CRASH,
    TIMEOUT,
    UNSUPPORTED,
    MANUAL_REVIEW_REQUIRED,
}

enum class AuditErrorType {
    DIGIT_CONFUSION,
    LETTER_CONFUSION,
    GREEK_SYMBOL_CONFUSION,
    OPERATOR_CONFUSION,
    DECIMAL_POINT_MISSED,
    MINUS_SIGN_MISSED,
    EQUALS_SIGN_MISREAD,
    MULTIPLICATION_X_CONFUSION,
    SUPERSCRIPT_MISSED,
    SUBSCRIPT_MISSED,
    SUPERSCRIPT_WRONG_PARENT,
    SUBSCRIPT_WRONG_PARENT,
    FRACTION_MISREAD,
    FRACTION_SCOPE_ERROR,
    ROOT_SCOPE_ERROR,
    BRACKET_MISMATCH,
    FUNCTION_NAME_MISREAD,
    LOG_BASE_MISREAD,
    TRIG_INVERSE_MISREAD,
    INTEGRAL_LIMIT_MISREAD,
    SUMMATION_LIMIT_MISREAD,
    DERIVATIVE_STRUCTURE_ERROR,
    MATRIX_ROW_ERROR,
    MATRIX_COLUMN_ERROR,
    MATRIX_BRACKET_ERROR,
    MULTILINE_GROUPING_ERROR,
    EXPRESSION_SPLIT,
    EXPRESSIONS_MERGED,
    GRAPH_NOT_DETECTED,
    GRAPH_TYPE_WRONG,
    GRAPH_LABEL_MISREAD,
    GRAPH_SCALE_MISREAD,
    SHAPE_NOT_DETECTED,
    SHAPE_TYPE_WRONG,
    DIAGRAM_LABEL_MISREAD,
    STROKE_ORDER_SENSITIVE,
    SIZE_SENSITIVE,
    LOCATION_SENSITIVE,
    CROWDING_SENSITIVE,
    OVERWRITING_ERROR,
    LOW_CONFIDENCE,
    FALSE_POSITIVE,
    TIMEOUT,
    CRASH,
}

data class ExpectedGraph(
    val type: String,
    val equation: String,
    val keyPoints: List<String> = emptyList(),
    val intercepts: List<String> = emptyList(),
    val turningPoints: List<String> = emptyList(),
    val asymptotes: List<String> = emptyList(),
    val axisLabels: List<String> = listOf("x", "y"),
    val scale: String = "1 unit",
    val quadrants: Set<Int> = emptySet(),
)

data class ExpectedDiagram(
    val shapeType: String,
    val labels: List<String> = emptyList(),
    val measurements: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
)

data class SmartBoardAuditCase(
    val id: String,
    val category: AuditCategory,
    val subcategory: String,
    val difficulty: AuditDifficulty,
    val expectedPlainText: String?,
    val expectedLatex: String?,
    val expectedStructure: String?,
    val expectedGraph: ExpectedGraph?,
    val expectedDiagram: ExpectedDiagram?,
    val handwritingProfile: HandwritingProfile,
    val strokeVariant: String,
    val canvasRegion: CanvasRegion,
    val tags: Set<String>,
)

data class SmartBoardAuditResult(
    val caseId: String,
    val rawRecognitionOutput: String?,
    val normalizedRecognitionOutput: String?,
    val detectedLatex: String?,
    val detectedObjects: List<String>,
    val confidence: Float?,
    val recognitionTimeMs: Long,
    val exactMatch: Boolean,
    val semanticMatch: Boolean,
    val structureMatch: Boolean,
    val layoutMatch: Boolean,
    val symbolScore: Double,
    val structureScore: Double,
    val spatialScore: Double,
    val semanticScore: Double,
    val overallScore: Double,
    val status: AuditStatus,
    val errorTypes: Set<AuditErrorType>,
    val evidencePath: String?,
    val notes: String?,
)

data class AuditInput(
    val case: SmartBoardAuditCase,
    val strokes: List<StrokeElement>,
    val bounds: SmartBoardBounds,
)

data class AuditThresholds(
    val overallPass: Double = .90,
    val easyPass: Double = .98,
    val mediumPass: Double = .93,
    val hardPass: Double = .85,
    val symbolAccuracy: Double = .98,
    val structuralAccuracy: Double = .92,
    val superscriptSubscriptAccuracy: Double = .90,
    val fractionStructureAccuracy: Double = .92,
    val matrixStructureAccuracy: Double = .88,
    val graphClassificationAccuracy: Double = .85,
    val geometryClassificationAccuracy: Double = .85,
)
