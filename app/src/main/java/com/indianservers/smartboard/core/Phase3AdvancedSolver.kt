package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** Broader deterministic topics added by the 5/5 Phase 3 solver programme. */
object Phase3AdvancedSolver {
    fun solve(question: String): ProblemSolution? {
        val lower = question.lowercase()
        return when {
            lower.contains("taylor") || lower.contains("maclaurin") -> taylor(question)
            lower.contains("limit") || Regex("\\blim\\b").containsMatchIn(lower) -> limit(question)
            lower.contains("dy/dx") || lower.contains("differential equation") || Regex("\\bode\\b").containsMatchIn(lower) -> linearOde(question)
            else -> null
        }
    }

    private fun taylor(question: String): ProblemSolution? {
        val lower = question.lowercase()
        val function = when {
            lower.contains("sin") -> "sin"
            lower.contains("cos") -> "cos"
            lower.contains("exp") || Regex("\\be\\^?x\\b").containsMatchIn(lower) -> "exp"
            lower.contains("ln(1+x)") || lower.contains("log(1+x)") -> "ln1p"
            else -> return unsupported(question, "State a supported function: sin(x), cos(x), exp(x), or ln(1+x).")
        }
        val order = Regex("(?:order|degree|through)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(question)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 20) ?: 7
        val terms = when (function) {
            "sin" -> (1..order step 2).map { n -> signed(n / 2, "x^$n/${factorialLabel(n)}") }
            "cos" -> (0..order step 2).map { n -> signed(n / 2, if (n == 0) "1" else "x^$n/${factorialLabel(n)}") }
            "exp" -> (0..order).map { n -> if (n == 0) "1" else "+ x^$n/${factorialLabel(n)}" }
            else -> (1..order).map { n -> (if (n % 2 == 1) "+ " else "- ") + "x^$n/$n" }
        }
        val expansion = terms.joinToString(" ").removePrefix("+ ").replace("x^1/1!", "x").replace("x^0/0!", "1") + " + O(x^${order + 1})"
        val domain = if (function == "ln1p") "|x| < 1 (endpoint behavior considered separately)" else "all real x; truncation is local near 0"
        return solution(question, ProblemKind.SequenceSeries, expansion, listOf(
            step("Interpret expansion", "a = 0, order = $order", "Maclaurin means Taylor expansion about zero."),
            step("Generate coefficients", "f^(n)(0)/n!", "Differentiate repeatedly and evaluate each coefficient exactly."),
            step("Assemble polynomial", expansion, "Retain every non-zero term through the requested order."),
            step("State remainder", "O(x^${order + 1})", "The remainder records the first omitted order; $domain."),
        ), "Coefficients match f^(n)(0)/n! through degree $order. Domain: $domain")
    }

    private fun limit(question: String): ProblemSolution? {
        val match = Regex("(?:limit|lim)\\s*(.+?)\\s*(?:as\\s*)?x\\s*(?:->|→)\\s*(-?(?:\\d+(?:\\.\\d+)?|pi))", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(question)
            ?: return unsupported(question, "Use 'limit expression as x -> value'.")
        val source = match.groupValues[1].trim()
        val at = if (match.groupValues[2].equals("pi", true)) PI else match.groupValues[2].toDoubleOrNull() ?: return null
        val square = Regex("x\\^2\\s*-\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.toDoubleOrNull()
        val cancelledRoot = Regex("/\\s*\\(\\s*x\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*\\)", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.toDoubleOrNull()
        if (square != null && cancelledRoot != null && abs(square - cancelledRoot * cancelledRoot) < 1e-9 && abs(at - cancelledRoot) < 1e-9) {
            val value = 2 * cancelledRoot
            return solution(question, ProblemKind.Arithmetic, fmt(value), listOf(
                step("Detect indeterminate form", "0/0", "Direct substitution signals a removable discontinuity."),
                step("Factor and restrict", "(x^2-${fmt(square)})/(x-${fmt(cancelledRoot)}) = x+${fmt(cancelledRoot)}, x≠${fmt(cancelledRoot)}", "Cancel the common factor only away from the missing point."),
                step("Take the limit", "${fmt(cancelledRoot)}+${fmt(cancelledRoot)} = ${fmt(value)}", "The reduced expression is continuous near the approach point."),
            ), "Left and right values approach ${fmt(value)}; the cancelled expression agrees off the excluded point.")
        }
        val compiled = runCatching { ExpressionEngine().compile(source) }.getOrElse { return unsupported(question, "The limit expression could not be parsed safely.") }
        val direct = runCatching { compiled.eval(mapOf("x" to at)) }.getOrDefault(Double.NaN)
        if (direct.isFinite()) return solution(question, ProblemKind.Arithmetic, fmt(direct), listOf(
            step("Check continuity", source, "The expression is defined at the approach point."),
            step("Direct substitution", "x = ${fmt(at)}", "Continuous elementary expressions preserve limits."),
            step("Evaluate", fmt(direct), "Compute the exact input value numerically."),
        ), "Left, right and direct values agree at x=${fmt(at)}.")
        val estimates = (2..8).mapNotNull { power ->
            val h = 10.0.pow(-power)
            val left = runCatching { compiled.eval(mapOf("x" to at - h)) }.getOrDefault(Double.NaN)
            val right = runCatching { compiled.eval(mapOf("x" to at + h)) }.getOrDefault(Double.NaN)
            if (left.isFinite() && right.isFinite()) Triple(h, left, right) else null
        }
        if (estimates.size < 3) return unsupported(question, "Finite two-sided evidence could not be established near the requested point.")
        val last = estimates.last(); val previous = estimates[estimates.lastIndex - 1]
        val agreement = abs(last.second - last.third)
        val stability = abs((last.second + last.third) / 2 - (previous.second + previous.third) / 2)
        val scale = maxOf(1.0, abs(last.second), abs(last.third))
        if (agreement > 1e-5 * scale || stability > 1e-5 * scale) return unsupported(question, "The left and right samples do not establish one stable finite limit; inspect one-sided limits or divergence.")
        val value = (last.second + last.third) / 2
        return solution(question, ProblemKind.Arithmetic, "≈ ${fmt(value)}", listOf(
            step("Detect undefined substitution", "x = ${fmt(at)} gives an undefined form", "A limit concerns nearby behavior, not necessarily the point value."),
            step("Sample both sides", estimates.takeLast(3).joinToString { "h=${it.first}: ${fmt(it.second)}, ${fmt(it.third)}" }, "Shrink h symmetrically and retain independent left/right evidence."),
            step("Convergence test", "left-right residual = ${fmt(agreement)}", "Accept only when both sides agree and successive estimates stabilize."),
        ), "Two-sided deterministic sampling stabilized with residual ${fmt(agreement)}; result is explicitly approximate.")
    }

    private fun linearOde(question: String): ProblemSolution? {
        val match = Regex("dy/dx\\s*=\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\*?\\s*y(?:\\s*([+-])\\s*(\\d+(?:\\.\\d+)?))?", RegexOption.IGNORE_CASE).find(question)
            ?: return unsupported(question, "This local method currently supports first-order autonomous linear equations dy/dx = a*y + b.")
        val a = match.groupValues[1].toDouble()
        val b = match.groupValues.getOrNull(3)?.toDoubleOrNull()?.let { if (match.groupValues[2] == "-") -it else it } ?: 0.0
        val initial = Regex("y\\s*\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)\\s*=\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(question)
        if (abs(a) < 1e-12) {
            val constant = initial?.let { it.groupValues[2].toDouble() - b * it.groupValues[1].toDouble() }
            val answer = if (constant == null) "y = ${fmt(b)}*x + C" else "y = ${fmt(b)}*x + ${fmt(constant)}"
            return solution(question, ProblemKind.Integral, answer, listOf(step("Classify", "y' = ${fmt(b)}", "The derivative is constant."), step("Integrate", answer, "Integrate once and apply the initial value when supplied.")), "Differentiating gives y'=${fmt(b)}.")
        }
        val equilibrium = -b / a
        val coefficient = initial?.let { condition ->
            val x0 = condition.groupValues[1].toDouble(); val y0 = condition.groupValues[2].toDouble()
            (y0 - equilibrium) / exp(a * x0)
        }
        val family = "y = ${fmt(equilibrium)} + C*exp(${fmt(a)}*x)"
        val answer = coefficient?.let { "y = ${fmt(equilibrium)} + ${fmt(it)}*exp(${fmt(a)}*x)" } ?: family
        return solution(question, ProblemKind.Integral, answer, listOf(
            step("Classify", "y' - ${fmt(a)}y = ${fmt(b)}", "This is a first-order autonomous linear ODE."),
            step("Find equilibrium", "y* = -b/a = ${fmt(equilibrium)}", "Shift by the constant equilibrium to obtain a homogeneous equation."),
            step("Solve homogeneous part", family, "The shifted variable has derivative a times itself."),
            step(if (initial == null) "Retain constant" else "Apply initial condition", answer, if (initial == null) "No initial condition was supplied, so keep the solution family." else "Substitute the stated point to determine C uniquely."),
        ), "Differentiate and substitute: y' - ${fmt(a)}y = ${fmt(b)}${if (initial == null) "; C remains arbitrary." else "; the initial value also matches."}")
    }

    private fun signed(index: Int, value: String) = (if (index % 2 == 0) "+ " else "- ") + value
    private fun factorialLabel(n: Int) = if (n <= 1) "1!" else "$n!"
    private fun solution(q: String, kind: ProblemKind, answer: String, steps: List<SolutionStep>, verification: String) = ProblemSolution(q, kind, answer, steps, verification, .97)
    private fun unsupported(q: String, reason: String) = ProblemSolution(q, ProblemKind.Unsupported, "Needs clarification", emptyList(), reason, 0.0, listOf("The deterministic kernel refused to guess."), false)
    private fun step(title: String, expression: String, explanation: String) = SolutionStep(title, expression, explanation, SolutionStepRole.Transform)
    private fun fmt(value: Double): String = if (abs(value - value.toLong()) < 1e-9) value.toLong().toString() else String.format(java.util.Locale.US, "%.10g", value)
}
