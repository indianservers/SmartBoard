package com.indianservers.smartboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vec2(x * scale, y * scale)
    fun distanceTo(other: Vec2) = hypot(x - other.x, y - other.y)
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = Vec3(x * scale, y * scale, z * scale)
    fun magnitude() = sqrt(x * x + y * y + z * z)
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun normalized(): Vec3 = magnitude().takeIf { it > 1e-12 }?.let { this * (1.0 / it) } ?: this
}

data class Vector3D(
    val id: String,
    val start: Vec3,
    val end: Vec3,
    val name: String = id,
) {
    val components: Vec3 get() = end - start
    val magnitude: Double get() = components.magnitude()
}

data class SegmentMeasurements(
    val midpoint: Vec2,
    val slope: Double?,
    val distance: Double,
    val exactDistance: String,
)

object Geometry2D {
    fun segment(a: Vec2, b: Vec2): SegmentMeasurements {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val square = dx * dx + dy * dy
        return SegmentMeasurements(
            midpoint = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0),
            slope = if (abs(dx) < 1e-9) null else dy / dx,
            distance = sqrt(square),
            exactDistance = "sqrt(${trim(square)})",
        )
    }

    fun polygonArea(points: List<Vec2>): Double {
        if (points.size < 3) return 0.0
        return abs(points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            a.x * b.y - b.x * a.y
        }) / 2.0
    }

    fun angle(a: Vec2, b: Vec2, c: Vec2): Double {
        val u = a - b
        val v = c - b
        val dot = u.x * v.x + u.y * v.y
        val mag = max(1e-9, u.distanceTo(Vec2(0.0, 0.0)) * v.distanceTo(Vec2(0.0, 0.0)))
        return Math.toDegrees(acos((dot / mag).coerceIn(-1.0, 1.0)))
    }

    fun centroid(a: Vec2, b: Vec2, c: Vec2) = Vec2(
        (a.x + b.x + c.x) / 3.0,
        (a.y + b.y + c.y) / 3.0,
    )

    fun circumcenter(a: Vec2, b: Vec2, c: Vec2): Vec2? {
        val d = 2.0 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
        if (abs(d) < 1e-9) return null
        val a2 = a.x * a.x + a.y * a.y
        val b2 = b.x * b.x + b.y * b.y
        val c2 = c.x * c.x + c.y * c.y
        return Vec2(
            (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / d,
            (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / d,
        )
    }

    fun incenter(a: Vec2, b: Vec2, c: Vec2): Vec2? {
        val oppositeA = b.distanceTo(c)
        val oppositeB = a.distanceTo(c)
        val oppositeC = a.distanceTo(b)
        val perimeter = oppositeA + oppositeB + oppositeC
        if (perimeter < 1e-9) return null
        return Vec2(
            (oppositeA * a.x + oppositeB * b.x + oppositeC * c.x) / perimeter,
            (oppositeA * a.y + oppositeB * b.y + oppositeC * c.y) / perimeter,
        )
    }

    fun orthocenter(a: Vec2, b: Vec2, c: Vec2): Vec2? {
        val center = circumcenter(a, b, c) ?: return null
        return Vec2(a.x + b.x + c.x - 2.0 * center.x, a.y + b.y + c.y - 2.0 * center.y)
    }

    fun lineIntersection(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
        val r = b - a
        val s = d - c
        val denominator = r.x * s.y - r.y * s.x
        if (abs(denominator) < 1e-9) return null
        val q = c - a
        val t = (q.x * s.y - q.y * s.x) / denominator
        return a + r * t
    }
}

class ExpressionEngine {
    fun compile(source: String): Expression = Parser(source).parse()
}

class Expression internal constructor(private val root: Node, val source: String) {
    fun eval(variables: Map<String, Double> = emptyMap()): Double = root.eval(variables)
}

internal sealed interface Node {
    fun eval(v: Map<String, Double>): Double
}

private data class NumberNode(val value: Double) : Node {
    override fun eval(v: Map<String, Double>) = value
}

private data class VariableNode(val name: String) : Node {
    override fun eval(v: Map<String, Double>) = when (name) {
        "pi", "π" -> PI
        "e" -> kotlin.math.E
        else -> v[name] ?: error("Missing value for $name")
    }
}

private data class UnaryNode(val op: Char, val node: Node) : Node {
    override fun eval(v: Map<String, Double>) = when (op) {
        '-' -> -node.eval(v)
        else -> node.eval(v)
    }
}

private data class BinaryNode(val op: Char, val left: Node, val right: Node) : Node {
    override fun eval(v: Map<String, Double>): Double {
        val a = left.eval(v)
        val b = right.eval(v)
        return when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> a / b
            '^' -> a.pow(b)
            else -> error("Unknown operator $op")
        }
    }
}

private data class ComparisonNode(val op: String, val left: Node, val right: Node) : Node {
    override fun eval(v: Map<String, Double>): Double {
        val a = left.eval(v)
        val b = right.eval(v)
        val result = when (op) {
            "<" -> a < b
            "<=" -> a <= b
            ">" -> a > b
            ">=" -> a >= b
            "==" -> abs(a - b) < 1e-12
            "!=" -> abs(a - b) >= 1e-12
            else -> error("Unknown comparison $op")
        }
        return if (result) 1.0 else 0.0
    }
}

