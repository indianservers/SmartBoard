package com.indianservers.smartboard.core

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.pow

data class ExactRational(val numerator: BigInteger, val denominator: BigInteger) : Comparable<ExactRational> {
    operator fun plus(other: ExactRational) = of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun minus(other: ExactRational) = of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: ExactRational) = of(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: ExactRational): ExactRational {
        require(!other.isZero) { "Division by zero" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }
    operator fun unaryMinus() = of(-numerator, denominator)
    fun pow(power: Int): ExactRational = if (power >= 0) of(numerator.pow(power), denominator.pow(power)) else of(denominator.pow(-power), numerator.pow(-power))
    val isZero get() = numerator == BigInteger.ZERO
    val isOne get() = numerator == denominator
    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()
    override fun compareTo(other: ExactRational): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)
    override fun toString(): String = if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    companion object {
        val ZERO = ExactRational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = ExactRational(BigInteger.ONE, BigInteger.ONE)
        fun of(numerator: BigInteger, denominator: BigInteger): ExactRational {
            require(denominator != BigInteger.ZERO) { "Zero denominator" }
            val sign = if (denominator.signum() < 0) -BigInteger.ONE else BigInteger.ONE
            val gcd = numerator.gcd(denominator)
            return ExactRational(numerator / gcd * sign, denominator / gcd * sign)
        }
        fun of(value: Long) = ExactRational(BigInteger.valueOf(value), BigInteger.ONE)
        fun parse(source: String): ExactRational {
            val clean = source.trim()
            if ('/' in clean) {
                val parts = clean.split('/', limit = 2)
                return of(BigInteger(parts[0].trim()), BigInteger(parts[1].trim()))
            }
            val decimal = BigDecimal(clean)
            return of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale().coerceAtLeast(0)))
        }
    }
}

sealed interface SymbolicExpression {
    data class Number(val value: ExactRational) : SymbolicExpression
    data class Variable(val name: String) : SymbolicExpression
    data class UnaryMinus(val value: SymbolicExpression) : SymbolicExpression
    data class Sum(val terms: List<SymbolicExpression>) : SymbolicExpression
    data class Product(val factors: List<SymbolicExpression>) : SymbolicExpression
    data class Power(val base: SymbolicExpression, val exponent: SymbolicExpression) : SymbolicExpression
    data class Function(val name: String, val arguments: List<SymbolicExpression>) : SymbolicExpression
}

data class CasStep(val title: String, val expression: String, val explanation: String)
data class CasRow(
    val input: String,
    val operation: String,
    val exact: String,
    val decimal: String?,
    val assumptions: List<String>,
    val steps: List<CasStep>,
    val supported: Boolean = true,
)

data class SymbolicComplex(val real: ExactRational, val imaginary: ExactRational) {
    operator fun plus(other: SymbolicComplex) = SymbolicComplex(real + other.real, imaginary + other.imaginary)
    operator fun times(other: SymbolicComplex) = SymbolicComplex(real * other.real - imaginary * other.imaginary, real * other.imaginary + imaginary * other.real)
    fun magnitudeSquared(): ExactRational = real * real + imaginary * imaginary
    override fun toString(): String = when {
        imaginary.isZero -> real.toString()
        real.isZero -> "${imaginary}i"
        imaginary.numerator.signum() < 0 -> "$real - ${-imaginary}i"
        else -> "$real + ${imaginary}i"
    }
}

data class SymbolicVector(val components: List<ExactRational>) {
    fun dot(other: SymbolicVector): ExactRational {
        require(components.size == other.components.size) { "Vector dimensions must match" }
        return components.indices.fold(ExactRational.ZERO) { total, index -> total + components[index] * other.components[index] }
    }
    fun projectionOnto(other: SymbolicVector): SymbolicVector {
        val denominator = other.dot(other)
        require(!denominator.isZero) { "Cannot project onto the zero vector" }
        val scale = dot(other) / denominator
        return SymbolicVector(other.components.map { it * scale })
    }
}

class SymbolicCasEngine {
    fun parse(source: String): SymbolicExpression = CasParser(source).parse()

