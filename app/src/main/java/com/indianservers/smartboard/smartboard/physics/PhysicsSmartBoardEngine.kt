package com.indianservers.smartboard.smartboard.physics

import com.indianservers.smartboard.core.AdvancedStatisticsEngine
import com.indianservers.smartboard.core.MathProblemSolver
import com.indianservers.smartboard.core.SymbolicCasEngine
import com.indianservers.smartboard.core.SymbolicExpression
import com.indianservers.smartboard.core.Vec3
import com.indianservers.smartboard.physics.formulas.model.PhysicsFormula
import com.indianservers.smartboard.physics.formulas.model.PhysicsFormulaFilters
import com.indianservers.smartboard.physics.formulas.model.PhysicsFormulaLevel
import com.indianservers.smartboard.physics.formulas.repository.OfflinePhysicsFormulaRepository
import com.indianservers.smartboard.physics.formulas.repository.PhysicsFormulaRepository
import com.indianservers.smartboard.physics.formulas.units.PhysicsUnit
import com.indianservers.smartboard.physics.formulas.units.PhysicsUnitDimension
import com.indianservers.smartboard.physics.formulas.units.PhysicsUnitSystem
import com.indianservers.smartboard.smartboard.models.DimensionTermResult
import com.indianservers.smartboard.smartboard.models.DimensionalAnalysisResult
import com.indianservers.smartboard.smartboard.models.DimensionalStatus
import com.indianservers.smartboard.smartboard.models.MeasurementUncertainty
import com.indianservers.smartboard.smartboard.models.PhysicalDimension
import com.indianservers.smartboard.smartboard.models.PhysicalQuantity
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsAmbiguity
import com.indianservers.smartboard.smartboard.models.PhysicsBoardAnalysis
import com.indianservers.smartboard.smartboard.models.PhysicsContentType
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramAnalysis
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramInference
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramType
import com.indianservers.smartboard.smartboard.models.PhysicsEngineMetadata
import com.indianservers.smartboard.smartboard.models.PhysicsEquation
import com.indianservers.smartboard.smartboard.models.PhysicsKnownValue
import com.indianservers.smartboard.smartboard.models.PhysicsResultStatus
import com.indianservers.smartboard.smartboard.models.PhysicsSolutionStep
import com.indianservers.smartboard.smartboard.models.PhysicsSubstitution
import com.indianservers.smartboard.smartboard.models.PhysicsTopic
import com.indianservers.smartboard.smartboard.models.PhysicsVectorValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class UnitParseResult(val unit: PhysicsUnit?, val normalized: String, val ambiguous: Boolean, val alternatives: List<String>, val message: String?)
data class UnitConversionResult(val value: Double, val from: PhysicsUnit, val to: PhysicsUnit)

interface SmartBoardPhysicsUnitAdapter {
    fun parseUnit(input: String): UnitParseResult
    fun convert(value: Double, from: PhysicsUnit, to: PhysicsUnit): UnitConversionResult
    fun dimensionOf(unit: PhysicsUnit): PhysicalDimension
    fun areCompatible(first: PhysicsUnit, second: PhysicsUnit): Boolean
}

class ExistingPhysicsUnitAdapter : SmartBoardPhysicsUnitAdapter {
    private val cache = ConcurrentHashMap<String, UnitParseResult>()
    override fun parseUnit(input: String): UnitParseResult = cache.getOrPut(input.trim()) {
        val normalized = normalizeUnit(input)
        val matches = PhysicsUnitSystem.units.filter { normalizeUnit(it.symbol) == normalized || it.name.equals(input.trim(), true) }
        when {
            matches.size == 1 -> UnitParseResult(matches.single(), normalized, false, emptyList(), null)
            matches.size > 1 -> UnitParseResult(null, normalized, true, matches.map(PhysicsUnit::name), "Select the intended physical unit.")
            else -> UnitParseResult(null, normalized, false, emptyList(), "Unknown Physics unit: ${input.trim()}")
        }
    }

    override fun convert(value: Double, from: PhysicsUnit, to: PhysicsUnit): UnitConversionResult {
        require(areCompatible(from, to)) { "Units must describe the same physical dimension." }
        return UnitConversionResult(PhysicsUnitSystem.convert(value, from.symbol, to.symbol), from, to)
    }

    override fun dimensionOf(unit: PhysicsUnit): PhysicalDimension = dimension(unit.dimension)
    override fun areCompatible(first: PhysicsUnit, second: PhysicsUnit) = first.dimension == second.dimension

    private fun normalizeUnit(value: String) = value.trim()
        .replace("Â²", "²").replace("^2", "²").replace("sec", "s", true)
        .replace("ohm", "Ω", true).replace("Î©", "Ω").replace("Â·", "·")
        .replace(" ", "")
}

