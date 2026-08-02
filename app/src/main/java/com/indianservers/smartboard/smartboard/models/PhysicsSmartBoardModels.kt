package com.indianservers.smartboard.smartboard.models

enum class PhysicsContentType {
    FORMULA, NUMERICAL_PROBLEM, KNOWN_VALUES, DERIVATION, VECTOR, MOTION_DIAGRAM,
    FREE_BODY_DIAGRAM, CIRCUIT_DIAGRAM, RAY_DIAGRAM, WAVE_DIAGRAM, FIELD_DIAGRAM,
    EXPERIMENTAL_DATA, GRAPH, UNIT_EXPRESSION, TEXT_STATEMENT, UNKNOWN,
}

enum class PhysicsTopic {
    UNITS_MEASUREMENT, KINEMATICS, DYNAMICS, WORK_ENERGY_POWER, MOMENTUM_COLLISIONS,
    CIRCULAR_MOTION, GRAVITATION, PROPERTIES_OF_MATTER, FLUID_MECHANICS, OSCILLATIONS,
    WAVES, SOUND, THERMAL_PHYSICS, ELECTROSTATICS, CURRENT_ELECTRICITY, MAGNETISM,
    ELECTROMAGNETIC_INDUCTION, ALTERNATING_CURRENT, ELECTROMAGNETIC_WAVES, RAY_OPTICS,
    WAVE_OPTICS, MODERN_PHYSICS, ATOMIC_PHYSICS, NUCLEAR_PHYSICS,
    SEMICONDUCTOR_ELECTRONICS, EXPERIMENTAL_PHYSICS, UNKNOWN,
}

enum class PhysicsDiagramType { FREE_BODY, MOTION, CIRCUIT, RAY, WAVE, FIELD, EXPERIMENTAL_SETUP, UNKNOWN }

enum class PhysicsActionType {
    IDENTIFY_QUANTITIES, EXPLAIN_FORMULA, REARRANGE_FORMULA, CHECK_DIMENSIONS,
    CONVERT_TO_SI, CONVERT_UNITS, SUBSTITUTE_VALUES, SOLVE_NUMERICAL, VERIFY_WORK,
    CHECK_SIGNIFICANT_FIGURES, ANALYZE_UNCERTAINTY, DRAW_GRAPH, ANALYZE_VECTOR,
    OPEN_2D, OPEN_3D, OPEN_CIRCUIT, OPEN_WAVE, OPEN_OPTICS, REVIEW_DIAGRAM,
    ANALYZE_EXPERIMENT, TUTOR_HINT, NEXT_STEP,
}

enum class PhysicsResultStatus { VERIFIED, PARTIALLY_VERIFIED, NEEDS_CONFIRMATION, UNSUPPORTED }
enum class DimensionalStatus { CONSISTENT, INCONSISTENT, AMBIGUOUS, UNSUPPORTED }
enum class PhysicsVerificationStatus { VALID, INVALID, UNCERTAIN }

data class PhysicalDimension(val powers: Map<String, Int> = emptyMap()) {
    operator fun times(other: PhysicalDimension) = PhysicalDimension(
        (powers.keys + other.powers.keys).associateWith { (powers[it] ?: 0) + (other.powers[it] ?: 0) }.filterValues { it != 0 },
    )
    operator fun div(other: PhysicalDimension) = PhysicalDimension(
        (powers.keys + other.powers.keys).associateWith { (powers[it] ?: 0) - (other.powers[it] ?: 0) }.filterValues { it != 0 },
    )
    fun pow(exponent: Int) = PhysicalDimension(powers.mapValues { it.value * exponent }.filterValues { it != 0 })
    fun spoken() = if (powers.isEmpty()) "dimensionless" else powers.entries.sortedBy(Map.Entry<String, Int>::key)
        .joinToString(" ") { (symbol, power) -> "$symbol${if (power == 1) "" else "^$power"}" }
}

data class MeasurementUncertainty(
    val absolute: Double,
    val relative: Double?,
    val percentage: Double?,
) {
    init { require(absolute >= 0.0 && absolute.isFinite()) }
}

data class PhysicsVectorValue(val components: List<Double>, val unit: String?, val directionDegrees: Double?) {
    init { require(components.size in 2..3 && components.all(Double::isFinite)) }
}

