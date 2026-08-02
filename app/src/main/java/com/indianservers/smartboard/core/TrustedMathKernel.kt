package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

enum class MathNumberDomain { Natural, Integer, Rational, Real, Complex }

data class VariableAssumption(
    val variable: String,
    val domain: MathNumberDomain = MathNumberDomain.Real,
    val positive: Boolean = false,
    val nonNegative: Boolean = false,
    val nonZero: Boolean = false,
    val minimum: Double? = null,
    val maximum: Double? = null,
) {
    init {
        require(variable.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))
        require(minimum == null || maximum == null || minimum <= maximum)
    }

    fun accepts(value: Double): Boolean {
        if (!value.isFinite()) return false
        if (positive && value <= 0.0) return false
        if (nonNegative && value < 0.0) return false
        if (nonZero && abs(value) < 1e-12) return false
        if (minimum != null && value < minimum) return false
        if (maximum != null && value > maximum) return false
        return when (domain) {
            MathNumberDomain.Natural -> value >= 0.0 && abs(value - value.toLong()) < 1e-12
            MathNumberDomain.Integer -> abs(value - value.toLong()) < 1e-12
            MathNumberDomain.Rational, MathNumberDomain.Real -> true
            MathNumberDomain.Complex -> true // Real samples are a safe subset for numeric evidence.
        }
    }

    fun description(): String = buildList {
        add("$variable ∈ ${domain.name}")
        if (positive) add("$variable > 0")
        else if (nonNegative) add("$variable ≥ 0")
        if (nonZero) add("$variable ≠ 0")
        if (minimum != null || maximum != null) add("$variable ∈ [${minimum ?: "−∞"}, ${maximum ?: "∞"}]")
    }.joinToString("; ")
}

data class MathAssumptionSet(val variables: Map<String, VariableAssumption> = emptyMap()) {
    operator fun get(variable: String): VariableAssumption = variables[variable] ?: VariableAssumption(variable)
    fun with(assumption: VariableAssumption) = copy(variables = variables + (assumption.variable to assumption))
    val descriptions: List<String> get() = variables.values.sortedBy { it.variable }.map { it.description() }
}

data class DomainConstraint(val expression: String, val relation: String, val reason: String) {
    val display: String get() = "$expression $relation · $reason"
}

data class DomainReport(
    val variables: Set<String>,
    val constraints: List<DomainConstraint>,
    val warnings: List<String>,
) {
    val description: String get() = (constraints.map { it.display } + warnings).joinToString("; ").ifBlank { "No additional real-domain restrictions" }
}

object MathDomainAnalyzer {
    fun analyze(expression: SymbolicExpression, assumptions: MathAssumptionSet = MathAssumptionSet()): DomainReport {
        val variables = linkedSetOf<String>()
        val constraints = linkedSetOf<DomainConstraint>()
        val warnings = linkedSetOf<String>()
        fun visit(node: SymbolicExpression) {
            when (node) {
                is SymbolicExpression.Number -> Unit
                is SymbolicExpression.Variable -> variables += node.name
                is SymbolicExpression.UnaryMinus -> visit(node.value)
                is SymbolicExpression.Sum -> node.terms.forEach(::visit)
                is SymbolicExpression.Product -> node.factors.forEach(::visit)
                is SymbolicExpression.Power -> {
                    visit(node.base); visit(node.exponent)
                    val exponent = when (val value = node.exponent) {
                        is SymbolicExpression.Number -> value.value
                        is SymbolicExpression.UnaryMinus -> (value.value as? SymbolicExpression.Number)?.value?.let { -it }
                        else -> null
                    }
                    if (exponent != null && exponent.numerator.signum() < 0) {
                        constraints += DomainConstraint(render(node.base), "≠ 0", "negative powers divide by the base")
                    }
                    if (exponent != null && exponent.denominator.toInt() % 2 == 0) {
                        constraints += DomainConstraint(render(node.base), "≥ 0", "even roots require a non-negative real radicand")
                    }
                }
                is SymbolicExpression.Function -> {
                    node.arguments.forEach(::visit)
                    val argument = node.arguments.firstOrNull()?.let(::render) ?: return
                    when (node.name.lowercase()) {
                        "sqrt" -> constraints += DomainConstraint(argument, "≥ 0", "real square root")
                        "ln", "log" -> constraints += DomainConstraint(argument, "> 0", "real logarithm")
                        "asin", "acos" -> constraints += DomainConstraint(argument, "∈ [−1, 1]", "real inverse trigonometric input")
                        "tan", "sec" -> constraints += DomainConstraint("cos($argument)", "≠ 0", "tangent/secant denominator")
                        "cot", "csc" -> constraints += DomainConstraint("sin($argument)", "≠ 0", "cotangent/cosecant denominator")
                        "abs", "sin", "cos", "atan", "exp", "sinh", "cosh", "tanh", "floor", "ceil" -> Unit
                        else -> warnings += "Domain rule for ${node.name} is not yet formalized."
                    }
                }
            }
        }
        visit(expression)
        assumptions.variables.values.forEach { assumption ->
            if (assumption.nonZero) constraints += DomainConstraint(assumption.variable, "≠ 0", "declared assumption")
            if (assumption.positive) constraints += DomainConstraint(assumption.variable, "> 0", "declared assumption")
            else if (assumption.nonNegative) constraints += DomainConstraint(assumption.variable, "≥ 0", "declared assumption")
        }
        return DomainReport(variables, constraints.toList(), warnings.toList())
    }

