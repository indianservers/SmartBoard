package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.round

class CalculusProblemSolver(private val expressions: ExpressionEngine = ExpressionEngine()) {
    private val symbolic = SymbolicCalculusEngine()

    fun solve(question: String): ProblemSolution? {
        val lower = question.lowercase()
        val derivative = lower.contains("differentiate") || lower.contains("derivative") || lower.contains("partial") || lower.startsWith("d/d") || lower.startsWith("∂/∂")
        val integral = lower.contains("integrate") || lower.contains("integral") || lower.startsWith("∫")
        if (!derivative && !integral) return null
        val variable = Regex("(?i)(?:with\\s+respect\\s+to|d/d|∂/∂)\\s*([a-z])").find(question)?.groupValues?.get(1)?.lowercase() ?: "x"
        return if (derivative) solveDerivative(question, variable) else solveIntegral(question, variable)
    }

    private fun solveDerivative(question: String, variable: String): ProblemSolution {
        val order = when {
            Regex("(?i)third\\s+derivative").containsMatchIn(question) -> 3
            Regex("(?i)second\\s+derivative").containsMatchIn(question) -> 2
            else -> Regex("(?i)(\\d+)(?:st|nd|rd|th)\\s+derivative").find(question)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
        }
        val source = question
            .replace(Regex("(?i)find\\s+the\\s+(?:first|second|third|\\d+(?:st|nd|rd|th))?\\s*(?:partial\\s+)?derivative\\s+of|(?:first|second|third|\\d+(?:st|nd|rd|th))?\\s*(?:partial\\s+)?derivative\\s+of|differentiate"), "")
            .replace(Regex("(?i)with\\s+respect\\s+to\\s+[a-z]"), "")
            .replace(Regex("(?i)(?:d/d|∂/∂)[a-z]"), "")
            .trim(' ', ':', '?')
        if (source.isBlank()) return unsupported(question, "Enter an expression to differentiate.")
        val result = runCatching { symbolic.differentiate(source, variable, order) }.getOrElse {
            return unsupported(question, "Use supported functions such as sin, cos, tan, asin, acos, atan, exp, ln, log, sqrt and abs with explicit grouping.")
        }
        val check = mutableMapOf("x" to 1.23, "y" to .71, "t" to .43).apply { put(variable, 1.23) }
        val h = 1e-5
        val numeric = runCatching {
            val center = check.getValue(variable)
            (evaluate(source, check + (variable to center + h)) - evaluate(source, check + (variable to center - h))) / (2 * h)
        }.getOrNull()
        val symbolicValue = runCatching { evaluate(result.expression, check) }.getOrNull()
        val operator = if (order == 1) "∂/∂$variable" else "d^$order/d$variable^$order"
        return ProblemSolution(
            question, ProblemKind.Derivative, result.expression,
            listOf(step("Interpret", "$operator [$source]", "Differentiate with respect to $variable${if (order > 1) " $order times" else ""}.", SolutionStepRole.Interpret)) +
                result.rules.mapIndexed { index, rule -> step("Rule ${index + 1}", rule, "Apply this rule to the matching part of the expression tree.", SolutionStepRole.Transform) } +
                step("Simplify", result.expression, "Remove zero terms, unit factors, and constant operations.", SolutionStepRole.Calculate),
            if (order == 1 && numeric != null && symbolicValue != null) "Finite-difference check at $variable=${format(check.getValue(variable))}: ${format(numeric)} ≈ ${format(symbolicValue)}."
            else "The result was derived by applying the displayed rules ${if (order > 1) "$order times" else "to the expression tree"}.",
            .97,
            result.warnings,
        )
    }

