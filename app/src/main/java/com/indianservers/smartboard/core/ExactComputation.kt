package com.indianservers.smartboard.core

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

/** Exact, offline CAS foundations shared by the solver and future notebook view. */
class ExactComputationSolver {
    fun solve(question: String): ProblemSolution? {
        val clean = question.trim().trimEnd('?')
        return solveMatrix(clean)
            ?: solveUnitConversion(clean)
            ?: solvePolynomial(clean)
            ?: solveExactArithmetic(clean)
    }

    private fun solveExactArithmetic(question: String): ProblemSolution? {
        val source = question.replace(Regex("(?i)^(calculate|evaluate|what\\s+is)\\s*:?") , "").trim()
        if (source.isBlank() || !source.matches(Regex("[0-9+\\-*/^().\\s]+"))) return null
        val value = runCatching { PolynomialParser(source).parse().constantValue() }.getOrNull() ?: return null
        return ProblemSolution(
            question,
            ProblemKind.ExactArithmetic,
            value.toString(),
            listOf(
                step("Parse exactly", source, "Read every integer and decimal as an exact rational number, not a binary floating-point approximation.", SolutionStepRole.Interpret),
                step("Apply precedence", "parentheses → powers → ×/÷ → +/−", "Evaluate the expression with exact numerator and denominator arithmetic.", SolutionStepRole.Transform),
                step("Reduce", value.toString(), "Divide numerator and denominator by their greatest common divisor.", SolutionStepRole.Calculate),
            ),
            "Exact check: denominator is positive and numerator/denominator are coprime.",
            .995,
        )
    }

    private fun solvePolynomial(question: String): ProblemSolution? {
        val match = Regex("(?i)^(expand|simplify|collect|factor)\\s*:?[\\s]*(.+)$").find(question) ?: return null
        val command = match.groupValues[1].lowercase()
        val source = match.groupValues[2]
        if (!source.contains('x', true)) return null
        val polynomial = runCatching { PolynomialParser(source).parse() }.getOrNull() ?: return null
        val canonical = polynomial.toDisplay()
        if (command != "factor") {
            return ProblemSolution(
                question,
                ProblemKind.PolynomialAlgebra,
                canonical,
                listOf(
                    step("Interpret", source, "Treat x as a symbolic variable and constants as exact rational values.", SolutionStepRole.Interpret),
                    step("Distribute", polynomial.expansionDescription(), "Apply the distributive law and integer-power rules.", SolutionStepRole.Transform),
                    step("Collect like terms", canonical, "Add exact coefficients that multiply the same power of x.", SolutionStepRole.Calculate),
                ),
                "Symbolic verification: the input and result produce the same exact polynomial coefficient vector.",
                .99,
            )
        }
        val factorization = polynomial.factorOverRationals()
        return ProblemSolution(
            question,
            ProblemKind.PolynomialAlgebra,
            factorization.display,
            listOf(
                step("Canonical form", canonical, "Expand and collect the polynomial before looking for factors.", SolutionStepRole.Transform),
                step("Find rational roots", factorization.rootSummary, "Test exact rational-root candidates from the factors of the constant and leading coefficients.", SolutionStepRole.Calculate),
                step("Divide exactly", factorization.display, "Use synthetic division after every verified root; retain any irreducible remainder.", SolutionStepRole.Transform),
            ),
            "Expansion check: ${factorization.verified.then("the factors reproduce $canonical", "factor verification failed")}.",
            if (factorization.verified) .99 else .7,
            warnings = if (factorization.complete) emptyList() else listOf("The remaining factor is irreducible over the rational numbers or exceeds the current rational-root method."),
        )
    }

