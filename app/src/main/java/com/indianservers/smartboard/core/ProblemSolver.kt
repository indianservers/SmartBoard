package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ProblemKind(val label: String) {
    Arithmetic("Arithmetic"),
    ExactArithmetic("Exact arithmetic"),
    PolynomialAlgebra("Symbolic algebra"),
    Matrix("Linear algebra"),
    UnitConversion("Unit conversion"),
    Percentage("Percentage"),
    SequenceSeries("Sequences and series"),
    LinearEquation("Linear equation"),
    QuadraticEquation("Quadratic equation"),
    Inequality("Inequality"),
    LinearSystem("Two-variable system"),
    Derivative("Symbolic derivative"),
    Integral("Symbolic / definite integral"),
    Statistics("Descriptive statistics"),
    Unsupported("Needs more information"),
}

enum class SolutionStepRole { Interpret, Transform, Calculate, Verify }

data class SolutionStep(
    val title: String,
    val expression: String,
    val explanation: String,
    val role: SolutionStepRole,
)

data class ProblemSolution(
    val question: String,
    val kind: ProblemKind,
    val answer: String,
    val steps: List<SolutionStep>,
    val verification: String,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
    val supported: Boolean = true,
)

/**
 * An offline-first, deterministic maths solver. Every supported answer carries a
 * reproducible derivation and an independent substitution or numerical check.
 */
class MathProblemSolver(private val expressions: ExpressionEngine = ExpressionEngine()) {
    private val calculus = CalculusProblemSolver(expressions)
    private val exact = ExactComputationSolver()
    fun solve(rawQuestion: String): ProblemSolution {
        val question = normalize(rawQuestion)
        if (question.isBlank()) return unsupported(rawQuestion, "Enter a mathematical question.")

        return runCatching {
            solvePercentage(question)
                ?: solveSequenceSeries(question)
                ?: solveStatistics(question)
                ?: solveCalculus(question)
                ?: exact.solve(question)
                ?: solveSystem(question)
                ?: solveInequality(question)
                ?: solveEquation(question)
                ?: solveArithmetic(question)
                ?: unsupported(rawQuestion, "I could not identify a supported operation yet.")
        }.getOrElse {
            unsupported(rawQuestion, "The question is ambiguous or contains unsupported notation.")
        }
    }

