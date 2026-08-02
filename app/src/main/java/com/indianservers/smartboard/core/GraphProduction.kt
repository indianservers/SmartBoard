package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

sealed interface TypedGraphExpression {
    val source: String
    val parameters: Set<String>
    data class Explicit(override val source: String, val expression: String, override val parameters: Set<String>) : TypedGraphExpression
    data class Implicit(override val source: String, val residual: String, override val parameters: Set<String>) : TypedGraphExpression
    data class Polar(override val source: String, val radius: String, override val parameters: Set<String>) : TypedGraphExpression
    data class Parametric(override val source: String, val x: String, val y: String, override val parameters: Set<String>) : TypedGraphExpression
    data class Piecewise(override val source: String, val branches: List<PiecewiseBranch>, override val parameters: Set<String>) : TypedGraphExpression
    data class Inequality(override val source: String, val predicate: String, override val parameters: Set<String>) : TypedGraphExpression
}

data class PiecewiseBranch(val condition: String, val expression: String)
data class TypedGraphSample(
    val expression: TypedGraphExpression,
    val curves: List<GraphSegment> = emptyList(),
    val implicitSegments: List<ImplicitSegment> = emptyList(),
    val inequalityCells: List<InequalityCell> = emptyList(),
)

object TypedGraphExpressionParser {
    private val reserved = setOf(
        "x", "y", "t", "theta", "pi", "e", "sin", "cos", "tan", "asin", "acos", "atan",
        "sqrt", "abs", "ln", "log", "exp", "min", "max", "floor", "ceil", "piecewise",
    )

    fun parse(source: String): TypedGraphExpression {
        val clean = source.trim()
        require(clean.isNotBlank()) { "Graph expression cannot be blank." }
        val compact = clean.lowercase().replace(" ", "")
        return when {
            compact.startsWith("piecewise{") -> {
                val body = clean.substringAfter('{').substringBeforeLast('}')
                val branches = body.split(';').filter(String::isNotBlank).map { part ->
                    val pieces = part.split(':', limit = 2)
                    require(pieces.size == 2) { "Piecewise branches use condition:expression." }
                    PiecewiseBranch(pieces[0].trim(), pieces[1].trim())
                }
                require(branches.isNotEmpty()) { "At least one piecewise branch is required." }
                TypedGraphExpression.Piecewise(clean, branches, parameters(branches.flatMap { listOf(it.condition, it.expression) }))
            }
            compact.startsWith("r=") -> stripEquation(clean).let { TypedGraphExpression.Polar(clean, it, parameters(listOf(it))) }
            compact.contains("x(t)=") && compact.contains("y(t)=") -> {
                val parts = clean.split(';').map(String::trim)
                val x = parts.firstOrNull { it.lowercase().replace(" ", "").startsWith("x(t)=") }?.let(::stripEquation) ?: error("Missing x(t).")
                val y = parts.firstOrNull { it.lowercase().replace(" ", "").startsWith("y(t)=") }?.let(::stripEquation) ?: error("Missing y(t).")
                TypedGraphExpression.Parametric(clean, x, y, parameters(listOf(x, y)))
            }
            Regex("<=|>=|<|>").containsMatchIn(clean) -> TypedGraphExpression.Inequality(clean, clean, parameters(listOf(clean)))
            '=' in clean && !compact.startsWith("y=") && !Regex("^[a-z][a-z0-9_]*\\(x\\)=", RegexOption.IGNORE_CASE).containsMatchIn(compact) -> {
                val sides = clean.split('=', limit = 2)
                val residual = "(${sides[0]})-(${sides[1]})"
                TypedGraphExpression.Implicit(clean, residual, parameters(listOf(residual)))
            }
            else -> stripEquation(clean).let { TypedGraphExpression.Explicit(clean, it, parameters(listOf(it))) }
        }
    }

    private fun parameters(expressions: List<String>): Set<String> = expressions.flatMap { source ->
        Regex("[A-Za-z][A-Za-z0-9_]*").findAll(source).map { it.value.lowercase() }.toList()
    }.filterNot { it in reserved }.toSortedSet()
}