private data class FunctionNode(val name: String, val args: List<Node>) : Node {
    override fun eval(v: Map<String, Double>): Double {
        val normalized = name.lowercase()
        if (normalized == "if") {
            require(args.size == 3) { "if requires condition, true value, false value" }
            return if (args[0].eval(v) != 0.0) args[1].eval(v) else args[2].eval(v)
        }
        val values = args.map { it.eval(v) }
        val x = values.firstOrNull() ?: error("$name requires an argument")
        return when (normalized) {
            "sin" -> sin(x)
            "cos" -> cos(x)
            "tan" -> tan(x)
            "sec" -> 1.0 / cos(x)
            "csc" -> 1.0 / sin(x)
            "cot" -> 1.0 / tan(x)
            "sinh" -> kotlin.math.sinh(x)
            "cosh" -> kotlin.math.cosh(x)
            "tanh" -> kotlin.math.tanh(x)
            "asin" -> asin(x)
            "acos" -> acos(x)
            "atan" -> atan(x)
            "sqrt" -> sqrt(x)
            "abs" -> abs(x)
            "exp" -> exp(x)
            "ln" -> ln(x)
            "log" -> kotlin.math.log10(x)
            "floor" -> floor(x)
            "ceil" -> ceil(x)
            "sign" -> when { x > 0.0 -> 1.0; x < 0.0 -> -1.0; else -> 0.0 }
            "min" -> values.minOrNull() ?: Double.NaN
            "max" -> values.maxOrNull() ?: Double.NaN
            else -> error("Unknown function $name")
        }
    }
}

private class Parser(private val input: String) {
    private var index = 0

    fun parse(): Expression {
        val node = parseComparison()
        skip()
        require(index == input.length) { "Unexpected token '${input[index]}'" }
        return Expression(node, input)
    }

    private fun parseComparison(): Node {
        var node = parseExpression()
        while (true) {
            node = when {
                matchString("<=") -> ComparisonNode("<=", node, parseExpression())
                matchString(">=") -> ComparisonNode(">=", node, parseExpression())
                matchString("==") -> ComparisonNode("==", node, parseExpression())
                matchString("!=") -> ComparisonNode("!=", node, parseExpression())
                matchString("<") -> ComparisonNode("<", node, parseExpression())
                matchString(">") -> ComparisonNode(">", node, parseExpression())
                else -> return node
            }
        }
    }

    private fun parseExpression(): Node {
        var node = parseTerm()
        while (true) {
            skip()
            node = when {
                match('+') -> BinaryNode('+', node, parseTerm())
                match('-') -> BinaryNode('-', node, parseTerm())
                else -> return node
            }
        }
    }

    private fun parseTerm(): Node {
        var node = parseUnary()
        while (true) {
            skip()
            node = when {
                match('*') -> BinaryNode('*', node, parseUnary())
                match('/') -> BinaryNode('/', node, parseUnary())
                shouldImplicitMultiply() -> BinaryNode('*', node, parseUnary())
                else -> return node
            }
        }
    }

    private fun parsePower(): Node {
        var node = parsePrimary()
        skip()
        if (match('^')) node = BinaryNode('^', node, parseUnary())
        return node
    }

    private fun parseUnary(): Node {
        skip()
        return when {
            match('-') -> UnaryNode('-', parsePower())
            match('+') -> parseUnary()
            else -> parsePower()
        }
    }

    private fun parsePrimary(): Node {
        skip()
        if (match('(')) {
            val node = parseComparison()
            require(match(')')) { "Missing closing parenthesis" }
            return node
        }
        if (peek()?.isDigit() == true || peek() == '.') return parseNumber()
        if (peek()?.isLetter() == true || peek() == 'π') {
            val name = parseIdentifier()
            skip()
            return if (match('(')) {
                val args = mutableListOf<Node>()
                skip()
                if (peek() != ')') {
                    do {
                        args += parseComparison()
                        skip()
                    } while (match(','))
                }
                require(match(')')) { "Missing closing parenthesis" }
                FunctionNode(name, args)
            } else {
                VariableNode(name)
            }
        }
        error("Expected number, variable, or function")
    }

    private fun parseNumber(): Node {
        val start = index
        while (peek()?.isDigit() == true || peek() == '.') index++
        return NumberNode(input.substring(start, index).toDouble())
    }

    private fun parseIdentifier(): String {
        val start = index
        while (peek()?.isLetter() == true || peek() == 'π') index++
        return input.substring(start, index)
    }

    private fun shouldImplicitMultiply(): Boolean {
        val c = peek() ?: return false
        return c == '(' || c == 'π' || c.isLetter()
    }

    private fun match(c: Char): Boolean {
        skip()
        if (peek() == c) {
            index++
            return true
        }
        return false
    }

    private fun matchString(value: String): Boolean {
        skip()
        if (input.startsWith(value, index)) {
            index += value.length
            return true
        }
        return false
    }

    private fun peek(): Char? = input.getOrNull(index)
    private fun skip() {
        while (peek()?.isWhitespace() == true) index++
    }
}

data class FunctionDefinition(
    val id: String,
    val name: String,
    val expression: String,
    val colorKey: String,
    val visible: Boolean = true,
)

data class CurveSample(val points: List<Vec2>, val breaks: Set<Int>)
data class ImplicitSegment(val start: Vec2, val end: Vec2)
enum class GraphDefinitionKind { Explicit, Polar, Parametric, Implicit }
data class RegressionResult(val slope: Double, val intercept: Double, val correlation: Double)
data class DataSummary(val count: Int, val meanX: Double, val meanY: Double, val standardDeviationY: Double, val regression: RegressionResult?)
data class QuadraticInsight(
    val vertex: Vec2,
    val axis: Double,
    val roots: List<Double>,
    val yIntercept: Double,
    val range: String,
    val factored: String?,
)

data class LinearInsight(val slope: Double, val xIntercept: Double?, val yIntercept: Double)