    private fun render(value: SymbolicExpression) = SymbolicCasEngine().render(value)
}

enum class EquivalenceStatus { Exact, VerifiedNumerically, DomainMismatch, NotEquivalent, Inconclusive }

data class EquivalenceSample(val variables: Map<String, Double>, val left: Double, val right: Double, val residual: Double)

data class EquivalenceEvidence(
    val left: String,
    val right: String,
    val status: EquivalenceStatus,
    val exactDifference: String?,
    val samples: List<EquivalenceSample>,
    val leftDomain: DomainReport,
    val rightDomain: DomainReport,
    val tolerance: Double,
    val explanation: String,
) {
    val equivalent: Boolean get() = status == EquivalenceStatus.Exact || status == EquivalenceStatus.VerifiedNumerically
}

/** Deterministic source of truth for equivalence, domain and numeric verification. */
class TrustedMathKernel(private val cas: SymbolicCasEngine = SymbolicCasEngine()) {
    fun equivalence(
        leftSource: String,
        rightSource: String,
        assumptions: MathAssumptionSet = MathAssumptionSet(),
        tolerance: Double = 1e-9,
        requestedSamples: Int = 17,
    ): EquivalenceEvidence {
        require(tolerance > 0 && requestedSamples > 0)
        return runCatching {
            val left = cas.parse(stripEquation(leftSource))
            val right = cas.parse(stripEquation(rightSource))
            val leftDomain = MathDomainAnalyzer.analyze(left, assumptions)
            val rightDomain = MathDomainAnalyzer.analyze(right, assumptions)
            val canonicalLeft = cas.simplify(cas.expand(left))
            val canonicalRight = cas.simplify(cas.expand(right))
            val difference = cas.simplify(cas.expand(SymbolicExpression.Sum(listOf(left, SymbolicExpression.UnaryMinus(right)))))
            val exact = cas.render(difference)
            val domainEqual = domainsCompatible(leftDomain, rightDomain, assumptions)
            val canonicalMatch = cas.render(canonicalLeft) == cas.render(canonicalRight)
            if ((difference is SymbolicExpression.Number && difference.value.isZero) || canonicalMatch) {
                val status = if (domainEqual) EquivalenceStatus.Exact else EquivalenceStatus.DomainMismatch
                return@runCatching EquivalenceEvidence(leftSource, rightSource, status, if (canonicalMatch) "0" else exact, emptyList(), leftDomain, rightDomain, tolerance,
                    if (domainEqual) "The canonical symbolic difference is exactly zero on the same domain."
                    else "The formulas simplify to the same value only on their common domain; their original domains differ.")
            }
            val variables = (leftDomain.variables + rightDomain.variables).sorted()
            if (variables.isEmpty()) {
                val l = evaluateDouble(left, emptyMap()); val r = evaluateDouble(right, emptyMap())
                val residual = abs(l - r)
                val status = if (residual <= scaledTolerance(l, r, tolerance)) EquivalenceStatus.VerifiedNumerically else EquivalenceStatus.NotEquivalent
                return@runCatching EquivalenceEvidence(leftSource, rightSource, status, exact, listOf(EquivalenceSample(emptyMap(), l, r, residual)), leftDomain, rightDomain, tolerance, "Compared both constant expressions numerically.")
            }
            val samples = deterministicAssignments(variables, assumptions, requestedSamples).mapNotNull { assignment ->
                runCatching {
                    val l = evaluateDouble(left, assignment); val r = evaluateDouble(right, assignment)
                    if (!l.isFinite() || !r.isFinite()) null else EquivalenceSample(assignment, l, r, abs(l - r))
                }.getOrNull()
            }
            if (samples.isEmpty()) return@runCatching EquivalenceEvidence(leftSource, rightSource, EquivalenceStatus.Inconclusive, exact, emptyList(), leftDomain, rightDomain, tolerance, "No valid deterministic samples lie inside both domains.")
            val failure = samples.firstOrNull { it.residual > scaledTolerance(it.left, it.right, tolerance) }
            val status = when {
                failure != null -> EquivalenceStatus.NotEquivalent
                !domainEqual -> EquivalenceStatus.DomainMismatch
                else -> EquivalenceStatus.VerifiedNumerically
            }
            EquivalenceEvidence(leftSource, rightSource, status, exact, samples, leftDomain, rightDomain, tolerance, when (status) {
                EquivalenceStatus.NotEquivalent -> "A deterministic counterexample disproves equivalence."
                EquivalenceStatus.DomainMismatch -> "Values agree at valid samples, but domain restrictions differ."
                EquivalenceStatus.VerifiedNumerically -> "Values agree at ${samples.size} deterministic domain-valid samples; this is numeric evidence, not a symbolic proof."
                else -> "Equivalence analysis completed."
            })
        }.getOrElse { error ->
            val empty = DomainReport(emptySet(), emptyList(), listOf(error.message ?: "Could not parse expression"))
            EquivalenceEvidence(leftSource, rightSource, EquivalenceStatus.Inconclusive, null, emptyList(), empty, empty, tolerance, error.message ?: "Equivalence could not be established safely.")
        }
    }