class TypedGraphEngine(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun sample(
        definition: TypedGraphExpression,
        xDomain: GraphDomain = GraphDomain(-10.0, 10.0),
        yDomain: GraphDomain = GraphDomain(-10.0, 10.0, "y"),
        parameterValues: Map<String, Double> = emptyMap(),
        samples: Int = 720,
    ): TypedGraphSample = when (definition) {
        is TypedGraphExpression.Explicit -> {
            val curve = AdvancedGraphEngine(expressions).adaptiveExplicit(
                AdvancedGraphDefinition(definition.expression, AdvancedGraphKind.Explicit, xDomain, parameterValues), seedIntervals = samples.coerceIn(24, 256) / 4,
            )
            TypedGraphSample(definition, curves = curve.segments)
        }
        is TypedGraphExpression.Polar -> TypedGraphSample(definition, curves = listOf(GraphSegment(samplePolar(definition, parameterValues, samples))))
        is TypedGraphExpression.Parametric -> TypedGraphSample(definition, curves = listOf(GraphSegment(sampleParametric(definition, parameterValues, samples))))
        is TypedGraphExpression.Piecewise -> TypedGraphSample(definition, curves = samplePiecewise(definition, xDomain, parameterValues, samples))
        is TypedGraphExpression.Implicit -> TypedGraphSample(definition, implicitSegments = marchingSquares(definition, xDomain, yDomain, parameterValues, min(240, max(24, samples / 4))))
        is TypedGraphExpression.Inequality -> TypedGraphSample(definition, inequalityCells = AdvancedGraphEngine(expressions).inequality(definition.predicate, xDomain, yDomain, min(200, max(20, samples / 4)), min(200, max(20, samples / 4))))
    }

    fun evaluate(definition: TypedGraphExpression, coordinate: Double, parameters: Map<String, Double> = emptyMap()): Vec2? = when (definition) {
        is TypedGraphExpression.Explicit -> value(definition.expression, parameters + ("x" to coordinate))?.let { Vec2(coordinate, it) }
        is TypedGraphExpression.Polar -> value(definition.radius, parameters + mapOf("t" to coordinate, "theta" to coordinate))?.let { Vec2(it * kotlin.math.cos(coordinate), it * kotlin.math.sin(coordinate)) }
        is TypedGraphExpression.Parametric -> {
            val variables = parameters + ("t" to coordinate)
            val x = value(definition.x, variables); val y = value(definition.y, variables)
            if (x == null || y == null) null else Vec2(x, y)
        }
        is TypedGraphExpression.Piecewise -> definition.branches.firstOrNull { value(it.condition, parameters + ("x" to coordinate))?.let { result -> result != 0.0 } == true }
            ?.let { value(it.expression, parameters + ("x" to coordinate)) }?.let { Vec2(coordinate, it) }
        is TypedGraphExpression.Implicit, is TypedGraphExpression.Inequality -> null
    }

    private fun samplePolar(definition: TypedGraphExpression.Polar, parameters: Map<String, Double>, count: Int): List<Vec2> = (0..count.coerceIn(32, 5000)).mapNotNull { index ->
        evaluate(definition, 2 * PI * index / count.coerceIn(32, 5000), parameters)
    }

    private fun sampleParametric(definition: TypedGraphExpression.Parametric, parameters: Map<String, Double>, count: Int): List<Vec2> = (0..count.coerceIn(32, 5000)).mapNotNull { index ->
        val t = -2 * PI + 4 * PI * index / count.coerceIn(32, 5000)
        evaluate(definition, t, parameters)
    }

    private fun samplePiecewise(definition: TypedGraphExpression.Piecewise, domain: GraphDomain, parameters: Map<String, Double>, count: Int): List<GraphSegment> {
        val segments = mutableListOf<GraphSegment>(); var current = mutableListOf<Vec2>()
        (0..count.coerceIn(32, 5000)).forEach { index ->
            val x = domain.minimum + (domain.maximum - domain.minimum) * index / count.coerceIn(32, 5000)
            val point = evaluate(definition, x, parameters)
            if (point == null) { if (current.size > 1) segments += GraphSegment(current); current = mutableListOf() } else current += point
        }
        if (current.size > 1) segments += GraphSegment(current)
        return segments
    }

    private fun marchingSquares(definition: TypedGraphExpression.Implicit, xDomain: GraphDomain, yDomain: GraphDomain, parameters: Map<String, Double>, cells: Int): List<ImplicitSegment> {
        val compiled = expressions.compile(definition.residual)
        fun point(column: Int, row: Int) = Vec2(
            xDomain.minimum + (xDomain.maximum - xDomain.minimum) * column / cells,
            yDomain.minimum + (yDomain.maximum - yDomain.minimum) * row / cells,
        )
        fun residual(point: Vec2) = runCatching { compiled.eval(parameters + mapOf("x" to point.x, "y" to point.y)) }.getOrDefault(Double.NaN)
        fun crossing(a: Vec2, b: Vec2, fa: Double, fb: Double): Vec2 {
            val ratio = if (abs(fa - fb) < 1e-15) .5 else fa / (fa - fb)
            return a + (b - a) * ratio.coerceIn(0.0, 1.0)
        }
        return buildList {
            for (row in 0 until cells) for (column in 0 until cells) {
                val corners = listOf(point(column, row), point(column + 1, row), point(column + 1, row + 1), point(column, row + 1))
                val values = corners.map(::residual)
                if (values.any { !it.isFinite() }) continue
                val intersections = (0..3).mapNotNull { edge ->
                    val next = (edge + 1) % 4; val a = values[edge]; val b = values[next]
                    if (a == 0.0 || b == 0.0 || a.sign() != b.sign()) crossing(corners[edge], corners[next], a, b) else null
                }
                if (intersections.size == 2) add(ImplicitSegment(intersections[0], intersections[1]))
                else if (intersections.size == 4) { add(ImplicitSegment(intersections[0], intersections[1])); add(ImplicitSegment(intersections[2], intersections[3])) }
            }
        }
    }

    private fun value(source: String, variables: Map<String, Double>): Double? = runCatching { expressions.compile(source).eval(variables) }.getOrNull()?.takeIf(Double::isFinite)
    private fun Double.sign() = when { this < 0 -> -1; this > 0 -> 1; else -> 0 }
}

