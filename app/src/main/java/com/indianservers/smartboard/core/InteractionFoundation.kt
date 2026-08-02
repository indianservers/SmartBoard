package com.indianservers.smartboard.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

enum class GestureMode(val label: String) { Idle("Ready"), Selecting("Selecting"), Lasso("Lasso selecting"), Moving("Moving object"), Resizing("Resizing"), Rotating("Rotating"), Panning("Panning"), Zooming("Zooming") }
enum class AxisConstraint { Free, X, Y, Z }
enum class SnapKind { Axis, Grid, Point, Center, Intersection, Tangent, Alignment }
data class SelectionState(val selectedIds: Set<String> = emptySet(), val primaryId: String? = null)
data class Bounds2D(val minimum: Vec2, val maximum: Vec2) {
    val center = (minimum + maximum) * .5
    val width = maximum.x - minimum.x
    val height = maximum.y - minimum.y
}
data class SnapGuide(val kind: SnapKind, val axis: AxisConstraint, val value: Double, val label: String)
data class SnapResult(val point: Vec2, val guides: List<SnapGuide>)
data class Viewport2D(val center: Vec2 = Vec2(0.0, 0.0), val zoom: Float = 1f)

object UniversalSelectionEngine {
    fun select(state: SelectionState, id: String, additive: Boolean = false): SelectionState =
        if (additive) state.copy(selectedIds = state.selectedIds + id, primaryId = id)
        else SelectionState(setOf(id), id)

    fun clear() = SelectionState()
    fun lasso(ids: Collection<String>): SelectionState = SelectionState(ids.toSet(), ids.lastOrNull())
}

object InteractionGeometry {
    fun bounds(points: Collection<Vec2>): Bounds2D? {
        if (points.isEmpty()) return null
        return Bounds2D(Vec2(points.minOf { it.x }, points.minOf { it.y }), Vec2(points.maxOf { it.x }, points.maxOf { it.y }))
    }

    fun pointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.lastIndex
        polygon.indices.forEach { current ->
            val a = polygon[current]; val b = polygon[previous]
            if ((a.y > point.y) != (b.y > point.y)) {
                val crossing = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                if (point.x < crossing) inside = !inside
            }
            previous = current
        }
        return inside
    }

    fun rotationDegrees(center: Vec2, start: Vec2, current: Vec2): Double =
        Math.toDegrees(atan2(current.y - center.y, current.x - center.x) - atan2(start.y - center.y, start.x - center.x))

    fun segmentIntersections(segments: Collection<Pair<Vec2, Vec2>>): List<Vec2> = buildList {
        val rows = segments.toList()
        rows.indices.forEach { first ->
            for (second in first + 1 until rows.size) {
                val (a, b) = rows[first]; val (c, d) = rows[second]
                val denominator = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
                if (abs(denominator) < 1e-12) continue
                val determinantA = a.x * b.y - a.y * b.x
                val determinantB = c.x * d.y - c.y * d.x
                val point = Vec2((determinantA * (c.x - d.x) - (a.x - b.x) * determinantB) / denominator, (determinantA * (c.y - d.y) - (a.y - b.y) * determinantB) / denominator)
                fun within(value: Double, p: Double, q: Double) = value in min(p, q) - 1e-9..max(p, q) + 1e-9
                if (within(point.x, a.x, b.x) && within(point.y, a.y, b.y) && within(point.x, c.x, d.x) && within(point.y, c.y, d.y)) add(point)
            }
        }
    }.distinctBy { round(it.x * 1e9) to round(it.y * 1e9) }

    fun tangentPoints(from: Vec2, center: Vec2, radius: Double): List<Vec2> {
        val offset = from - center
        val distanceSquared = offset.x * offset.x + offset.y * offset.y
        val radiusSquared = radius * radius
        if (radius <= 0.0 || distanceSquared <= radiusSquared + 1e-12) return emptyList()
        val along = radiusSquared / distanceSquared
        val across = radius * sqrt(distanceSquared - radiusSquared) / distanceSquared
        return listOf(
            center + Vec2(along * offset.x - across * offset.y, along * offset.y + across * offset.x),
            center + Vec2(along * offset.x + across * offset.y, along * offset.y - across * offset.x),
        )
    }

    fun equalSpacingCandidates(points: Collection<Vec2>): List<Vec2> = buildList {
        val values = points.toList()
        values.indices.forEach { first ->
            for (second in first + 1 until values.size) {
                val a = values[first]
                val b = values[second]
                add((a + b) * .5)
                add(a * 2.0 - b)
                add(b * 2.0 - a)
            }
        }
    }.distinctBy { round(it.x * 1e8) to round(it.y * 1e8) }

    fun fit(points: Collection<Vec2>, aspectRatio: Double = 1.0, padding: Double = 1.35): Viewport2D {
        val bounds = bounds(points) ?: return Viewport2D()
        val spanX = max(bounds.width, .5)
        val spanY = max(bounds.height, .5)
        val normalizedSpan = max(spanX, spanY * aspectRatio)
        return Viewport2D(bounds.center, (14.0 / (normalizedSpan * padding)).toFloat().coerceIn(.05f, 20f))
    }
}