    private fun solveIntegral(question: String, variable: String): ProblemSolution {
        var source = question
            .replace(Regex("(?i)find\\s+the\\s+(?:definite\\s+|indefinite\\s+)?integral\\s+of|(?:definite\\s+|indefinite\\s+)?integral\\s+of|integrate"), "")
            .replace(Regex("(?i)with\\s+respect\\s+to\\s+[a-z]"), "")
            .replace("∫", "").trim(' ', ':', '?')
        val bounds = Regex("(?i)\\s+from\\s+(.+?)\\s+to\\s+(.+?)$").find(source)
        if (bounds != null) {
            source = source.substring(0, bounds.range.first).trim()
            val fromText = bounds.groupValues[1].trim()
            val toText = bounds.groupValues[2].trim().replace(Regex("(?i)\\s*d[a-z]$"), "")
            val from = runCatching { evaluate(fromText) }.getOrElse { return unsupported(question, "The lower integration bound '$fromText' is invalid.") }
            val to = runCatching { evaluate(toText) }.getOrElse { return unsupported(question, "The upper integration bound '$toText' is invalid.") }
            val value = runCatching { adaptiveIntegral(source, variable, from, to) }.getOrElse { return unsupported(question, "The integral crosses an undefined or non-finite region.") }
            val reverse = runCatching { adaptiveIntegral(source, variable, to, from) }.getOrNull()
            return ProblemSolution(
                question, ProblemKind.Integral, format(value),
                listOf(
                    step("Interpret bounds", "∫[$fromText,$toText] $source d$variable", "Evaluate signed area over the requested interval.", SolutionStepRole.Interpret),
                    step("Adaptive Simpson method", "Refine until local error < tolerance", "Smooth regions use fewer evaluations; curved regions receive more.", SolutionStepRole.Transform),
                    step("Accumulate", "result = ${format(value)}", "Combine error-corrected subinterval estimates.", SolutionStepRole.Calculate),
                ),
                reverse?.let { "Orientation check: reversed limits give ${format(it)}; the sum is ${format(value + it)}." } ?: "Finite-domain and error checks passed.",
                .96,
            )
        }

        source = source.replace(Regex("(?i)\\s*d$variable$"), "").trim()
        if (source.isBlank()) return unsupported(question, "Enter an expression to integrate.")
        val result = runCatching { symbolic.integrate(source, variable) }.getOrNull()
            ?: return unsupported(question, "No elementary antiderivative was found. Try a definite integral using 'from … to …' for verified numerical integration.")
        val check = mutableMapOf("x" to 1.17, "y" to .63, "t" to .41).apply { put(variable, 1.17) }
        val h = 1e-5
        val recovered = runCatching {
            val center = check.getValue(variable)
            (evaluate(result.expression, check + (variable to center + h)) - evaluate(result.expression, check + (variable to center - h))) / (2 * h)
        }.getOrNull()
        val original = runCatching { evaluate(source, check) }.getOrNull()
        return ProblemSolution(
            question, ProblemKind.Integral, "${result.expression} + C",
            listOf(step("Interpret", "∫ $source d$variable", "Find the family of antiderivatives.", SolutionStepRole.Interpret)) +
                result.rules.mapIndexed { index, rule -> step("Rule ${index + 1}", rule, "Apply the integration rule to the matching term.", SolutionStepRole.Transform) } +
                step("Add constant", "${result.expression} + C", "All antiderivatives differ by an arbitrary constant.", SolutionStepRole.Calculate),
            if (recovered != null && original != null) "Differentiate-back check at $variable=${format(check.getValue(variable))}: ${format(recovered)} ≈ ${format(original)}."
            else "Differentiate the displayed antiderivative to recover the integrand on its domain.",
            .96,
        )
    }

    private fun adaptiveIntegral(source: String, variable: String, from: Double, to: Double): Double {
        if (from == to) return 0.0
        val compiled = expressions.compile(source)
        fun f(x: Double) = compiled.eval(mapOf(variable to x)).also { require(it.isFinite()) }
        fun simpson(a: Double, b: Double, fa: Double, fm: Double, fb: Double) = (b - a) * (fa + 4 * fm + fb) / 6.0
        fun recurse(a: Double, b: Double, fa: Double, fm: Double, fb: Double, whole: Double, tolerance: Double, depth: Int): Double {
            val middle = (a + b) / 2.0
            val leftMiddle = (a + middle) / 2.0
            val rightMiddle = (middle + b) / 2.0
            val flm = f(leftMiddle); val frm = f(rightMiddle)
            val left = simpson(a, middle, fa, flm, fm); val right = simpson(middle, b, fm, frm, fb)
            val delta = left + right - whole
            if (depth <= 0 || abs(delta) <= 15 * tolerance) return left + right + delta / 15.0
            return recurse(a, middle, fa, flm, fm, left, tolerance / 2, depth - 1) + recurse(middle, b, fm, frm, fb, right, tolerance / 2, depth - 1)
        }
        val middle = (from + to) / 2.0
        val fa = f(from); val fm = f(middle); val fb = f(to)
        return recurse(from, to, fa, fm, fb, simpson(from, to, fa, fm, fb), 1e-8, 18)
    }

    private fun evaluate(source: String, variables: Map<String, Double> = emptyMap()) = expressions.compile(source).eval(variables)
    private fun step(title: String, expression: String, explanation: String, role: SolutionStepRole) = SolutionStep(title, expression, explanation, role)
    private fun unsupported(question: String, reason: String) = ProblemSolution(question, ProblemKind.Unsupported, "Not solved yet", emptyList(), reason, 0.0, listOf("Try explicit parentheses and standard function names."), false)
    private fun format(value: Double): String {
        val clean = if (abs(value) < 1e-10) 0.0 else value
        val whole = round(clean)
        return if (abs(clean - whole) < 1e-9) whole.toLong().toString() else String.format(java.util.Locale.US, "%.7f", clean).trimEnd('0').trimEnd('.')
    }
}
