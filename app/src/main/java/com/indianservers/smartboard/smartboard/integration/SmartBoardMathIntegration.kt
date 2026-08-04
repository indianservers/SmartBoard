package com.indianservers.smartboard.smartboard.integration

import com.indianservers.smartboard.core.AdvancedStatisticsEngine
import com.indianservers.smartboard.core.CasRow
import com.indianservers.smartboard.core.MathProblemSolver
import com.indianservers.smartboard.core.SymbolicCasEngine
import com.indianservers.smartboard.core.TrustedMathKernel
import com.indianservers.smartboard.core.TypedGraphExpression
import com.indianservers.smartboard.core.TypedGraphExpressionParser
import com.indianservers.smartboard.core.TypedGraphEngine
import com.indianservers.smartboard.core.Graph3D
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SolutionStep
import com.indianservers.smartboard.smartboard.models.SolutionStepStatus
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class SmartBoardMathAction {
    EVALUATE, SIMPLIFY, FACTOR, EXPAND, SOLVE, DIFFERENTIATE, INTEGRATE, LIMIT,
    MATRIX, STATISTICS, PLOT_2D, PLOT_3D, OPEN_GEOMETRY_2D, OPEN_GEOMETRY_3D, VERIFY_WORK,
}

data class SmartBoardExpressionAnalysis(
    val normalized: String,
    val type: MathExpressionType,
    val parserVerified: Boolean,
    val actions: List<SmartBoardMathAction>,
    val warnings: List<String> = emptyList(),
)

data class SmartBoardLatexPreparation(
    val latex: String,
    val engineExpression: String,
    val analysis: SmartBoardExpressionAnalysis,
    val warnings: List<String>,
)

/**
 * Preserves authored LaTeX while producing a conservative expression for the shared engines.
 * This is intentionally not a second CAS parser.
 */
object SmartBoardLatexAdapter {
    fun prepare(source: String): Result<SmartBoardLatexPreparation> = runCatching {
        val latex = SafeLatexPreview.validate(source).getOrThrow()
        val engine = toEngineExpression(latex)
        val analysis = SmartBoardExpressionAnalyzer.analyze(engine)
        SmartBoardLatexPreparation(
            latex = latex,
            engineExpression = engine,
            analysis = analysis,
            warnings = buildList {
                if (!analysis.parserVerified) add("Notation is safe to store, but this form is not supported by the current CAS/Graph parser.")
                if (Regex("""\\(?:begin|end)\{""").containsMatchIn(latex)) add("Matrix and cases notation remains editable LaTeX and uses specialist CAS actions.")
            },
        )
    }

    fun toEngineExpression(source: String): String {
        val typedPiecewise = source.trim().startsWith("piecewise{", ignoreCase = true)
        var value = source.trim().replace("\\left", "").replace("\\right", "")
            .replace(Regex("""\^\{([^{}]+)\}""")) { "^(${it.groupValues[1]})" }
            .replace(Regex("""_\{([^{}]+)\}""")) { "_${it.groupValues[1]}" }
        repeat(16) {
            val before = value
            value = Regex("""\\(?:dfrac|tfrac|frac)\{([^{}]*)\}\{([^{}]*)\}""")
                .replace(value) { "((${it.groupValues[1]})/(${it.groupValues[2]}))" }
            value = Regex("""\\sqrt\{([^{}]*)\}""").replace(value) { "sqrt(${it.groupValues[1]})" }
            if (value == before) return@repeat
        }
        value = value
            .replace(Regex("""\\begin\{(?:p|b|v)?matrix\}"""), "[")
            .replace(Regex("""\\end\{(?:p|b|v)?matrix\}"""), "]")
            .replace(Regex("""\\begin\{(?:cases|aligned)\}"""), "")
            .replace(Regex("""\\end\{(?:cases|aligned)\}"""), "")
            .replace("\\\\", ";")
            .replace("&", ",")
            .replace("\\cdot", "*").replace("\\times", "*").replace("\\div", "/")
            .replace("\\pm", "±").replace("\\mp", "∓")
            .replace("\\leq", "<=").replace("\\le", "<=")
            .replace("\\geq", ">=").replace("\\ge", ">=")
            .replace("\\neq", "!=").replace("\\ne", "!=")
            .replace("\\pi", "pi").replace("\\theta", "theta")
            .replace("\\lambda", "lambda").replace("\\phi", "phi")
            .replace("\\infty", "infinity")
            .replace(Regex("""\\(sin|cos|tan|sec|csc|cot|asin|acos|atan|sinh|cosh|tanh|log|ln|exp|min|max)\b""")) {
                it.groupValues[1]
            }
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!typedPiecewise) value = value.replace('{', '(').replace('}', ')')
        return value
    }
}