    fun simplify(source: String): CasRow = casRow(source, "simplify") {
        val parsed = parse(source)
        val simplified = simplify(parsed)
        CasRow(
            source,
            "simplify",
            render(simplified),
            evaluate(simplified).getOrNull()?.let(::formatDecimal),
            assumptions(simplified),
            listOf(
                CasStep("Parse AST", render(parsed), "Create one typed symbolic tree for CAS, calculator, graph, notebook and solver boundaries."),
                CasStep("Apply identities", render(simplified), "Use arithmetic identities, fraction normalization and safe elementary simplifications."),
            ),
        )
    }

    /** Simplifies only identities justified by the supplied domain assumptions. */
    fun simplify(source: String, assumptionSet: MathAssumptionSet): CasRow =
        AdvancedSymbolicCas.simplify(this, source, assumptionSet)

    fun partialFractions(source: String, variable: String = "x"): CasRow =
        AdvancedSymbolicCas.partialFractions(this, source, variable)

    fun solveSystem(equations: List<String>, variables: List<String>): CasRow {
        val linear = AdvancedSymbolicCas.solveSystem(this, equations, variables)
        return if (linear.supported) linear else ExtendedSymbolicCas.solveNonlinearSystem(this, equations, variables)
    }

    fun solveNonlinearSystem(equations: List<String>, variables: List<String>): CasRow =
        ExtendedSymbolicCas.solveNonlinearSystem(this, equations, variables)

    fun solveInequalities(inequalities: List<String>, variable: String = "x"): CasRow {
        val linear = AdvancedSymbolicCas.solveInequalities(this, inequalities, variable)
        return if (linear.supported || inequalities.size != 1) linear else ComputationalBreadthCas.solveFactoredInequality(this, inequalities.single(), variable)
    }

    fun derivative(source: String, variable: String = "x"): CasRow =
        AdvancedSymbolicCas.derivative(this, source, variable)

    fun integral(source: String, variable: String = "x"): CasRow {
        val direct = AdvancedSymbolicCas.integral(this, source, variable)
        return if (direct.supported) direct else ComputationalBreadthCas.broaderIntegral(source, variable)
    }

    fun limit(source: String, variable: String = "x", approaching: String = "0"): CasRow {
        val direct = AdvancedSymbolicCas.limit(this, source, variable, approaching)
        return if (direct.supported) direct else ComputationalBreadthCas.broaderLimit(source, variable, approaching)
    }

    fun determinant(matrix: String): CasRow = AdvancedSymbolicCas.determinant(matrix)

    fun rowReduce(matrix: String): CasRow = AdvancedSymbolicCas.rowReduce(matrix)

    fun eigenvalues(matrix: String): CasRow = AdvancedSymbolicCas.eigenvalues(matrix)

    fun matrixDecomposition(matrix: String, method: String): CasRow = ExtendedSymbolicCas.decomposeMatrix(matrix, method)

    fun solveOde(source: String): CasRow {
        val direct = when {
            source.contains("y'''") -> ComputationalBreadthCas.evaluate(this, source, "higher ode")!!
            source.contains("y''") -> ExtendedSymbolicCas.solveHigherOde(source)
            else -> AdvancedSymbolicCas.solveOde(this, source)
        }
        return if (direct.supported) direct else ComputationalBreadthCas.evaluate(this, source, "nonlinear ode") ?: direct
    }

    fun laplace(source: String): CasRow = ExtendedSymbolicCas.laplace(this, source)

    fun inverseLaplace(source: String): CasRow = ExtendedSymbolicCas.inverseLaplace(source)

    fun zTransform(source: String): CasRow = ExtendedSymbolicCas.zTransform(source)

    fun expand(source: String): CasRow = casRow(source, "expand") {
        val parsed = parse(source)
        val expanded = expand(parsed).let(::simplify)
        CasRow(source, "expand", render(expanded), evaluate(expanded).getOrNull()?.let(::formatDecimal), assumptions(expanded), listOf(
            CasStep("Parse AST", render(parsed), "Read products and powers structurally."),
            CasStep("Distribute", render(expanded), "Distribute products over sums and expand small integer powers."),
        ))
    }