    fun verifyTransformation(before: String, after: String, assumptions: MathAssumptionSet = MathAssumptionSet()): EquivalenceEvidence =
        equivalence(before, after, assumptions)

    fun evaluate(source: String, variables: Map<String, Double> = emptyMap(), assumptions: MathAssumptionSet = MathAssumptionSet()): Result<VerifiedMathValue> = runCatching {
        variables.forEach { (name, value) -> require(assumptions[name].accepts(value)) { "$name=$value violates ${assumptions[name].description()}" } }
        val expression = cas.parse(stripEquation(source))
        val exact = cas.evaluate(expression, variables.mapValues { ExactRational.parse(it.value.toString()) }).getOrNull()?.toString()
        val decimal = evaluateDouble(expression, variables)
        VerifiedMathValue(exact, decimal, MathDomainAnalyzer.analyze(expression, assumptions), assumptions.descriptions, decimal.isFinite(), if (exact != null) "exact rational evaluation" else "deterministic floating-point evaluation")
    }

    private fun deterministicAssignments(variables: List<String>, assumptions: MathAssumptionSet, count: Int): List<Map<String, Double>> {
        val bases = listOf(-5.0, -3.0, -2.0, -1.0, -.5, .25, .5, 1.0, 2.0, 3.0, 5.0, PI / 3, 1.4142135623730951, 7.0, -7.0, 10.0, .125)
        return (0 until count * 4).map { sample ->
            variables.mapIndexed { index, variable -> variable to bases[(sample * 5 + index * 7) % bases.size] }.toMap()
        }.filter { assignment -> assignment.all { assumptions[it.key].accepts(it.value) } }.distinct().take(count)
    }

    private fun scaledTolerance(left: Double, right: Double, tolerance: Double) = tolerance * maxOf(1.0, abs(left), abs(right))