object PhysicsDimensions {
    val Scalar = PhysicalDimension()
    val Length = PhysicalDimension(mapOf("L" to 1))
    val Mass = PhysicalDimension(mapOf("M" to 1))
    val Time = PhysicalDimension(mapOf("T" to 1))
    val Current = PhysicalDimension(mapOf("I" to 1))
    val Temperature = PhysicalDimension(mapOf("Θ" to 1))
    val Amount = PhysicalDimension(mapOf("N" to 1))
    val LuminousIntensity = PhysicalDimension(mapOf("J" to 1))
    val Velocity = Length / Time
    val Acceleration = Velocity / Time
    val Force = Mass * Acceleration
    val Energy = Force * Length
    val Power = Energy / Time
}

private fun dimension(value: PhysicsUnitDimension): PhysicalDimension = when (value) {
    PhysicsUnitDimension.Length -> PhysicsDimensions.Length
    PhysicsUnitDimension.Time -> PhysicsDimensions.Time
    PhysicsUnitDimension.Mass -> PhysicsDimensions.Mass
    PhysicsUnitDimension.Velocity -> PhysicsDimensions.Velocity
    PhysicsUnitDimension.Acceleration -> PhysicsDimensions.Acceleration
    PhysicsUnitDimension.Force -> PhysicsDimensions.Force
    PhysicsUnitDimension.Energy, PhysicsUnitDimension.Torque -> PhysicsDimensions.Energy
    PhysicsUnitDimension.Power -> PhysicsDimensions.Power
    PhysicsUnitDimension.Pressure -> PhysicsDimensions.Force / PhysicsDimensions.Length.pow(2)
    PhysicsUnitDimension.Charge -> PhysicsDimensions.Current * PhysicsDimensions.Time
    PhysicsUnitDimension.Current -> PhysicsDimensions.Current
    PhysicsUnitDimension.Voltage -> PhysicsDimensions.Power / PhysicsDimensions.Current
    PhysicsUnitDimension.Resistance -> PhysicsDimensions.Power / PhysicsDimensions.Current.pow(2)
    PhysicsUnitDimension.Capacitance -> (PhysicsDimensions.Current * PhysicsDimensions.Time) / (PhysicsDimensions.Power / PhysicsDimensions.Current)
    PhysicsUnitDimension.MagneticField -> PhysicsDimensions.Force / (PhysicsDimensions.Current * PhysicsDimensions.Length)
    PhysicsUnitDimension.Frequency -> PhysicsDimensions.Time.pow(-1)
    PhysicsUnitDimension.Temperature -> PhysicsDimensions.Temperature
    PhysicsUnitDimension.Angle, PhysicsUnitDimension.Dimensionless -> PhysicsDimensions.Scalar
    PhysicsUnitDimension.Momentum -> PhysicsDimensions.Mass * PhysicsDimensions.Velocity
}

class PhysicsFormulaMatcher(private val repository: PhysicsFormulaRepository = OfflinePhysicsFormulaRepository()) {
    private val cache = ConcurrentHashMap<String, PhysicsFormula>()

    fun match(source: String): PhysicsFormula? {
        val key = normalize(source)
        cache[key]?.let { return it }
        val normalized = normalize(source.substringBefore('\n'))
        val all = repository.search("", PhysicsFormulaFilters(level = PhysicsFormulaLevel.Postgraduate))
        return all.maxByOrNull { formula -> similarity(normalized, normalize(formula.equation)) }
            ?.takeIf { similarity(normalized, normalize(it.equation)) >= .62 }
            ?.also { cache[key] = it }
    }

    fun selectFor(quantities: List<PhysicalQuantity>): PhysicsFormula? {
        val symbols = quantities.map(PhysicalQuantity::symbol).toSet()
        if (symbols.isEmpty()) return null
        return repository.search("", PhysicsFormulaFilters(level = PhysicsFormulaLevel.Postgraduate))
            .filter { formula -> symbols.all { symbol -> formula.variables.any { it.symbol == symbol } } }
            .minByOrNull { formula -> formula.variables.size - symbols.size }
    }

    private fun similarity(first: String, second: String): Double {
        if (first == second) return 1.0
        val left = first.split(Regex("[=+\\-*/^()]")).filter(String::isNotBlank).toSet()
        val right = second.split(Regex("[=+\\-*/^()]")).filter(String::isNotBlank).toSet()
        val union = left + right
        return if (union.isEmpty()) 0.0 else (left intersect right).size.toDouble() / union.size
    }

    companion object {
        fun normalize(value: String) = value.lowercase(Locale.ROOT)
            .replace(" ", "").replace("×", "*").replace("·", "*").replace("Â·", "*")
            .replace("²", "^2").replace("Â²", "^2").replace("½", "1/2")
            .replace("Δ", "d").replace("Î”", "d").replace("λ", "lambda").replace("Î»", "lambda")
            .replace("π", "pi").replace("Ï€", "pi").replace("Ω", "ohm").replace("Î©", "ohm")
    }
}

class PhysicsQuantityParser(private val units: SmartBoardPhysicsUnitAdapter = ExistingPhysicsUnitAdapter()) {
    private val assignment = Regex(
        """(?m)([A-Za-zΔλθρτΦε][A-Za-z0-9₀-₉_ΔλθρτΦε]*)\s*=\s*(\?|[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)\s*(?:±\s*([0-9.]+)\s*)?([A-Za-z°ΩÂÎ/·²^0-9-]+)?""",
    )

