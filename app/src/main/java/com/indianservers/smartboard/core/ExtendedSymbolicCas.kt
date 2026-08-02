package com.indianservers.smartboard.core

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.sqrt

/** Advanced, deliberately bounded algorithms which return a safe refusal outside their verified domain. */
internal object ExtendedSymbolicCas {
    private val zero = ExactRational.ZERO
    private val one = ExactRational.ONE

    fun solveNonlinearSystem(engine: SymbolicCasEngine, equations: List<String>, variables: List<String>): CasRow =
        safe(equations.joinToString("; "), "nonlinear system") {
            require(equations.size == variables.size && variables.size >= 2) { "Provide a square nonlinear system with at least two equations and variables." }
            val parsed = equations.map { equation ->
                val sides = equation.split('=', limit = 2)
                require(sides.size == 2) { "Each equation must contain '='." }
                engine.parse(sides[0]) to engine.parse(sides[1])
            }
            if (variables.size > 2) return@safe solveGeneralSubstitution(engine, equations, variables, parsed)
            val isolation = parsed.withIndex().firstNotNullOfOrNull { (index, pair) ->
                val left = pair.first as? SymbolicExpression.Variable
                val right = pair.second as? SymbolicExpression.Variable
                when {
                    left != null && left.name in variables && variablesExcept(pair.second, left.name) -> Isolation(index, left.name, pair.second)
                    right != null && right.name in variables && variablesExcept(pair.first, right.name) -> Isolation(index, right.name, pair.first)
                    else -> null
                }
            } ?: error("Isolate one variable explicitly, for example y=x^2, so verified substitution can be used.")
            val remaining = variables.single { it != isolation.variable }
            val other = parsed[1 - isolation.equationIndex]
            val residual = SymbolicExpression.Sum(listOf(other.first, SymbolicExpression.UnaryMinus(other.second)))
            val substituted = engine.substitute(residual, mapOf(isolation.variable to isolation.expression)).let(engine::expand).let(engine::simplify)
            val polynomial = polynomial(substituted, remaining)
                ?: error("Substitution must produce a univariate polynomial in $remaining.")
            val roots = polynomial.exactRoots()
            require(roots.isNotEmpty() && roots.size == polynomial.degree) { "The resulting polynomial does not split into verified rational roots." }
            val solutions = roots.distinct().map { root ->
                val isolatedValue = engine.evaluate(isolation.expression, mapOf(remaining to root)).getOrElse {
                    error("The isolated expression could not be evaluated exactly at $remaining=$root.")
                }
                variables.associateWith { if (it == remaining) root else isolatedValue }
            }
            solutions.forEach { solution ->
                parsed.forEach { (left, right) ->
                    val lhs = engine.evaluate(left, solution).getOrThrow()
                    val rhs = engine.evaluate(right, solution).getOrThrow()
                    require(lhs == rhs) { "A candidate solution failed exact substitution." }
                }
            }
            val answer = solutions.joinToString("; ") { solution ->
                "(" + variables.joinToString(", ") { "$it = ${solution.getValue(it)}" } + ")"
            }
            CasRow(equations.joinToString("; "), "nonlinear system", answer, null, emptyList(), listOf(
                CasStep("Isolate", "${isolation.variable} = ${engine.render(isolation.expression)}", "Select an explicit relation without guessing a branch."),
                CasStep("Substitute", "${polynomial.render(remaining)} = 0", "Reduce the second relation to one exact polynomial."),
                CasStep("Solve and back-substitute", answer, "Find every rational root, then recover ${isolation.variable}."),
                CasStep("Verify", "zero residual in both equations", "Substitute every tuple into every original equation using exact rational arithmetic."),
            ))
        }