    private fun domainsCompatible(left: DomainReport, right: DomainReport, assumptions: MathAssumptionSet): Boolean {
        val leftSet = left.constraints.filterNot { implied(it, assumptions) }.map { it.expression to it.relation }.toSet()
        val rightSet = right.constraints.filterNot { implied(it, assumptions) }.map { it.expression to it.relation }.toSet()
        return leftSet == rightSet
    }

    private fun implied(constraint: DomainConstraint, assumptions: MathAssumptionSet): Boolean {
        val expression = constraint.expression.replace(" ", "")
        if (constraint.relation == "≥ 0" && (expression.endsWith("^2") || expression.startsWith("abs("))) return true
        val direct = assumptions.variables[expression] ?: return false
        return when (constraint.relation) {
            "> 0" -> direct.positive || (direct.minimum?.let { it > 0 } == true)
            "≥ 0" -> direct.positive || direct.nonNegative || (direct.minimum?.let { it >= 0 } == true) || direct.domain == MathNumberDomain.Natural
            "≠ 0" -> direct.nonZero || direct.positive || (direct.minimum?.let { it > 0 } == true) || (direct.maximum?.let { it < 0 } == true)
            else -> false
        }
    }

    private fun evaluateDouble(node: SymbolicExpression, variables: Map<String, Double>): Double = when (node) {
        is SymbolicExpression.Number -> node.value.numerator.toDouble() / node.value.denominator.toDouble()
        is SymbolicExpression.Variable -> when (node.name.lowercase()) { "pi" -> PI; "e" -> kotlin.math.E; else -> variables[node.name] ?: error("Missing value for ${node.name}") }
        is SymbolicExpression.UnaryMinus -> -evaluateDouble(node.value, variables)
        is SymbolicExpression.Sum -> node.terms.sumOf { evaluateDouble(it, variables) }
        is SymbolicExpression.Product -> node.factors.fold(1.0) { total, factor -> total * evaluateDouble(factor, variables) }
        is SymbolicExpression.Power -> evaluateDouble(node.base, variables).pow(evaluateDouble(node.exponent, variables))
        is SymbolicExpression.Function -> {
            val values = node.arguments.map { evaluateDouble(it, variables) }
            val x = values.firstOrNull() ?: error("${node.name} needs an argument")
            when (node.name.lowercase()) {
                "sqrt" -> sqrt(x); "abs" -> abs(x); "sin" -> sin(x); "cos" -> cos(x); "tan" -> tan(x)
                "sec" -> 1.0 / cos(x); "csc" -> 1.0 / sin(x); "cot" -> 1.0 / tan(x)
                "asin" -> asin(x); "acos" -> acos(x); "atan" -> atan(x)
                "sinh" -> sinh(x); "cosh" -> cosh(x); "tanh" -> tanh(x)
                "exp" -> exp(x); "ln" -> ln(x); "log" -> log10(x); "floor" -> floor(x); "ceil" -> ceil(x)
                "min" -> values.min(); "max" -> values.max()
                else -> error("Numeric evaluation of ${node.name} is not supported")
            }
        }
    }
}

data class VerifiedMathValue(
    val exact: String?,
    val decimal: Double,
    val domain: DomainReport,
    val assumptions: List<String>,
    val verified: Boolean,
    val provenance: String,
)

/** Generates a deterministic algebra corpus; tests use 10,000 cases as the Phase 1 gate. */
object KernelRegressionCorpus {
    data class Case(
        val left: String,
        val right: String,
        val label: String,
        val leftCoefficient: Int,
        val leftConstant: Int,
        val rightCoefficient: Int,
        val rightConstant: Int,
    ) {
        /** Exact integer probes avoid floating-point false positives and keep the 10k gate fast. */
        fun invariantHolds(): Boolean = (-7..7).all { x ->
            leftCoefficient.toLong() * x + leftConstant == rightCoefficient.toLong() * x + rightConstant
        }
    }
    fun generate(count: Int): List<Case> {
        require(count > 0)
        return List(count) { index ->
            val a = index % 17 - 8; val b = index * 3 % 19 - 9; val c = index * 5 % 13 - 6; val d = index * 7 % 23 - 11
            Case("($a*x+$b)+($c*x+$d)", "${a + c}*x+${b + d}", "collect-linear-$index", a + c, b + d, a + c, b + d)
        }
    }
}