class GraphAnalysis(private val engine: ExpressionEngine = ExpressionEngine()) {
    fun definitionKind(expression: String): GraphDefinitionKind {
        val normalized = expression.trim().lowercase()
        return when {
            normalized.startsWith("r=") || normalized.startsWith("r =") -> GraphDefinitionKind.Polar
            normalized.contains("x(t)") && normalized.contains("y(t)") && normalized.contains(';') -> GraphDefinitionKind.Parametric
            normalized.contains('=') && !normalized.startsWith("y=") && !normalized.startsWith("y =") &&
                !normalized.substringBefore('=').contains("(x)") -> GraphDefinitionKind.Implicit
            else -> GraphDefinitionKind.Explicit
        }
    }

    fun sampleDefinition(expression: String, minX: Double, maxX: Double, steps: Int = 360): CurveSample = when (definitionKind(expression)) {
        GraphDefinitionKind.Explicit -> sample(expression, minX, maxX, steps)
        GraphDefinitionKind.Polar -> samplePolar(expression, steps)
        GraphDefinitionKind.Parametric -> sampleParametric(expression, steps)
        GraphDefinitionKind.Implicit -> CurveSample(emptyList(), emptySet())
    }

    fun sample(expression: String, minX: Double, maxX: Double, steps: Int = 360): CurveSample {
        val compiled = engine.compile(stripEquation(expression))
        val points = mutableListOf<Vec2>()
        val breaks = mutableSetOf<Int>()
        var lastY: Double? = null
        for (i in 0..steps) {
            val x = minX + (maxX - minX) * i / steps
            val y = runCatching { compiled.eval(mapOf("x" to x)) }.getOrElse { Double.NaN }
            val previousY = lastY
            if (!y.isFinite() || (previousY != null && abs(y - previousY) > 20.0)) {
                breaks += points.size
                lastY = null
            } else {
                points += Vec2(x, y)
                lastY = y
            }
        }
        return CurveSample(points, breaks)
    }

    fun samplePolar(expression: String, steps: Int = 480): CurveSample {
        val compiled = engine.compile(stripEquation(expression))
        val points = (0..steps).mapNotNull { i ->
            val t = 2.0 * PI * i / steps
            val r = runCatching { compiled.eval(mapOf("t" to t, "theta" to t)) }.getOrDefault(Double.NaN)
            if (r.isFinite()) Vec2(r * cos(t), r * sin(t)) else null
        }
        return CurveSample(points, emptySet())
    }

    fun sampleParametric(expression: String, steps: Int = 480): CurveSample {
        val parts = expression.split(';')
        require(parts.size >= 2) { "Parametric form requires x(t)=...; y(t)=..." }
        val xExpression = engine.compile(stripEquation(parts.first { it.lowercase().contains("x(t)") }))
        val yExpression = engine.compile(stripEquation(parts.first { it.lowercase().contains("y(t)") }))
        val points = (0..steps).mapNotNull { i ->
            val t = -2.0 * PI + 4.0 * PI * i / steps
            val variables = mapOf("t" to t)
            val x = runCatching { xExpression.eval(variables) }.getOrDefault(Double.NaN)
            val y = runCatching { yExpression.eval(variables) }.getOrDefault(Double.NaN)
            if (x.isFinite() && y.isFinite()) Vec2(x, y) else null
        }
        return CurveSample(points, emptySet())
    }