data class GraphParameterHandle(
    val parameter: GraphParameter,
    val normalizedPosition: Double,
    val affectedRows: Set<String>,
    val accessibleLabel: String,
)

object GraphParameterHandleEngine {
    fun handles(state: GraphWorkspaceState): List<GraphParameterHandle> = state.parameters.values.sortedBy { it.name }.map { parameter ->
        val affected = state.rows.filter { row -> row.typed?.parameters?.contains(parameter.name.lowercase()) == true }.map { it.id }.toSet()
        GraphParameterHandle(parameter, (parameter.value - parameter.minimum) / (parameter.maximum - parameter.minimum), affected,
            "Parameter ${parameter.name}, value ${format(parameter.value)}, range ${format(parameter.minimum)} to ${format(parameter.maximum)}")
    }

    fun drag(state: GraphWorkspaceState, parameter: String, normalizedPosition: Double): GraphWorkspaceState {
        val item = state.parameters[parameter] ?: error("Unknown graph parameter '$parameter'.")
        val value = item.minimum + normalizedPosition.coerceIn(0.0, 1.0) * (item.maximum - item.minimum)
        val snapped = kotlin.math.round(value / item.step) * item.step
        return GraphWorkspaceReducer().reduce(state, GraphWorkspaceAction.SetParameter(parameter, snapped))
    }

    private fun format(value: Double) = if (abs(value - value.toLong()) < 1e-9) value.toLong().toString() else "%.4f".format(value).trimEnd('0').trimEnd('.')
}

data class GraphDataColumn(val name: String, val values: List<Double?>, val formula: String? = null)
data class ProfessionalGraphTable(val columns: List<GraphDataColumn>, val rowCount: Int, val sourceDelimiter: Char? = null) {
    init { require(rowCount in 0..100_000 && columns.all { it.values.size == rowCount }) }
    fun row(index: Int): Map<String, Double?> { require(index in 0 until rowCount); return columns.associate { it.name to it.values[index] } }
}