    fun substitute(source: String, replacements: Map<String, String>): CasRow = casRow(source, "substitute") {
        val parsed = parse(source)
        val values = replacements.mapValues { parse(it.value) }
        val substituted = substitute(parsed, values).let(::simplify)
        CasRow(source, "substitute", render(substituted), evaluate(substituted).getOrNull()?.let(::formatDecimal), assumptions(substituted), listOf(
            CasStep("Parse AST", render(parsed), "Keep variables as named AST leaves."),
            CasStep("Replace symbols", values.entries.joinToString { "${it.key}=${render(it.value)}" }, "Substitute only complete symbol names."),
            CasStep("Simplify", render(substituted), "Normalize the resulting tree."),
        ))
    }

    fun factor(source: String, variable: String = "x"): CasRow = casRow(source, "factor") {
        val expanded = expand(parse(source)).let(::simplify)
        val polynomial = polynomialCoefficients(expanded, variable)
        if (polynomial == null) {
            AdvancedSymbolicCas.factorRational(this, source, variable)
        } else {
            val factorText = factorPolynomial(polynomial, variable)
            CasRow(source, "factor", factorText, evaluate(expanded).getOrNull()?.let(::formatDecimal), assumptions(expanded), listOf(
                CasStep("Canonical polynomial", render(expanded), "Expand and collect terms before factoring."),
                CasStep("Rational roots", factorText, "Use exact rational candidates for small univariate polynomials."),
            ))
        }
    }

    fun casRow(source: String, operation: String): CasRow = when (operation.lowercase()) {
        "simplify" -> simplify(source)
        "expand" -> expand(source)
        "factor" -> factor(source)
        "partial fractions", "partialfractions", "apart" -> partialFractions(source)
        "derivative", "differentiate", "diff" -> derivative(source)
        "integral", "integrate" -> integral(source)
        "limit" -> limit(source)
        "ode" -> solveOde(source)
        "laplace", "laplace transform" -> laplace(source)
        "inverse laplace", "inverse laplace transform", "ilaplace" -> inverseLaplace(source)
        "z transform", "z-transform", "ztransform" -> zTransform(source)
        "determinant", "det" -> determinant(source)
        "rref", "row reduce", "rowreduce" -> rowReduce(source)
        "eigenvalues", "eigen" -> eigenvalues(source)
        "lu", "plu", "qr", "cholesky", "chol" -> matrixDecomposition(source, operation)
        "system", "solve system", "nonlinear system" -> {
            val equations = source.removePrefix("{").removeSuffix("}").split(';').map(String::trim).filter(String::isNotBlank)
            val variables = Regex("[A-Za-z][A-Za-z0-9_]*").findAll(source).map { it.value }.filterNot { it.lowercase() in setOf("sin", "cos", "tan", "exp", "ln", "log", "sqrt") }.distinct().sorted().toList()
            if (operation.equals("nonlinear system", ignoreCase = true)) solveNonlinearSystem(equations, variables) else solveSystem(equations, variables)
        }
        "inequality", "inequalities" -> solveInequalities(source.split(Regex("\\s*(?:&&|;|\\band\\b)\\s*", RegexOption.IGNORE_CASE)).filter(String::isNotBlank))
        else -> ComputationalBreadthCas.evaluate(this, source, operation)
            ?: CasRow(source, operation, "Not supported", null, emptyList(), listOf(CasStep("Unsupported", operation, "This CAS operation is not implemented yet.")), supported = false)
    }

    private fun casRow(source: String, operation: String, block: () -> CasRow): CasRow =
        runCatching(block).getOrElse { error ->
            CasRow(source, operation, "Not supported", null, emptyList(), listOf(CasStep("Unsupported", source, error.message ?: "CAS operation failed safely.")), supported = false)
        }

    fun simplify(expression: SymbolicExpression): SymbolicExpression = when (expression) {
        is SymbolicExpression.Number, is SymbolicExpression.Variable -> expression
        is SymbolicExpression.UnaryMinus -> when (val value = simplify(expression.value)) {
            is SymbolicExpression.Number -> SymbolicExpression.Number(-value.value)
            is SymbolicExpression.UnaryMinus -> value.value
            else -> SymbolicExpression.UnaryMinus(value)
        }
        is SymbolicExpression.Sum -> simplifySum(expression.terms.map(::simplify))
        is SymbolicExpression.Product -> simplifyProduct(expression.factors.map(::simplify))
        is SymbolicExpression.Power -> simplifyPower(simplify(expression.base), simplify(expression.exponent))
        is SymbolicExpression.Function -> {
            val args = expression.arguments.map(::simplify)
            val numeric = args.singleOrNull() as? SymbolicExpression.Number
            if (numeric != null && expression.name.lowercase() == "sqrt" && numeric.value.numerator.signum() >= 0) {
                val root = sqrtInteger(numeric.value.numerator)?.let { n -> sqrtInteger(numeric.value.denominator)?.let { d -> ExactRational.of(n, d) } }
                if (root != null) SymbolicExpression.Number(root) else SymbolicExpression.Function(expression.name.lowercase(), args)
            } else SymbolicExpression.Function(expression.name.lowercase(), args)
        }
    }

