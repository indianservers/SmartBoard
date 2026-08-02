package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong

enum class GraphAddKind(val label: String, val starter: String) {
    Expression("Expression", "x"), Point("Point", "(1,1)"), Table("Table", "table"),
    Inequality("Inequality", "y <= x^2"), Regression("Regression", "y1 ~ a*x1+b"), Slider("Slider", "a*x")
}
enum class GraphLineStyle { Solid, Dashed, Dotted }
enum class AxisNumberFormat { Adaptive, Decimal, Fraction, Pi, Scientific }
enum class GraphTransformKind { TranslateX, TranslateY, ReflectX, ReflectY, StretchX, StretchY }

data class GraphAxisSettings(
    val xName: String = "x", val yName: String = "y", val xUnit: String = "", val yUnit: String = "",
    val format: AxisNumberFormat = AxisNumberFormat.Adaptive, val gridVisible: Boolean = true,
    val xLogarithmic: Boolean = false, val yLogarithmic: Boolean = false,
)
data class GraphViewState(val center: Vec2 = Vec2(0.0, 0.0), val zoom: Float = 1f)
data class GraphDomainSelection(val minimum: Double = -10.0, val maximum: Double = 10.0, val leftClosed: Boolean = true, val rightClosed: Boolean = true) {
    init { require(minimum.isFinite() && maximum.isFinite() && minimum < maximum) }
    fun contains(x: Double) = x > minimum && x < maximum || leftClosed && x == minimum || rightClosed && x == maximum
}
data class GraphLayer(val id: String, val order: Int, val visible: Boolean = true)
data class GraphSnapshot(val name: String, val expressions: List<String>, val view: GraphViewState)
data class GraphComparisonPoint(val x: Double, val first: Double, val second: Double) { val difference = first - second }
data class GraphMiniMap(val world: Bounds2D, val viewport: Bounds2D)

class GraphViewHistory(initial: GraphViewState = GraphViewState()) {
    private val back = mutableListOf<GraphViewState>()
    private val forward = mutableListOf<GraphViewState>()
    var current: GraphViewState = initial; private set
    fun commit(next: GraphViewState): GraphViewState {
        if (next != current) { back += current; current = next; forward.clear() }
        return current
    }
    fun back(): GraphViewState { if (back.isNotEmpty()) { forward += current; current = back.removeAt(back.lastIndex) }; return current }
    fun forward(): GraphViewState { if (forward.isNotEmpty()) { back += current; current = forward.removeAt(forward.lastIndex) }; return current }
    fun canGoBack() = back.isNotEmpty()
    fun canGoForward() = forward.isNotEmpty()
}

object GraphUxEngine {
    fun contextActions(hasSelection: Boolean): List<String> = if (hasSelection)
        listOf("Edit", "Trace", "Tangent", "Derivative", "Integral", "Domain", "Style", "Duplicate", "Hide", "Delete")
    else listOf("Add equation", "Add point", "Paste", "Fit view", "Axis settings", "Snapshot")

    fun format(value: Double, mode: AxisNumberFormat, step: Double = 1.0): String {
        if (!value.isFinite()) return "undefined"
        val chosen = if (mode == AxisNumberFormat.Adaptive) when {
            abs(value) >= 1e5 || abs(value) in 1e-12..1e-4 -> AxisNumberFormat.Scientific
            abs(value / PI - round(value / PI)) < 1e-8 -> AxisNumberFormat.Pi
            else -> AxisNumberFormat.Decimal
        } else mode
        return when (chosen) {
            AxisNumberFormat.Scientific -> "%.3e".format(value)
            AxisNumberFormat.Pi -> formatPi(value)
            AxisNumberFormat.Fraction -> formatFraction(value)
            AxisNumberFormat.Decimal, AxisNumberFormat.Adaptive -> {
                val digits = when { step >= 1 -> 0; step >= .1 -> 1; step >= .01 -> 2; else -> 3 }
                "% .${digits}f".format(value).trim()
            }
        }
    }