class ProfessionalGraphTableEngine(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun paste(source: String, hasHeader: Boolean = true): ProfessionalGraphTable {
        val lines = source.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return ProfessionalGraphTable(emptyList(), 0)
        require(lines.size <= 100_001) { "Tables support at most 100,000 data rows." }
        val delimiter = if (lines.first().count { it == '\t' } > lines.first().count { it == ',' }) '\t' else ','
        val rows = lines.map { parseDelimited(it, delimiter) }
        val width = rows.maxOf { it.size }
        val headers = if (hasHeader) rows.first().mapIndexed { index, value -> safeName(value, index) } else (0 until width).map { "c${it + 1}" }
        val data = if (hasHeader) rows.drop(1) else rows
        val names = (0 until width).map { headers.getOrElse(it) { "c${it + 1}" } }.toMutableList()
        names.indices.forEach { index -> if (names.take(index).contains(names[index])) names[index] = "${names[index]}_${index + 1}" }
        return ProfessionalGraphTable((0 until width).map { column -> GraphDataColumn(names[column], data.map { it.getOrNull(column)?.trim()?.takeIf(String::isNotBlank)?.toDoubleOrNull() }) }, data.size, delimiter)
    }

    fun calculatedColumn(table: ProfessionalGraphTable, name: String, formula: String): ProfessionalGraphTable {
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,31}")) && table.columns.none { it.name == name })
        val compiled = expressions.compile(formula)
        val values = (0 until table.rowCount).map { row ->
            val variables = table.columns.mapNotNull { column -> column.values[row]?.let { column.name to it } }.toMap() + ("row" to (row + 1).toDouble())
            runCatching { compiled.eval(variables) }.getOrNull()?.takeIf(Double::isFinite)
        }
        return table.copy(columns = table.columns + GraphDataColumn(name, values, formula))
    }

    fun series(table: ProfessionalGraphTable, x: String, y: String): List<Vec2> {
        val first = table.columns.firstOrNull { it.name == x } ?: error("Unknown column '$x'.")
        val second = table.columns.firstOrNull { it.name == y } ?: error("Unknown column '$y'.")
        return (0 until table.rowCount).mapNotNull { row -> first.values[row]?.let { a -> second.values[row]?.let { b -> Vec2(a, b) } } }
    }

    private fun parseDelimited(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>(); val token = StringBuilder(); var quoted = false; var index = 0
        while (index < line.length) { val char = line[index]; when { char == '"' && quoted && line.getOrNull(index + 1) == '"' -> { token.append('"'); index++ }; char == '"' -> quoted = !quoted; char == delimiter && !quoted -> { result += token.toString(); token.clear() }; else -> token.append(char) }; index++ }
        result += token.toString(); return result
    }
    private fun safeName(value: String, index: Int) = value.trim().replace(Regex("[^A-Za-z0-9_]"), "_").trim('_').let { if (it.firstOrNull()?.isLetter() == true) it else "c${index + 1}" }.ifBlank { "c${index + 1}" }
}

enum class GraphRegressionKind { Linear, Polynomial, Exponential, Logarithmic, Logistic, Custom }
data class RegressionBand(val x: Double, val estimate: Double, val lower: Double, val upper: Double)
data class ProfessionalRegressionResult(
    val kind: GraphRegressionKind,
    val expression: String,
    val coefficients: Map<String, Double>,
    val fitted: List<Vec2>,
    val residuals: List<Vec2>,
    val confidenceBand: List<RegressionBand>,
    val rSquared: Double,
    val rmse: Double,
    val aic: Double,
    val bic: Double,
    val warnings: List<String> = emptyList(),
)
data class RegressionComparison(val ranked: List<ProfessionalRegressionResult>, val guidance: List<String>)