    fun parse(source: String, confidence: Float? = null): List<PhysicalQuantity> = assignment.findAll(source).mapIndexed { index, match ->
        val symbol = match.groupValues[1]
        val valueText = match.groupValues[2]
        val value = valueText.takeUnless { it == "?" }?.toDoubleOrNull()
        val unitText = match.groupValues[4].takeIf(String::isNotBlank)
        val unit = unitText?.let(units::parseUnit)?.unit
        val absolute = match.groupValues[3].toDoubleOrNull()
        PhysicalQuantity(
            id = "quantity-$index-$symbol",
            symbol = symbol,
            canonicalName = null,
            scalarValue = value,
            exactValue = valueText.takeUnless { it == "?" },
            unitSymbol = unit?.symbol ?: unitText,
            dimension = unit?.let(units::dimensionOf),
            uncertainty = absolute?.let { MeasurementUncertainty(it, value?.takeIf { it != 0.0 }?.let { v -> it / abs(v) }, value?.takeIf { it != 0.0 }?.let { v -> it / abs(v) * 100 }) },
            vector = null,
            sourceElementId = null,
            confidence = confidence,
        )
    }.toList()
}

class PhysicsActionResolver {
    fun resolve(type: PhysicsContentType, hasAmbiguity: Boolean): List<PhysicsActionType> {
        if (hasAmbiguity) return listOf(PhysicsActionType.IDENTIFY_QUANTITIES)
        return when (type) {
            PhysicsContentType.FORMULA -> listOf(
                PhysicsActionType.IDENTIFY_QUANTITIES, PhysicsActionType.EXPLAIN_FORMULA,
                PhysicsActionType.REARRANGE_FORMULA, PhysicsActionType.CHECK_DIMENSIONS,
                PhysicsActionType.SUBSTITUTE_VALUES, PhysicsActionType.DRAW_GRAPH,
            )
            PhysicsContentType.NUMERICAL_PROBLEM, PhysicsContentType.KNOWN_VALUES -> listOf(
                PhysicsActionType.IDENTIFY_QUANTITIES, PhysicsActionType.CONVERT_TO_SI,
                PhysicsActionType.SOLVE_NUMERICAL, PhysicsActionType.CHECK_DIMENSIONS,
                PhysicsActionType.CHECK_SIGNIFICANT_FIGURES,
            )
            PhysicsContentType.VECTOR -> listOf(PhysicsActionType.ANALYZE_VECTOR, PhysicsActionType.OPEN_2D, PhysicsActionType.OPEN_3D)
            PhysicsContentType.EXPERIMENTAL_DATA -> listOf(PhysicsActionType.ANALYZE_EXPERIMENT, PhysicsActionType.DRAW_GRAPH, PhysicsActionType.ANALYZE_UNCERTAINTY)
            PhysicsContentType.FREE_BODY_DIAGRAM, PhysicsContentType.MOTION_DIAGRAM -> listOf(PhysicsActionType.REVIEW_DIAGRAM, PhysicsActionType.OPEN_2D)
            PhysicsContentType.CIRCUIT_DIAGRAM -> listOf(PhysicsActionType.REVIEW_DIAGRAM, PhysicsActionType.OPEN_CIRCUIT)
            PhysicsContentType.RAY_DIAGRAM -> listOf(PhysicsActionType.REVIEW_DIAGRAM, PhysicsActionType.OPEN_OPTICS)
            PhysicsContentType.WAVE_DIAGRAM -> listOf(PhysicsActionType.REVIEW_DIAGRAM, PhysicsActionType.OPEN_WAVE)
            PhysicsContentType.FIELD_DIAGRAM -> listOf(PhysicsActionType.REVIEW_DIAGRAM, PhysicsActionType.OPEN_2D)
            PhysicsContentType.UNIT_EXPRESSION -> listOf(PhysicsActionType.CONVERT_UNITS, PhysicsActionType.CHECK_DIMENSIONS)
            else -> listOf(PhysicsActionType.IDENTIFY_QUANTITIES)
        }
    }
}