    fun expand(expression: SymbolicExpression): SymbolicExpression = when (expression) {
        is SymbolicExpression.Sum -> SymbolicExpression.Sum(expression.terms.map(::expand))
        is SymbolicExpression.Product -> expression.factors.map(::expand).reduce { acc, next -> distribute(acc, next) }
        is SymbolicExpression.Power -> {
            val base = expand(expression.base)
            val exponent = expression.exponent
            if (exponent is SymbolicExpression.Number && exponent.value.denominator == BigInteger.ONE && exponent.value.numerator in BigInteger.ZERO..BigInteger.valueOf(8)) {
                val n = exponent.value.numerator.toInt()
                if (n == 0) number(1) else (1 until n).fold(base) { acc, _ -> distribute(acc, base) }
            } else SymbolicExpression.Power(base, expand(exponent))
        }
        is SymbolicExpression.UnaryMinus -> SymbolicExpression.UnaryMinus(expand(expression.value))
        is SymbolicExpression.Function -> expression.copy(arguments = expression.arguments.map(::expand))
        else -> expression
    }

    private fun distribute(a: SymbolicExpression, b: SymbolicExpression): SymbolicExpression = when {
        a is SymbolicExpression.Sum -> SymbolicExpression.Sum(a.terms.map { distribute(it, b) })
        b is SymbolicExpression.Sum -> SymbolicExpression.Sum(b.terms.map { distribute(a, it) })
        else -> SymbolicExpression.Product(listOf(a, b))
    }

    fun substitute(expression: SymbolicExpression, replacements: Map<String, SymbolicExpression>): SymbolicExpression = when (expression) {
        is SymbolicExpression.Variable -> replacements[expression.name] ?: expression
        is SymbolicExpression.UnaryMinus -> SymbolicExpression.UnaryMinus(substitute(expression.value, replacements))
        is SymbolicExpression.Sum -> SymbolicExpression.Sum(expression.terms.map { substitute(it, replacements) })
        is SymbolicExpression.Product -> SymbolicExpression.Product(expression.factors.map { substitute(it, replacements) })
        is SymbolicExpression.Power -> SymbolicExpression.Power(substitute(expression.base, replacements), substitute(expression.exponent, replacements))
        is SymbolicExpression.Function -> expression.copy(arguments = expression.arguments.map { substitute(it, replacements) })
        is SymbolicExpression.Number -> expression
    }

    fun evaluate(expression: SymbolicExpression, variables: Map<String, ExactRational> = emptyMap()): Result<ExactRational> = runCatching {
        when (val simplified = simplify(expression)) {
            is SymbolicExpression.Number -> simplified.value
            is SymbolicExpression.Variable -> variables[simplified.name] ?: error("Missing value for ${simplified.name}")
            is SymbolicExpression.UnaryMinus -> -evaluate(simplified.value, variables).getOrThrow()
            is SymbolicExpression.Sum -> simplified.terms.fold(ExactRational.ZERO) { total, term -> total + evaluate(term, variables).getOrThrow() }
            is SymbolicExpression.Product -> simplified.factors.fold(ExactRational.ONE) { total, factor -> total * evaluate(factor, variables).getOrThrow() }
            is SymbolicExpression.Power -> {
                val base = evaluate(simplified.base, variables).getOrThrow()
                val exponent = evaluate(simplified.exponent, variables).getOrThrow()
                require(exponent.denominator == BigInteger.ONE && exponent.numerator.bitLength() < 31) { "Only integer exact powers are supported" }
                base.pow(exponent.numerator.toInt())
            }
            is SymbolicExpression.Function -> error("Exact evaluation of ${simplified.name} is not supported yet")
        }
    }