class ProfessionalRegressionEngine(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun fit(points: List<Vec2>, kind: GraphRegressionKind, degree: Int = 2, customExpression: String? = null, customParameters: List<String> = emptyList()): ProfessionalRegressionResult {
        val data = points.filter { it.x.isFinite() && it.y.isFinite() }
        require(data.size >= 3) { "At least three finite points are required." }
        return when (kind) {
            GraphRegressionKind.Linear -> polynomial(data, 1, kind)
            GraphRegressionKind.Polynomial -> polynomial(data, degree.coerceIn(2, 8), kind)
            GraphRegressionKind.Exponential -> transformed(data.filter { it.y > 0 }, kind, { it.x }, { ln(it.y) }) { coefficients, x -> exp(coefficients[0] + coefficients[1] * x) }
            GraphRegressionKind.Logarithmic -> transformed(data.filter { it.x > 0 }, kind, { ln(it.x) }, { it.y }) { coefficients, x -> coefficients[0] + coefficients[1] * ln(x) }
            GraphRegressionKind.Logistic -> nonlinear(data, kind, "L/(1+exp(k*(center-x)))", listOf("L", "k", "center"), listOf(data.maxOf { it.y }, 1.0, data.map { it.x }.average()))
            GraphRegressionKind.Custom -> nonlinear(data, kind, customExpression ?: error("Custom expression is required."), customParameters, List(customParameters.size) { 1.0 })
        }
    }

    fun compare(results: List<ProfessionalRegressionResult>): RegressionComparison {
        require(results.isNotEmpty())
        val ranked = results.sortedWith(compareBy<ProfessionalRegressionResult> { it.bic }.thenBy { it.rmse })
        val best = ranked.first()
        return RegressionComparison(ranked, buildList {
            add("${best.kind.name} has the lowest BIC and is the preferred balance of fit and complexity.")
            if (ranked.size > 1 && ranked[1].bic - best.bic < 2) add("The two leading models have similar support; prefer the simpler interpretable model.")
            if (best.rSquared < .5) add("All candidate models explain limited variation; inspect residual structure and omitted variables.")
            if (best.warnings.isNotEmpty()) addAll(best.warnings)
            add("Association and predictive fit do not establish causation.")
        })
    }

    private fun polynomial(data: List<Vec2>, degree: Int, kind: GraphRegressionKind): ProfessionalRegressionResult {
        require(data.size > degree)
        val matrix = Array(degree + 1) { row -> DoubleArray(degree + 1) { column -> data.sumOf { it.x.pow(row + column) } } }
        val target = DoubleArray(degree + 1) { power -> data.sumOf { it.y * it.x.pow(power) } }
        val coefficients = solve(matrix, target) ?: error("Regression design is singular.")
        val expression = coefficients.mapIndexed { power, value -> when (power) { 0 -> fmt(value); 1 -> "${signed(value)}*x"; else -> "${signed(value)}*x^$power" } }.joinToString("").removePrefix("+")
        return result(data, kind, expression, coefficients.indices.associate { "a$it" to coefficients[it] }) { x -> coefficients.indices.sumOf { coefficients[it] * x.pow(it) } }
    }

    private fun transformed(data: List<Vec2>, kind: GraphRegressionKind, tx: (Vec2) -> Double, ty: (Vec2) -> Double, predictor: (DoubleArray, Double) -> Double): ProfessionalRegressionResult {
        require(data.size >= 3) { "The model domain leaves too few points." }
        val x = data.map(tx); val y = data.map(ty); val mx = x.average(); val my = y.average(); val denominator = x.sumOf { (it - mx).pow(2) }
        require(denominator > 1e-15); val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / denominator; val coefficients = doubleArrayOf(my - slope * mx, slope)
        val expression = if (kind == GraphRegressionKind.Exponential) "${fmt(exp(coefficients[0]))}*exp(${fmt(slope)}*x)" else "${fmt(coefficients[0])}+${fmt(slope)}*ln(x)"
        return result(data, kind, expression, mapOf("a" to coefficients[0], "b" to coefficients[1])) { value -> predictor(coefficients, value) }
    }

    private fun nonlinear(data: List<Vec2>, kind: GraphRegressionKind, expression: String, parameters: List<String>, initial: List<Double>): ProfessionalRegressionResult {
        require(parameters.isNotEmpty() && parameters.distinct().size == parameters.size)
        val compiled = expressions.compile(expression); val values = initial.toMutableList()
        fun predict(x: Double, coefficients: List<Double>) = compiled.eval(parameters.indices.associate { parameters[it] to coefficients[it] } + ("x" to x))
        fun loss(coefficients: List<Double>) = data.sumOf { point -> (point.y - predict(point.x, coefficients)).pow(2) } / data.size
        var learningRate = .02
        repeat(3000) {
            val base = loss(values); val gradient = values.indices.map { index -> val h = 1e-5 * max(1.0, abs(values[index])); val shifted = values.toMutableList(); shifted[index] += h; (loss(shifted) - base) / h }
            val candidate = values.indices.map { values[it] - learningRate * gradient[it].coerceIn(-1e4, 1e4) }
            if (candidate.all(Double::isFinite) && loss(candidate) <= base) values.indices.forEach { values[it] = candidate[it] } else learningRate *= .5
        }
        return result(data, kind, expression, parameters.indices.associate { parameters[it] to values[it] }) { x -> predict(x, values) }
    }

    private fun result(data: List<Vec2>, kind: GraphRegressionKind, expression: String, coefficients: Map<String, Double>, predict: (Double) -> Double): ProfessionalRegressionResult {
        val fitted = data.map { Vec2(it.x, predict(it.x)) }; require(fitted.all { it.y.isFinite() })
        val residuals = data.indices.map { Vec2(data[it].x, data[it].y - fitted[it].y) }; val sse = residuals.sumOf { it.y * it.y }; val mean = data.map { it.y }.average(); val sst = data.sumOf { (it.y - mean).pow(2) }
        val rmse = sqrt(sse / data.size); val k = coefficients.size; val safeSse = max(sse, 1e-300); val aic = data.size * ln(safeSse / data.size) + 2 * k; val bic = data.size * ln(safeSse / data.size) + k * ln(data.size.toDouble())
        val critical = 1.95996398454; val band = fitted.sortedBy { it.x }.map { RegressionBand(it.x, it.y, it.y - critical * rmse, it.y + critical * rmse) }
        return ProfessionalRegressionResult(kind, expression, coefficients, fitted, residuals, band, if (sst < 1e-15) 1.0 else 1 - sse / sst, rmse, aic, bic,
            if (residuals.zipWithNext().count { it.first.y * it.second.y < 0 } < residuals.size / 5) listOf("Residuals show long same-sign runs; the model form may be inadequate.") else emptyList())
    }

    private fun solve(matrix: Array<DoubleArray>, target: DoubleArray): DoubleArray? {
        val n = target.size; val augmented = Array(n) { row -> DoubleArray(n + 1) { column -> if (column < n) matrix[row][column] else target[row] } }
        for (column in 0 until n) { val pivot = (column until n).maxBy { abs(augmented[it][column]) }; if (abs(augmented[pivot][column]) < 1e-12) return null; val swap = augmented[pivot]; augmented[pivot] = augmented[column]; augmented[column] = swap; val divisor = augmented[column][column]; for (c in column..n) augmented[column][c] /= divisor; for (row in 0 until n) if (row != column) { val scale = augmented[row][column]; for (c in column..n) augmented[row][c] -= scale * augmented[column][c] } }
        return DoubleArray(n) { augmented[it][n] }
    }
    private fun fmt(value: Double) = "%.8g".format(value)
    private fun signed(value: Double) = if (value < 0) fmt(value) else "+${fmt(value)}"
}