class PhysicsBoardAnalyzer(
    private val matcher: PhysicsFormulaMatcher = PhysicsFormulaMatcher(),
    private val quantityParser: PhysicsQuantityParser = PhysicsQuantityParser(),
    private val actions: PhysicsActionResolver = PhysicsActionResolver(),
) {
    fun analyze(source: String, confidence: Float? = null): PhysicsBoardAnalysis {
        val quantities = quantityParser.parse(source, confidence)
        val formula = matcher.match(source) ?: matcher.selectFor(quantities)
        val diagram = PhysicsDiagramClassifier.classify(source)
        val type = when {
            diagram != null -> diagram.type.toContentType()
            quantities.size >= 2 && quantities.any { it.scalarValue == null } -> PhysicsContentType.NUMERICAL_PROBLEM
            quantities.isNotEmpty() && '=' !in source.substringAfterLast('\n') -> PhysicsContentType.KNOWN_VALUES
            looksLikeVector(source) -> PhysicsContentType.VECTOR
            looksLikeDataset(source) -> PhysicsContentType.EXPERIMENTAL_DATA
            '=' in source -> PhysicsContentType.FORMULA
            quantities.any { it.unitSymbol != null } -> PhysicsContentType.UNIT_EXPRESSION
            source.any(Char::isLetter) -> PhysicsContentType.TEXT_STATEMENT
            else -> PhysicsContentType.UNKNOWN
        }
        val ambiguities = quantityAmbiguities(quantities, formula)
        val unknown = quantities.filter { it.scalarValue == null }
        return PhysicsBoardAnalysis(
            contentType = type,
            topic = formula?.categoryId?.let(::topicForCategory),
            quantities = quantities.map { quantity ->
                val variable = formula?.variables?.firstOrNull { it.symbol == quantity.symbol }
                quantity.copy(canonicalName = variable?.meaning ?: variable?.spokenName)
            },
            equations = formula?.let { listOf(PhysicsEquation(it.equation, it.id, confidence)) }.orEmpty(),
            knownValues = quantities.filter { it.scalarValue != null }.map { PhysicsKnownValue(it, confirmed = false) },
            unknownQuantities = unknown,
            diagrams = listOfNotNull(diagram),
            suggestedActions = actions.resolve(type, ambiguities.isNotEmpty()),
            ambiguities = ambiguities,
            warnings = buildList {
                if (confidence != null && confidence < .65f) add("Recognition confidence is low; confirm symbols and units.")
                if (formula == null && type in setOf(PhysicsContentType.FORMULA, PhysicsContentType.NUMERICAL_PROBLEM)) add("Confirm the applicable formula before solving.")
            },
        )
    }

    private fun quantityAmbiguities(quantities: List<PhysicalQuantity>, formula: PhysicsFormula?): List<PhysicsAmbiguity> =
        quantities.filter { it.symbol in setOf("m", "s", "V", "T") && formula?.variables?.none { variable -> variable.symbol == it.symbol } != false }
            .map {
                val alternatives = when (it.symbol) {
                    "m" -> listOf("mass", "metre", "milli-prefix")
                    "s" -> listOf("displacement", "second")
                    "V" -> listOf("voltage", "volume")
                    else -> listOf("temperature", "period", "tesla")
                }
                PhysicsAmbiguity(it.symbol, alternatives, "Context does not uniquely determine ${it.symbol}.")
            }

    private fun looksLikeVector(source: String) = Regex("""\([^)]+,[^)]+(?:,[^)]+)?\)|\d+\s*[ijk](?:\s*[+-]\s*\d+\s*[ijk])+""").containsMatchIn(source)
    private fun looksLikeDataset(source: String) = source.lines().count { line -> line.split(Regex("\\s+|,")).count { it.toDoubleOrNull() != null } >= 2 } >= 2
}

object PhysicsDiagramClassifier {
    fun classify(labelOrContext: String): PhysicsDiagramAnalysis? {
        val lower = labelOrContext.lowercase()
        val type = when {
            Regex("\\b(free.?body|normal|friction|tension|weight arrow)\\b").containsMatchIn(lower) -> PhysicsDiagramType.FREE_BODY
            Regex("\\b(circuit|battery|resistor|ammeter|voltmeter|switch)\\b").containsMatchIn(lower) -> PhysicsDiagramType.CIRCUIT
            Regex("\\b(ray|lens|mirror|focal|refraction|reflection)\\b").containsMatchIn(lower) -> PhysicsDiagramType.RAY
            Regex("\\b(wave|crest|trough|wavelength|amplitude)\\b").containsMatchIn(lower) -> PhysicsDiagramType.WAVE
            Regex("\\b(field line|charge|magnetic pole|equipotential)\\b").containsMatchIn(lower) -> PhysicsDiagramType.FIELD
            Regex("\\b(motion diagram|position dots|velocity arrow)\\b").containsMatchIn(lower) -> PhysicsDiagramType.MOTION
            else -> return null
        }
        return PhysicsDiagramAnalysis(
            type,
            emptyList(),
            emptyList(),
            listOf(PhysicsDiagramInference("Diagram type inferred from confirmed labels; review objects and connectivity.", .65f)),
            .65f,
        )
    }
}

private fun PhysicsDiagramType.toContentType() = when (this) {
    PhysicsDiagramType.FREE_BODY -> PhysicsContentType.FREE_BODY_DIAGRAM
    PhysicsDiagramType.MOTION -> PhysicsContentType.MOTION_DIAGRAM
    PhysicsDiagramType.CIRCUIT -> PhysicsContentType.CIRCUIT_DIAGRAM
    PhysicsDiagramType.RAY -> PhysicsContentType.RAY_DIAGRAM
    PhysicsDiagramType.WAVE -> PhysicsContentType.WAVE_DIAGRAM
    PhysicsDiagramType.FIELD -> PhysicsContentType.FIELD_DIAGRAM
    PhysicsDiagramType.EXPERIMENTAL_SETUP -> PhysicsContentType.EXPERIMENTAL_DATA
    PhysicsDiagramType.UNKNOWN -> PhysicsContentType.UNKNOWN
}