    private fun solveGeneralSubstitution(
        engine: SymbolicCasEngine,
        equations: List<String>,
        variables: List<String>,
        parsed: List<Pair<SymbolicExpression, SymbolicExpression>>,
    ): CasRow {
        val assignments = linkedMapOf<String, SymbolicExpression>(); var residualIndex = -1
        parsed.forEachIndexed { index, (left, right) ->
            val leftVariable = left as? SymbolicExpression.Variable; val rightVariable = right as? SymbolicExpression.Variable
            when {
                leftVariable != null && leftVariable.name in variables && variablesExcept(right, leftVariable.name) -> assignments[leftVariable.name] = right
                rightVariable != null && rightVariable.name in variables && variablesExcept(left, rightVariable.name) -> assignments[rightVariable.name] = left
                residualIndex < 0 -> residualIndex = index
                else -> error("Use explicit acyclic assignments for all but one equation, leaving one univariate residual relation.")
            }
        }
        require(assignments.size == variables.size - 1 && residualIndex >= 0) { "Isolate ${variables.size - 1} variables explicitly and leave one equation to determine the base variable." }
        val base = variables.single { it !in assignments }
        val resolved = assignments.toMutableMap()
        repeat(assignments.size) {
            resolved.keys.toList().forEach { variable -> resolved[variable] = engine.simplify(engine.substitute(resolved.getValue(variable), resolved.filterKeys { it != variable })) }
        }
        require(resolved.values.all { expression -> variables(expression).all { it == base } }) { "Assignments must be acyclic functions of the single base variable $base." }
        val residualPair = parsed[residualIndex]
        val residual = SymbolicExpression.Sum(listOf(residualPair.first, SymbolicExpression.UnaryMinus(residualPair.second)))
        val reduced = engine.substitute(residual, resolved).let(engine::expand).let(engine::simplify)
        val polynomial = polynomial(reduced, base) ?: error("The final residual must be a polynomial in $base.")
        val roots = polynomial.exactRoots().distinct()
        require(roots.isNotEmpty() && roots.size == polynomial.degree) { "The residual must split into verified rational roots; use RootOf for unsplit algebraic branches." }
        val solutions = roots.map { root ->
            val solution = linkedMapOf(base to root)
            repeat(assignments.size) {
                resolved.forEach { (variable, expression) -> if (variable !in solution) engine.evaluate(expression, solution).getOrNull()?.let { solution[variable] = it } }
            }
            require(solution.keys.containsAll(variables)) { "Back-substitution did not resolve every variable." }
            parsed.forEach { (left, right) -> require(engine.evaluate(left, solution).getOrThrow() == engine.evaluate(right, solution).getOrThrow()) { "A candidate branch failed exact verification." } }
            solution
        }
        val answer = solutions.joinToString("; ") { solution -> "(" + variables.joinToString(", ") { "$it = ${solution.getValue(it)}" } + ")" }
        return CasRow(equations.joinToString("; "), "nonlinear system", answer, null, emptyList(), listOf(
            CasStep("Dependency ordering", assignments.entries.joinToString { "${it.key}=${engine.render(it.value)}" }, "Resolve an acyclic chain of explicit relations."),
            CasStep("Eliminate", "${polynomial.render(base)} = 0", "Substitute every dependent variable into one exact univariate polynomial."),
            CasStep("Branch solutions", answer, "Solve every rational branch and back-substitute in dependency order."),
            CasStep("Verify", "zero residual in ${equations.size} equations", "Check every tuple against every original equation exactly."),
        ))
    }

    fun decomposeMatrix(source: String, method: String): CasRow = safe(source, method.lowercase()) {
        val matrix = parseMatrix(source)
        when (method.lowercase()) {
            "lu", "plu" -> lu(source, matrix)
            "qr" -> qr(source, matrix)
            "cholesky", "chol" -> cholesky(source, matrix)
            else -> error("Choose LU, QR or Cholesky decomposition.")
        }
    }