/**
 * Uses the shared CAS/graph parsers as evidence. Syntax inspection only distinguishes container
 * forms that those parsers intentionally do not own (systems, matrices, datasets and calculus
 * notation).
 */
object SmartBoardExpressionAnalyzer {
    fun analyze(source: String): SmartBoardExpressionAnalysis {
        val normalized = normalize(source)
        if (normalized.isBlank()) return SmartBoardExpressionAnalysis("", MathExpressionType.UNKNOWN, false, emptyList(), listOf("Expression is empty."))
        val lower = normalized.lowercase(Locale.ROOT)
        val graph = runCatching { TypedGraphExpressionParser.parse(normalized) }.getOrNull()
        val casParsed = runCatching { SymbolicCasEngine().parse(equationResidual(normalized)) }.isSuccess
        val type = when {
            isMatrix(normalized) -> MathExpressionType.MATRIX
            isSystem(normalized) -> MathExpressionType.SYSTEM
            lower.startsWith("d/d") || lower.startsWith("diff(") || lower.startsWith("derivative(") -> MathExpressionType.DERIVATIVE
            lower.startsWith("int(") || lower.startsWith("integral(") || '∫' in normalized -> MathExpressionType.INTEGRAL
            lower.startsWith("lim") || lower.startsWith("limit(") -> MathExpressionType.LIMIT
            isDataset(normalized) -> MathExpressionType.DATASET
            graph is TypedGraphExpression.Inequality -> MathExpressionType.INEQUALITY
            '=' in normalized -> MathExpressionType.EQUATION
            graph is TypedGraphExpression.Polar || graph is TypedGraphExpression.Parametric ||
                graph is TypedGraphExpression.Piecewise || graph is TypedGraphExpression.Explicit -> MathExpressionType.FUNCTION
            normalized.matches(Regex("[+-]?\\d+(?:\\.\\d+)?")) -> MathExpressionType.NUMBER
            casParsed && normalized.any(Char::isLetter) -> MathExpressionType.ALGEBRAIC_EXPRESSION
            casParsed -> MathExpressionType.ARITHMETIC
            else -> MathExpressionType.UNKNOWN
        }
        return SmartBoardExpressionAnalysis(normalized, type, graph != null || casParsed, actions(type, graph))
    }