private fun topicForCategory(category: String): PhysicsTopic = when (category) {
    "measurements-units" -> PhysicsTopic.UNITS_MEASUREMENT
    "motion-kinematics" -> PhysicsTopic.KINEMATICS
    "newtonian-mechanics" -> PhysicsTopic.DYNAMICS
    "work-energy-power" -> PhysicsTopic.WORK_ENERGY_POWER
    "momentum-collisions" -> PhysicsTopic.MOMENTUM_COLLISIONS
    "gravitation" -> PhysicsTopic.GRAVITATION
    "properties-matter" -> PhysicsTopic.PROPERTIES_OF_MATTER
    "fluids" -> PhysicsTopic.FLUID_MECHANICS
    "oscillations" -> PhysicsTopic.OSCILLATIONS
    "waves" -> PhysicsTopic.WAVES
    "sound" -> PhysicsTopic.SOUND
    "thermodynamics" -> PhysicsTopic.THERMAL_PHYSICS
    "electrostatics" -> PhysicsTopic.ELECTROSTATICS
    "current-electricity" -> PhysicsTopic.CURRENT_ELECTRICITY
    "magnetism" -> PhysicsTopic.MAGNETISM
    "electromagnetic-induction" -> PhysicsTopic.ELECTROMAGNETIC_INDUCTION
    "alternating-current" -> PhysicsTopic.ALTERNATING_CURRENT
    "electromagnetic-waves" -> PhysicsTopic.ELECTROMAGNETIC_WAVES
    "ray-optics" -> PhysicsTopic.RAY_OPTICS
    "wave-optics" -> PhysicsTopic.WAVE_OPTICS
    "modern-physics" -> PhysicsTopic.MODERN_PHYSICS
    "nuclear-physics" -> PhysicsTopic.NUCLEAR_PHYSICS
    "electronics" -> PhysicsTopic.SEMICONDUCTOR_ELECTRONICS
    else -> PhysicsTopic.UNKNOWN
}