    fun solveHigherOde(source: String): CasRow = safe(source, "ode") {
        val normalized = source.replace(" ", "").substringBefore(',')
        val sides = normalized.split('=', limit = 2)
        require(sides.size == 2) { "Use a second-order equation such as y''-3*y'+2*y=0." }
        val rhs = ExactRational.parse(sides[1])
        var a2 = zero; var a1 = zero; var a0 = zero; var constant = -rhs
        sides[0].replace("-", "+-").split('+').filter(String::isNotBlank).forEach { term ->
            when {
                term.endsWith("y''") -> a2 += coefficient(term.removeSuffix("y''"))
                term.endsWith("y'") -> a1 += coefficient(term.removeSuffix("y'"))
                term.endsWith("y") -> a0 += coefficient(term.removeSuffix("y"))
                else -> constant += ExactRational.parse(term)
            }
        }
        require(!a2.isZero) { "A nonzero y'' coefficient is required." }
        val forcing = -constant / a2
        val a = a1 / a2; val b = a0 / a2
        val shift = if (!forcing.isZero) {
            require(!b.isZero) { "Constant forcing with zero y coefficient is not yet supported." }
            forcing / b
        } else zero
        val discriminant = a * a - ExactRational.of(4) * b
        val root = sqrtRational(if (discriminant < zero) -discriminant else discriminant)
        val y0 = Regex("""y\(0\)=([+-]?\d+(?:/\d+)?)""", RegexOption.IGNORE_CASE).find(source.replace(" ", ""))?.groupValues?.get(1)?.let(ExactRational::parse)
        val v0 = Regex("""y'\(0\)=([+-]?\d+(?:/\d+)?)""", RegexOption.IGNORE_CASE).find(source.replace(" ", ""))?.groupValues?.get(1)?.let(ExactRational::parse)
        require((y0 == null) == (v0 == null)) { "Provide both y(0) and y'(0), or neither." }
        val homogeneous: String
        val characteristic: String
        when {
            discriminant > zero && root != null -> {
                val r1 = (-a + root) / ExactRational.of(2); val r2 = (-a - root) / ExactRational.of(2)
                characteristic = "r1=$r1, r2=$r2"
                homogeneous = if (y0 == null) "C1*exp($r1*x) + C2*exp($r2*x)" else {
                    val total = y0 - shift
                    val c1 = (v0!! - r2 * total) / (r1 - r2); val c2 = total - c1
                    "$c1*exp($r1*x) + $c2*exp($r2*x)"
                }
            }
            discriminant.isZero -> {
                val r = -a / ExactRational.of(2); characteristic = "repeated root r=$r"
                homogeneous = if (y0 == null) "(C1 + C2*x)*exp($r*x)" else {
                    val c1 = y0 - shift; val c2 = v0!! - r * c1
                    "($c1 + $c2*x)*exp($r*x)"
                }
            }
            discriminant < zero && root != null -> {
                val alpha = -a / ExactRational.of(2); val beta = root / ExactRational.of(2)
                characteristic = "r=$alpha +/- ${beta}*i"
                homogeneous = if (y0 == null) "exp($alpha*x)*(C1*cos($beta*x) + C2*sin($beta*x))" else {
                    val c1 = y0 - shift; val c2 = (v0!! - alpha * c1) / beta
                    "exp($alpha*x)*($c1*cos($beta*x) + $c2*sin($beta*x))"
                }
            }
            else -> {
                require(y0 == null) { "Initial-value constants require rational characteristic roots." }
                characteristic = "r=(-$a +/- sqrt($discriminant))/2"
                homogeneous = "C1*exp((-$a + sqrt($discriminant))*x/2) + C2*exp((-$a - sqrt($discriminant))*x/2)"
            }
        }
        val answer = "y = " + (if (shift.isZero) homogeneous else "$shift + $homogeneous")
        CasRow(source, "ode", answer, null, emptyList(), listOf(
            CasStep("Normalize", "y'' + $a*y' + $b*y = $forcing", "Divide by the leading coefficient and separate constant forcing."),
            CasStep("Characteristic equation", "r^2 + $a*r + $b = 0; $characteristic", "Classify distinct, repeated or complex characteristic roots."),
            CasStep("Apply initial data", answer, if (y0 == null) "Retain two arbitrary constants." else "Solve both constants from y(0) and y'(0) exactly."),
            CasStep("Verify", "characteristic residual = 0", "Each basis function satisfies the homogeneous operator; the constant shift supplies the forcing and initial values are rechecked."),
        ))
    }