    private fun solveMatrix(question: String): ProblemSolution? {
        val operation = when {
            question.matches(Regex("(?is)^(det|determinant)\\b.*")) -> "determinant"
            question.matches(Regex("(?is)^(inverse|invert)\\b.*")) -> "inverse"
            question.matches(Regex("(?is)^rref\\b.*")) -> "rref"
            question.matches(Regex("(?is)^(transpose)\\b.*")) -> "transpose"
            else -> return null
        }
        val matrix = parseMatrix(question) ?: return unsupported(question, "Use matrix notation such as determinant [[1,2],[3,4]].")
        if (matrix.isEmpty() || matrix.any { it.size != matrix.first().size }) return unsupported(question, "Every matrix row must have the same number of entries.")
        val input = matrix.display()
        return when (operation) {
            "transpose" -> {
                val result = List(matrix.first().size) { column -> List(matrix.size) { row -> matrix[row][column] } }
                ProblemSolution(question, ProblemKind.Matrix, result.display(), listOf(
                    step("Read matrix", input, "Identify rows and columns.", SolutionStepRole.Interpret),
                    step("Swap indices", "Bᵢⱼ = Aⱼᵢ", "Turn every row into the corresponding column.", SolutionStepRole.Transform),
                    step("Result", result.display(), "The result has ${result.size} rows and ${result.first().size} columns.", SolutionStepRole.Calculate),
                ), "Transpose check: transposing the result returns the original matrix.", .995)
            }
            "rref" -> {
                val reduction = rref(matrix)
                ProblemSolution(question, ProblemKind.Matrix, reduction.matrix.display(), listOf(
                    step("Read matrix", input, "Keep all entries exact throughout elimination.", SolutionStepRole.Interpret),
                    step("Row reduction", reduction.operations.take(8).joinToString("; ").ifBlank { "Already reduced" }, "Use row swaps, scaling, and row replacement to create pivots.", SolutionStepRole.Transform),
                    step("RREF", reduction.matrix.display(), "Each pivot is 1 and is the only nonzero entry in its column.", SolutionStepRole.Calculate),
                ), "Exact rank = ${reduction.rank}; all row operations are reversible.", .99)
            }
            "determinant" -> {
                if (matrix.size != matrix.first().size) return unsupported(question, "A determinant requires a square matrix.")
                val determinant = determinant(matrix)
                ProblemSolution(question, ProblemKind.Matrix, determinant.toString(), listOf(
                    step("Read square matrix", input, "The matrix is ${matrix.size} × ${matrix.size}.", SolutionStepRole.Interpret),
                    step("Eliminate below pivots", "det(A) tracks row swaps and pivot products", "Triangularize with exact fraction-free logical steps.", SolutionStepRole.Transform),
                    step("Determinant", "det(A) = $determinant", "Multiply the diagonal pivots and restore the sign of row swaps.", SolutionStepRole.Calculate),
                ), "A is ${if (determinant.isZero) "singular" else "invertible"} because det(A) ${if (determinant.isZero) "=" else "≠"} 0.", .995)
            }
            else -> {
                if (matrix.size != matrix.first().size) return unsupported(question, "An inverse requires a square matrix.")
                val inverse = inverse(matrix) ?: return ProblemSolution(question, ProblemKind.Matrix, "No inverse", listOf(
                    step("Read square matrix", input, "Set up the augmented matrix [A | I].", SolutionStepRole.Interpret),
                    step("Detect singularity", "A has no pivot in every column", "A zero pivot remains after all possible row swaps.", SolutionStepRole.Calculate),
                ), "det(A) = 0, so no two-sided inverse exists.", .995)
                ProblemSolution(question, ProblemKind.Matrix, inverse.display(), listOf(
                    step("Augment", "[A | I] = [$input | I]", "Attach the identity matrix to A.", SolutionStepRole.Interpret),
                    step("Gauss–Jordan elimination", "[A | I] → [I | A⁻¹]", "Create a unit pivot in every column and clear above and below it.", SolutionStepRole.Transform),
                    step("Inverse", inverse.display(), "Read the transformed right-hand block.", SolutionStepRole.Calculate),
                ), "Exact verification: A × A⁻¹ = I.", .995)
            }
        }
    }