object SmartSnapEngine {
    fun constrain(delta: Vec2, axis: AxisConstraint): Vec2 = when (axis) {
        AxisConstraint.X -> Vec2(delta.x, 0.0)
        AxisConstraint.Y -> Vec2(0.0, delta.y)
        AxisConstraint.Z -> Vec2(0.0, 0.0)
        AxisConstraint.Free -> delta
    }

    fun constrain(delta: Vec3, axis: AxisConstraint): Vec3 = when (axis) {
        AxisConstraint.X -> Vec3(delta.x, 0.0, 0.0)
        AxisConstraint.Y -> Vec3(0.0, delta.y, 0.0)
        AxisConstraint.Z -> Vec3(0.0, 0.0, delta.z)
        AxisConstraint.Free -> delta
    }

    fun snap(
        raw: Vec2,
        nearby: Collection<Vec2>,
        gridSize: Double = .5,
        tolerance: Double = .16,
        centers: Collection<Vec2> = emptyList(),
        intersections: Collection<Vec2> = emptyList(),
        tangents: Collection<Vec2> = emptyList(),
        equalSpacing: Collection<Vec2> = emptyList(),
    ): SnapResult {
        val guides = mutableListOf<SnapGuide>()
        var x = raw.x; var y = raw.y
        var bestXDistance = Double.POSITIVE_INFINITY
        var bestYDistance = Double.POSITIVE_INFINITY
        fun consider(candidate: Vec2, kind: SnapKind, label: String) {
            val xDistance = abs(raw.x - candidate.x)
            val yDistance = abs(raw.y - candidate.y)
            if (xDistance <= tolerance && xDistance < bestXDistance) { x = candidate.x; bestXDistance = xDistance; guides += SnapGuide(kind, AxisConstraint.X, x, label) }
            if (yDistance <= tolerance && yDistance < bestYDistance) { y = candidate.y; bestYDistance = yDistance; guides += SnapGuide(kind, AxisConstraint.Y, y, label) }
        }
        consider(Vec2(0.0, 0.0), SnapKind.Axis, "axis")
        if (gridSize > 0) consider(Vec2(round(raw.x / gridSize) * gridSize, round(raw.y / gridSize) * gridSize), SnapKind.Grid, "grid")
        nearby.forEach {
            val exact = abs(raw.x - it.x) <= tolerance && abs(raw.y - it.y) <= tolerance
            consider(it, if (exact) SnapKind.Point else SnapKind.Alignment, if (exact) "point" else "align")
        }
        centers.forEach { consider(it, SnapKind.Center, "centre") }
        intersections.forEach { consider(it, SnapKind.Intersection, "intersection") }
        tangents.forEach { consider(it, SnapKind.Tangent, "tangent") }
        equalSpacing.forEach { consider(it, SnapKind.Alignment, "equal spacing") }
        return SnapResult(Vec2(x, y), listOfNotNull(guides.lastOrNull { it.axis == AxisConstraint.X }, guides.lastOrNull { it.axis == AxisConstraint.Y }).distinct())
    }
}

object PrecisionInteraction {
    fun apply(delta: Vec2, enabled: Boolean) = if (enabled) delta * .18 else delta
    fun apply(delta: Vec3, enabled: Boolean) = if (enabled) delta * .18 else delta
}