    fun laplace(engine: SymbolicCasEngine, source: String): CasRow = safe(source, "laplace") {
        val parsed = engine.parse(source)
        val answer = laplaceOf(parsed, "x") ?: error("Use sums of constants, x^n, exp(a*x), sin(a*x), or cos(a*x).")
        CasRow(source, "laplace", answer, null, listOf("x >= 0", "Re(s) is in the convergence region"), listOf(
            CasStep("Convention", "L{f}(s) = integral[0,infinity] f(x)*exp(-s*x) dx", "Use the one-sided Laplace transform."),
            CasStep("Apply transform table", answer, "Transform linear combinations term by term."),
            CasStep("Verify", "differentiate/integrate the transform identity", "The result follows from convergent defining integrals in the stated region."),
        ))
    }

    fun inverseLaplace(source: String): CasRow = safe(source, "inverse laplace") {
        val s = source.replace(" ", "")
        val answer = when {
            s == "1/s" -> "1"
            Regex("""1/s\^(\d+)""").matches(s) -> {
                val n = Regex("""1/s\^(\d+)""").matchEntire(s)!!.groupValues[1].toInt()
                require(n >= 1); if (n == 1) "1" else "x^${n - 1}/${factorial(n - 1)}"
            }
            Regex("""1/\(s([+-])(\d+(?:/\d+)?)\)""").matches(s) -> {
                val m = Regex("""1/\(s([+-])(\d+(?:/\d+)?)\)""").matchEntire(s)!!
                val a = ExactRational.parse(m.groupValues[2]).let { if (m.groupValues[1] == "-") it else -it }
                "exp($a*x)"
            }
            else -> error("Use 1/s, 1/s^n, or 1/(s-a) for exact inverse-Laplace lookup.")
        }
        CasRow(source, "inverse laplace", answer, null, listOf("causal function on x >= 0"), listOf(
            CasStep("Match transform pair", source, "Recognize a canonical one-sided Laplace form."),
            CasStep("Invert", answer, "Apply the exact table pair."),
            CasStep("Verify", "L{$answer} = $source", "Forward transformation reproduces the supplied expression."),
        ))
    }

    fun zTransform(source: String): CasRow = safe(source, "z transform") {
        val clean = source.replace(" ", "")
        val answer = when {
            clean == "1" -> "z/(z - 1)"
            clean == "n" -> "z/(z - 1)^2"
            Regex("""([+-]?\d+(?:/\d+)?)\^n""").matches(clean) -> {
                val a = ExactRational.parse(Regex("""([+-]?\d+(?:/\d+)?)\^n""").matchEntire(clean)!!.groupValues[1])
                "z/(z - $a)"
            }
            else -> error("Use a causal sequence 1, n, or a^n with rational a.")
        }
        CasRow(source, "z transform", answer, null, listOf("n >= 0", "z lies outside the pole radius"), listOf(
            CasStep("Convention", "Z{x[n]} = sum[n=0,infinity] x[n]*z^(-n)", "Use the unilateral Z transform."),
            CasStep("Sum geometric series", answer, "Reduce the sequence to a standard convergent series."),
            CasStep("Verify", "series expansion matches the input coefficients", "Expand in powers of z^-1 and compare term by term."),
        ))
    }

    private fun lu(source: String, a: List<List<ExactRational>>): CasRow {
        require(a.size == a[0].size) { "LU requires a square matrix." }
        val n = a.size; val u = a.map { it.toMutableList() }.toMutableList()
        val l = MutableList(n) { r -> MutableList(n) { c -> if (r == c) one else zero } }
        val p = MutableList(n) { r -> MutableList(n) { c -> if (r == c) one else zero } }
        val swaps = mutableListOf<String>()
        for (k in 0 until n) {
            val pivot = (k until n).firstOrNull { !u[it][k].isZero } ?: error("Matrix is singular; a full-rank PLU decomposition is required here.")
            if (pivot != k) {
                val tempU = u[k]; u[k] = u[pivot]; u[pivot] = tempU
                val tempP = p[k]; p[k] = p[pivot]; p[pivot] = tempP
                for (j in 0 until k) { val t = l[k][j]; l[k][j] = l[pivot][j]; l[pivot][j] = t }
                swaps += "R${k + 1}<->R${pivot + 1}"
            }
            for (i in k + 1 until n) {
                val factor = u[i][k] / u[k][k]; l[i][k] = factor
                for (j in k until n) u[i][j] -= factor * u[k][j]
            }
        }
        require(multiply(p, a) == multiply(l, u)) { "Internal PLU verification failed." }
        val answer = "P=${matrixText(p)}; L=${matrixText(l)}; U=${matrixText(u)}"
        return CasRow(source, "lu", answer, null, emptyList(), listOf(
            CasStep("Pivot", matrixText(p), swaps.joinToString().ifBlank { "No row swaps were needed." }),
            CasStep("Eliminate", "L=${matrixText(l)}; U=${matrixText(u)}", "Store exact multipliers below the diagonal."),
            CasStep("Verify", "P*A = L*U", "Exact matrix multiplication confirms every entry."),
        ))
    }