    fun transform(expression: String, kind: GraphTransformKind, amount: Double): String = when (kind) {
        GraphTransformKind.TranslateX -> "(${stripEquation(expression).replace("x", "(x-${clean(amount)})")})"
        GraphTransformKind.TranslateY -> "(${stripEquation(expression)})+${clean(amount)}"
        GraphTransformKind.ReflectX -> "-(${stripEquation(expression)})"
        GraphTransformKind.ReflectY -> "(${stripEquation(expression).replace("x", "(-x)")})"
        GraphTransformKind.StretchX -> "(${stripEquation(expression).replace("x", "(x/${clean(amount.coerceAtLeast(.01))})")})"
        GraphTransformKind.StretchY -> "${clean(amount)}*(${stripEquation(expression)})"
    }

    fun compare(first: Expression, second: Expression, minimum: Double, maximum: Double, samples: Int = 160): List<GraphComparisonPoint> {
        require(minimum < maximum && samples in 2..5000)
        return (0..samples).mapNotNull { index ->
            val x = minimum + (maximum - minimum) * index / samples
            val a = runCatching { first.eval(mapOf("x" to x)) }.getOrNull()
            val b = runCatching { second.eval(mapOf("x" to x)) }.getOrNull()
            if (a?.isFinite() == true && b?.isFinite() == true) GraphComparisonPoint(x, a, b) else null
        }
    }

    fun avoidLabelCollisions(anchors: List<Vec2>, width: Double = 2.5, height: Double = .7): List<Vec2> {
        val placed = mutableListOf<Vec2>()
        anchors.forEach { anchor ->
            var candidate = anchor
            var attempts = 0
            while (placed.any { abs(it.x - candidate.x) < width && abs(it.y - candidate.y) < height } && attempts++ < 12) candidate += Vec2(.0, height)
            placed += candidate
        }
        return placed
    }

    fun minimap(content: Collection<Vec2>, view: GraphViewState, aspect: Double = 1.0): GraphMiniMap {
        val world = InteractionGeometry.bounds(content) ?: Bounds2D(Vec2(-10.0, -10.0), Vec2(10.0, 10.0))
        val halfWidth = 7.0 / view.zoom
        val halfHeight = halfWidth / aspect.coerceAtLeast(.1)
        return GraphMiniMap(world, Bounds2D(view.center - Vec2(halfWidth, halfHeight), view.center + Vec2(halfWidth, halfHeight)))
    }

    fun reorder(layers: List<GraphLayer>, id: String, delta: Int): List<GraphLayer> {
        val ordered = layers.sortedBy { it.order }.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return ordered
        val target = (index + delta).coerceIn(ordered.indices)
        val item = ordered.removeAt(index); ordered.add(target, item)
        return ordered.mapIndexed { order, layer -> layer.copy(order = order) }
    }

    private fun formatPi(value: Double): String {
        if (abs(value) < 1e-12) return "0"
        val ratio = value / PI
        val denominator = (1..12).firstOrNull { abs(ratio * it - round(ratio * it)) < 1e-7 } ?: return "%.3f".format(value)
        val numerator = (ratio * denominator).roundToLong()
        return when { denominator == 1 -> if (numerator == 1L) "π" else if (numerator == -1L) "-π" else "${numerator}π"; numerator == 1L -> "π/$denominator"; numerator == -1L -> "-π/$denominator"; else -> "${numerator}π/$denominator" }
    }
    private fun formatFraction(value: Double): String {
        val denominator = (1..64).minBy { abs(value * it - round(value * it)) }
        val numerator = round(value * denominator).toLong()
        val divisor = gcd(abs(numerator), denominator.toLong())
        return if (denominator / divisor == 1L) "${numerator / divisor}" else "${numerator / divisor}/${denominator / divisor}"
    }
    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a.coerceAtLeast(1) else gcd(b, a % b)
    private fun clean(value: Double) = if (abs(value - round(value)) < 1e-9) round(value).toLong().toString() else "%.4f".format(value).trimEnd('0').trimEnd('.')
}