    fun implicitSegments(
        expression: String,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        density: Int = 54,
    ): List<ImplicitSegment> {
        val (left, right) = expression.split('=', limit = 2).takeIf { it.size == 2 }
            ?: error("Implicit equation requires =")
        val compiled = engine.compile("($left)-($right)")
        fun value(x: Double, y: Double) = runCatching { compiled.eval(mapOf("x" to x, "y" to y)) }.getOrDefault(Double.NaN)
        fun crossing(a: Vec2, av: Double, b: Vec2, bv: Double): Vec2? {
            if (!av.isFinite() || !bv.isFinite()) return null
            if (abs(av) < 1e-12) return a
            if (abs(bv) < 1e-12) return b
            if ((av > 0) == (bv > 0)) return null
            val t = (av / (av - bv)).coerceIn(0.0, 1.0)
            return a + (b - a) * t
        }
        val segments = mutableListOf<ImplicitSegment>()
        for (ix in 0 until density) {
            val x0 = minX + (maxX - minX) * ix / density
            val x1 = minX + (maxX - minX) * (ix + 1) / density
            for (iy in 0 until density) {
                val y0 = minY + (maxY - minY) * iy / density
                val y1 = minY + (maxY - minY) * (iy + 1) / density
                val corners = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
                val values = corners.map { value(it.x, it.y) }
                val hits = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0).mapNotNull { (a, b) ->
                    crossing(corners[a], values[a], corners[b], values[b])
                }
                if (hits.size >= 2) {
                    segments += ImplicitSegment(hits[0], hits[1])
                    if (hits.size == 4) segments += ImplicitSegment(hits[2], hits[3])
                }
            }
        }
        return segments
    }

    fun roots(expression: String, minX: Double, maxX: Double, steps: Int = 600): List<Double> {
        val compiled = engine.compile(stripEquation(expression))
        val zero = engine.compile("0")
        return intersections(compiled, zero, minX, maxX).map { it.x }.distinctBy { trim(it) }
    }

    fun derivative(expression: String, x: Double): Double {
        val compiled = engine.compile(stripEquation(expression))
        return derivative(compiled, x)
    }

    private fun derivative(compiled: Expression, x: Double): Double {
        val h = max(1e-6, abs(x) * 1e-5)
        return (compiled.eval(mapOf("x" to x + h)) - compiled.eval(mapOf("x" to x - h))) / (2.0 * h)
    }

    fun integral(expression: String, from: Double, to: Double, steps: Int = 600): Double {
        val compiled = engine.compile(stripEquation(expression))
        if (from == to) return 0.0
        val n = if (steps % 2 == 0) steps else steps + 1
        val h = (to - from) / n
        var sum = compiled.eval(mapOf("x" to from)) + compiled.eval(mapOf("x" to to))
        for (i in 1 until n) {
            val y = compiled.eval(mapOf("x" to from + i * h))
            sum += if (i % 2 == 0) 2.0 * y else 4.0 * y
        }
        return sum * h / 3.0
    }

    fun extrema(expression: String, minX: Double, maxX: Double, steps: Int = 500): List<Vec2> {
        val compiled = engine.compile(stripEquation(expression))
        val results = mutableListOf<Vec2>()
        var previousX = minX
        var previousDerivative = derivative(compiled, previousX)
        for (i in 1..steps) {
            val x = minX + (maxX - minX) * i / steps
            val d = derivative(compiled, x)
            if (d.isFinite() && previousDerivative.isFinite() && (d > 0) != (previousDerivative > 0)) {
                val candidate = (previousX + x) / 2.0
                val y = runCatching { compiled.eval(mapOf("x" to candidate)) }.getOrDefault(Double.NaN)
                if (y.isFinite()) results += Vec2(candidate, y)
            }
            previousX = x
            previousDerivative = d
        }
        return results.distinctBy { trim(it.x) }
    }

    fun quadratic(a: Double, b: Double, c: Double): QuadraticInsight {
        val vx = -b / (2.0 * a)
        val vy = a * vx * vx + b * vx + c
        val d = b * b - 4.0 * a * c
        val roots = if (d >= 0.0) {
            listOf((-b - sqrt(d)) / (2.0 * a), (-b + sqrt(d)) / (2.0 * a)).sorted()
        } else {
            emptyList()
        }
        return QuadraticInsight(
            vertex = Vec2(vx, vy),
            axis = vx,
            roots = roots,
            yIntercept = c,
            range = if (a > 0) "y >= ${trim(vy)}" else "y <= ${trim(vy)}",
            factored = if (roots.size == 2) "(x - ${trim(roots[0])})(x - ${trim(roots[1])})" else null,
        )
    }

    fun linear(m: Double, b: Double) = LinearInsight(
        slope = m,
        xIntercept = if (abs(m) < 1e-9) null else -b / m,
        yIntercept = b,
    )

    fun intersections(f: Expression, g: Expression, minX: Double, maxX: Double): List<Vec2> {
        val roots = mutableListOf<Vec2>()
        var prevX = minX
        var prev = f.eval(mapOf("x" to prevX)) - g.eval(mapOf("x" to prevX))
        val n = 400
        for (i in 1..n) {
            val x = minX + (maxX - minX) * i / n
            val value = f.eval(mapOf("x" to x)) - g.eval(mapOf("x" to x))
            if (prev == 0.0 || value == 0.0 || prev.sign() != value.sign()) {
                val root = bisect(f, g, prevX, x)
                roots += Vec2(root, f.eval(mapOf("x" to root)))
            }
            prevX = x
            prev = value
        }
        return roots.distinctBy { trim(it.x) }
    }

    private fun bisect(f: Expression, g: Expression, left: Double, right: Double): Double {
        var lo = left
        var hi = right
        repeat(40) {
            val mid = (lo + hi) / 2.0
            val a = f.eval(mapOf("x" to lo)) - g.eval(mapOf("x" to lo))
            val b = f.eval(mapOf("x" to mid)) - g.eval(mapOf("x" to mid))
            if (a.sign() == b.sign()) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }
}

object StatisticsEngine {
    fun summarize(points: List<Vec2>): DataSummary {
        if (points.isEmpty()) return DataSummary(0, Double.NaN, Double.NaN, Double.NaN, null)
        val meanX = points.sumOf { it.x } / points.size
        val meanY = points.sumOf { it.y } / points.size
        val varianceY = points.sumOf { (it.y - meanY).pow(2) } / points.size
        val covariance = points.sumOf { (it.x - meanX) * (it.y - meanY) }
        val varianceXSum = points.sumOf { (it.x - meanX).pow(2) }
        val regression = if (varianceXSum < 1e-12) null else {
            val slope = covariance / varianceXSum
            val intercept = meanY - slope * meanX
            val yVarianceSum = points.sumOf { (it.y - meanY).pow(2) }
            val correlation = if (yVarianceSum < 1e-12) 0.0 else covariance / sqrt(varianceXSum * yVarianceSum)
            RegressionResult(slope, intercept, correlation.coerceIn(-1.0, 1.0))
        }
        return DataSummary(points.size, meanX, meanY, sqrt(varianceY), regression)
    }
}

object ProbabilityEngine {
    fun normalPdf(x: Double, mean: Double = 0.0, standardDeviation: Double = 1.0): Double {
        require(standardDeviation > 0.0)
        val z = (x - mean) / standardDeviation
        return exp(-0.5 * z * z) / (standardDeviation * sqrt(2.0 * PI))
    }

    fun binomialPmf(successes: Int, trials: Int, probability: Double): Double {
        require(trials >= 0 && successes in 0..trials && probability in 0.0..1.0)
        var combination = 1.0
        for (i in 1..successes) combination *= (trials - successes + i).toDouble() / i
        return combination * probability.pow(successes) * (1.0 - probability).pow(trials - successes)
    }
}

enum class SolidType {
    Cube, Cuboid, Sphere, Hemisphere, Cylinder, Cone, Frustum, Pyramid,
    TriangularPrism, PentagonalPrism, HexagonalPrism, OctagonalPrism,
    Tetrahedron, TriangularPyramid, Octahedron, Wedge,
    Torus, Ellipsoid, Paraboloid, Capsule,
}

data class Solid(
    val type: SolidType,
    val width: Double,
    val height: Double = width,
    val depth: Double = width,
    val radius: Double = width / 2.0,
    val topRadius: Double = radius / 2.0,
    val position: Vec3 = Vec3(0.0, 0.0, 0.0),
    val rotation: Vec3 = Vec3(0.0, 0.0, 0.0),
)