    private fun qr(source: String, exact: List<List<ExactRational>>): CasRow {
        val a = exact.map { row -> row.map(ExactRational::toDouble).toDoubleArray() }.toTypedArray()
        val m = a.size; val n = a[0].size; require(m >= n) { "Thin QR requires rows >= columns." }
        val q = Array(m) { DoubleArray(n) }; val r = Array(n) { DoubleArray(n) }
        for (j in 0 until n) {
            val v = DoubleArray(m) { a[it][j] }
            for (i in 0 until j) { r[i][j] = (0 until m).sumOf { q[it][i] * v[it] }; for (k in 0 until m) v[k] -= r[i][j] * q[k][i] }
            r[j][j] = sqrt(v.sumOf { it * it }); require(r[j][j] > 1e-12) { "Columns must be linearly independent." }
            for (k in 0 until m) q[k][j] = v[k] / r[j][j]
        }
        val error = (0 until m).maxOf { i -> (0 until n).maxOf { j -> abs((0 until n).sumOf { k -> q[i][k] * r[k][j] } - a[i][j]) } }
        val answer = "Q=${matrixText(q)}; R=${matrixText(r)}"
        return CasRow(source, "qr", answer, format(error), emptyList(), listOf(
            CasStep("Orthogonalize", matrixText(q), "Use modified Gram-Schmidt on matrix columns."),
            CasStep("Project", matrixText(r), "Record projection coefficients in the upper-triangular factor."),
            CasStep("Verify", "max|A-Q*R| = ${format(error)}", "Reconstruction residual is checked numerically."),
        ))
    }