data class PhysicalQuantity(
    val id: String,
    val symbol: String,
    val canonicalName: String?,
    val scalarValue: Double?,
    val exactValue: String?,
    val unitSymbol: String?,
    val dimension: PhysicalDimension?,
    val uncertainty: MeasurementUncertainty?,
    val vector: PhysicsVectorValue?,
    val sourceElementId: String?,
    val confidence: Float?,
) {
    init {
        require(id.isNotBlank() && symbol.isNotBlank())
        require(scalarValue == null || scalarValue.isFinite())
        require(confidence == null || confidence in 0f..1f)
    }
}

data class PhysicsEquation(val source: String, val formulaId: String?, val confidence: Float?)
data class PhysicsKnownValue(val quantity: PhysicalQuantity, val confirmed: Boolean)
data class PhysicsAmbiguity(val token: String, val interpretations: List<String>, val message: String)
data class PhysicsDiagramObject(val id: String, val kind: String, val label: String?, val bounds: SmartBoardBounds, val confidence: Float?)
data class PhysicsDiagramRelation(val fromId: String, val toId: String, val relation: String)
data class PhysicsDiagramInference(val description: String, val confidence: Float, val requiresConfirmation: Boolean = true)
data class PhysicsDiagramAnalysis(
    val type: PhysicsDiagramType,
    val objects: List<PhysicsDiagramObject>,
    val confirmedRelations: List<PhysicsDiagramRelation>,
    val inferredRelations: List<PhysicsDiagramInference>,
    val confidence: Float?,
)

data class PhysicsBoardAnalysis(
    val contentType: PhysicsContentType,
    val topic: PhysicsTopic?,
    val quantities: List<PhysicalQuantity>,
    val equations: List<PhysicsEquation>,
    val knownValues: List<PhysicsKnownValue>,
    val unknownQuantities: List<PhysicalQuantity>,
    val diagrams: List<PhysicsDiagramAnalysis>,
    val suggestedActions: List<PhysicsActionType>,
    val ambiguities: List<PhysicsAmbiguity>,
    val warnings: List<String>,
)

data class DimensionTermResult(
    val term: String,
    val dimension: PhysicalDimension?,
    val compatible: Boolean?,
    val explanation: String,
)

data class DimensionalAnalysisResult(
    val status: DimensionalStatus,
    val leftDimension: PhysicalDimension?,
    val rightDimension: PhysicalDimension?,
    val termResults: List<DimensionTermResult>,
    val explanation: String,
    val warnings: List<String> = emptyList(),
)

data class PhysicsSolutionStep(val title: String, val expression: String, val explanation: String, val verified: Boolean)
data class PhysicsSubstitution(val symbol: String, val value: Double, val unitSymbol: String?)
data class PhysicsEngineMetadata(val engines: List<String>, val deterministic: Boolean)

data class PhysicsExpressionElement(
    override val id: String,
    val rawSource: String,
    val correctedSource: String?,
    val contentType: PhysicsContentType,
    val topic: PhysicsTopic?,
    val formulaId: String?,
    val sourceStrokeIds: List<String>,
    val recognitionConfidence: Float?,
    val ambiguities: List<String>,
    val warnings: List<String>,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && rawSource.isNotBlank())
        require(recognitionConfidence == null || recognitionConfidence in 0f..1f)
    }
    val displaySource get() = correctedSource?.takeIf(String::isNotBlank) ?: rawSource
}

data class PhysicsResultElement(
    override val id: String,
    val sourceElementIds: List<String>,
    val actionType: PhysicsActionType,
    val title: String,
    val formulaLatex: String?,
    val rearrangedFormulaLatex: String?,
    val substitutions: List<PhysicsSubstitution>,
    val exactResultLatex: String?,
    val numericalResult: Double?,
    val resultUnitSymbol: String?,
    val significantFigures: Int?,
    val steps: List<PhysicsSolutionStep>,
    val assumptions: List<String>,
    val warnings: List<String>,
    val engineMetadata: PhysicsEngineMetadata,
    val status: PhysicsResultStatus,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(numericalResult == null || numericalResult.isFinite())
        require(significantFigures == null || significantFigures in 1..15)
    }
}

data class PhysicsDiagramElement(
    override val id: String,
    val diagramType: PhysicsDiagramType,
    val sourceStrokeIds: List<String>,
    val detectedObjects: List<PhysicsDiagramObject>,
    val confirmedRelations: List<PhysicsDiagramRelation>,
    val inferredRelations: List<PhysicsDiagramInference>,
    val confidence: Float?,
    override val bounds: SmartBoardBounds,
    override val createdAt: Long,
    override val hidden: Boolean = false,
) : SmartBoardElement {
    init { require(id.isNotBlank() && (confidence == null || confidence in 0f..1f)) }
}