data class SolidMeasurements(val volume: Double, val surfaceArea: Double, val faces: Int, val edges: Int, val vertices: Int)

object Geometry3D {
    fun formula(type: SolidType): String = when (type) {
        SolidType.Cube -> "V = a cubed; A = 6a squared"
        SolidType.Cuboid -> "V = lwh; A = 2(lw + lh + wh)"
        SolidType.Sphere -> "V = 4 pi r cubed / 3; A = 4 pi r squared"
        SolidType.Hemisphere -> "V = 2 pi r cubed / 3; A = 3 pi r squared"
        SolidType.Cylinder -> "V = pi r squared h; A = 2 pi r(r + h)"
        SolidType.Cone -> "V = pi r squared h / 3; A = pi r(r + s)"
        SolidType.Frustum -> "V = pi h(R squared + Rr + r squared) / 3"
        SolidType.Pyramid -> "V = base area times h / 3"
        SolidType.TriangularPrism -> "V = triangle base area times length"
        SolidType.PentagonalPrism -> "V = pentagon base area times h"
        SolidType.HexagonalPrism -> "V = 3 sqrt(3) s squared h / 2"
        SolidType.OctagonalPrism -> "V = 2(1 + sqrt(2))s squared h"
        SolidType.Tetrahedron -> "V = a cubed / (6 sqrt(2)); A = sqrt(3) a squared"
        SolidType.TriangularPyramid -> "V = triangle base area times h / 3"
        SolidType.Octahedron -> "V = sqrt(2) a cubed / 3; A = 2 sqrt(3) a squared"
        SolidType.Wedge -> "V = base times height times length / 2"
        SolidType.Torus -> "V = 2 pi squared R r squared; A = 4 pi squared Rr"
        SolidType.Ellipsoid -> "V = 4 pi abc / 3"
        SolidType.Paraboloid -> "V = pi r squared h / 2"
        SolidType.Capsule -> "V = pi r squared l + 4 pi r cubed / 3"
    }

    fun measure(solid: Solid): SolidMeasurements = when (solid.type) {
        SolidType.Cube -> SolidMeasurements(solid.width.pow(3), 6 * solid.width.pow(2), 6, 12, 8)
        SolidType.Cuboid -> SolidMeasurements(
            solid.width * solid.height * solid.depth,
            2 * (solid.width * solid.height + solid.width * solid.depth + solid.height * solid.depth),
            6,
            12,
            8,
        )
        SolidType.Sphere -> SolidMeasurements(4.0 / 3.0 * PI * solid.radius.pow(3), 4 * PI * solid.radius.pow(2), 1, 0, 0)
        SolidType.Hemisphere -> SolidMeasurements(2.0 / 3.0 * PI * solid.radius.pow(3), 3 * PI * solid.radius.pow(2), 2, 1, 0)
        SolidType.Cylinder -> SolidMeasurements(PI * solid.radius.pow(2) * solid.height, 2 * PI * solid.radius * (solid.radius + solid.height), 3, 2, 0)
        SolidType.Cone -> {
            val slant = sqrt(solid.radius.pow(2) + solid.height.pow(2))
            SolidMeasurements(PI * solid.radius.pow(2) * solid.height / 3.0, PI * solid.radius * (solid.radius + slant), 2, 1, 1)
        }
        SolidType.Frustum -> {
            val slant = sqrt((solid.radius - solid.topRadius).pow(2) + solid.height.pow(2))
            val volume = PI * solid.height * (solid.radius.pow(2) + solid.radius * solid.topRadius + solid.topRadius.pow(2)) / 3.0
            val area = PI * (solid.radius + solid.topRadius) * slant + PI * (solid.radius.pow(2) + solid.topRadius.pow(2))
            SolidMeasurements(volume, area, 3, 2, 0)
        }
        SolidType.Pyramid -> {
            val slantWidth = sqrt(solid.height.pow(2) + (solid.depth / 2).pow(2))
            val slantDepth = sqrt(solid.height.pow(2) + (solid.width / 2).pow(2))
            SolidMeasurements(
                solid.width * solid.depth * solid.height / 3.0,
                solid.width * solid.depth + solid.width * slantWidth + solid.depth * slantDepth,
                5, 8, 5,
            )
        }
        SolidType.TriangularPrism -> {
            val side = sqrt((solid.width / 2).pow(2) + solid.height.pow(2))
            SolidMeasurements(
                solid.width * solid.height * solid.depth / 2.0,
                solid.width * solid.height + solid.depth * (solid.width + 2 * side),
                5, 9, 6,
            )
        }
        SolidType.PentagonalPrism, SolidType.HexagonalPrism, SolidType.OctagonalPrism -> {
            val sides = when (solid.type) { SolidType.PentagonalPrism -> 5; SolidType.HexagonalPrism -> 6; else -> 8 }
            val baseArea = sides * solid.radius.pow(2) * kotlin.math.sin(2 * PI / sides) / 2
            val perimeter = 2 * sides * solid.radius * kotlin.math.sin(PI / sides)
            SolidMeasurements(baseArea * solid.height, 2 * baseArea + perimeter * solid.height, sides + 2, 3 * sides, 2 * sides)
        }
        SolidType.Tetrahedron -> SolidMeasurements(solid.width.pow(3) / (6 * sqrt(2.0)), sqrt(3.0) * solid.width.pow(2), 4, 6, 4)
        SolidType.TriangularPyramid -> {
            val baseArea = solid.width * solid.depth / 2
            val a = sqrt(solid.height.pow(2) + (solid.width / 2).pow(2)); val b = sqrt(solid.height.pow(2) + (solid.depth / 2).pow(2))
            SolidMeasurements(baseArea * solid.height / 3, baseArea + (solid.width * a + solid.depth * b) / 2, 4, 6, 4)
        }
        SolidType.Octahedron -> SolidMeasurements(sqrt(2.0) * solid.width.pow(3) / 3.0, 2 * sqrt(3.0) * solid.width.pow(2), 8, 12, 6)
        SolidType.Wedge -> {
            val slope = sqrt(solid.width.pow(2) + solid.height.pow(2))
            SolidMeasurements(solid.width * solid.height * solid.depth / 2, solid.width * solid.height + solid.depth * (solid.width + solid.height + slope), 5, 9, 6)
        }
        SolidType.Torus -> SolidMeasurements(2 * PI.pow(2) * solid.width * solid.radius.pow(2), 4 * PI.pow(2) * solid.width * solid.radius, 1, 0, 0)
        SolidType.Ellipsoid -> {
            val a = solid.width / 2; val b = solid.height / 2; val c = solid.depth / 2
            val p = 1.6075
            val area = 4 * PI * ((a.pow(p) * b.pow(p) + a.pow(p) * c.pow(p) + b.pow(p) * c.pow(p)) / 3).pow(1 / p)
            SolidMeasurements(4 * PI * a * b * c / 3, area, 1, 0, 0)
        }
        SolidType.Paraboloid -> {
            val r = solid.radius; val h = solid.height
            val curved = PI * r / (6 * h.pow(2)) * ((r.pow(2) + 4 * h.pow(2)).pow(1.5) - r.pow(3))
            SolidMeasurements(PI * r.pow(2) * h / 2, PI * r.pow(2) + curved, 2, 1, 1)
        }
        SolidType.Capsule -> {
            val r = solid.radius.coerceAtMost(solid.height / 2); val cylinder = (solid.height - 2 * r).coerceAtLeast(0.0)
            SolidMeasurements(PI * r.pow(2) * cylinder + 4 * PI * r.pow(3) / 3, 2 * PI * r * cylinder + 4 * PI * r.pow(2), 1, 0, 0)
        }
    }
}