class PhysicsDimensionalAnalyzer(
    private val cas: SymbolicCasEngine = SymbolicCasEngine(),
    private val matcher: PhysicsFormulaMatcher = PhysicsFormulaMatcher(),
) {
    fun check(equation: String): DimensionalAnalysisResult {
        val parts = equation.split('=', limit = 2)
        if (parts.size != 2) return unsupported("Enter one equation with an equals sign.")
        val formula = matcher.match(equation)
        val dimensions = buildMap {
            commonDimensions().forEach { (symbol, dimension) -> put(symbol, dimension) }
            formula?.variables?.forEach { variable ->
                parseDimension(variable.dimension)?.let { putIfAbsent(normalizeSymbol(variable.symbol), it) }
            }
        }
        return runCatching {
            val leftSource = normalizeExpression(parts[0])
            val rightSource = normalizeExpression(parts[1])
            val left = infer(cas.parse(leftSource), dimensions)
            val right = infer(cas.parse(rightSource), dimensions)
            val status = when {
                left.dimension == null || right.dimension == null || left.ambiguous || right.ambiguous -> DimensionalStatus.AMBIGUOUS
                left.dimension == right.dimension -> DimensionalStatus.CONSISTENT
                else -> DimensionalStatus.INCONSISTENT
            }
            DimensionalAnalysisResult(
                status,
                left.dimension,
                right.dimension,
                left.terms + right.terms,
                when (status) {
                    DimensionalStatus.CONSISTENT -> "Both sides have ${left.dimension?.spoken()}. Dimensional consistency is necessary but does not prove physical correctness."
                    DimensionalStatus.INCONSISTENT -> "The two sides have different physical dimensions."
                    DimensionalStatus.AMBIGUOUS -> "Confirm ambiguous symbols or units before deciding dimensional consistency."
                    DimensionalStatus.UNSUPPORTED -> "The equation contains unsupported dimensional operations."
                },
            )
        }.getOrElse { unsupported(it.message ?: "The equation could not be analyzed.") }
    }

    private data class Inferred(val dimension: PhysicalDimension?, val ambiguous: Boolean, val terms: List<DimensionTermResult>)

    private fun infer(expression: SymbolicExpression, symbols: Map<String, PhysicalDimension>): Inferred = when (expression) {
        is SymbolicExpression.Number -> Inferred(PhysicsDimensions.Scalar, false, emptyList())
        is SymbolicExpression.Variable -> symbols[normalizeSymbol(expression.name)]?.let { Inferred(it, false, emptyList()) }
            ?: Inferred(null, true, listOf(DimensionTermResult(expression.name, null, null, "Unknown symbol dimension.")))
        is SymbolicExpression.UnaryMinus -> infer(expression.value, symbols)
        is SymbolicExpression.Product -> expression.factors.map { infer(it, symbols) }.let { values ->
            Inferred(values.mapNotNull(Inferred::dimension).takeIf { it.size == values.size }?.fold(PhysicsDimensions.Scalar, PhysicalDimension::times),
                values.any(Inferred::ambiguous), values.flatMap(Inferred::terms))
        }
        is SymbolicExpression.Power -> {
            val base = infer(expression.base, symbols)
            val exponent = (expression.exponent as? SymbolicExpression.Number)?.value?.toDouble()?.toInt()
            Inferred(exponent?.let { base.dimension?.pow(it) }, base.ambiguous || exponent == null, base.terms)
        }
        is SymbolicExpression.Sum -> {
            val values = expression.terms.map { infer(it, symbols) }
            val known = values.mapNotNull(Inferred::dimension)
            val compatible = known.isNotEmpty() && known.distinct().size == 1 && known.size == values.size
            Inferred(
                known.firstOrNull().takeIf { compatible },
                values.any(Inferred::ambiguous) || !compatible,
                values.flatMap(Inferred::terms) + expression.terms.zip(values).map { (term, value) ->
                    DimensionTermResult(cas.render(term), value.dimension, compatible, if (compatible) "Term matches the sum dimension." else "Terms in a sum must have the same dimension.")
                },
            )
        }
        is SymbolicExpression.Function -> {
            val args = expression.arguments.map { infer(it, symbols) }
            val dimensionless = args.all { it.dimension == PhysicsDimensions.Scalar }
            Inferred(PhysicsDimensions.Scalar.takeIf { dimensionless }, args.any(Inferred::ambiguous) || !dimensionless, args.flatMap(Inferred::terms))
        }
    }

    private fun normalizeExpression(value: String): String {
        var result = PhysicsFormulaMatcher.normalize(value)
        listOf("ut" to "u*t", "at" to "a*t", "ma" to "m*a", "ir" to "i*r", "vi" to "v*i", "flambda" to "f*lambda").forEach { (from, to) ->
            if (result == from || result.contains("+$from") || result.contains("-$from")) result = result.replace(from, to)
        }
        return result
    }

    private fun normalizeSymbol(value: String) = PhysicsFormulaMatcher.normalize(value)
    private fun parseDimension(value: String?): PhysicalDimension? {
        val source = value ?: return null
        val powers = mutableMapOf<String, Int>()
        Regex("""([MLTIKΘNJ])(?:\^?([+\-]?\d+)|([⁻]?[¹²³]))?""").findAll(source.replace("⁻", "-")).forEach { match ->
            val raw = match.groupValues[2].ifBlank { match.groupValues[3] }
            val exponent = raw.replace("¹", "1").replace("²", "2").replace("³", "3").toIntOrNull() ?: 1
            powers[match.groupValues[1]] = exponent
        }
        return PhysicalDimension(powers).takeIf { powers.isNotEmpty() || source.trim() == "1" }
    }

    private fun commonDimensions() = mapOf(
        "s" to PhysicsDimensions.Length, "d" to PhysicsDimensions.Length, "l" to PhysicsDimensions.Length,
        "u" to PhysicsDimensions.Velocity, "v" to PhysicsDimensions.Velocity,
        "a" to PhysicsDimensions.Acceleration, "t" to PhysicsDimensions.Time,
        "m" to PhysicsDimensions.Mass, "f" to PhysicsDimensions.Force,
        "i" to PhysicsDimensions.Current, "r" to (PhysicsDimensions.Power / PhysicsDimensions.Current.pow(2)),
    )

    private fun unsupported(message: String) = DimensionalAnalysisResult(DimensionalStatus.UNSUPPORTED, null, null, emptyList(), message)
}

data class PhysicsCalculationOutcome(
    val title: String,
    val formula: PhysicsFormula?,
    val target: PhysicalQuantity?,
    val substitutions: List<PhysicsSubstitution>,
    val numericalResult: Double?,
    val resultUnit: String?,
    val steps: List<PhysicsSolutionStep>,
    val warnings: List<String>,
    val status: PhysicsResultStatus,
    val engineMetadata: PhysicsEngineMetadata,
)