    fun render(expression: SymbolicExpression, parentPrecedence: Int = 0): String {
        val precedence = when (expression) {
            is SymbolicExpression.Sum -> 1
            is SymbolicExpression.Product -> 2
            is SymbolicExpression.Power -> 3
            is SymbolicExpression.UnaryMinus -> 4
            else -> 5
        }
        val text = when (expression) {
            is SymbolicExpression.Number -> expression.value.toString()
            is SymbolicExpression.Variable -> expression.name
            is SymbolicExpression.UnaryMinus -> "-${render(expression.value, precedence)}"
            is SymbolicExpression.Sum -> expression.terms.joinToString(" + ") { render(it, precedence) }.replace("+ -", "- ")
            is SymbolicExpression.Product -> expression.factors.joinToString("*") { render(it, precedence) }
            is SymbolicExpression.Power -> "${render(expression.base, precedence)}^${render(expression.exponent, precedence)}"
            is SymbolicExpression.Function -> "${expression.name}(${expression.arguments.joinToString { render(it) }})"
        }
        return if (precedence < parentPrecedence) "($text)" else text
    }

    private fun simplifySum(terms: List<SymbolicExpression>): SymbolicExpression {
        val flat = terms.flatMap { if (it is SymbolicExpression.Sum) it.terms else listOf(it) }
        var constant = ExactRational.ZERO
        val rest = mutableListOf<SymbolicExpression>()
        flat.forEach { term -> if (term is SymbolicExpression.Number) constant += term.value else rest += term }
        val output = buildList {
            if (!constant.isZero) add(SymbolicExpression.Number(constant))
            addAll(rest)
        }
        singleVariable(output)?.let { variable ->
            polynomialCoefficients(SymbolicExpression.Sum(output), variable)?.let { coefficients ->
                return polynomialExpression(coefficients, variable)
            }
        }
        return when (output.size) { 0 -> number(0); 1 -> output.single(); else -> SymbolicExpression.Sum(output) }
    }

    private fun simplifyProduct(factors: List<SymbolicExpression>): SymbolicExpression {
        val flat = factors.flatMap { if (it is SymbolicExpression.Product) it.factors else listOf(it) }
        var constant = ExactRational.ONE
        val rest = mutableListOf<SymbolicExpression>()
        flat.forEach { factor -> if (factor is SymbolicExpression.Number) constant *= factor.value else rest += factor }
        if (constant.isZero) return number(0)
        val output = buildList {
            if (!constant.isOne || rest.isEmpty()) add(SymbolicExpression.Number(constant))
            addAll(rest)
        }
        return when (output.size) { 0 -> number(1); 1 -> output.single(); else -> SymbolicExpression.Product(output) }
    }

    private fun simplifyPower(base: SymbolicExpression, exponent: SymbolicExpression): SymbolicExpression {
        if (exponent is SymbolicExpression.Number) {
            if (exponent.value.isZero) return number(1)
            if (exponent.value.isOne) return base
            if (base is SymbolicExpression.Number && exponent.value.denominator == BigInteger.ONE && exponent.value.numerator.bitLength() < 31) {
                return SymbolicExpression.Number(base.value.pow(exponent.value.numerator.toInt()))
            }
        }
        if (base is SymbolicExpression.Number && base.value.isOne) return number(1)
        return SymbolicExpression.Power(base, exponent)
    }

    private fun polynomialCoefficients(expression: SymbolicExpression, variable: String): List<ExactRational>? {
        val terms = when (expression) { is SymbolicExpression.Sum -> expression.terms; else -> listOf(expression) }
        val map = mutableMapOf<Int, ExactRational>()
        for (term in terms) {
            val (degree, coefficient) = monomial(term, variable) ?: return null
            map[degree] = (map[degree] ?: ExactRational.ZERO) + coefficient
        }
        val max = map.keys.maxOrNull() ?: 0
        return List(max + 1) { map[it] ?: ExactRational.ZERO }
    }