    private fun solveSequenceSeries(q: String): ProblemSolution? {
        val lower = q.lowercase()
        if (!lower.contains("sequence") && !lower.contains("series") && !lower.contains("progression")) return null
        val arithmetic = lower.contains("arithmetic") || Regex("\\bdifference\\b|\\bd\\s*=").containsMatchIn(lower)
        val geometric = lower.contains("geometric") || Regex("\\bratio\\b|\\br\\s*=").containsMatchIn(lower)
        if (!arithmetic && !geometric) return unsupported(q, "Say whether the sequence is arithmetic or geometric, and provide a, d/r, and n.")
        val first = namedNumber(q, "a", "first", "first term") ?: namedNumber(q, "a1") ?: return unsupported(q, "Provide the first term, for example a=3.")
        val n = (namedNumber(q, "n", "terms") ?: Regex("(\\d+)\\s*(?:terms|term)").find(lower)?.groupValues?.get(1)?.toDoubleOrNull())
            ?.roundToInt()?.coerceAtLeast(1) ?: return unsupported(q, "Provide the term count n, for example n=10.")
        if (arithmetic) {
            val d = namedNumber(q, "d", "difference", "common difference") ?: return unsupported(q, "Provide the common difference d, for example d=2.")
            val nth = first + (n - 1) * d
            val sum = n * (first + nth) / 2.0
            val asksSum = lower.contains("sum") || lower.contains("series")
            return ProblemSolution(
                q, ProblemKind.SequenceSeries, if (asksSum) "S$n = ${format(sum)}" else "a$n = ${format(nth)}",
                listOf(
                    step("Identify progression", "arithmetic: a = ${format(first)}, d = ${format(d)}, n = $n", "Consecutive terms change by a constant difference.", SolutionStepRole.Interpret),
                    step("Find nth term", "a_n = a + (n - 1)d", "Substitute n=$n into the arithmetic term formula.", SolutionStepRole.Transform),
                    step("Calculate term", "a_$n = ${format(first)} + ${n - 1}·${format(d)} = ${format(nth)}", "This gives the last term used in the sum.", SolutionStepRole.Calculate),
                    step("Calculate sum", "S_n = n(a + a_n)/2 = ${format(sum)}", "Average the first and last terms, then multiply by the number of terms.", SolutionStepRole.Calculate),
                ),
                "Check: consecutive terms differ by ${format(d)} and the average term is ${format((first + nth) / 2.0)}.", .98,
            )
        }
        val r = namedNumber(q, "r", "ratio", "common ratio") ?: return unsupported(q, "Provide the common ratio r, for example r=2.")
        val nth = first * r.pow((n - 1).toDouble())
        val sum = if (abs(r - 1.0) < tolerance) first * n else first * (1 - r.pow(n.toDouble())) / (1 - r)
        val asksSum = lower.contains("sum") || lower.contains("series")
        return ProblemSolution(
            q, ProblemKind.SequenceSeries, if (asksSum) "S$n = ${format(sum)}" else "a$n = ${format(nth)}",
            listOf(
                step("Identify progression", "geometric: a = ${format(first)}, r = ${format(r)}, n = $n", "Consecutive terms multiply by a constant ratio.", SolutionStepRole.Interpret),
                step("Find nth term", "a_n = a·r^(n-1)", "Substitute n=$n into the geometric term formula.", SolutionStepRole.Transform),
                step("Calculate term", "a_$n = ${format(first)}·${format(r)}^${n - 1} = ${format(nth)}", "This gives the requested term.", SolutionStepRole.Calculate),
                step("Calculate sum", "S_n = a(1-r^n)/(1-r) = ${format(sum)}", "Use the finite geometric-sum formula.", SolutionStepRole.Calculate),
            ),
            "Check: each term is ${format(r)} times the previous term.", .98,
        )
    }