class PhysicsNumericalSolver(
    private val matcher: PhysicsFormulaMatcher = PhysicsFormulaMatcher(),
    private val units: SmartBoardPhysicsUnitAdapter = ExistingPhysicsUnitAdapter(),
    private val solver: MathProblemSolver = MathProblemSolver(),
    private val timeoutMillis: Long = 8_000,
) {
    suspend fun solve(source: String): PhysicsCalculationOutcome = withContext(Dispatchers.Default) {
        withTimeout(timeoutMillis) {
            val quantities = PhysicsQuantityParser(units).parse(source)
            val formula = matcher.match(source) ?: matcher.selectFor(quantities)
                ?: return@withTimeout unsupported("No applicable formula was confirmed.")
            val target = quantities.singleOrNull { it.scalarValue == null }
                ?: return@withTimeout unsupported("Select exactly one unknown quantity.")
            val known = quantities.filter { it.scalarValue != null }
            val siValues = known.associate { quantity ->
                val parsed = quantity.unitSymbol?.let(units::parseUnit)?.unit
                quantity.symbol to if (parsed == null) quantity.scalarValue!! else parsed.toSi(quantity.scalarValue!!)
            }
            val equation = formula.equation.normalizedForSolver()
            val substituted = substituteSymbols(equation, siValues).replaceSymbol(target.symbol, "x")
            val solution = solver.solve("Solve $substituted")
            val result = Regex("""x\s*=\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)""").find(solution.answer)?.groupValues?.get(1)?.toDoubleOrNull()
            if (!solution.supported || result == null) return@withTimeout unsupported("The existing solver could not isolate ${target.symbol}; choose a formula form or target explicitly.")
            val expectedUnit = formula.variables.firstOrNull { it.symbol == target.symbol }?.siUnit
            val substitutions = known.map { PhysicsSubstitution(it.symbol, siValues.getValue(it.symbol), it.unitSymbol) }
            PhysicsCalculationOutcome(
                title = "Solve for ${target.symbol}",
                formula = formula,
                target = target,
                substitutions = substitutions,
                numericalResult = result,
                resultUnit = expectedUnit,
                steps = listOf(
                    PhysicsSolutionStep("Select formula", formula.equation, "Matched the existing reviewed Physics formula registry.", true),
                    PhysicsSolutionStep("Convert to SI", substitutions.joinToString { "${it.symbol}=${it.value}" }, "Converted confirmed compatible units with PhysicsUnitSystem.", true),
                ) + solution.steps.map { PhysicsSolutionStep(it.title, it.expression, it.explanation, true) },
                warnings = formula.assumptions + "Report significant figures only after the full-precision calculation.",
                status = PhysicsResultStatus.VERIFIED,
                engineMetadata = PhysicsEngineMetadata(listOf("OfflinePhysicsFormulaRepository", "PhysicsUnitSystem", "MathProblemSolver"), true),
            )
        }
    }

    private fun unsupported(message: String) = PhysicsCalculationOutcome(
        "Physics calculation needs confirmation", null, null, emptyList(), null, null, emptyList(), listOf(message),
        PhysicsResultStatus.UNSUPPORTED, PhysicsEngineMetadata(listOf("OfflinePhysicsFormulaRepository", "MathProblemSolver"), true),
    )

    private fun substituteSymbols(source: String, values: Map<String, Double>) =
        values.entries.fold(source) { current, (symbol, value) -> current.replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(symbol)}(?![A-Za-z0-9_])"), "($value)") }
}

private fun String.replaceSymbol(symbol: String, replacement: String) =
    replace(Regex("(?<![A-Za-z0-9_])${Regex.escape(symbol)}(?![A-Za-z0-9_])"), replacement)

private fun String.normalizedForSolver() = replace(" ", "").replace("×", "*").replace("·", "*").replace("Â·", "*")
    .replace("²", "^2").replace("Â²", "^2").replace("½", "(1/2)")
    .replace("π", "pi").replace("Ï€", "pi").replace("√", "sqrt")
    .let { source ->
        var result = source
        listOf("ut" to "u*t", "at" to "a*t", "ma" to "m*a", "IR" to "I*R", "VI" to "V*I", "fλ" to "f*lambda").forEach { (from, to) -> result = result.replace(from, to) }
        result
    }

object PhysicsSignificantFigures {
    fun count(source: String): Int {
        val mantissa = source.trim().lowercase().substringBefore('e').replace(".", "").replace("+", "").replace("-", "")
        return mantissa.dropWhile { it == '0' }.length.coerceAtLeast(1)
    }

    fun round(value: Double, figures: Int): Double {
        require(value.isFinite() && figures in 1..15)
        if (value == 0.0) return 0.0
        val scale = 10.0.pow(figures - 1 - kotlin.math.floor(log10(abs(value))).toInt())
        return round(value * scale) / scale
    }
}

data class PhysicsUncertaintySummary(
    val mean: Double,
    val sampleStandardDeviation: Double,
    val standardError: Double,
    val absoluteUncertainty: Double?,
    val relativeUncertainty: Double?,
    val percentageUncertainty: Double?,
)

object PhysicsUncertaintyAdapter {
    fun summarize(values: List<Double>, statedAbsolute: Double? = null): PhysicsUncertaintySummary {
        val stats = AdvancedStatisticsEngine.summarize(values)
        val uncertainty = statedAbsolute ?: stats.standardError
        return PhysicsUncertaintySummary(
            stats.mean, stats.sampleStandardDeviation, stats.standardError, uncertainty,
            uncertainty.takeIf { stats.mean != 0.0 }?.div(abs(stats.mean)),
            uncertainty.takeIf { stats.mean != 0.0 }?.div(abs(stats.mean))?.times(100),
        )
    }
}

data class PhysicsVectorAnalysis(val components: Vec3, val magnitude: Double, val directionDegrees: Double, val unit: String?)