    private fun monomial(expression: SymbolicExpression, variable: String): Pair<Int, ExactRational>? = when (expression) {
        is SymbolicExpression.Number -> 0 to expression.value
        is SymbolicExpression.Variable -> if (expression.name == variable) 1 to ExactRational.ONE else null
        is SymbolicExpression.Power -> {
            val base = expression.base as? SymbolicExpression.Variable ?: return null
            val exponent = expression.exponent as? SymbolicExpression.Number ?: return null
            if (base.name == variable && exponent.value.denominator == BigInteger.ONE) exponent.value.numerator.toInt() to ExactRational.ONE else null
        }
        is SymbolicExpression.Product -> {
            var degree = 0
            var coefficient = ExactRational.ONE
            expression.factors.forEach { factor ->
                val part = monomial(factor, variable) ?: return null
                degree += part.first
                coefficient *= part.second
            }
            degree to coefficient
        }
        is SymbolicExpression.UnaryMinus -> monomial(expression.value, variable)?.let { it.first to -it.second }
        else -> null
    }

    private fun factorPolynomial(coefficients: List<ExactRational>, variable: String): String {
        if (coefficients.size == 3) {
            val c = coefficients[0]
            val b = coefficients[1]
            val a = coefficients[2]
            val roots = (-20..20).map { ExactRational.of(it.toLong()) }.filter { r -> a * r * r + b * r + c == ExactRational.ZERO }
            if (roots.size == 2) return "${renderLinearFactor(a, roots[0], variable)}${renderLinearFactor(ExactRational.ONE, roots[1], variable)}"
            if (roots.size == 1) return "${renderLinearFactor(a, roots[0], variable)}${renderPolynomial(listOf(-roots[0] * a + b, a), variable)}"
        }
        return renderPolynomial(coefficients, variable)
    }

    private fun polynomialExpression(coefficients: List<ExactRational>, variable: String): SymbolicExpression {
        val terms = coefficients.mapIndexedNotNull { degree, coefficient ->
            if (coefficient.isZero) null else when (degree) {
                0 -> SymbolicExpression.Number(coefficient)
                1 -> if (coefficient.isOne) SymbolicExpression.Variable(variable) else SymbolicExpression.Product(listOf(SymbolicExpression.Number(coefficient), SymbolicExpression.Variable(variable)))
                else -> {
                    val power = SymbolicExpression.Power(SymbolicExpression.Variable(variable), SymbolicExpression.Number(ExactRational.of(degree.toLong())))
                    if (coefficient.isOne) power else SymbolicExpression.Product(listOf(SymbolicExpression.Number(coefficient), power))
                }
            }
        }
        return when (terms.size) {
            0 -> number(0)
            1 -> terms.single()
            else -> SymbolicExpression.Sum(terms)
        }
    }

    private fun singleVariable(expressions: List<SymbolicExpression>): String? {
        val names = mutableSetOf<String>()
        fun visit(node: SymbolicExpression): Boolean = when (node) {
            is SymbolicExpression.Number -> true
            is SymbolicExpression.Variable -> { names += node.name; true }
            is SymbolicExpression.UnaryMinus -> visit(node.value)
            is SymbolicExpression.Product -> node.factors.all(::visit)
            is SymbolicExpression.Power -> node.base is SymbolicExpression.Variable && node.exponent is SymbolicExpression.Number && visit(node.base)
            else -> false
        }
        if (!expressions.all(::visit)) return null
        return names.singleOrNull()
    }

    private fun renderLinearFactor(leading: ExactRational, root: ExactRational, variable: String): String {
        val prefix = if (leading.isOne) "" else leading.toString()
        val sign = if (root.numerator.signum() < 0) "+" else "-"
        return "$prefix($variable $sign ${if (root.numerator.signum() < 0) -root else root})"
    }

    private fun renderPolynomial(coefficients: List<ExactRational>, variable: String): String {
        val terms = coefficients.mapIndexedNotNull { degree, coefficient ->
            if (coefficient.isZero) null else when (degree) {
                0 -> coefficient.toString()
                1 -> "${coefficient}*$variable"
                else -> "${coefficient}*$variable^$degree"
            }
        }
        return terms.reversed().joinToString(" + ").replace("+ -", "- ").ifBlank { "0" }
    }