data class SolidMesh(
    val vertices: List<Vec3>,
    val edges: List<Pair<Int, Int>>,
    val faces: List<List<Int>>,
)

/** Renderer-neutral solid geometry used by Canvas today and GPU/AR renderers later. */
object SolidMeshFactory {
    fun create(solid: Solid, segments: Int = 24): SolidMesh = when (solid.type) {
        SolidType.Cube, SolidType.Cuboid -> box(solid.width, solid.height, solid.depth)
        SolidType.Pyramid -> pyramid(solid)
        SolidType.TriangularPrism -> triangularPrism(solid)
        SolidType.PentagonalPrism -> regularPrism(solid, 5)
        SolidType.HexagonalPrism -> regularPrism(solid, 6)
        SolidType.OctagonalPrism -> regularPrism(solid, 8)
        SolidType.Tetrahedron -> tetrahedron(solid.width)
        SolidType.TriangularPyramid -> triangularPyramid(solid)
        SolidType.Octahedron -> octahedron(solid.width)
        SolidType.Wedge -> wedge(solid)
        SolidType.Cylinder -> ringSolid(solid, segments, solid.radius, solid.radius)
        SolidType.Cone -> ringSolid(solid, segments, solid.radius, 0.0)
        SolidType.Frustum -> ringSolid(solid, segments, solid.radius, solid.topRadius)
        SolidType.Sphere -> latitudeSolid(solid.radius, segments, false)
        SolidType.Hemisphere -> latitudeSolid(solid.radius, segments, true)
        SolidType.Torus -> torus(solid.width, solid.radius, segments)
        SolidType.Ellipsoid -> ellipsoid(solid, segments)
        SolidType.Paraboloid -> paraboloid(solid, segments)
        SolidType.Capsule -> capsule(solid, segments)
    }

    private fun box(w: Double, h: Double, d: Double): SolidMesh {
        val x = w / 2; val y = h / 2; val z = d / 2
        val vertices = listOf(
            Vec3(-x, -y, -z), Vec3(x, -y, -z), Vec3(x, y, -z), Vec3(-x, y, -z),
            Vec3(-x, -y, z), Vec3(x, -y, z), Vec3(x, y, z), Vec3(-x, y, z),
        )
        val faces = listOf(listOf(0, 1, 2, 3), listOf(4, 7, 6, 5), listOf(0, 4, 5, 1), listOf(3, 2, 6, 7), listOf(0, 3, 7, 4), listOf(1, 5, 6, 2))
        return mesh(vertices, faces)
    }

    private fun pyramid(s: Solid): SolidMesh {
        val w = s.width / 2; val h = s.height / 2; val d = s.depth / 2
        return mesh(
            listOf(Vec3(-w, -h, -d), Vec3(w, -h, -d), Vec3(w, -h, d), Vec3(-w, -h, d), Vec3(0.0, h, 0.0)),
            listOf(listOf(0, 3, 2, 1), listOf(0, 1, 4), listOf(1, 2, 4), listOf(2, 3, 4), listOf(3, 0, 4)),
        )
    }

    private fun triangularPrism(s: Solid): SolidMesh {
        val w = s.width / 2; val h = s.height / 2; val d = s.depth / 2
        return mesh(
            listOf(Vec3(-w, -h, -d), Vec3(w, -h, -d), Vec3(0.0, h, -d), Vec3(-w, -h, d), Vec3(w, -h, d), Vec3(0.0, h, d)),
            listOf(listOf(0, 2, 1), listOf(3, 4, 5), listOf(0, 1, 4, 3), listOf(1, 2, 5, 4), listOf(2, 0, 3, 5)),
        )
    }