enum class GraphPointKind { Root, YIntercept, Extremum, Intersection }
data class DeterministicGraphPoint(val id: String, val kind: GraphPointKind, val point: Vec2, val rowIds: List<String>, val accessibleLabel: String)

class DeterministicGraphInterestEngine(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun points(rows: List<InteractiveGraphRow>, domain: GraphDomain, parameters: Map<String, Double>): List<DeterministicGraphPoint> {
        val explicit = rows.filter { it.visible && it.typed is TypedGraphExpression.Explicit }.map { it to expressions.compile((it.typed as TypedGraphExpression.Explicit).expression) }
        val output = mutableListOf<DeterministicGraphPoint>()
        explicit.forEach { (row, expression) ->
            roots(domain) { x -> eval(expression, x, parameters) }.forEach { x -> output += point(GraphPointKind.Root, Vec2(x, 0.0), listOf(row.id)) }
            runCatching { expression.eval(parameters + ("x" to 0.0)) }.getOrNull()?.takeIf(Double::isFinite)?.let { output += point(GraphPointKind.YIntercept, Vec2(0.0, it), listOf(row.id)) }
            extrema(expression, domain, parameters).forEach { output += point(GraphPointKind.Extremum, it, listOf(row.id)) }
        }
        for (first in explicit.indices) for (second in first + 1 until explicit.size) {
            val a = explicit[first]; val b = explicit[second]
            roots(domain) { x ->
                val firstValue = eval(a.second, x, parameters); val secondValue = eval(b.second, x, parameters)
                if (firstValue == null || secondValue == null) null else firstValue - secondValue
            }.forEach { x ->
                val y = a.second.eval(parameters + ("x" to x)); if (y.isFinite()) output += point(GraphPointKind.Intersection, Vec2(x, y), listOf(a.first.id, b.first.id))
            }
        }
        return output.sortedWith(compareBy<DeterministicGraphPoint> { it.point.x }.thenBy { it.kind.ordinal }.thenBy { it.rowIds.joinToString() })
            .fold(mutableListOf<DeterministicGraphPoint>()) { acc, item -> if (acc.none { it.kind == item.kind && it.rowIds == item.rowIds && it.point.distanceTo(item.point) < 1e-7 }) acc += item; acc }
            .mapIndexed { index, item -> item.copy(id = "poi-${index + 1}") }
    }

    private fun roots(domain: GraphDomain, evaluate: (Double) -> Double?): List<Double> {
        val count = 2048; val candidates = mutableListOf<Double>(); var x0 = domain.minimum; var y0 = evaluate(x0)
        for (index in 1..count) { val x1 = domain.minimum + (domain.maximum - domain.minimum) * index / count; val y1 = evaluate(x1); if (y0 != null && y1 != null) { if (abs(y0) < 1e-10) candidates += x0; if (y0 * y1 < 0) candidates += bisect(x0, x1, evaluate) }; x0 = x1; y0 = y1 }
        return candidates.sorted().fold(mutableListOf()) { acc, value -> if (acc.lastOrNull()?.let { abs(it - value) < 1e-7 } != true) acc += value; acc }
    }
    private fun extrema(expression: Expression, domain: GraphDomain, parameters: Map<String, Double>): List<Vec2> {
        val count = 1024; val step = (domain.maximum - domain.minimum) / count
        return (1 until count).mapNotNull { index -> val x = domain.minimum + index * step; val left = eval(expression, x - step, parameters); val center = eval(expression, x, parameters); val right = eval(expression, x + step, parameters); if (left != null && center != null && right != null && (center > left && center > right || center < left && center < right)) Vec2(x, center) else null }
    }
    private fun bisect(from: Double, to: Double, evaluate: (Double) -> Double?): Double { var a = from; var b = to; var fa = evaluate(a) ?: return a; repeat(70) { val middle = (a + b) / 2; val fm = evaluate(middle) ?: return@repeat; if (fa * fm <= 0) b = middle else { a = middle; fa = fm } }; return (a + b) / 2 }
    private fun eval(expression: Expression, x: Double, parameters: Map<String, Double>) = runCatching { expression.eval(parameters + ("x" to x)) }.getOrNull()?.takeIf(Double::isFinite)
    private fun point(kind: GraphPointKind, point: Vec2, rows: List<String>) = DeterministicGraphPoint("", kind, point, rows, "${kind.name.lowercase()} at x ${fmt(point.x)}, y ${fmt(point.y)}")
    private fun fmt(value: Double) = "%.6g".format(value)
}