    private fun assumptions(expression: SymbolicExpression): List<String> = buildList {
        fun visit(node: SymbolicExpression) {
            when (node) {
                is SymbolicExpression.Function -> {
                    if (node.name in setOf("ln", "log")) add("arguments of ${node.name} are positive")
                    if (node.name == "sqrt") add("sqrt argument is non-negative")
                    node.arguments.forEach(::visit)
                }
                is SymbolicExpression.Power -> { visit(node.base); visit(node.exponent) }
                is SymbolicExpression.Sum -> node.terms.forEach(::visit)
                is SymbolicExpression.Product -> node.factors.forEach(::visit)
                is SymbolicExpression.UnaryMinus -> visit(node.value)
                else -> Unit
            }
        }
        visit(expression)
    }.distinct()

    private fun number(value: Long) = SymbolicExpression.Number(ExactRational.of(value))
    private fun formatDecimal(value: ExactRational): String = trim(value.toDouble())
    private fun sqrtInteger(value: BigInteger): BigInteger? {
        if (value.signum() < 0) return null
        val root = kotlin.math.sqrt(value.toDouble()).toLong()
        return BigInteger.valueOf(root).takeIf { it * it == value }
    }
}

private class CasParser(private val source: String) {
    private var index = 0
    fun parse(): SymbolicExpression {
        val expression = parseExpression()
        skip()
        require(index == source.length) { "Unexpected token '${source.getOrNull(index)}'" }
        return expression
    }

    private fun parseExpression(): SymbolicExpression {
        val terms = mutableListOf(parseTerm())
        while (true) {
            skip()
            when {
                match('+') -> terms += parseTerm()
                match('-') -> terms += SymbolicExpression.UnaryMinus(parseTerm())
                else -> return if (terms.size == 1) terms.single() else SymbolicExpression.Sum(terms)
            }
        }
    }

    private fun parseTerm(): SymbolicExpression {
        val factors = mutableListOf(parsePower())
        while (true) {
            skip()
            when {
                match('*') -> factors += parsePower()
                match('/') -> factors += SymbolicExpression.Power(parsePower(), SymbolicExpression.Number(ExactRational.of(-1)))
                shouldImplicitMultiply() -> factors += parsePower()
                else -> return if (factors.size == 1) factors.single() else SymbolicExpression.Product(factors)
            }
        }
    }

    private fun parsePower(): SymbolicExpression {
        var node = parseUnary()
        skip()
        if (match('^')) node = SymbolicExpression.Power(node, parsePower())
        return node
    }

    private fun parseUnary(): SymbolicExpression {
        skip()
        return when {
            match('-') -> SymbolicExpression.UnaryMinus(parseUnary())
            match('+') -> parseUnary()
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): SymbolicExpression {
        skip()
        if (match('(')) {
            val expression = parseExpression()
            require(match(')')) { "Missing closing parenthesis" }
            return expression
        }
        if (peek()?.isDigit() == true || peek() == '.') return parseNumber()
        if (peek()?.isLetter() == true || peek() == 'π') {
            val name = parseIdentifier().let { if (it == "π") "pi" else it }
            skip()
            if (match('(')) {
                val args = mutableListOf<SymbolicExpression>()
                skip()
                if (peek() != ')') {
                    do {
                        args += parseExpression()
                        skip()
                    } while (match(','))
                }
                require(match(')')) { "Missing closing parenthesis" }
                return SymbolicExpression.Function(name.lowercase(), args)
            }
            return if (name == "pi") SymbolicExpression.Variable("pi") else SymbolicExpression.Variable(name)
        }
        error("Expected number, variable or function")
    }

    private fun parseNumber(): SymbolicExpression {
        val start = index
        while (peek()?.isDigit() == true || peek() == '.') index++
        return SymbolicExpression.Number(ExactRational.parse(source.substring(start, index)))
    }

    private fun parseIdentifier(): String {
        val start = index
        while (peek()?.isLetterOrDigit() == true || peek() == '_' || peek() == 'π') index++
        return source.substring(start, index)
    }

    private fun shouldImplicitMultiply(): Boolean {
        val c = peek() ?: return false
        return c == '(' || c.isLetter() || c == 'π'
    }
    private fun match(char: Char): Boolean {
        skip()
        if (peek() == char) {
            index++
            return true
        }
        return false
    }
    private fun peek(): Char? = source.getOrNull(index)
    private fun skip() {
        while (peek()?.isWhitespace() == true) index++
    }
}
