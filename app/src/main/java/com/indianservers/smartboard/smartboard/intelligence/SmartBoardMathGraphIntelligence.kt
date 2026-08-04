package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.core.EquivalenceStatus
import com.indianservers.smartboard.core.TrustedMathKernel
import com.indianservers.smartboard.smartboard.integration.SmartBoardWorkVerificationAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class EquivalentExpressionResult(
    val left: String,
    val right: String,
    val equivalent: Boolean,
    val status: EquivalenceStatus,
    val explanation: String,
    val counterexample: String? = null,
)

data class DiscoveredGraphParameter(
    val symbol: String,
    val semanticName: String,
    val minimum: Double,
    val maximum: Double,
    val initial: Double,
    val step: Double,
)

data class InkGraphCandidate(
    val expression: String,
    val confidence: Float,
    val fitError: Double,
    val family: String,
    val explanation: String,
)

data class GraphFromInkSuggestion(
    val sourceStrokeIds: Set<String>,
    val axisElementIds: Set<String>,
    val candidates: List<InkGraphCandidate>,
    val parameters: List<DiscoveredGraphParameter>,
    val bounds: SmartBoardBounds,
    val createdAt: Long,
)

data class LocalizedMathMistake(
    val invalidStepIndex: Int,
    val beforeExpression: String,
    val afterExpression: String,
    val bounds: SmartBoardBounds,
    val message: String,
    val likelyCause: String,
)

data class CanvasSpatialHint(
    val id: String,
    val anchorBounds: SmartBoardBounds,
    val text: String,
    val relatedElementIds: Set<String>,
    val warning: Boolean = false,
)