    private fun triangularPyramid(s: Solid): SolidMesh {
        val w=s.width/2;val h=s.height/2;val d=s.depth/2
        return mesh(listOf(Vec3(-w,-h,-d),Vec3(w,-h,-d),Vec3(0.0,-h,d),Vec3(0.0,h,0.0)),listOf(listOf(0,2,1),listOf(0,1,3),listOf(1,2,3),listOf(2,0,3)))
    }

    private fun wedge(s: Solid): SolidMesh {
        val w=s.width/2;val h=s.height/2;val d=s.depth/2
        return mesh(listOf(Vec3(-w,-h,-d),Vec3(w,-h,-d),Vec3(-w,h,-d),Vec3(-w,-h,d),Vec3(w,-h,d),Vec3(-w,h,d)),listOf(listOf(0,2,1),listOf(3,4,5),listOf(0,1,4,3),listOf(1,2,5,4),listOf(2,0,3,5)))
    }

    private fun regularPrism(s: Solid, sides: Int): SolidMesh {
        val vertices = mutableListOf<Vec3>()
        listOf(-s.height / 2, s.height / 2).forEach { y ->
            repeat(sides) { i ->
                val angle = 2 * PI * i / sides - PI / 2
                vertices += Vec3(cos(angle) * s.radius, y, sin(angle) * s.radius)
            }
        }
        val faces = mutableListOf<List<Int>>()
        faces += (0 until sides).reversed().toList()
        faces += (0 until sides).map { sides + it }
        repeat(sides) { i ->
            val next = (i + 1) % sides
            faces += listOf(i, next, sides + next, sides + i)
        }
        return mesh(vertices, faces)
    }

    private fun tetrahedron(edge: Double): SolidMesh {
        val a = edge / (2 * sqrt(2.0))
        return mesh(listOf(Vec3(a, a, a), Vec3(a, -a, -a), Vec3(-a, a, -a), Vec3(-a, -a, a)), listOf(listOf(0, 1, 2), listOf(0, 3, 1), listOf(0, 2, 3), listOf(1, 3, 2)))
    }

    private fun octahedron(edge: Double): SolidMesh {
        val r = edge / sqrt(2.0)
        val v = listOf(Vec3(r, 0.0, 0.0), Vec3(-r, 0.0, 0.0), Vec3(0.0, r, 0.0), Vec3(0.0, -r, 0.0), Vec3(0.0, 0.0, r), Vec3(0.0, 0.0, -r))
        return mesh(v, listOf(listOf(2, 0, 4), listOf(2, 4, 1), listOf(2, 1, 5), listOf(2, 5, 0), listOf(3, 4, 0), listOf(3, 1, 4), listOf(3, 5, 1), listOf(3, 0, 5)))
    }

    private fun ringSolid(s: Solid, count: Int, bottomRadius: Double, topRadius: Double): SolidMesh {
        val vertices = mutableListOf<Vec3>()
        repeat(count) { i -> val t = i * 2 * PI / count; vertices += Vec3(cos(t) * bottomRadius, -s.height / 2, sin(t) * bottomRadius) }
        val topStart = vertices.size
        if (topRadius <= 1e-9) vertices += Vec3(0.0, s.height / 2, 0.0)
        else repeat(count) { i -> val t = i * 2 * PI / count; vertices += Vec3(cos(t) * topRadius, s.height / 2, sin(t) * topRadius) }
        val faces = mutableListOf<List<Int>>()
        faces += (0 until count).reversed().toList()
        if (topRadius <= 1e-9) repeat(count) { i -> faces += listOf(i, (i + 1) % count, topStart) }
        else {
            faces += (0 until count).map { topStart + it }
            repeat(count) { i -> faces += listOf(i, (i + 1) % count, topStart + (i + 1) % count, topStart + i) }
        }
        return mesh(vertices, faces)
    }

    private fun latitudeSolid(radius: Double, segments: Int, hemisphere: Boolean): SolidMesh {
        val rings = if (hemisphere) segments / 4 else segments / 2
        val vertices = mutableListOf<Vec3>()
        for (j in 0..rings) {
            val latitude = if (hemisphere) j * PI / 2 / rings else -PI / 2 + j * PI / rings
            repeat(segments) { i ->
                val longitude = i * 2 * PI / segments
                vertices += Vec3(radius * cos(latitude) * cos(longitude), radius * sin(latitude), radius * cos(latitude) * sin(longitude))
            }
        }
        val faces = mutableListOf<List<Int>>()
        for (j in 0 until rings) repeat(segments) { i ->
            val next = (i + 1) % segments
            faces += listOf(j * segments + i, j * segments + next, (j + 1) * segments + next, (j + 1) * segments + i)
        }
        if (hemisphere) faces += (0 until segments).reversed().toList()
        return mesh(vertices, faces)
    }

    private fun capsule(s: Solid, segments: Int): SolidMesh {
        val r=s.radius.coerceAtMost(s.height/2);val cylinderHalf=(s.height-2*r).coerceAtLeast(0.0)/2;val ringCount=(segments/2).coerceAtLeast(8)
        val vertices=mutableListOf<Vec3>()
        for(j in 0..ringCount){val latitude=-PI/2+j*PI/ringCount;val centerY=if(latitude<0)-cylinderHalf else cylinderHalf;repeat(segments){i->val longitude=i*2*PI/segments;vertices+=Vec3(r*cos(latitude)*cos(longitude),centerY+r*sin(latitude),r*cos(latitude)*sin(longitude))}}
        val faces=mutableListOf<List<Int>>();for(j in 0 until ringCount)repeat(segments){i->val next=(i+1)%segments;faces+=listOf(j*segments+i,j*segments+next,(j+1)*segments+next,(j+1)*segments+i)}
        return mesh(vertices,faces)
    }