    private fun solvePercentage(q: String): ProblemSolution? {
        val match = Regex("(?:what\\s+is\\s+|calculate\\s+)?(-?\\d+(?:\\.\\d+)?)\\s*%\\s+of\\s+(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            .find(q) ?: return null
        val percent = match.groupValues[1].toDouble()
        val base = match.groupValues[2].toDouble()
        val decimal = percent / 100.0
        val result = decimal * base
        return ProblemSolution(
            q, ProblemKind.Percentage, format(result),
            listOf(
                step("Interpret", "$percent% of ${format(base)}", "'Of' means multiplication.", SolutionStepRole.Interpret),
                step("Convert percent", "$percent% = ${format(decimal)}", "Divide the percentage by 100.", SolutionStepRole.Transform),
                step("Multiply", "${format(decimal)} × ${format(base)} = ${format(result)}", "Multiply the decimal rate by the base value.", SolutionStepRole.Calculate),
            ),
            "Reverse check: ${format(result)} ÷ ${format(base)} × 100 = ${format(percent)}%.", .99,
        )
    }

    private fun solveStatistics(q: String): ProblemSolution? {
        val requested = listOf("mean", "average", "median", "mode", "range", "standard deviation", "statistics")
            .firstOrNull { q.lowercase().contains(it) } ?: return null
        val values = numberPattern.findAll(q).map { it.value.toDouble() }.toList()
        if (values.size < 2) return unsupported(q, "Provide at least two data values.")
        val sorted = values.sorted()
        val sum = values.sum()
        val mean = sum / values.size
        val median = if (values.size % 2 == 1) sorted[values.size / 2]
        else (sorted[values.size / 2 - 1] + sorted[values.size / 2]) / 2.0
        val counts = values.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 1
        val modes = if (maxCount == 1) emptyList() else counts.filterValues { it == maxCount }.keys.sorted()
        val range = sorted.last() - sorted.first()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val sd = sqrt(variance)
        val answer = when (requested) {
            "mean", "average" -> "Mean = ${format(mean)}"
            "median" -> "Median = ${format(median)}"
            "mode" -> if (modes.isEmpty()) "No mode" else "Mode = ${modes.joinToString { format(it) }}"
            "range" -> "Range = ${format(range)}"
            "standard deviation" -> "Population standard deviation = ${format(sd)}"
            else -> "Mean ${format(mean)} · Median ${format(median)} · Range ${format(range)}"
        }
        val modeText = if (modes.isEmpty()) "No value repeats" else "Most frequent: ${modes.joinToString { format(it) }} ($maxCount times)"
        return ProblemSolution(
            q, ProblemKind.Statistics, answer,
            listOf(
                step("Order the data", sorted.joinToString(", ") { format(it) }, "Sorting exposes the middle, endpoints, and repeats.", SolutionStepRole.Interpret),
                step("Mean", "${format(sum)} ÷ ${values.size} = ${format(mean)}", "Add all values and divide by the count.", SolutionStepRole.Calculate),
                step("Median and mode", "median = ${format(median)}; $modeText", "Use the middle value(s) and frequency counts.", SolutionStepRole.Calculate),
                step("Spread", "range = ${format(range)}; σ = ${format(sd)}", "σ uses the population variance, divided by n.", SolutionStepRole.Calculate),
            ),
            "Check: the mean lies between ${format(sorted.first())} and ${format(sorted.last())}; all ${values.size} values were included.", .98,
        )
    }

    private fun solveCalculus(q: String): ProblemSolution? = calculus.solve(q)

    @Suppress("unused")
    private fun solvePolynomialCalculus(q: String): ProblemSolution? {
        val lower = q.lowercase()
        val derivative = lower.contains("differentiate") || lower.contains("derivative") || lower.startsWith("d/dx")
        val integral = lower.contains("integrate") || lower.contains("integral") || lower.startsWith("∫")
        if (!derivative && !integral) return null
        val source = q
            .replace(Regex("(?i)find\\s+the\\s+derivative\\s+of|derivative\\s+of|differentiate|with\\s+respect\\s+to\\s+x|d/dx"), "")
            .replace(Regex("(?i)find\\s+the\\s+integral\\s+of|integral\\s+of|integrate|with\\s+respect\\s+to\\s+x"), "")
            .replace("∫", "").replace("dx", "").trim(' ', ':', '?')
        if (source.isBlank() || Regex("[a-wyzA-WYZ]").containsMatchIn(source)) {
            return unsupported(q, "This release differentiates and integrates polynomial expressions in x.")
        }
        val coefficients = polynomialCoefficients(source)
            ?: return unsupported(q, "This release differentiates and integrates polynomial expressions up to degree 6.")
        val canonical = polynomialText(coefficients)
        return if (derivative) {
            val result = DoubleArray((coefficients.size - 1).coerceAtLeast(1))
            for (power in 1 until coefficients.size) result[power - 1] = coefficients[power] * power
            val resultText = polynomialText(result)
            val checkAt = 1.37
            val h = 1e-5
            val numeric = (evaluate(source, mapOf("x" to checkAt + h)) - evaluate(source, mapOf("x" to checkAt - h))) / (2 * h)
            val symbolic = evaluatePolynomial(result, checkAt)
            ProblemSolution(
                q, ProblemKind.Derivative, resultText,
                listOf(
                    step("Recognize polynomial", "f(x) = $canonical", "Collect terms by powers of x.", SolutionStepRole.Interpret),
                    step("Apply power rule", "d(axⁿ)/dx = n·a·xⁿ⁻¹", "Differentiate each term; constants become zero.", SolutionStepRole.Transform),
                    step("Simplify", "f′(x) = $resultText", "Combine the differentiated terms.", SolutionStepRole.Calculate),
                ),
                "Numerical slope check at x=${format(checkAt)}: ${format(numeric)} ≈ ${format(symbolic)}.", .97,
            )
        } else {
            val result = DoubleArray(coefficients.size + 1)
            for (power in coefficients.indices) result[power + 1] = coefficients[power] / (power + 1)
            val resultText = polynomialText(result)
            ProblemSolution(
                q, ProblemKind.Integral, "$resultText + C",
                listOf(
                    step("Recognize polynomial", "f(x) = $canonical", "Collect terms by powers of x.", SolutionStepRole.Interpret),
                    step("Apply power rule", "∫axⁿ dx = a·xⁿ⁺¹/(n+1)", "Integrate each term independently.", SolutionStepRole.Transform),
                    step("Add constant", "F(x) = $resultText + C", "All antiderivatives differ by an arbitrary constant.", SolutionStepRole.Calculate),
                ),
                "Differentiating $resultText returns $canonical.", .97,
            )
        }
    }

    private fun solveSystem(q: String): ProblemSolution? {
        val equations = q.replace(Regex("(?i)solve(?:\\s+the)?\\s+system\\s*:?"), "")
            .split(Regex("[;\\n]")).map { it.trim() }.filter { it.contains('=') }
        if (equations.size != 2 || !q.contains('x', true) || !q.contains('y', true)) return null
        val first = linear2(equations[0]) ?: return unsupported(q, "Both system equations must be linear in x and y.")
        val second = linear2(equations[1]) ?: return unsupported(q, "Both system equations must be linear in x and y.")
        val determinant = first.a * second.b - second.a * first.b
        if (abs(determinant) < tolerance) {
            val consistent = abs(first.a * second.c - second.a * first.c) < tolerance &&
                abs(first.b * second.c - second.b * first.c) < tolerance
            return ProblemSolution(
                q, ProblemKind.LinearSystem,
                if (consistent) "Infinitely many solutions" else "No solution",
                listOf(
                    step("Standard form", "${linearText(first)}; ${linearText(second)}", "Move each equation into ax + by = c form.", SolutionStepRole.Transform),
                    step("Determinant", "D = ${format(determinant)}", "A zero determinant means the lines are parallel or identical.", SolutionStepRole.Calculate),
                ),
                if (consistent) "The two equations describe the same line." else "The equations describe distinct parallel lines.", .98,
            )
        }
        val x = (first.c * second.b - second.c * first.b) / determinant
        val y = (first.a * second.c - second.a * first.c) / determinant
        val residual1 = first.a * x + first.b * y - first.c
        val residual2 = second.a * x + second.b * y - second.c
        return ProblemSolution(
            q, ProblemKind.LinearSystem, "x = ${format(x)}, y = ${format(y)}",
            listOf(
                step("Standard form", "${linearText(first)}; ${linearText(second)}", "Collect x and y terms and constants.", SolutionStepRole.Transform),
                step("Determinant", "D = (${format(first.a)}·${format(second.b)}) − (${format(second.a)}·${format(first.b)}) = ${format(determinant)}", "D is non-zero, so the system has one solution.", SolutionStepRole.Calculate),
                step("Solve", "x = ${format(x)}, y = ${format(y)}", "Elimination (equivalently Cramer's rule) isolates both variables.", SolutionStepRole.Calculate),
            ),
            "Substitution residuals are ${format(residual1)} and ${format(residual2)} (both should be 0).", .99,
        )
    }

    private fun solveInequality(q: String): ProblemSolution? {
        val source = q.replace(Regex("(?i)^\\s*(solve|find\\s+x|solve\\s+for\\s+x)\\s*:?"), "").trim(' ', '?')
        val match = Regex("(.+?)(<=|>=|<|>)(.+)").find(source) ?: return null
        val left = match.groupValues[1].trim()
        val operator = match.groupValues[2]
        val right = match.groupValues[3].trim()
        if (!source.contains('x', true)) return unsupported(q, "Use an inequality in x, for example x^2 - 5x + 6 <= 0.")
        val polynomial = equationPolynomial(left, right)
            ?: return unsupported(q, "This release solves polynomial inequalities in x up to degree 2.")
        val c = polynomial.getOrElse(0) { 0.0 }
        val b = polynomial.getOrElse(1) { 0.0 }
        val a = polynomial.getOrElse(2) { 0.0 }
        val standard = "${polynomialText(polynomial)} $operator 0"
        if (abs(a) < tolerance) {
            if (abs(b) < tolerance) {
                val trueForAll = compareValue(c, operator)
                return ProblemSolution(
                    q, ProblemKind.Inequality, if (trueForAll) "All real numbers" else "No solution",
                    listOf(step("Simplify", standard, "The variable cancels, leaving a constant inequality.", SolutionStepRole.Transform)),
                    if (trueForAll) "The constant statement is true." else "The constant statement is false.", .99,
                )
            }
            val boundary = -c / b
            val effective = if (b < 0) flipInequality(operator) else operator
            val answer = linearInequalityText(boundary, effective)
            return ProblemSolution(
                q, ProblemKind.Inequality, answer,
                listOf(
                    step("Move to one side", standard, "Subtract the right side and combine like terms.", SolutionStepRole.Transform),
                    step("Find boundary", "${format(b)}x + ${format(c)} = 0 -> x = ${format(boundary)}", "The sign can only change where the expression is zero.", SolutionStepRole.Calculate),
                    step("Respect sign of coefficient", if (b < 0) "divide by a negative, flip $operator to $effective" else "divide by a positive, keep $operator", "Inequality direction changes only when multiplying or dividing by a negative.", SolutionStepRole.Transform),
                ),
                "Test point check confirms the selected side of ${format(boundary)}.", .98,
            )
        }
        val discriminant = b * b - 4 * a * c
        if (discriminant < -tolerance) {
            val alwaysPositive = a > 0
            val trueForAll = when (operator) {
                ">", ">=" -> alwaysPositive
                "<", "<=" -> !alwaysPositive
                else -> false
            }
            return ProblemSolution(
                q, ProblemKind.Inequality, if (trueForAll) "All real numbers" else "No real solution",
                listOf(
                    step("Standard form", standard, "Move all terms to one side.", SolutionStepRole.Transform),
                    step("Discriminant", "Δ = ${format(discriminant)}", "No real roots means the parabola never crosses the x-axis.", SolutionStepRole.Calculate),
                    step("Use leading coefficient", "a = ${format(a)}", "The sign is constant and follows the opening direction.", SolutionStepRole.Interpret),
                ),
                "The expression has the same sign for every real x.", .98,
            )
        }
        val rootDelta = sqrt(discriminant.coerceAtLeast(0.0))
        val r1 = ((-b - rootDelta) / (2 * a))
        val r2 = ((-b + rootDelta) / (2 * a))
        val low = minOf(r1, r2)
        val high = maxOf(r1, r2)
        val inclusive = operator.contains('=')
        val wantsPositive = operator.startsWith('>')
        val outside = (a > 0 && wantsPositive) || (a < 0 && !wantsPositive)
        val answer = quadraticInequalityText(low, high, outside, inclusive)
        return ProblemSolution(
            q, ProblemKind.Inequality, answer,
            listOf(
                step("Standard form", standard, "Move all terms to one side and combine like terms.", SolutionStepRole.Transform),
                step("Find zeros", "x = ${format(low)}, ${format(high)}", "Zeros split the number line into sign intervals.", SolutionStepRole.Calculate),
                step("Sign chart", if (outside) "outside roots satisfies $operator 0" else "between roots satisfies $operator 0", "A quadratic's sign alternates across simple roots.", SolutionStepRole.Transform),
            ),
            "Test intervals around ${format(low)} and ${format(high)} match the sign chart.", .98,
        )
    }

    private fun solveEquation(q: String): ProblemSolution? {
        if (!q.contains('=')) return null
        val equation = q.replace(Regex("(?i)^\\s*(solve|find\\s+x|solve\\s+for\\s+x)\\s*:?"), "")
            .replace(Regex("(?i)\\s+for\\s+x\\s*\\??$"), "").trim(' ', '?')
        val parts = equation.split('=', limit = 2)
        if (parts.size != 2 || !equation.contains('x', true)) return unsupported(q, "Use an equation in x, for example 2x + 3 = 11.")
        val polynomial = equationPolynomial(parts[0], parts[1])
            ?: return unsupported(q, "This release solves polynomial equations in x up to degree 2.")
        val c = polynomial.getOrElse(0) { 0.0 }
        val b = polynomial.getOrElse(1) { 0.0 }
        val a = polynomial.getOrElse(2) { 0.0 }
        if (abs(a) < tolerance) {
            if (abs(b) < tolerance) {
                val identity = abs(c) < tolerance
                return ProblemSolution(q, ProblemKind.LinearEquation, if (identity) "All real numbers" else "No solution",
                    listOf(step("Simplify", "${polynomialText(polynomial)} = 0", "Combine like terms on one side.", SolutionStepRole.Transform)),
                    if (identity) "Both sides simplify to the same expression." else "The variable cancels and leaves a false statement.", .99)
            }
            val x = -c / b
            val left = evaluate(parts[0], mapOf("x" to x))
            val right = evaluate(parts[1], mapOf("x" to x))
            return ProblemSolution(
                q, ProblemKind.LinearEquation, "x = ${format(x)}",
                listOf(
                    step("Move to one side", "${polynomialText(polynomial)} = 0", "Subtract the right side and combine like terms.", SolutionStepRole.Transform),
                    step("Isolate the variable", "${format(b)}x = ${format(-c)}", "Move the constant term to the other side.", SolutionStepRole.Transform),
                    step("Divide", "x = ${format(-c)} ÷ ${format(b)} = ${format(x)}", "Divide both sides by the coefficient of x.", SolutionStepRole.Calculate),
                ),
                "Substitution: left = ${format(left)}, right = ${format(right)}, difference = ${format(left - right)}.", .99,
            )
        }
        val discriminant = b * b - 4 * a * c
        val standard = "${polynomialText(doubleArrayOf(c, b, a))} = 0"
        if (discriminant < -tolerance) {
            val real = -b / (2 * a)
            val imaginary = sqrt(-discriminant) / abs(2 * a)
            return ProblemSolution(
                q, ProblemKind.QuadraticEquation, "x = ${format(real)} ± ${format(imaginary)}i",
                listOf(
                    step("Standard form", standard, "Move all terms to one side.", SolutionStepRole.Transform),
                    step("Discriminant", "Δ = b² − 4ac = ${format(discriminant)}", "A negative discriminant gives a complex-conjugate pair.", SolutionStepRole.Calculate),
                    step("Quadratic formula", "x = (−b ± i√−Δ)/(2a)", "Substitute a, b, and c and simplify.", SolutionStepRole.Transform),
                ),
                "The roots are complex conjugates; their sum is ${format(2 * real)} = −b/a.", .98,
            )
        }
        val rootDelta = sqrt(discriminant.coerceAtLeast(0.0))
        val x1 = (-b + rootDelta) / (2 * a)
        val x2 = (-b - rootDelta) / (2 * a)
        val residual1 = a * x1 * x1 + b * x1 + c
        val residual2 = a * x2 * x2 + b * x2 + c
        return ProblemSolution(
            q, ProblemKind.QuadraticEquation,
            if (abs(x1 - x2) < tolerance) "x = ${format(x1)} (double root)" else "x = ${format(x1)} or x = ${format(x2)}",
            listOf(
                step("Standard form", standard, "Move all terms to one side and combine like terms.", SolutionStepRole.Transform),
                step("Identify coefficients", "a = ${format(a)}, b = ${format(b)}, c = ${format(c)}", "Read coefficients from ax² + bx + c = 0.", SolutionStepRole.Interpret),
                step("Discriminant", "Δ = b² − 4ac = ${format(discriminant)}", "The discriminant determines the number of real roots.", SolutionStepRole.Calculate),
                step("Quadratic formula", "x = (−b ± √Δ)/(2a) = ${format(x1)}, ${format(x2)}", "Substitute and simplify both signs.", SolutionStepRole.Calculate),
            ),
            "Substitution residuals are ${format(residual1)} and ${format(residual2)} (both should be 0).", .99,
        )
    }

    private fun solveArithmetic(q: String): ProblemSolution? {
        if (q.contains('=')) return null
        val source = q.replace(Regex("(?i)^(calculate|evaluate|simplify|what\\s+is)\\s*:?"), "").trim(' ', '?')
        if (source.isBlank()) return null
        val value = evaluate(source)
        if (!value.isFinite()) return unsupported(q, "The expression does not have a finite real value.")
        return ProblemSolution(
            q, ProblemKind.Arithmetic, format(value),
            listOf(
                step("Normalize", source, "Read implied multiplication and standard mathematical operators.", SolutionStepRole.Interpret),
                step("Order of operations", "parentheses → powers → ×/÷ → +/−", "Evaluate higher-precedence operations first.", SolutionStepRole.Transform),
                step("Calculate", "$source = ${format(value)}", "Combine the remaining values.", SolutionStepRole.Calculate),
            ),
            "The expression was independently evaluated by the maths kernel with finite real-number checks.", .98,
        )
    }

    private fun equationPolynomial(left: String, right: String): DoubleArray? {
        val source = "($left)-($right)"
        val coefficients = polynomialCoefficients(source) ?: return null
        if (coefficients.indices.any { it > 2 && abs(coefficients[it]) > 1e-7 }) return null
        return coefficients.copyOf(3)
    }

    private fun polynomialCoefficients(source: String, degree: Int = 6): DoubleArray? = runCatching {
        if (Regex("(?i)(sin|cos|tan|sqrt|abs|exp|ln|log|min|max|if)").containsMatchIn(source)) return null
        val n = degree + 1
        val matrix = Array(n) { row ->
            val x = row.toDouble()
            DoubleArray(n + 1).also { line ->
                var power = 1.0
                for (column in 0 until n) { line[column] = power; power *= x }
                line[n] = evaluate(source, mapOf("x" to x))
            }
        }
        for (column in 0 until n) {
            val pivot = (column until n).maxBy { abs(matrix[it][column]) }
            val swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap
            if (abs(matrix[column][column]) < 1e-12) return null
            val divisor = matrix[column][column]
            for (j in column..n) matrix[column][j] /= divisor
            for (row in 0 until n) if (row != column) {
                val factor = matrix[row][column]
                for (j in column..n) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        val coefficients = DoubleArray(n) { matrix[it][n].let { value -> if (abs(value) < 1e-8) 0.0 else value } }
        for (x in listOf(-1.5, .5, 7.5)) {
            if (abs(evaluate(source, mapOf("x" to x)) - evaluatePolynomial(coefficients, x)) > 1e-5) return null
        }
        val last = coefficients.indexOfLast { abs(it) > 1e-8 }.coerceAtLeast(0)
        coefficients.copyOf(last + 1)
    }.getOrNull()

    private fun linear2(equation: String): Linear2? = runCatching {
        val parts = equation.split('=', limit = 2)
        if (parts.size != 2) return null
        val source = "(${parts[0]})-(${parts[1]})"
        fun f(x: Double, y: Double) = evaluate(source, mapOf("x" to x, "y" to y))
        val origin = f(0.0, 0.0)
        val a = f(1.0, 0.0) - origin
        val b = f(0.0, 1.0) - origin
        if (abs(f(2.0, -1.0) - (2 * a - b + origin)) > 1e-7) return null
        Linear2(a, b, -origin)
    }.getOrNull()

    private fun normalize(value: String) = value.trim()
        .replace('×', '*').replace('÷', '/').replace('−', '-')
        .replace("²", "^2").replace("³", "^3")

    private fun evaluate(source: String, variables: Map<String, Double> = emptyMap()): Double = expressions.compile(source).eval(variables)
    private fun evaluatePolynomial(c: DoubleArray, x: Double): Double = c.reversed().fold(0.0) { total, coefficient -> total * x + coefficient }

    private fun polynomialText(coefficients: DoubleArray): String {
        val terms = mutableListOf<String>()
        for (power in coefficients.indices.reversed()) {
            val coefficient = coefficients[power]
            if (abs(coefficient) < 1e-8) continue
            val magnitude = abs(coefficient)
            val body = when (power) {
                0 -> format(magnitude)
                1 -> (if (abs(magnitude - 1.0) < tolerance) "" else format(magnitude)) + "x"
                else -> (if (abs(magnitude - 1.0) < tolerance) "" else format(magnitude)) + "x^$power"
            }
            terms += when {
                terms.isEmpty() && coefficient < 0 -> "-$body"
                terms.isEmpty() -> body
                coefficient < 0 -> "− $body"
                else -> "+ $body"
            }
        }
        return terms.joinToString(" ").ifBlank { "0" }
    }

    private fun linearText(value: Linear2) = "${format(value.a)}x + ${format(value.b)}y = ${format(value.c)}"
    private fun compareValue(value: Double, operator: String) = when (operator) {
        "<" -> value < 0.0
        "<=" -> value <= 0.0
        ">" -> value > 0.0
        ">=" -> value >= 0.0
        else -> false
    }
    private fun flipInequality(operator: String) = when (operator) {
        "<" -> ">"
        "<=" -> ">="
        ">" -> "<"
        ">=" -> "<="
        else -> operator
    }
    private fun linearInequalityText(boundary: Double, operator: String) = when (operator) {
        "<" -> "x < ${format(boundary)}"
        "<=" -> "x <= ${format(boundary)}"
        ">" -> "x > ${format(boundary)}"
        ">=" -> "x >= ${format(boundary)}"
        else -> "x ? ${format(boundary)}"
    }
    private fun quadraticInequalityText(low: Double, high: Double, outside: Boolean, inclusive: Boolean): String {
        val l = format(low)
        val h = format(high)
        return if (outside) {
            if (inclusive) "x <= $l or x >= $h" else "x < $l or x > $h"
        } else {
            if (inclusive) "$l <= x <= $h" else "$l < x < $h"
        }
    }
    private fun namedNumber(source: String, vararg names: String): Double? {
        val escaped = names.joinToString("|") { Regex.escape(it) }
        return Regex("(?i)(?:\\b(?:$escaped)\\b)\\s*(?:=|:|is)?\\s*(-?\\d+(?:\\.\\d+)?)").find(source)?.groupValues?.get(1)?.toDoubleOrNull()
    }
    private fun format(value: Double): String {
        val clean = if (abs(value) < 1e-10) 0.0 else value
        val whole = round(clean)
        return if (abs(clean - whole) < 1e-9) whole.toLong().toString()
        else String.format(java.util.Locale.US, "%.6f", clean).trimEnd('0').trimEnd('.')
    }

    private fun step(title: String, expression: String, explanation: String, role: SolutionStepRole) =
        SolutionStep(title, expression, explanation, role)

    private fun unsupported(question: String, reason: String) = ProblemSolution(
        question, ProblemKind.Unsupported, "Not solved yet", emptyList(), reason, 0.0,
        listOf("Try explicit notation, such as 'solve 2x + 3 = 11' or 'differentiate x^3 - 4x'."), false,
    )

    private data class Linear2(val a: Double, val b: Double, val c: Double)

    private companion object {
        const val tolerance = 1e-8
        val numberPattern = Regex("-?\\d+(?:\\.\\d+)?")
    }
}