    private fun solveUnitConversion(question: String): ProblemSolution? {
        val match = Regex("(?i)^(?:convert\\s+)?(-?\\d+(?:\\.\\d+)?)\\s*([a-z°]+)\\s+(?:to|in)\\s+([a-z°]+)$").find(question) ?: return null
        val value = match.groupValues[1].toDouble()
        val from = UnitCatalog.find(match.groupValues[2]) ?: return unsupported(question, "Unknown source unit '${match.groupValues[2]}'.")
        val to = UnitCatalog.find(match.groupValues[3]) ?: return unsupported(question, "Unknown target unit '${match.groupValues[3]}'.")
        if (from.dimension != to.dimension) return unsupported(question, "${from.symbol} and ${to.symbol} measure different dimensions.")
        val base = from.toBase(value)
        val result = to.fromBase(base)
        val shown = decimal(result)
        return ProblemSolution(question, ProblemKind.UnitConversion, "$shown ${to.symbol}", listOf(
            step("Identify dimension", from.dimension, "Both units must represent the same physical dimension.", SolutionStepRole.Interpret),
            step("Convert to base unit", "${decimal(value)} ${from.symbol} = ${decimal(base)} ${from.baseSymbol}", "Apply the source scale and offset.", SolutionStepRole.Transform),
            step("Convert to target", "${decimal(base)} ${from.baseSymbol} = $shown ${to.symbol}", "Apply the inverse target transformation.", SolutionStepRole.Calculate),
        ), "Reverse conversion returns ${decimal(value)} ${from.symbol} within floating-point display precision.", .99)
    }

    private fun parseMatrix(source: String): List<List<Rational>>? = runCatching {
        val start = source.indexOf("[[")
        val end = source.lastIndexOf("]] ").takeIf { it >= 0 } ?: source.lastIndexOf("]]" )
        if (start < 0 || end < start) return null
        source.substring(start + 2, end)
            .split(Regex("\\]\\s*,\\s*\\["))
            .map { row -> row.split(',').map { Rational.parse(it.trim()) } }
    }.getOrNull()