    private fun actions(type: MathExpressionType, graph: TypedGraphExpression?): List<SmartBoardMathAction> = when (type) {
        MathExpressionType.NUMBER, MathExpressionType.ARITHMETIC ->
            listOf(SmartBoardMathAction.EVALUATE, SmartBoardMathAction.SIMPLIFY)
        MathExpressionType.ALGEBRAIC_EXPRESSION ->
            listOf(SmartBoardMathAction.SIMPLIFY, SmartBoardMathAction.FACTOR, SmartBoardMathAction.EXPAND, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.EQUATION ->
            listOf(SmartBoardMathAction.SOLVE, SmartBoardMathAction.PLOT_2D, SmartBoardMathAction.OPEN_GEOMETRY_2D)
        MathExpressionType.SYSTEM -> listOf(SmartBoardMathAction.SOLVE, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.INEQUALITY -> listOf(SmartBoardMathAction.SOLVE, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.FUNCTION -> buildList {
            add(SmartBoardMathAction.PLOT_2D)
            add(SmartBoardMathAction.DIFFERENTIATE)
            add(SmartBoardMathAction.INTEGRATE)
            if (graph == null) add(SmartBoardMathAction.SIMPLIFY)
        }
        MathExpressionType.DERIVATIVE -> listOf(SmartBoardMathAction.DIFFERENTIATE, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.INTEGRAL -> listOf(SmartBoardMathAction.INTEGRATE, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.LIMIT -> listOf(SmartBoardMathAction.LIMIT, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.MATRIX -> listOf(SmartBoardMathAction.MATRIX)
        MathExpressionType.DATASET, MathExpressionType.STATISTICAL -> listOf(SmartBoardMathAction.STATISTICS, SmartBoardMathAction.PLOT_2D)
        MathExpressionType.COORDINATE, MathExpressionType.VECTOR -> listOf(SmartBoardMathAction.OPEN_GEOMETRY_2D)
        MathExpressionType.CALCULUS -> listOf(SmartBoardMathAction.DIFFERENTIATE, SmartBoardMathAction.INTEGRATE, SmartBoardMathAction.LIMIT)
        MathExpressionType.UNKNOWN -> emptyList()
    }

    private fun normalize(value: String) = value.trim()
        .replace('×', '*').replace('÷', '/').replace('−', '-').replace('π', 'p').replace("²", "^2").replace("³", "^3")
        .replace(Regex("\\s+"), " ")
    private fun equationResidual(value: String): String {
        if ('=' !in value || Regex("<=|>=").containsMatchIn(value)) return value
        val sides = value.split('=', limit = 2)
        return "(${sides[0]})-(${sides[1]})"
    }
    private fun isSystem(value: String) = (';' in value || '\n' in value) && value.split(';', '\n').count { '=' in it } > 1
    private fun isMatrix(value: String) = Regex("^\\s*(\\[\\s*\\[|\\{\\s*\\{|matrix\\s*\\()", RegexOption.IGNORE_CASE).containsMatchIn(value)
    private fun isDataset(value: String): Boolean {
        val body = value.removePrefix("[").removeSuffix("]").removePrefix("{").removeSuffix("}")
        val parts = body.split(',').map(String::trim)
        return parts.size >= 3 && parts.all { it.toDoubleOrNull() != null }
    }
}

data class SmartBoardEngineResult(
    val title: String,
    val exact: String?,
    val approximate: String?,
    val steps: List<String>,
    val assumptions: List<String>,
    val supported: Boolean,
    val verified: Boolean,
)

class SmartBoardCasAdapter(
    private val cas: SymbolicCasEngine = SymbolicCasEngine(),
    private val solver: MathProblemSolver = MathProblemSolver(),
    private val timeoutMillis: Long = 8_000,
) {
    suspend fun execute(source: String, action: SmartBoardMathAction): SmartBoardEngineResult =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) {
                when (action) {
                    SmartBoardMathAction.SOLVE -> solverResult(source)
                    else -> casResult(source, action)
                }
            }
        }

    private fun casResult(source: String, action: SmartBoardMathAction): SmartBoardEngineResult {
        val row = when (action) {
            SmartBoardMathAction.EVALUATE, SmartBoardMathAction.SIMPLIFY -> cas.simplify(source)
            SmartBoardMathAction.FACTOR -> cas.factor(source)
            SmartBoardMathAction.EXPAND -> cas.expand(source)
            SmartBoardMathAction.DIFFERENTIATE -> cas.derivative(unwrap(source, "d/dx", "diff", "derivative"))
            SmartBoardMathAction.INTEGRATE -> cas.integral(unwrap(source, "integral", "int"))
            SmartBoardMathAction.LIMIT -> cas.casRow(source, "limit")
            SmartBoardMathAction.MATRIX -> cas.rowReduce(source)
            else -> return unsupported(action)
        }
        return row.toEngineResult()
    }

    private fun solverResult(source: String): SmartBoardEngineResult {
        val solved = solver.solve(if (source.trim().startsWith("solve", true)) source else "Solve $source")
        return SmartBoardEngineResult(
            title = solved.kind.label,
            exact = solved.answer.takeIf { solved.supported },
            approximate = null,
            steps = solved.steps.map { "${it.title}: ${it.expression} — ${it.explanation}" },
            assumptions = solved.warnings,
            supported = solved.supported,
            verified = solved.supported && solved.verification.isNotBlank(),
        )
    }

    private fun CasRow.toEngineResult() = SmartBoardEngineResult(
        operation.replaceFirstChar(Char::titlecase), exact.takeIf { supported }, decimal,
        steps.map { "${it.title}: ${it.expression} — ${it.explanation}" }, assumptions, supported, supported,
    )
    private fun unsupported(action: SmartBoardMathAction) = SmartBoardEngineResult(
        action.name, null, null, listOf("This action routes to another existing module."), emptyList(), false, false,
    )
    private fun unwrap(source: String, vararg prefixes: String): String {
        val trimmed = source.trim()
        val prefix = prefixes.firstOrNull { trimmed.startsWith(it, true) } ?: return trimmed
        return trimmed.removePrefix(prefix).trim().removeSurrounding("(", ")")
    }
}

data class SmartBoardStatisticsResult(val summary: List<String>, val histogramBinCount: Int)

object SmartBoardStatisticsAdapter {
    fun parseConfirmedData(source: String): List<Double> {
        val values = source.trim().removePrefix("[").removeSuffix("]").removePrefix("{").removeSuffix("}")
            .split(',', ';', '\n').map(String::trim).filter(String::isNotBlank).map(String::toDouble)
        require(values.isNotEmpty() && values.size <= 100_000 && values.all(Double::isFinite)) { "Confirm a finite numeric dataset." }
        return values
    }

    fun summarize(confirmedSource: String): SmartBoardStatisticsResult {
        val values = parseConfirmedData(confirmedSource)
        val result = AdvancedStatisticsEngine.summarize(values)
        return SmartBoardStatisticsResult(
            listOf(
                "Count = ${result.count}", "Mean = ${result.mean}", "Median = ${result.median}",
                "Mode = ${result.modes.ifEmpty { listOf("none") }.joinToString()}",
                "Range = ${result.range}", "Population variance = ${result.populationVariance}",
                "Sample variance = ${result.sampleVariance}", "Population SD = ${result.populationStandardDeviation}",
                "Q1 = ${result.fiveNumber.firstQuartile}", "Q3 = ${result.fiveNumber.thirdQuartile}", "IQR = ${result.interquartileRange}",
            ),
            AdvancedStatisticsEngine.histogram(values).size,
        )
    }
}

data class SmartBoardGraphHandoff(val kind: SmartBoardGraphKind, val expression: String, val route: String)

object SmartBoardGraphAdapter {
    fun prepare(source: String, threeDimensional: Boolean = false): Result<SmartBoardGraphHandoff> = runCatching {
        val preparedSource = SmartBoardLatexAdapter.prepare(source).getOrThrow().engineExpression
        require(preparedSource.length <= 4_000)
        if (threeDimensional) {
            val mesh = Graph3D().mesh(preparedSource, density = 4)
            require(mesh.vertices.isNotEmpty()) { "The expression does not produce a finite 3D surface." }
            SmartBoardGraphHandoff(SmartBoardGraphKind.SURFACE_3D, preparedSource, "graph3d")
        } else {
            val typed = TypedGraphExpressionParser.parse(preparedSource)
            val parameters = typed.parameters.associateWith { 1.0 }
            TypedGraphEngine().sample(typed, parameterValues = parameters, samples = 48)
            val kind = when (typed) {
                is TypedGraphExpression.Explicit, is TypedGraphExpression.Piecewise -> SmartBoardGraphKind.EXPLICIT_2D
                is TypedGraphExpression.Implicit, is TypedGraphExpression.Inequality -> SmartBoardGraphKind.IMPLICIT_2D
                is TypedGraphExpression.Parametric -> SmartBoardGraphKind.PARAMETRIC_2D
                is TypedGraphExpression.Polar -> SmartBoardGraphKind.POLAR_2D
            }
            SmartBoardGraphHandoff(kind, preparedSource, "graph2d")
        }
    }
}

data class SmartBoardStepVerification(
    val steps: List<SolutionStep>,
    val firstInvalidStepIndex: Int?,
    val conclusive: Boolean,
)

class SmartBoardWorkVerificationAdapter(
    private val kernel: TrustedMathKernel = TrustedMathKernel(),
    private val solver: MathProblemSolver = MathProblemSolver(),
) {
    fun verify(expressions: List<Pair<String, Float?>>): SmartBoardStepVerification {
        require(expressions.isNotEmpty())
        val checked = mutableListOf<SolutionStep>()
        var firstInvalid: Int? = null
        expressions.forEachIndexed { index, (expression, confidence) ->
            val status: SolutionStepStatus
            val feedback: String
            if (confidence != null && confidence < .55f) {
                status = SolutionStepStatus.UNCERTAIN
                feedback = "Recognition is uncertain; correct this line before judging the mathematics."
            } else if (index == 0) {
                status = SolutionStepStatus.VALID
                feedback = "Starting expression."
            } else {
                val before = expressions[index - 1].first
                val after = expression
                val solutionEquivalent = if ('=' in before && '=' in after) {
                    val beforeSolved = solver.solve("Solve $before")
                    val afterSolved = solver.solve("Solve $after")
                    beforeSolved.supported && afterSolved.supported && beforeSolved.answer == afterSolved.answer
                } else false
                val evidence = kernel.equivalence(equationResidual(before), equationResidual(after))
                status = when {
                    solutionEquivalent || evidence.equivalent -> SolutionStepStatus.VALID
                    evidence.status.name == "Inconclusive" -> SolutionStepStatus.UNCERTAIN
                    else -> SolutionStepStatus.INVALID
                }
                feedback = evidence.explanation
                if (status == SolutionStepStatus.INVALID && firstInvalid == null) firstInvalid = index
            }
            checked += SolutionStep("step-$index", expression, emptyList(), confidence, status, feedback)
        }
        return SmartBoardStepVerification(checked, firstInvalid, checked.none { it.status == SolutionStepStatus.UNCERTAIN })
    }

    private fun equationResidual(value: String): String {
        val sides = value.split('=', limit = 2)
        return if (sides.size == 2) "(${sides[0]})-(${sides[1]})" else value
    }
}
