package com.indianservers.smartboard.smartboard.canvas

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardViewport
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot

object SmartBoardCoordinates {
    /** Converts physical screen pixels into density-independent canvas/render coordinates. */
    fun screenToCanvas(screen: SmartBoardPoint, density: Float): SmartBoardPoint {
        require(density.isFinite() && density > 0f)
        return SmartBoardPoint(screen.x / density, screen.y / density)
    }

    /** Converts canvas/render coordinates into persistent document coordinates. */
    fun canvasToDocument(canvas: SmartBoardPoint, viewport: SmartBoardViewport) =
        SmartBoardPoint((canvas.x - viewport.panX) / viewport.zoom, (canvas.y - viewport.panY) / viewport.zoom)

    fun documentToCanvas(document: SmartBoardPoint, viewport: SmartBoardViewport) =
        SmartBoardPoint(document.x * viewport.zoom + viewport.panX, document.y * viewport.zoom + viewport.panY)

    fun screenToDocument(screen: SmartBoardPoint, density: Float, viewport: SmartBoardViewport) =
        canvasToDocument(screenToCanvas(screen, density), viewport)

    fun documentToScreen(document: SmartBoardPoint, density: Float, viewport: SmartBoardViewport): SmartBoardPoint {
        val canvas = documentToCanvas(document, viewport)
        return SmartBoardPoint(canvas.x * density, canvas.y * density)
    }
}

object SmartBoardStrokeGeometry {
    fun bounds(points: List<StrokePoint>, width: Float = 0f) =
        SmartBoardBounds.from(points.map(StrokePoint::position), width / 2f)

    fun simplify(points: List<StrokePoint>, tolerance: Float): List<StrokePoint> {
        if (points.size <= 2 || tolerance <= 0f) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        simplifyRange(points, 0, points.lastIndex, tolerance * tolerance, keep)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun simplifyRange(points: List<StrokePoint>, first: Int, last: Int, toleranceSquared: Float, keep: BooleanArray) {
        if (last <= first + 1) return
        var maximum = -1f
        var index = -1
        for (candidate in first + 1 until last) {
            val distance = segmentDistanceSquared(points[candidate].position, points[first].position, points[last].position)
            if (distance > maximum) {
                maximum = distance
                index = candidate
            }
        }
        if (maximum > toleranceSquared && index > first) {
            keep[index] = true
            simplifyRange(points, first, index, toleranceSquared, keep)
            simplifyRange(points, index, last, toleranceSquared, keep)
        }
    }

    fun distanceToStroke(point: SmartBoardPoint, stroke: StrokeElement): Float =
        stroke.points.zipWithNext().minOfOrNull { (a, b) -> kotlin.math.sqrt(segmentDistanceSquared(point, a.position, b.position)) }
            ?: Float.POSITIVE_INFINITY

    private fun segmentDistanceSquared(point: SmartBoardPoint, start: SmartBoardPoint, end: SmartBoardPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (abs(dx) + abs(dy) < 1e-7f) {
            val px = point.x - start.x
            val py = point.y - start.y
            return px * px + py * py
        }
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val px = point.x - (start.x + t * dx)
        val py = point.y - (start.y + t * dy)
        return px * px + py * py
    }
}

object SmartBoardSelection {
    fun rectangle(elements: List<SmartBoardElement>, bounds: SmartBoardBounds): Set<String> =
        elements.filter { !it.hidden && it.bounds.intersects(bounds) }.mapTo(linkedSetOf(), SmartBoardElement::id)

    fun lasso(elements: List<SmartBoardElement>, polygon: List<SmartBoardPoint>): Set<String> {
        if (polygon.size < 3) return emptySet()
        return elements.filter { !it.hidden && contains(polygon, it.bounds.center) }.mapTo(linkedSetOf(), SmartBoardElement::id)
    }

    fun tap(elements: List<SmartBoardElement>, point: SmartBoardPoint, tolerance: Float = 8f): String? =
        elements.asReversed().firstOrNull { element ->
            !element.hidden && when (element) {
                is StrokeElement -> SmartBoardStrokeGeometry.distanceToStroke(point, element) <= tolerance + element.width / 2f
                else -> element.bounds.expand(tolerance).contains(point)
            }
        }?.id

    fun contains(polygon: List<SmartBoardPoint>, point: SmartBoardPoint): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            if ((current.y > point.y) != (previous.y > point.y)) {
                val crossing = (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x
                if (point.x < crossing) inside = !inside
            }
            previous = current
        }
        return inside
    }

    fun groupedSelection(seed: Set<String>, relationships: List<com.indianservers.smartboard.smartboard.models.SmartBoardRelationship>): Set<String> {
        val groups = relationships.filter { it.type == com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType.GROUP }
        var result = seed
        var changed: Boolean
        do {
            val expanded = groups.filter { group -> group.elementIds.any(result::contains) }.flatMapTo(linkedSetOf()) { it.elementIds }
            val next = result + expanded
            changed = next.size != result.size
            result = next
        } while (changed)
        return result
    }
}

fun SmartBoardPoint.distanceTo(other: SmartBoardPoint) = hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()