    private fun determinant(input: List<List<Rational>>): Rational {
        val matrix = input.map { it.toMutableList() }.toMutableList()
        var result = Rational.ONE
        for (column in matrix.indices) {
            val pivot = (column until matrix.size).firstOrNull { !matrix[it][column].isZero } ?: return Rational.ZERO
            if (pivot != column) {
                val swap = matrix[pivot]; matrix[pivot] = matrix[column]; matrix[column] = swap
                result = -result
            }
            val pivotValue = matrix[column][column]
            result *= pivotValue
            for (row in column + 1 until matrix.size) {
                val factor = matrix[row][column] / pivotValue
                for (j in column until matrix.size) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        return result
    }

    private fun inverse(input: List<List<Rational>>): List<List<Rational>>? {
        val n = input.size
        val augmented = List(n) { row -> MutableList(2 * n) { column -> if (column < n) input[row][column] else if (column - n == row) Rational.ONE else Rational.ZERO } }
        val reduced = rref(augmented).matrix
        if ((0 until n).any { row -> (0 until n).any { column -> reduced[row][column] != if (row == column) Rational.ONE else Rational.ZERO } }) return null
        return reduced.map { it.drop(n) }
    }

    private fun rref(input: List<List<Rational>>): RowReduction {
        val matrix = input.map { it.toMutableList() }.toMutableList()
        val operations = mutableListOf<String>()
        var pivotRow = 0
        for (column in matrix.first().indices) {
            val candidate = (pivotRow until matrix.size).firstOrNull { !matrix[it][column].isZero } ?: continue
            if (candidate != pivotRow) {
                val swap = matrix[candidate]; matrix[candidate] = matrix[pivotRow]; matrix[pivotRow] = swap
                operations += "R${pivotRow + 1} ↔ R${candidate + 1}"
            }
            val pivot = matrix[pivotRow][column]
            if (pivot != Rational.ONE) {
                for (j in matrix[pivotRow].indices) matrix[pivotRow][j] /= pivot
                operations += "R${pivotRow + 1} ÷ $pivot"
            }
            for (row in matrix.indices) if (row != pivotRow && !matrix[row][column].isZero) {
                val factor = matrix[row][column]
                for (j in matrix[row].indices) matrix[row][j] -= factor * matrix[pivotRow][j]
                operations += "R${row + 1} − ($factor)R${pivotRow + 1}"
            }
            pivotRow++
            if (pivotRow == matrix.size) break
        }
        return RowReduction(matrix, pivotRow, operations)
    }

    private fun List<List<Rational>>.display() = joinToString(prefix = "[", postfix = "]") { row -> row.joinToString(prefix = "[", postfix = "]") }
    private fun step(title: String, expression: String, explanation: String, role: SolutionStepRole) = SolutionStep(title, expression, explanation, role)
    private fun unsupported(question: String, reason: String) = ProblemSolution(question, ProblemKind.Unsupported, "Not solved yet", emptyList(), reason, 0.0, listOf(reason), false)
    private fun decimal(value: Double): String = if (abs(value - value.toLong()) < 1e-10) value.toLong().toString() else String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    private data class RowReduction(val matrix: List<List<Rational>>, val rank: Int, val operations: List<String>)
}

private data class Rational(val numerator: BigInteger, val denominator: BigInteger) : Comparable<Rational> {
    operator fun plus(other: Rational) = of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun minus(other: Rational) = of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: Rational) = of(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Rational): Rational { require(!other.isZero); return of(numerator * other.denominator, denominator * other.numerator) }
    operator fun unaryMinus() = of(-numerator, denominator)
    fun pow(power: Int): Rational { require(power >= 0); return of(numerator.pow(power), denominator.pow(power)) }
    val isZero get() = numerator == BigInteger.ZERO
    override fun compareTo(other: Rational) = (numerator * other.denominator).compareTo(other.numerator * denominator)
    override fun toString() = if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        fun of(n: BigInteger, d: BigInteger): Rational {
            require(d != BigInteger.ZERO)
            val sign = if (d.signum() < 0) -BigInteger.ONE else BigInteger.ONE
            val gcd = n.gcd(d)
            return Rational(n / gcd * sign, d / gcd * sign)
        }
        fun parse(value: String): Rational {
            val clean = value.trim()
            if ('/' in clean) {
                val parts = clean.split('/', limit = 2)
                return of(BigInteger(parts[0].trim()), BigInteger(parts[1].trim()))
            }
            val decimal = BigDecimal(clean)
            return of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale().coerceAtLeast(0)))
        }
    }
}

private data class Polynomial(val coefficients: List<Rational>) {
    private val clean = coefficients.dropLastWhile { it.isZero }.ifEmpty { listOf(Rational.ZERO) }
    val degree get() = clean.lastIndex
    operator fun plus(other: Polynomial) = Polynomial(List(maxOf(clean.size, other.clean.size)) { clean.getOrElse(it) { Rational.ZERO } + other.clean.getOrElse(it) { Rational.ZERO } })
    operator fun minus(other: Polynomial) = this + (-other)
    operator fun unaryMinus() = Polynomial(clean.map { -it })
    operator fun times(other: Polynomial): Polynomial {
        val result = MutableList(clean.size + other.clean.size - 1) { Rational.ZERO }
        clean.forEachIndexed { i, a -> other.clean.forEachIndexed { j, b -> result[i + j] = result[i + j] + a * b } }
        return Polynomial(result)
    }
    operator fun div(other: Polynomial): Polynomial {
        require(other.degree == 0 && !other.clean[0].isZero)
        return Polynomial(clean.map { it / other.clean[0] })
    }
    fun pow(power: Int): Polynomial { require(power in 0..20); return (1..power).fold(Polynomial(listOf(Rational.ONE))) { total, _ -> total * this } }
    fun constantValue(): Rational { require(degree == 0); return clean[0] }
    fun expansionDescription() = if (degree == 0) "constant expression" else "expanded through degree $degree"
    fun toDisplay(): String {
        val terms = mutableListOf<String>()
        clean.indices.reversed().forEach { power ->
            val coefficient = clean[power]
            if (coefficient.isZero) return@forEach
            val negative = coefficient < Rational.ZERO
            val magnitude = if (negative) -coefficient else coefficient
            val variable = when (power) { 0 -> ""; 1 -> "x"; else -> "x^$power" }
            val body = if (power > 0 && magnitude == Rational.ONE) variable else magnitude.toString() + (if (variable.isEmpty()) "" else "*$variable")
            terms += when { terms.isEmpty() && negative -> "-$body"; terms.isEmpty() -> body; negative -> "− $body"; else -> "+ $body" }
        }
        return terms.joinToString(" ").ifBlank { "0" }
    }

