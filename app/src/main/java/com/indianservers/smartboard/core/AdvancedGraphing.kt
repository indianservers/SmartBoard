package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class AdvancedGraphKind { Explicit, Polar, Parametric, Implicit, Inequality, Sequence, VectorField }

data class GraphDomain(val minimum: Double, val maximum: Double, val variable: String = "x") {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum < maximum) { "A finite increasing graph domain is required" }
    }
}

data class AdvancedGraphDefinition(
    val expression: String,
    val kind: AdvancedGraphKind,
    val domain: GraphDomain = GraphDomain(-10.0, 10.0),
    val parameters: Map<String, Double> = emptyMap(),
)

data class GraphSegment(val points: List<Vec2>)
data class AdvancedCurveSample(
    val segments: List<GraphSegment>,
    val evaluations: Int,
    val discontinuities: Int,
    val domain: GraphDomain,
) {
    val points: List<Vec2> get() = segments.flatMap { it.points }
}

data class SequenceTerm(val index: Int, val value: Double)
data class VectorFieldSample(val position: Vec2, val vector: Vec2, val magnitude: Double)
data class InequalityCell(val center: Vec2, val satisfied: Boolean)

/** Renderer-independent advanced graph services shared by 2D, lessons, and future AR views. */
class AdvancedGraphEngine(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun classify(source: String): AdvancedGraphKind {
        val value = source.lowercase().replace(" ", "")
        return when {
            value.startsWith("a(n)=") || value.startsWith("u(n)=") -> AdvancedGraphKind.Sequence
            value.startsWith("vector:") || (value.contains(';') && value.contains("p(x,y)") && value.contains("q(x,y)")) -> AdvancedGraphKind.VectorField
            listOf("<=", ">=", "<", ">").any(value::contains) && value.contains('y') -> AdvancedGraphKind.Inequality
            value.startsWith("r=") -> AdvancedGraphKind.Polar
            value.contains("x(t)") && value.contains("y(t)") -> AdvancedGraphKind.Parametric
            value.contains('=') && !value.startsWith("y=") && !value.substringBefore('=').contains("(x)") -> AdvancedGraphKind.Implicit
            else -> AdvancedGraphKind.Explicit
        }
    }

    fun adaptiveExplicit(
        definition: AdvancedGraphDefinition,
        tolerance: Double = 0.015,
        maximumDepth: Int = 10,
        seedIntervals: Int = 24,
    ): AdvancedCurveSample {
        require(definition.kind == AdvancedGraphKind.Explicit)
        require(tolerance > 0.0 && maximumDepth in 1..18 && seedIntervals in 2..512)
        val compiled = expressions.compile(stripEquation(definition.expression))
        var evaluations = 0
        var discontinuities = 0
        fun evaluate(x: Double): Vec2? {
            evaluations++
            val y = runCatching { compiled.eval(definition.parameters + (definition.domain.variable to x)) }.getOrDefault(Double.NaN)
            return y.takeIf { it.isFinite() && abs(it) < 1e12 }?.let { Vec2(x, it) }
        }

        val segments = mutableListOf<MutableList<Vec2>>()
        var current = mutableListOf<Vec2>()
        fun breakSegment() {
            if (current.size > 1) segments += current
            current = mutableListOf()
            discontinuities++
        }
        fun append(point: Vec2) {
            if (current.lastOrNull() != point) current += point
        }
        fun refine(a: Vec2?, b: Vec2?, depth: Int) {
            if (a == null || b == null) {
                breakSegment()
                return
            }
            val midX = (a.x + b.x) / 2.0
            val midpoint = evaluate(midX)
            if (midpoint == null) {
                breakSegment()
                return
            }
            val linearY = (a.y + b.y) / 2.0
            val scale = max(1.0, max(abs(a.y), max(abs(b.y), abs(midpoint.y))))
            val error = abs(midpoint.y - linearY) / scale
            val jump = abs(b.y - a.y) > 80.0 * scale.coerceAtMost(10.0)
            if (depth < maximumDepth && (error > tolerance || jump)) {
                refine(a, midpoint, depth + 1)
                refine(midpoint, b, depth + 1)
            } else if (jump) {
                breakSegment()
            } else {
                append(a)
                append(b)
            }
        }

        val width = definition.domain.maximum - definition.domain.minimum
        for (i in 0 until seedIntervals) {
            val x0 = definition.domain.minimum + width * i / seedIntervals
            val x1 = definition.domain.minimum + width * (i + 1) / seedIntervals
            refine(evaluate(x0), evaluate(x1), 0)
        }
        if (current.size > 1) segments += current
        return AdvancedCurveSample(segments.map { GraphSegment(it.toList()) }, evaluations, discontinuities, definition.domain)
    }

    fun sequence(expression: String, first: Int, last: Int, parameters: Map<String, Double> = emptyMap()): List<SequenceTerm> {
        require(first <= last && last - first <= 10_000)
        val compiled = expressions.compile(stripEquation(expression))
        return (first..last).mapNotNull { n ->
            runCatching { compiled.eval(parameters + ("n" to n.toDouble())) }.getOrNull()
                ?.takeIf(Double::isFinite)?.let { SequenceTerm(n, it) }
        }
    }

    fun vectorField(
        xExpression: String,
        yExpression: String,
        xDomain: GraphDomain,
        yDomain: GraphDomain,
        columns: Int = 17,
        rows: Int = 17,
        normalize: Boolean = true,
    ): List<VectorFieldSample> {
        require(columns in 2..100 && rows in 2..100)
        val xCompiled = expressions.compile(stripEquation(xExpression))
        val yCompiled = expressions.compile(stripEquation(yExpression))
        return buildList {
            for (row in 0 until rows) for (column in 0 until columns) {
                val x = xDomain.minimum + (xDomain.maximum - xDomain.minimum) * column / (columns - 1)
                val y = yDomain.minimum + (yDomain.maximum - yDomain.minimum) * row / (rows - 1)
                val variables = mapOf("x" to x, "y" to y)
                val u = runCatching { xCompiled.eval(variables) }.getOrDefault(Double.NaN)
                val v = runCatching { yCompiled.eval(variables) }.getOrDefault(Double.NaN)
                val magnitude = hypot(u, v)
                if (u.isFinite() && v.isFinite() && magnitude.isFinite()) {
                    val scale = if (normalize && magnitude > 1e-12) 1.0 / magnitude else 1.0
                    add(VectorFieldSample(Vec2(x, y), Vec2(u * scale, v * scale), magnitude))
                }
            }
        }
    }

    fun inequality(
        expression: String,
        xDomain: GraphDomain,
        yDomain: GraphDomain,
        columns: Int = 50,
        rows: Int = 50,
    ): List<InequalityCell> {
        require(columns in 2..200 && rows in 2..200)
        val compiled = expressions.compile(expression)
        return buildList {
            for (row in 0 until rows) for (column in 0 until columns) {
                val x = xDomain.minimum + (xDomain.maximum - xDomain.minimum) * (column + .5) / columns
                val y = yDomain.minimum + (yDomain.maximum - yDomain.minimum) * (row + .5) / rows
                val result = runCatching { compiled.eval(mapOf("x" to x, "y" to y)) }.getOrDefault(0.0)
                add(InequalityCell(Vec2(x, y), result != 0.0))
            }
        }
    }

    fun arcLength(sample: AdvancedCurveSample): Double = sample.segments.sumOf { segment ->
        segment.points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    }

    fun range(sample: AdvancedCurveSample): ClosedFloatingPointRange<Double>? {
        val values = sample.points.map { it.y }.filter(Double::isFinite)
        return if (values.isEmpty()) null else (values.min()..values.max())
    }
}