class GraphKeyboardNavigator(private val points: List<DeterministicGraphPoint>) {
    private var index = if (points.isEmpty()) -1 else 0
    fun current(): DeterministicGraphPoint? = points.getOrNull(index)
    fun next(): DeterministicGraphPoint? { if (points.isNotEmpty()) index = (index + 1) % points.size; return current() }
    fun previous(): DeterministicGraphPoint? { if (points.isNotEmpty()) index = (index - 1 + points.size) % points.size; return current() }
    fun next(kind: GraphPointKind): DeterministicGraphPoint? { if (points.isEmpty()) return null; repeat(points.size) { next(); if (current()?.kind == kind) return current() }; return null }
    fun shortcuts() = mapOf("ArrowRight" to "next point of interest", "ArrowLeft" to "previous point of interest", "R" to "next root", "E" to "next extremum", "I" to "next intersection")
}

data class GraphTransformation(val kind: GraphTransformKind, val amount: Double)
data class CompositeGraphTransformation(val name: String, val steps: List<GraphTransformation>)
object ReusableGraphTransformationEngine {
    fun apply(source: String, transformation: CompositeGraphTransformation): String = transformation.steps.fold(source) { result, step -> GraphUxEngine.transform(result, step.kind, step.amount) }
    fun apply(rows: List<InteractiveGraphRow>, ids: Set<String>, transformation: CompositeGraphTransformation): List<InteractiveGraphRow> = rows.map { if (it.id in ids) it.copy(source = apply(it.source, transformation), typed = TypedGraphExpressionParser.parse(apply(it.source, transformation))) else it }
}