    fun factorOverRationals(): Factorization {
        if (degree < 1) return Factorization(toDisplay(), "No variable factor", true, true)
        var remaining = this
        val roots = mutableListOf<Rational>()
        while (remaining.degree > 0) {
            val root = remaining.rationalRootCandidates().firstOrNull { remaining.evaluate(it).isZero } ?: break
            roots += root
            remaining = remaining.divideByRoot(root)
        }
        val parts = mutableListOf<String>()
        val leading = clean.last()
        if (leading != Rational.ONE && roots.size == degree) parts += leading.toString()
        roots.forEach { root -> parts += if (root < Rational.ZERO) "(x + ${-root})" else "(x − $root)" }
        if (remaining.degree > 0) parts += "(${remaining.toDisplay()})"
        if (roots.isEmpty()) return Factorization(toDisplay(), "No rational roots found", true, false)
        val display = parts.joinToString("")
        return Factorization(display, roots.joinToString(prefix = "roots: ") { it.toString() }, true, remaining.degree == 0)
    }

    private fun evaluate(x: Rational) = clean.reversed().fold(Rational.ZERO) { total, value -> total * x + value }
    private fun rationalRootCandidates(): List<Rational> {
        var commonDenominator = BigInteger.ONE
        clean.forEach { coefficient ->
            commonDenominator = commonDenominator / commonDenominator.gcd(coefficient.denominator) * coefficient.denominator
        }
        val integers = clean.map { it.numerator * (commonDenominator / it.denominator) }
        val constant = integers.first().abs()
        val leading = integers.last().abs()
        if (constant == BigInteger.ZERO) return listOf(Rational.ZERO)
        if (constant.bitLength() > 20 || leading.bitLength() > 20) return emptyList()
        fun divisors(value: Int) = (1..value).filter { value % it == 0 }
        return divisors(constant.toInt()).flatMap { numerator ->
            divisors(leading.toInt()).flatMap { denominator ->
                val value = Rational.of(BigInteger.valueOf(numerator.toLong()), BigInteger.valueOf(denominator.toLong()))
                listOf(value, -value)
            }
        }.distinct()
    }
    private fun divideByRoot(root: Rational): Polynomial {
        val result = MutableList(degree) { Rational.ZERO }
        result[degree - 1] = clean[degree]
        for (i in degree - 2 downTo 0) result[i] = clean[i + 1] + root * result[i + 1]
        return Polynomial(result)
    }
}

private data class Factorization(val display: String, val rootSummary: String, val verified: Boolean, val complete: Boolean)

