package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

data class Line2D(val point: Vec2, val direction: Vec2) {
    init { require(direction.x * direction.x + direction.y * direction.y > 1e-18) }
}

data class Circle2D(val center: Vec2, val radius: Double) { init { require(radius > 0.0) } }
data class AxisAlignedBounds2D(val minimum: Vec2, val maximum: Vec2)
enum class ConicKind { Circle, Ellipse, Parabola, Hyperbola, Degenerate }

object AnalyticGeometry2D {
    fun lineThrough(a: Vec2, b: Vec2): Line2D = Line2D(a, b - a)

    fun signedSide(line: Line2D, point: Vec2): Double =
        line.direction.x * (point.y - line.point.y) - line.direction.y * (point.x - line.point.x)

    fun distance(line: Line2D, point: Vec2): Double = abs(signedSide(line, point)) /
        sqrt(line.direction.x * line.direction.x + line.direction.y * line.direction.y)

    fun project(line: Line2D, point: Vec2): Vec2 {
        val delta = point - line.point
        val denominator = line.direction.x * line.direction.x + line.direction.y * line.direction.y
        val t = (delta.x * line.direction.x + delta.y * line.direction.y) / denominator
        return line.point + line.direction * t
    }

    fun intersect(first: Line2D, second: Line2D): Vec2? {
        val determinant = first.direction.x * second.direction.y - first.direction.y * second.direction.x
        if (abs(determinant) < 1e-12) return null
        val delta = second.point - first.point
        val t = (delta.x * second.direction.y - delta.y * second.direction.x) / determinant
        return first.point + first.direction * t
    }

    fun circleLineIntersections(circle: Circle2D, line: Line2D): List<Vec2> {
        val foot = project(line, circle.center)
        val distance = foot.distanceTo(circle.center)
        if (distance > circle.radius + 1e-10) return emptyList()
        if (abs(distance - circle.radius) < 1e-10) return listOf(foot)
        val directionLength = sqrt(line.direction.x * line.direction.x + line.direction.y * line.direction.y)
        val offset = line.direction * (sqrt(circle.radius * circle.radius - distance * distance) / directionLength)
        return listOf(foot - offset, foot + offset)
    }

    fun classifyConic(a: Double, b: Double, c: Double, d: Double = 0.0, e: Double = 0.0, f: Double = 0.0): ConicKind {
        if (listOf(a, b, c, d, e, f).all { abs(it) < 1e-12 }) return ConicKind.Degenerate
        val discriminant = b * b - 4 * a * c
        return when {
            abs(discriminant) < 1e-10 -> ConicKind.Parabola
            discriminant > 0 -> ConicKind.Hyperbola
            abs(b) < 1e-10 && abs(a - c) < 1e-10 -> ConicKind.Circle
            else -> ConicKind.Ellipse
        }
    }

    fun convexHull(points: List<Vec2>): List<Vec2> {
        val sorted = points.distinct().sortedWith(compareBy<Vec2> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted
        fun cross(o: Vec2, a: Vec2, b: Vec2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Vec2>()
        for (point in sorted) {
            while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0) lower.removeAt(lower.lastIndex)
            lower += point
        }
        val upper = mutableListOf<Vec2>()
        for (point in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0) upper.removeAt(upper.lastIndex)
            upper += point
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    fun bounds(points: List<Vec2>): AxisAlignedBounds2D? = if (points.isEmpty()) null else AxisAlignedBounds2D(
        Vec2(points.minOf { it.x }, points.minOf { it.y }),
        Vec2(points.maxOf { it.x }, points.maxOf { it.y }),
    )
}

data class Line3D(val point: Vec3, val direction: Vec3) { init { require(direction.magnitude() > 1e-12) } }
data class Plane3D(val point: Vec3, val normal: Vec3) { init { require(normal.magnitude() > 1e-12) } }
data class AxisAlignedBounds3D(val minimum: Vec3, val maximum: Vec3)

object AnalyticGeometry3D {
    fun planeThrough(a: Vec3, b: Vec3, c: Vec3): Plane3D? {
        val normal = cross(b - a, c - a)
        return normal.takeIf { it.magnitude() > 1e-12 }?.let { Plane3D(a, it.normalized()) }
    }

    fun cross(a: Vec3, b: Vec3) = Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    fun triangleArea(a: Vec3, b: Vec3, c: Vec3) = cross(b - a, c - a).magnitude() / 2.0
    fun signedDistance(plane: Plane3D, point: Vec3) = (point - plane.point).dot(plane.normal) / plane.normal.magnitude()
    fun project(plane: Plane3D, point: Vec3) = point - plane.normal.normalized() * signedDistance(plane, point)

    fun intersect(line: Line3D, plane: Plane3D): Vec3? {
        val denominator = plane.normal.dot(line.direction)
        if (abs(denominator) < 1e-12) return null
        val t = plane.normal.dot(plane.point - line.point) / denominator
        return line.point + line.direction * t
    }

    fun planeIntersection(first: Plane3D, second: Plane3D): Line3D? {
        val direction = cross(first.normal, second.normal)
        val denominator = direction.dot(direction)
        if (denominator < 1e-18) return null
        val d1 = first.normal.dot(first.point)
        val d2 = second.normal.dot(second.point)
        val point = cross((second.normal * d1) - (first.normal * d2), direction) * (1.0 / denominator)
        return Line3D(point, direction.normalized())
    }

    fun angleBetween(first: Plane3D, second: Plane3D): Double {
        val cosine = abs(first.normal.dot(second.normal)) / (first.normal.magnitude() * second.normal.magnitude())
        return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    }

    fun bounds(points: List<Vec3>): AxisAlignedBounds3D? = if (points.isEmpty()) null else AxisAlignedBounds3D(
        Vec3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z }),
        Vec3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z }),
    )
}

data class SurfaceDifferential(val point: Vec3, val gradient: Vec3, val unitNormal: Vec3, val tangentPlane: Plane3D)

class SurfaceCalculus(private val expressions: ExpressionEngine = ExpressionEngine()) {
    fun analyze(expression: String, x: Double, y: Double): SurfaceDifferential {
        val compiled = expressions.compile(stripEquation(expression))
        fun f(a: Double, b: Double) = compiled.eval(mapOf("x" to a, "y" to b))
        val hX = maxOf(1e-6, abs(x) * 1e-5)
        val hY = maxOf(1e-6, abs(y) * 1e-5)
        val z = f(x, y)
        val fx = (f(x + hX, y) - f(x - hX, y)) / (2 * hX)
        val fy = (f(x, y + hY) - f(x, y - hY)) / (2 * hY)
        val normal = Vec3(-fx, -fy, 1.0).normalized()
        val point = Vec3(x, y, z)
        return SurfaceDifferential(point, Vec3(fx, fy, 0.0), normal, Plane3D(point, normal))
    }
}