class SmartBoardMathGraphIntelligenceEngine(
    private val kernel: TrustedMathKernel = TrustedMathKernel(),
    private val verifier: SmartBoardWorkVerificationAdapter = SmartBoardWorkVerificationAdapter(),
) {
    fun equivalent(left: String, right: String): EquivalentExpressionResult {
        val evidence = kernel.equivalence(equationResidual(left), equationResidual(right))
        val counterexample = evidence.samples.firstOrNull { it.residual > evidence.tolerance }?.let { sample ->
            sample.variables.entries.joinToString(prefix = "At ", separator = ", ") { "${it.key}=${format(it.value)}" } +
                ", values differ by ${format(sample.residual)}"
        }
        return EquivalentExpressionResult(
            left,
            right,
            evidence.equivalent,
            evidence.status,
            evidence.explanation,
            counterexample,
        )
    }

    fun discoverParameters(expression: String): List<DiscoveredGraphParameter> {
        val reserved = setOf(
            "x", "y", "z", "e", "pi", "sin", "cos", "tan", "asin", "acos", "atan",
            "sqrt", "abs", "ln", "log", "exp", "min", "max",
        )
        return Regex("""\b[A-Za-z][A-Za-z0-9_]*\b""").findAll(expression)
            .map { it.value }
            .filterNot { it.lowercase() in reserved }
            .distinct()
            .take(16)
            .map { symbol ->
                val semantic = when (symbol.lowercase()) {
                    "a", "amp", "amplitude" -> "amplitude"
                    "b", "w", "omega", "frequency", "f" -> "frequency / horizontal scale"
                    "c", "d", "k" -> "offset"
                    "m" -> "slope"
                    "h" -> "horizontal shift"
                    else -> "parameter $symbol"
                }
                val range = when (semantic) {
                    "amplitude" -> Triple(-10.0, 10.0, 1.0)
                    "frequency / horizontal scale" -> Triple(.1, 10.0, 1.0)
                    "slope" -> Triple(-8.0, 8.0, 1.0)
                    else -> Triple(-10.0, 10.0, 1.0)
                }
                DiscoveredGraphParameter(symbol, semantic, range.first, range.second, range.third, .1)
            }.toList()
    }

    fun analyzeInk(
        strokes: List<StrokeElement>,
        shapes: List<ShapeElement>,
        expressions: List<MathExpressionElement>,
        now: Long,
    ): GraphFromInkSuggestion? {
        val visible = strokes.filterNot(StrokeElement::hidden)
        val explicitAxes = shapes.filter {
            !it.hidden && it.shapeType in setOf(
                SmartBoardShapeType.COORDINATE_AXES,
                SmartBoardShapeType.GRAPH_GRID,
            )
        }
        val lineAxes = visible.filter(::isAxisLine)
        val axisIds = explicitAxes.mapTo(linkedSetOf(), ShapeElement::id) + lineAxes.map(StrokeElement::id)
        val axisBounds = explicitAxes.firstOrNull()?.bounds ?: crossingAxisBounds(lineAxes)
        val curve = visible.filterNot { it.id in axisIds }
            .filter { it.points.size >= 5 && it.bounds.width >= 24f && it.bounds.height >= 10f }
            .maxByOrNull { it.points.size }
        val expressionCandidates = expressions.mapNotNull { expression ->
            val source = expression.normalizedExpression ?: expression.displayLatex
            runCatching {
                InkGraphCandidate(source, expression.recognitionConfidence ?: .8f, 0.0, "recognized equation",
                    "Handwritten equation is graphable and remains editable")
            }.getOrNull()
        }
        val fitted = curve?.let { fitCurve(it, axisBounds) }.orEmpty()
        val candidates = (expressionCandidates + fitted).distinctBy(InkGraphCandidate::expression)
            .sortedByDescending(InkGraphCandidate::confidence).take(5)
        if (candidates.isEmpty()) return null
        val sourceIds = curve?.let { setOf(it.id) }.orEmpty() +
            expressions.flatMap(MathExpressionElement::sourceStrokeIds)
        val bounds = when {
            axisBounds != null && curve != null -> union(axisBounds, curve.bounds)
            curve != null -> curve.bounds
            expressions.isNotEmpty() -> union(expressions.map(MathExpressionElement::bounds))
            else -> SmartBoardBounds.Empty
        }
        return GraphFromInkSuggestion(
            sourceStrokeIds = sourceIds,
            axisElementIds = axisIds,
            candidates = candidates,
            parameters = discoverParameters(candidates.first().expression),
            bounds = bounds,
            createdAt = now,
        )
    }

    fun localizeMistake(lines: List<MathExpressionElement>): LocalizedMathMistake? {
        if (lines.size < 2) return null
        val ordered = lines.sortedWith(compareBy<MathExpressionElement> { it.bounds.top }.thenBy { it.bounds.left })
        val verified = verifier.verify(ordered.map { (it.normalizedExpression ?: it.displayLatex) to it.recognitionConfidence })
        val invalid = verified.firstInvalidStepIndex ?: return null
        val before = verified.steps[invalid - 1].expression
        val after = verified.steps[invalid].expression
        val feedback = verified.steps[invalid].feedback.orEmpty()
        return LocalizedMathMistake(
            invalid,
            before,
            after,
            ordered[invalid].bounds,
            feedback.ifBlank { "This transformation does not preserve equivalence." },
            likelyMistake(before, after),
        )
    }

    fun nextStepHint(expression: MathExpressionElement): CanvasSpatialHint {
        val source = expression.normalizedExpression ?: expression.displayLatex
        val normalized = source.lowercase()
        val hint = when {
            "sin" in normalized || "cos" in normalized || "tan" in normalized ->
                "Try the relevant identity, then simplify only one side."
            "d/d" in normalized || "derivative" in normalized ->
                "Apply the chain or power rule to the highlighted term."
            "integral" in normalized || "\\int" in normalized ->
                "Identify an antiderivative; remember the constant of integration."
            "\\frac" in normalized || "/" in normalized ->
                "Use a common denominator before combining these terms."
            '=' in normalized ->
                "Apply the same inverse operation to both sides."
            else -> "Group like terms, then simplify the selected component."
        }
        return CanvasSpatialHint(
            "hint-${expression.id}",
            expression.bounds,
            hint,
            setOf(expression.id),
        )
    }

    fun mistakeHint(mistake: LocalizedMathMistake, relatedIds: Set<String>): CanvasSpatialHint =
        CanvasSpatialHint(
            id = "mistake-${mistake.invalidStepIndex}",
            anchorBounds = mistake.bounds,
            text = "${mistake.likelyCause}: ${mistake.message}",
            relatedElementIds = relatedIds,
            warning = true,
        )

    private fun fitCurve(stroke: StrokeElement, axes: SmartBoardBounds?): List<InkGraphCandidate> {
        val origin = axes?.center ?: stroke.bounds.center
        val scaleX = max(axes?.width?.div(10f) ?: stroke.bounds.width.div(10f), 1f)
        val scaleY = max(axes?.height?.div(10f) ?: stroke.bounds.height.div(10f), 1f)
        val points = stroke.points.map {
            ((it.x - origin.x) / scaleX).toDouble() to ((origin.y - it.y) / scaleY).toDouble()
        }.filter { it.first.isFinite() && it.second.isFinite() }
        if (points.size < 5) return emptyList()
        val variance = points.map { it.second }.let { ys ->
            val mean = ys.average()
            ys.sumOf { (it - mean) * (it - mean) } / ys.size
        }.coerceAtLeast(1e-6)
        val candidates = mutableListOf<InkGraphCandidate>()

        val linear = linearFit(points) { it.first }
        candidates += candidate(
            "y=${format(linear.first)}*x${signed(linear.second)}",
            normalizedError(points, variance) { linear.first * it + linear.second },
            "line",
        )

        quadraticFit(points)?.let { (a, b, c) ->
            candidates += candidate(
                "y=${format(a)}*x^2${signed(b)}*x${signed(c)}",
                normalizedError(points, variance) { a * it * it + b * it + c },
                "quadratic / parabola",
            )
        }

        sineFit(points)?.let { fit ->
            candidates += candidate(
                "y=${format(fit.amplitude)}*sin(${format(fit.omega)}*x${signed(fit.phase)})${signed(fit.offset)}",
                fit.error / variance,
                "sine wave",
            )
        }

        if (points.all { it.second > 0.0 }) {
            val logFit = linearFit(points.map { it.first to kotlin.math.ln(it.second.coerceAtLeast(1e-6)) }) { it.first }
            val a = exp(logFit.second)
            val b = logFit.first
            candidates += candidate(
                "y=${format(a)}*exp(${format(b)}*x)",
                normalizedError(points, variance) { a * exp(b * it) },
                "exponential",
            )
        }
        return candidates.sortedByDescending(InkGraphCandidate::confidence).take(4)
    }

    private data class SineFit(val amplitude: Double, val omega: Double, val phase: Double, val offset: Double, val error: Double)

    private fun sineFit(points: List<Pair<Double, Double>>): SineFit? {
        var best: SineFit? = null
        for (step in 1..48) {
            val omega = .15 + step * .075
            val matrix = Array(3) { DoubleArray(3) }
            val vector = DoubleArray(3)
            points.forEach { (x, y) ->
                val row = doubleArrayOf(sin(omega * x), cos(omega * x), 1.0)
                for (i in 0..2) {
                    vector[i] += row[i] * y
                    for (j in 0..2) matrix[i][j] += row[i] * row[j]
                }
            }
            val solution = solve(matrix, vector) ?: continue
            val amplitude = sqrt(solution[0] * solution[0] + solution[1] * solution[1])
            val phase = kotlin.math.atan2(solution[1], solution[0])
            val error = points.sumOf { (x, y) ->
                val residual = y - (amplitude * sin(omega * x + phase) + solution[2])
                residual * residual
            } / points.size
            val fit = SineFit(amplitude, omega, phase, solution[2], error)
            if (best?.let { fit.error < it.error } != false) best = fit
        }
        return best
    }

    private fun quadraticFit(points: List<Pair<Double, Double>>): Triple<Double, Double, Double>? {
        val matrix = Array(3) { DoubleArray(3) }
        val vector = DoubleArray(3)
        points.forEach { (x, y) ->
            val row = doubleArrayOf(x * x, x, 1.0)
            for (i in 0..2) {
                vector[i] += row[i] * y
                for (j in 0..2) matrix[i][j] += row[i] * row[j]
            }
        }
        return solve(matrix, vector)?.let { Triple(it[0], it[1], it[2]) }
    }

    private fun solve(source: Array<DoubleArray>, values: DoubleArray): DoubleArray? {
        val n = values.size
        val matrix = Array(n) { row -> DoubleArray(n + 1) { column -> if (column == n) values[row] else source[row][column] } }
        for (column in 0 until n) {
            val pivot = (column until n).maxByOrNull { abs(matrix[it][column]) } ?: return null
            if (abs(matrix[pivot][column]) < 1e-9) return null
            val swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap
            val divisor = matrix[column][column]
            for (j in column..n) matrix[column][j] /= divisor
            for (row in 0 until n) if (row != column) {
                val factor = matrix[row][column]
                for (j in column..n) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        return DoubleArray(n) { matrix[it][n] }
    }

    private fun linearFit(points: List<Pair<Double, Double>>, xValue: (Pair<Double, Double>) -> Double): Pair<Double, Double> {
        val xs = points.map(xValue)
        val ys = points.map { it.second }
        val xMean = xs.average()
        val yMean = ys.average()
        val denominator = xs.sumOf { (it - xMean) * (it - xMean) }
        val slope = if (denominator < 1e-9) 0.0 else xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) } / denominator
        return slope to (yMean - slope * xMean)
    }

    private fun candidate(expression: String, error: Double, family: String): InkGraphCandidate {
        val confidence = (1.0 / (1.0 + error.coerceAtLeast(0.0))).toFloat().coerceIn(.05f, .98f)
        return InkGraphCandidate(expression, confidence, error, family, "Estimated from the drawn curve; choose before converting")
    }

    private fun normalizedError(points: List<Pair<Double, Double>>, variance: Double, predict: (Double) -> Double): Double =
        points.sumOf { (x, y) -> val residual = y - predict(x); residual * residual } / points.size / variance

    private fun isAxisLine(stroke: StrokeElement): Boolean {
        val long = max(stroke.bounds.width, stroke.bounds.height)
        val short = kotlin.math.min(stroke.bounds.width, stroke.bounds.height).coerceAtLeast(1f)
        return stroke.points.size <= 12 && long >= 90f && long / short >= 7f
    }

    private fun crossingAxisBounds(lines: List<StrokeElement>): SmartBoardBounds? {
        val horizontal = lines.maxByOrNull { it.bounds.width }
        val vertical = lines.maxByOrNull { it.bounds.height }
        if (horizontal == null || vertical == null || horizontal.id == vertical.id) return null
        return union(horizontal.bounds, vertical.bounds)
    }

    private fun likelyMistake(before: String, after: String): String = when {
        before.count { it == '-' } != after.count { it == '-' } -> "Possible sign error"
        before.contains('(') && !after.contains('(') -> "Check distribution across parentheses"
        Regex("""\^\s*\d""").containsMatchIn(before) || Regex("""\^\s*\d""").containsMatchIn(after) -> "Check the exponent rule"
        listOf("sin", "cos", "tan").any { it in before.lowercase() || it in after.lowercase() } -> "Check the trigonometric identity"
        listOf("d/d", "integral", "\\int").any { it in before.lowercase() || it in after.lowercase() } -> "Check the calculus rule"
        else -> "Unsupported algebraic transformation"
    }

    private fun equationResidual(value: String): String {
        val sides = value.split('=', limit = 2)
        return if (sides.size == 2) "(${sides[0]})-(${sides[1]})" else value
    }

    private fun union(a: SmartBoardBounds, b: SmartBoardBounds) = SmartBoardBounds(
        minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom),
    )

    private fun union(bounds: List<SmartBoardBounds>) = SmartBoardBounds(
        bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom },
    )

    private fun format(value: Double): String = when {
        abs(value) < 1e-9 -> "0"
        abs(value - value.toLong()) < 1e-7 -> value.toLong().toString()
        else -> String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }

    private fun signed(value: Double): String = if (value >= 0) "+${format(value)}" else format(value)
}