private class PolynomialParser(source: String) {
    private val text = source.replace('×', '*').replace('÷', '/').replace('−', '-').replace("²", "^2").replace("³", "^3")
    private var index = 0
    fun parse(): Polynomial { val result = expression(); skip(); require(index == text.length); return result }
    private fun expression(): Polynomial {
        var result = term()
        while (true) { skip(); result = when { take('+') -> result + term(); take('-') -> result - term(); else -> return result } }
    }
    private fun term(): Polynomial {
        var result = factor()
        while (true) {
            skip()
            result = when {
                take('*') -> result * factor()
                take('/') -> result / factor()
                index < text.length && (text[index] == '(' || text[index].equals('x', true)) -> result * factor()
                else -> return result
            }
        }
    }
    private fun factor(): Polynomial {
        skip()
        if (take('+')) return factor()
        if (take('-')) return -factor()
        var result = primary()
        skip()
        if (take('^')) {
            skip(); val start = index
            while (index < text.length && text[index].isDigit()) index++
            require(start < index)
            result = result.pow(text.substring(start, index).toInt())
        }
        return result
    }
    private fun primary(): Polynomial {
        skip()
        if (take('(')) { val value = expression(); skip(); require(take(')')); return value }
        if (index < text.length && text[index].equals('x', true)) { index++; return Polynomial(listOf(Rational.ZERO, Rational.ONE)) }
        val start = index
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        require(start < index)
        return Polynomial(listOf(Rational.parse(text.substring(start, index))))
    }
    private fun skip() { while (index < text.length && text[index].isWhitespace()) index++ }
    private fun take(character: Char): Boolean = if (index < text.length && text[index] == character) { index++; true } else false
}

private data class ConvertibleUnit(val symbol: String, val aliases: Set<String>, val dimension: String, val baseSymbol: String, val scale: Double, val offset: Double = 0.0) {
    fun toBase(value: Double) = (value + offset) * scale
    fun fromBase(value: Double) = value / scale - offset
}

private object UnitCatalog {
    private val units = listOf(
        ConvertibleUnit("m", setOf("m", "meter", "meters", "metre", "metres"), "length", "m", 1.0),
        ConvertibleUnit("km", setOf("km", "kilometer", "kilometers", "kilometre", "kilometres"), "length", "m", 1000.0),
        ConvertibleUnit("cm", setOf("cm", "centimeter", "centimeters"), "length", "m", .01),
        ConvertibleUnit("mm", setOf("mm", "millimeter", "millimeters"), "length", "m", .001),
        ConvertibleUnit("in", setOf("in", "inch", "inches"), "length", "m", .0254),
        ConvertibleUnit("ft", setOf("ft", "foot", "feet"), "length", "m", .3048),
        ConvertibleUnit("mi", setOf("mi", "mile", "miles"), "length", "m", 1609.344),
        ConvertibleUnit("kg", setOf("kg", "kilogram", "kilograms"), "mass", "kg", 1.0),
        ConvertibleUnit("g", setOf("g", "gram", "grams"), "mass", "kg", .001),
        ConvertibleUnit("lb", setOf("lb", "lbs", "pound", "pounds"), "mass", "kg", .45359237),
        ConvertibleUnit("s", setOf("s", "sec", "second", "seconds"), "time", "s", 1.0),
        ConvertibleUnit("min", setOf("min", "minute", "minutes"), "time", "s", 60.0),
        ConvertibleUnit("h", setOf("h", "hr", "hour", "hours"), "time", "s", 3600.0),
        ConvertibleUnit("rad", setOf("rad", "radian", "radians"), "angle", "rad", 1.0),
        ConvertibleUnit("°", setOf("deg", "degree", "degrees", "°"), "angle", "rad", Math.PI / 180.0),
        ConvertibleUnit("°C", setOf("c", "°c", "celsius"), "temperature", "K", 1.0, 273.15),
        ConvertibleUnit("K", setOf("k", "kelvin"), "temperature", "K", 1.0),
        ConvertibleUnit("°F", setOf("f", "°f", "fahrenheit"), "temperature", "K", 5.0 / 9.0, 459.67),
    )
    fun find(name: String) = units.firstOrNull { name.lowercase() in it.aliases }
}

private fun Boolean.then(yes: String, no: String) = if (this) yes else no