data class GraphAudioNote(val time: Double, val pitch: Double, val pan: Double, val emphasis: Boolean, val description: String)
data class StructuredGraphSummary(val title: String, val overview: String, val domain: String, val range: String, val features: List<String>, val navigationHints: List<String>)
data class AccessibleGraphTrace(val notes: List<GraphAudioNote>, val summary: StructuredGraphSummary)

object AccessibleGraphDescriptionEngine {
    fun build(row: InteractiveGraphRow, sample: TypedGraphSample, points: List<DeterministicGraphPoint>): AccessibleGraphTrace {
        val all = sample.curves.flatMap { it.points }
        val minX = all.minOfOrNull { it.x } ?: -1.0; val maxX = all.maxOfOrNull { it.x } ?: 1.0; val minY = all.minOfOrNull { it.y } ?: -1.0; val maxY = all.maxOfOrNull { it.y } ?: 1.0
        val selected = if (all.size <= 500) all else all.filterIndexed { index, _ -> index % max(1, all.size / 500) == 0 }
        val notes = selected.mapIndexed { index, point ->
            val near = points.any { it.rowIds.contains(row.id) && it.point.distanceTo(point) < max(1e-5, (maxX - minX) / 300) }
            GraphAudioNote(index / max(1.0, (selected.size - 1).toDouble()), 48 + 36 * ((point.y - minY) / max(1e-12, maxY - minY)), -1 + 2 * ((point.x - minX) / max(1e-12, maxX - minX)), near, "x ${fmt(point.x)}, y ${fmt(point.y)}")
        }
        val rowPoints = points.filter { row.id in it.rowIds }
        val summary = StructuredGraphSummary(
            "Graph of ${row.source}",
            when (val typed = row.typed) { is TypedGraphExpression.Explicit -> "Explicit function of x."; is TypedGraphExpression.Implicit -> "Implicit relation in x and y."; is TypedGraphExpression.Polar -> "Polar curve traced by angle."; is TypedGraphExpression.Parametric -> "Parametric curve traced by t."; is TypedGraphExpression.Piecewise -> "Piecewise function with ${typed.branches.size} branches."; is TypedGraphExpression.Inequality -> "Shaded inequality region."; null -> "Graph expression." },
            if (all.isEmpty()) "not sampled as a one-dimensional curve" else "x from ${fmt(minX)} to ${fmt(maxX)}",
            if (all.isEmpty()) "not applicable" else "sampled y from ${fmt(minY)} to ${fmt(maxY)}",
            rowPoints.groupBy { it.kind }.map { (kind, values) -> "${values.size} ${kind.name.lowercase()} point${if (values.size == 1) "" else "s"}: ${values.take(4).joinToString { "(${fmt(it.point.x)}, ${fmt(it.point.y)})" }}" },
            listOf("Use left and right arrows to move between deterministic points of interest.", "Use R for roots, E for extrema and I for intersections.", "Audio pitch follows y and stereo position follows x."),
        )
        return AccessibleGraphTrace(notes, summary)
    }
    private fun fmt(value: Double) = "%.5g".format(value)
}