object PhysicsVectorAdapter {
    fun analyze(source: String): PhysicsVectorAnalysis {
        val componentMatch = Regex("""\(\s*([+-]?[0-9.]+)\s*,\s*([+-]?[0-9.]+)(?:\s*,\s*([+-]?[0-9.]+))?\s*\)\s*([A-Za-z/²Â]+)?""").find(source)
            ?: error("Confirm a 2D or 3D component vector.")
        val x = componentMatch.groupValues[1].toDouble()
        val y = componentMatch.groupValues[2].toDouble()
        val z = componentMatch.groupValues[3].toDoubleOrNull() ?: 0.0
        val vector = Vec3(x, y, z)
        return PhysicsVectorAnalysis(vector, vector.magnitude(), atan2(y, x) * 180 / PI, componentMatch.groupValues[4].takeIf(String::isNotBlank))
    }
}

data class PhysicsRearrangementResult(
    val target: String,
    val expression: String?,
    val steps: List<PhysicsSolutionStep>,
    val verified: Boolean,
    val warning: String?,
)

class PhysicsFormulaRearranger(
    private val cas: SymbolicCasEngine = SymbolicCasEngine(),
) {
    fun rearrange(equation: String, target: String): PhysicsRearrangementResult {
        if (target.isBlank() || !Regex("""[A-Za-z][A-Za-z0-9_]*""").matches(target)) {
            return PhysicsRearrangementResult(target, null, emptyList(), false, "Choose one target symbol.")
        }
        val parts = equation.split('=', limit = 2)
        if (parts.size != 2) return PhysicsRearrangementResult(target, null, emptyList(), false, "Enter one equation.")
        if (parts[0].trim() == target) {
            return PhysicsRearrangementResult(
                target, "$target = ${parts[1].trim()}",
                listOf(PhysicsSolutionStep("Already isolated", equation, "$target is already the subject.", true)),
                true, null,
            )
        }
        val solved = runCatching { cas.solveSystem(listOf(equation.normalizedForSolver()), listOf(target)) }.getOrNull()
        return if (solved?.supported == true && solved.exact.contains(target)) {
            PhysicsRearrangementResult(
                target,
                solved.exact,
                solved.steps.map { PhysicsSolutionStep(it.title, it.expression, it.explanation, true) },
                true,
                null,
            )
        } else {
            PhysicsRearrangementResult(target, null, emptyList(), false, "The existing symbolic engine could not safely isolate $target.")
        }
    }
}

data class PhysicsUnitConversionOutcome(
    val inputValue: Double,
    val inputUnit: String,
    val outputValue: Double?,
    val outputUnit: String,
    val verified: Boolean,
    val message: String,
)

class PhysicsUnitConverter(private val units: SmartBoardPhysicsUnitAdapter = ExistingPhysicsUnitAdapter()) {
    fun convert(source: String): PhysicsUnitConversionOutcome {
        val match = Regex("""([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*([A-Za-z°Ω/^²0-9·-]+)\s+(?:to|in)\s+([A-Za-z°Ω/^²0-9·-]+)""", RegexOption.IGNORE_CASE)
            .find(source.trim())
            ?: return PhysicsUnitConversionOutcome(Double.NaN, "", null, "", false, "Use a request such as 72 km/h to m/s.")
        val value = match.groupValues[1].toDouble()
        val from = units.parseUnit(match.groupValues[2])
        val to = units.parseUnit(match.groupValues[3])
        if (from.unit == null || to.unit == null) {
            return PhysicsUnitConversionOutcome(value, match.groupValues[2], null, match.groupValues[3], false, from.message ?: to.message ?: "Confirm both units.")
        }
        if (!units.areCompatible(from.unit, to.unit)) {
            return PhysicsUnitConversionOutcome(value, from.unit.symbol, null, to.unit.symbol, false, "The units describe different physical dimensions.")
        }
        val converted = units.convert(value, from.unit, to.unit)
        return PhysicsUnitConversionOutcome(value, from.unit.symbol, converted.value, to.unit.symbol, true, "Converted with the existing Physics unit system.")
    }
}

data class PhysicsMisconceptionEvidence(val id: String, val message: String, val evidence: String)

object PhysicsMisconceptionDetector {
    fun detect(source: String): List<PhysicsMisconceptionEvidence> = buildList {
        val lower = source.lowercase(Locale.ROOT)
        if (Regex("""(?:speed|velocity)\s*(?:has|=)\s*(?:no\s+)?direction""").containsMatchIn(lower)) {
            add(PhysicsMisconceptionEvidence("velocity-direction", "Velocity includes direction; speed does not.", "The written statement conflates speed and velocity."))
        }
        if (Regex("""current\s+(?:is\s+)?(?:used|consumed)""").containsMatchIn(lower)) {
            add(PhysicsMisconceptionEvidence("current-consumed", "In a steady series circuit, charge flow is continuous; components transfer energy.", "The written statement says current is consumed."))
        }
        if (Regex("""(?:heavier|greater mass).*(?:fall|falls).*(?:faster|greater acceleration)""").containsMatchIn(lower)) {
            add(PhysicsMisconceptionEvidence("mass-free-fall", "Neglecting air resistance, free-fall acceleration is independent of mass.", "The written claim links mass to free-fall acceleration."))
        }
    }
}
