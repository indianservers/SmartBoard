package com.indianservers.smartboard.core

import kotlin.math.abs

enum class MathQueryIntent(val label: String) {
    SolveEquation("Solve equation"), Simplify("Simplify"), Expand("Expand"), Factor("Factor"),
    Differentiate("Differentiate"), Integrate("Integrate"), Limit("Limit"), Series("Series"),
    DifferentialEquation("Differential equation"), Matrix("Matrix"), Statistics("Statistics"),
    Probability("Probability"), UnitConversion("Unit conversion"), Evaluate("Evaluate"), Unknown("Needs classification")
}

enum class InterpretationStatus { Clear, AssumptionRequired, NeedsClarification }

data class MathQueryCandidate(
    val normalizedQuery: String,
    val intent: MathQueryIntent,
    val confidence: Double,
    val explanation: String,
)

data class MathQueryInterpretation(
    val original: String,
    val selected: MathQueryCandidate,
    val alternatives: List<MathQueryCandidate> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val ambiguities: List<String> = emptyList(),
    val status: InterpretationStatus = InterpretationStatus.Clear,
)

/** Offline, deterministic natural-language/notation front door. It selects plans; it never supplies answers. */
object DeterministicMathQueryInterpreter {
    fun interpret(raw: String): MathQueryInterpretation {
        val clean = normalize(raw)
        val lower = clean.lowercase()
        val intent = when {
            Regex("\\b(dy/dx|differential equation|ode)\\b").containsMatchIn(lower) -> MathQueryIntent.DifferentialEquation
            Regex("\\b(limit|lim)\\b").containsMatchIn(lower) -> MathQueryIntent.Limit
            Regex("\\b(maclaurin|taylor|series expansion)\\b").containsMatchIn(lower) -> MathQueryIntent.Series
            Regex("\\b(differentiate|derivative|d/dx|partial derivative)\\b").containsMatchIn(lower) -> MathQueryIntent.Differentiate
            Regex("\\b(integrate|integral|antiderivative)\\b").containsMatchIn(lower) -> MathQueryIntent.Integrate
            Regex("\\b(simplify|reduce)\\b").containsMatchIn(lower) -> MathQueryIntent.Simplify
            Regex("\\b(expand|multiply out)\\b").containsMatchIn(lower) -> MathQueryIntent.Expand
            Regex("\\b(factor|factorise|factorize)\\b").containsMatchIn(lower) -> MathQueryIntent.Factor
            Regex("\\b(matrix|determinant|inverse|eigen|rank|rref)\\b").containsMatchIn(lower) -> MathQueryIntent.Matrix
            Regex("\\b(mean|median|mode|variance|standard deviation|regression|anova)\\b").containsMatchIn(lower) -> MathQueryIntent.Statistics
            Regex("\\b(probability|distribution|binomial|poisson|normal distribution)\\b").containsMatchIn(lower) -> MathQueryIntent.Probability
            Regex("\\b(convert|in meters|in kilometres|in kilometers|to cm|to kg|to radians|to degrees)\\b").containsMatchIn(lower) -> MathQueryIntent.UnitConversion
            '=' in clean || Regex("^solve\\b").containsMatchIn(lower) -> MathQueryIntent.SolveEquation
            clean.isNotBlank() -> MathQueryIntent.Evaluate
            else -> MathQueryIntent.Unknown
        }

        val assumptions = mutableListOf<String>()
        val ambiguities = mutableListOf<String>()
        val alternatives = mutableListOf<MathQueryCandidate>()
        var normalized = clean
        if (Regex("\\blog\\s*\\(").containsMatchIn(lower) && !Regex("log_[0-9]|base\\s+[0-9]").containsMatchIn(lower)) {
            assumptions += "log means base 10; use ln for the natural logarithm or state another base."
        }
        if (Regex("sin\\s*\\^\\s*-?1").containsMatchIn(lower)) {
            ambiguities += "sin^-1 can mean arcsin or reciprocal csc. Use asin(x) or 1/sin(x)."
            alternatives += MathQueryCandidate(normalized.replace(Regex("sin\\s*\\^\\s*-?1", RegexOption.IGNORE_CASE), "asin"), MathQueryIntent.Evaluate, .54, "Interpret the superscript as inverse function notation.")
            alternatives += MathQueryCandidate(normalized.replace(Regex("sin\\s*\\^\\s*-?1", RegexOption.IGNORE_CASE), "1/sin"), MathQueryIntent.Simplify, .46, "Interpret the exponent as a reciprocal.")
        }
        if (Regex("\\d+\\s*/\\s*\\d+\\s*[a-zA-Z]").containsMatchIn(clean)) {
            ambiguities += "A fraction followed by a variable needs grouping: (a/b)x and a/(bx) differ."
        }
        if (Regex("\\b(sin|cos|tan)\\s+[a-zA-Z]\\s*\\^\\s*2\\b").containsMatchIn(lower)) {
            assumptions += "Function application binds before the power: sin x^2 is interpreted as sin(x^2)."
            alternatives += MathQueryCandidate(normalized.replace(Regex("(sin|cos|tan)\\s+([a-zA-Z])\\s*\\^\\s*2", RegexOption.IGNORE_CASE), "$1($2)^2"), intent, .35, "Alternative reading: square the trigonometric value.")
        }
        if (intent == MathQueryIntent.SolveEquation && Regex("[a-zA-Z]").findAll(clean.substringBefore('=')).map { it.value.lowercase() }.toSet().size > 1) {
            assumptions += "Solve for x unless another unknown is explicitly requested."
        }
        if (lower.startsWith("what is ")) normalized = clean.substring(8).trim()
        val status = when { ambiguities.isNotEmpty() -> InterpretationStatus.NeedsClarification; assumptions.isNotEmpty() -> InterpretationStatus.AssumptionRequired; else -> InterpretationStatus.Clear }
        val confidence = when (status) { InterpretationStatus.Clear -> .98; InterpretationStatus.AssumptionRequired -> .86; InterpretationStatus.NeedsClarification -> .58 }
        val selected = MathQueryCandidate(normalized, intent, confidence, "Classified locally from explicit operation words and mathematical structure.")
        return MathQueryInterpretation(raw, selected, alternatives, assumptions, ambiguities, status)
    }