    private fun torus(majorRadius: Double, minorRadius: Double, segments: Int): SolidMesh {
        val rings = (segments / 2).coerceAtLeast(8)
        val vertices = mutableListOf<Vec3>()
        repeat(rings) { j -> repeat(segments) { i ->
            val u = i * 2 * PI / segments; val v = j * 2 * PI / rings
            vertices += Vec3((majorRadius + minorRadius * cos(v)) * cos(u), minorRadius * sin(v), (majorRadius + minorRadius * cos(v)) * sin(u))
        } }
        val faces = mutableListOf<List<Int>>()
        repeat(rings) { j -> repeat(segments) { i ->
            val nextI = (i + 1) % segments; val nextJ = (j + 1) % rings
            faces += listOf(j * segments + i, j * segments + nextI, nextJ * segments + nextI, nextJ * segments + i)
        } }
        return mesh(vertices, faces)
    }

    private fun ellipsoid(s: Solid, segments: Int): SolidMesh {
        val sphere = latitudeSolid(1.0, segments, false)
        return sphere.copy(vertices = sphere.vertices.map { Vec3(it.x * s.width / 2, it.y * s.height / 2, it.z * s.depth / 2) })
    }

    private fun paraboloid(s: Solid, segments: Int): SolidMesh {
        val rings = (segments / 2).coerceAtLeast(6)
        val vertices = mutableListOf(Vec3(0.0, -s.height / 2, 0.0))
        for (ring in 1..rings) {
            val t = ring.toDouble() / rings
            val radius = s.radius * sqrt(t)
            val y = -s.height / 2 + s.height * t
            repeat(segments) { i ->
                val angle = 2 * PI * i / segments
                vertices += Vec3(radius * cos(angle), y, radius * sin(angle))
            }
        }
        val faces = mutableListOf<List<Int>>()
        repeat(segments) { i -> faces += listOf(0, 1 + i, 1 + (i + 1) % segments) }
        for (ring in 1 until rings) repeat(segments) { i ->
            val a = 1 + (ring - 1) * segments + i
            val b = 1 + (ring - 1) * segments + (i + 1) % segments
            val c = 1 + ring * segments + (i + 1) % segments
            val d = 1 + ring * segments + i
            faces += listOf(a, b, c, d)
        }
        val topStart = 1 + (rings - 1) * segments
        faces += (0 until segments).map { topStart + it }
        return mesh(vertices, faces)
    }

    private fun mesh(vertices: List<Vec3>, faces: List<List<Int>>): SolidMesh {
        val edges = linkedSetOf<Pair<Int, Int>>()
        faces.forEach { face -> face.indices.forEach { i ->
            val a = face[i]; val b = face[(i + 1) % face.size]
            edges += if (a < b) a to b else b to a
        } }
        return SolidMesh(vertices, edges.toList(), faces)
    }
}

object CrossSection3D {
    /** Intersects a mesh with a local-space plane and returns the ordered section points. */
    fun intersect(mesh: SolidMesh, normal: Vec3, offset: Double): List<Vec3> {
        val n = normal.normalized()
        val points = mutableListOf<Vec3>()
        mesh.edges.forEach { (aIndex, bIndex) ->
            val a = mesh.vertices[aIndex]; val b = mesh.vertices[bIndex]
            val da = n.dot(a) - offset; val db = n.dot(b) - offset
            when {
                kotlin.math.abs(da) < 1e-8 -> points += a
                kotlin.math.abs(db) < 1e-8 -> points += b
                da * db < 0.0 -> points += a + (b - a) * (da / (da - db))
            }
        }
        val unique = points.distinctBy { Triple((it.x * 100000).toInt(), (it.y * 100000).toInt(), (it.z * 100000).toInt()) }
        if (unique.size < 3) return unique
        val center = unique.reduce(Vec3::plus) * (1.0 / unique.size)
        val helper = if (kotlin.math.abs(n.y) < .9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val basisA = AnalyticGeometry3D.cross(n, helper).normalized()
        val basisB = AnalyticGeometry3D.cross(n, basisA).normalized()
        return unique.sortedBy { point ->
            val delta = point - center
            kotlin.math.atan2(delta.dot(basisB), delta.dot(basisA))
        }
    }
}

data class SurfaceMesh(val vertices: List<Vec3>, val rows: Int, val columns: Int)
data class SurfaceInsight(val classification: String, val vertex: Vec3?, val range: String, val symmetry: String)

class Graph3D(private val engine: ExpressionEngine = ExpressionEngine()) {
    fun mesh(expression: String, min: Double = -3.0, max: Double = 3.0, density: Int = 32): SurfaceMesh {
        val compiled = engine.compile(stripEquation(expression).replace("y", "yy"))
        val vertices = mutableListOf<Vec3>()
        for (i in 0..density) {
            val x = min + (max - min) * i / density
            for (j in 0..density) {
                val y = min + (max - min) * j / density
                val z = runCatching { compiled.eval(mapOf("x" to x, "yy" to y)) }.getOrDefault(Double.NaN)
                if (z.isFinite()) vertices += Vec3(x, y, z.coerceIn(-8.0, 8.0))
            }
        }
        return SurfaceMesh(vertices, density + 1, density + 1)
    }

    fun insight(expression: String): SurfaceInsight {
        val normalized = stripEquation(expression).replace(" ", "")
        return if (normalized in setOf("x^2+y^2", "x*x+y*y")) {
            SurfaceInsight(
                classification = "Elliptic paraboloid",
                vertex = Vec3(0.0, 0.0, 0.0),
                range = "z >= 0",
                symmetry = "Rotational symmetry about the z-axis",
            )
        } else {
            SurfaceInsight("Sampled surface", null, "Computed on selected domain", "Inspect by slices and contours")
        }
    }
}

fun stripEquation(value: String): String = value.substringAfter("=").trim().ifBlank { value.trim() }

fun trim(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString() else rounded.toString()
}

private fun Double.sign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}