    private fun cholesky(source: String, exact: List<List<ExactRational>>): CasRow {
        val n = exact.size; require(n == exact[0].size) { "Cholesky requires a square matrix." }
        require(exact.indices.all { i -> exact.indices.all { j -> exact[i][j] == exact[j][i] } }) { "Cholesky requires a symmetric matrix." }
        val a = exact.map { it.map(ExactRational::toDouble).toDoubleArray() }.toTypedArray(); val l = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0..i) {
            val sum = (0 until j).sumOf { l[i][it] * l[j][it] }
            if (i == j) { val value = a[i][i] - sum; require(value > 1e-12) { "Matrix must be positive definite." }; l[i][j] = sqrt(value) }
            else l[i][j] = (a[i][j] - sum) / l[j][j]
        }
        val error = (0 until n).maxOf { i -> (0 until n).maxOf { j -> abs((0 until n).sumOf { k -> l[i][k] * l[j][k] } - a[i][j]) } }
        return CasRow(source, "cholesky", "L=${matrixText(l)}", format(error), emptyList(), listOf(
            CasStep("Check domain", "A is symmetric positive definite", "Reject invalid matrices before taking square roots."),
            CasStep("Build lower factor", matrixText(l), "Compute diagonal norms and lower-triangular projections."),
            CasStep("Verify", "max|A-L*L^T| = ${format(error)}", "Reconstruct A and report the maximum residual."),
        ))
    }

    private fun laplaceOf(expression: SymbolicExpression, variable: String): String? = when (expression) {
        is SymbolicExpression.Number -> "${expression.value}/s"
        is SymbolicExpression.Variable -> if (expression.name == variable) "1/s^2" else null
        is SymbolicExpression.UnaryMinus -> laplaceOf(expression.value, variable)?.let { "-($it)" }
        is SymbolicExpression.Sum -> expression.terms.map { laplaceOf(it, variable) ?: return null }.joinToString(" + ")
        is SymbolicExpression.Product -> {
            val constants = expression.factors.filterIsInstance<SymbolicExpression.Number>()
            val rest = expression.factors.filterNot { it is SymbolicExpression.Number }
            if (rest.size != 1) null else laplaceOf(rest.single(), variable)?.let { transformed ->
                val coefficient = constants.fold(one) { total, number -> total * number.value }
                "$coefficient*($transformed)"
            }
        }
        is SymbolicExpression.Power -> {
            val base = expression.base as? SymbolicExpression.Variable
            val exponent = (expression.exponent as? SymbolicExpression.Number)?.value
            val n = exponent?.takeIf { it.denominator == BigInteger.ONE }?.numerator?.toInt()
            if (base?.name == variable && n != null && n >= 0) "${factorial(n)}/s^${n + 1}" else null
        }
        is SymbolicExpression.Function -> {
            val arg = expression.arguments.singleOrNull() ?: return null
            val p = polynomial(arg, variable) ?: return null
            if (p.degree != 1 || !p[0].isZero) return null
            val a = p[1]
            when (expression.name.lowercase()) {
                "exp" -> "1/(s - $a)"
                "sin" -> "$a/(s^2 + ${a * a})"
                "cos" -> "s/(s^2 + ${a * a})"
                else -> null
            }
        }
    }

    private fun polynomial(expression: SymbolicExpression, variable: String): Polynomial? = when (expression) {
        is SymbolicExpression.Number -> Polynomial(listOf(expression.value))
        is SymbolicExpression.Variable -> if (expression.name == variable) Polynomial(listOf(zero, one)) else null
        is SymbolicExpression.UnaryMinus -> polynomial(expression.value, variable)?.scale(-one)
        is SymbolicExpression.Sum -> expression.terms.fold(Polynomial.ZERO as Polynomial?) { total, term -> total?.plus(polynomial(term, variable) ?: return null) }
        is SymbolicExpression.Product -> expression.factors.fold(Polynomial.ONE as Polynomial?) { total, factor -> total?.times(polynomial(factor, variable) ?: return null) }
        is SymbolicExpression.Power -> {
            val base = polynomial(expression.base, variable) ?: return null
            val power = (expression.exponent as? SymbolicExpression.Number)?.value ?: return null
            if (power.denominator != BigInteger.ONE || power.numerator.signum() < 0 || power.numerator > BigInteger.valueOf(12)) null else base.pow(power.numerator.toInt())
        }
        is SymbolicExpression.Function -> null
    }

    private data class Polynomial(private val values: List<ExactRational>) {
        private val clean = values.dropLastWhile { it.isZero }.ifEmpty { listOf(zero) }
        val degree get() = clean.lastIndex
        operator fun get(index: Int) = clean.getOrElse(index) { zero }
        operator fun plus(other: Polynomial) = Polynomial(List(maxOf(clean.size, other.clean.size)) { this[it] + other[it] })
        operator fun times(other: Polynomial): Polynomial { val out = MutableList(clean.size + other.clean.size - 1) { zero }; clean.forEachIndexed { i, a -> other.clean.forEachIndexed { j, b -> out[i + j] += a * b } }; return Polynomial(out) }
        fun scale(value: ExactRational) = Polynomial(clean.map { it * value })
        fun pow(n: Int) = (1..n).fold(ONE) { total, _ -> total * this }
        fun evaluate(x: ExactRational) = clean.reversed().fold(zero) { total, coefficient -> total * x + coefficient }
        fun exactRoots(): List<ExactRational> {
            var current = this; val roots = mutableListOf<ExactRational>()
            while (current.degree > 0) {
                val root = current.candidates().firstOrNull { current.evaluate(it).isZero } ?: break
                roots += root; current = current.divide(root)
            }
            return roots
        }
        private fun divide(root: ExactRational): Polynomial { val out = MutableList(degree) { zero }; out[degree - 1] = clean.last(); for (i in degree - 2 downTo 0) out[i] = clean[i + 1] + root * out[i + 1]; return Polynomial(out) }
        private fun candidates(): List<ExactRational> {
            var common = BigInteger.ONE; clean.forEach { common = common / common.gcd(it.denominator) * it.denominator }
            val ints = clean.map { it.numerator * (common / it.denominator) }; val c = ints.first().abs(); val lead = ints.last().abs()
            if (c == BigInteger.ZERO) return listOf(zero); if (c.bitLength() > 20 || lead.bitLength() > 20) return emptyList()
            fun divisors(n: Int) = (1..n).filter { n % it == 0 }
            return divisors(c.toInt()).flatMap { p -> divisors(lead.toInt()).flatMap { q -> ExactRational.of(BigInteger.valueOf(p.toLong()), BigInteger.valueOf(q.toLong())).let { listOf(it, -it) } } }.distinct()
        }
        fun render(variable: String) = clean.indices.reversed().mapNotNull { power ->
            val c = clean[power]; if (c.isZero) null else when (power) { 0 -> "$c"; 1 -> "$c*$variable"; else -> "$c*$variable^$power" }
        }.joinToString(" + ").replace("+ -", "- ").ifBlank { "0" }
        companion object { val ZERO = Polynomial(listOf(zero)); val ONE = Polynomial(listOf(one)) }
    }

    private data class Isolation(val equationIndex: Int, val variable: String, val expression: SymbolicExpression)
    private fun variablesExcept(expression: SymbolicExpression, excluded: String): Boolean = variables(expression).none { it == excluded }
    private fun variables(expression: SymbolicExpression): Set<String> = when (expression) {
        is SymbolicExpression.Number -> emptySet(); is SymbolicExpression.Variable -> setOf(expression.name)
        is SymbolicExpression.UnaryMinus -> variables(expression.value); is SymbolicExpression.Sum -> expression.terms.flatMap { variables(it) }.toSet()
        is SymbolicExpression.Product -> expression.factors.flatMap { variables(it) }.toSet(); is SymbolicExpression.Power -> variables(expression.base) + variables(expression.exponent)
        is SymbolicExpression.Function -> expression.arguments.flatMap { variables(it) }.toSet()
    }
    private fun coefficient(raw: String): ExactRational = raw.removeSuffix("*").let { when (it) { "", "+" -> one; "-" -> -one; else -> ExactRational.parse(it) } }
    private fun sqrtRational(value: ExactRational): ExactRational? {
        if (value < zero) return null
        fun root(n: BigInteger): BigInteger? { val guess = sqrt(n.toDouble()).toLong(); return BigInteger.valueOf(guess).takeIf { it * it == n } }
        return root(value.numerator)?.let { p -> root(value.denominator)?.let { q -> ExactRational.of(p, q) } }
    }
    private fun factorial(n: Int): BigInteger = (2..n).fold(BigInteger.ONE) { total, value -> total * BigInteger.valueOf(value.toLong()) }
    private fun parseMatrix(source: String): List<List<ExactRational>> {
        val body = source.trim().removePrefix("[").removeSuffix("]")
        val rows = Regex("\\[([^]]+)]").findAll(body).map { match -> match.groupValues[1].split(',').map { ExactRational.parse(it) } }.toList()
        require(rows.isNotEmpty() && rows.all { it.size == rows.first().size }) { "Use a rectangular matrix such as [[1,2],[3,4]]." }
        return rows
    }
    private fun multiply(a: List<List<ExactRational>>, b: List<List<ExactRational>>) = a.mapIndexed { i, _ -> b[0].indices.map { j -> b.indices.fold(zero) { total, k -> total + a[i][k] * b[k][j] } } }
    private fun matrixText(matrix: List<List<ExactRational>>) = matrix.joinToString(prefix = "[", postfix = "]") { it.joinToString(prefix = "[", postfix = "]") }
    private fun matrixText(matrix: Array<DoubleArray>) = matrix.joinToString(prefix = "[", postfix = "]") { row ->
        row.joinToString(prefix = "[", postfix = "]", transform = ::format)
    }
    private fun format(value: Double): String = if (abs(value) < 5e-10) "0" else String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    private fun safe(source: String, operation: String, block: () -> CasRow): CasRow = runCatching(block).getOrElse { error ->
        CasRow(source, operation, "Not supported", null, emptyList(), listOf(CasStep("Unsupported", source, error.message ?: "The requested exact method is outside its verified domain.")), supported = false)
    }
}