    private fun normalize(value: String) = value.trim()
        .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
        .replace("²", "^2").replace("³", "^3").replace("√", "sqrt")
        .replace(Regex("\\s+"), " ")
}

enum class SolverResultKind(val label: String) { Exact("Exact"), Decimal("Decimal"), Domain("Domain"), Verification("Verification"), Graph("Graph"), Table("Table") }
data class SolverResultForm(val kind: SolverResultKind, val value: String, val provenance: String, val available: Boolean = true)
data class SolverAlternative(val method: SolverMethod, val answer: String, val stepCount: Int, val reason: String)

object SolverResultPresenter {
    fun forms(solution: ProblemSolution, interpretation: MathQueryInterpretation, handoffs: List<SolverHandoff>): List<SolverResultForm> {
        val exact = solution.answer
        val decimal = decimalForm(exact)
        val domain = domainOf(interpretation.selected.normalizedQuery)
        val graph = handoffs.firstOrNull { it.destination == SolverDestination.Graph && it.enabled }
        val table = handoffs.firstOrNull { it.destination == SolverDestination.Table && it.enabled }
        return listOf(
            SolverResultForm(SolverResultKind.Exact, exact, "Deterministic local kernel", solution.supported),
            SolverResultForm(SolverResultKind.Decimal, decimal ?: "No separate decimal form", "Derived from the exact answer", decimal != null),
            SolverResultForm(SolverResultKind.Domain, domain, "Typed AST domain analysis"),
            SolverResultForm(SolverResultKind.Verification, solution.verification, "Independent substitution, reverse operation or residual"),
            SolverResultForm(SolverResultKind.Graph, graph?.payload ?: "Not applicable", "Shared graph handoff", graph != null),
            SolverResultForm(SolverResultKind.Table, table?.payload ?: "Not applicable", "Shared spreadsheet handoff", table != null),
        )
    }

    private fun decimalForm(answer: String): String? {
        val fraction = Regex("(-?\\d+)\\s*/\\s*(\\d+)").find(answer) ?: return null
        val denominator = fraction.groupValues[2].toDouble()
        if (abs(denominator) < 1e-15) return null
        val value = fraction.groupValues[1].toDouble() / denominator
        return answer.replaceRange(fraction.range, String.format(java.util.Locale.US, "%.12g", value))
    }

    private fun domainOf(query: String): String {
        val withoutOperation = query.replaceFirst(Regex("(?i)^(simplify|expand|factor|differentiate|integrate|evaluate)\\s+"), "")
        val source = withoutOperation.substringBefore(" with respect").substringBefore(" from ").trim()
            .let { if ('=' in it) it.substringBefore('=') else it }
        return runCatching {
            val expression = SymbolicCasEngine().parse(source)
            MathDomainAnalyzer.analyze(expression).description
        }.getOrDefault("Domain follows the interpreted problem statement; no additional restriction was derived.")
    }
}
