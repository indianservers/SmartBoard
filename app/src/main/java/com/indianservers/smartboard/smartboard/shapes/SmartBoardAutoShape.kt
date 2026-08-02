package com.indianservers.smartboard.smartboard.shapes

import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class AutoShapeCandidate(
    val type: SmartBoardShapeType,
    val points: List<SmartBoardPoint>,
    val bounds: SmartBoardBounds,
    val confidence: Float,
    val rationale: String,
    val sourceStrokeIds: List<String>,
) {
    init {
        require(points.size >= 2)
        require(confidence in 0f..1f)
        require(sourceStrokeIds.isNotEmpty())
    }

    fun toElement(
        id: String,
        now: Long,
        strokeWidth: Float,
        argbColor: Long,
        opacity: Float,
    ) = ShapeElement(
        id, type, points, sourceStrokeIds, confidence, strokeWidth, argbColor, opacity,
        bounds = bounds, createdAt = now,
    )
}

data class AutoShapeSuggestion(
    val candidates: List<AutoShapeCandidate>,
    val selectedIndex: Int = 0,
    val createdAt: Long,
    val forced: Boolean,
) {
    init {
        require(candidates.isNotEmpty())
        require(selectedIndex in candidates.indices)
    }
    val selected get() = candidates[selectedIndex]
    fun select(index: Int) = copy(selectedIndex = index.coerceIn(candidates.indices))
}

interface StrokePreprocessor {
    fun normalize(strokes: List<StrokeElement>): List<List<SmartBoardPoint>>
}

interface AutoShapeRecognizer {
    fun recognize(strokes: List<StrokeElement>, forced: Boolean = false): List<AutoShapeCandidate>
}

interface ConfidenceEvaluator {
    fun shouldSuggest(candidate: AutoShapeCandidate, forced: Boolean): Boolean
}

object DefaultShapeConfidenceEvaluator : ConfidenceEvaluator {
    override fun shouldSuggest(candidate: AutoShapeCandidate, forced: Boolean): Boolean {
        val threshold = when {
            forced -> .52f
            candidate.type in setOf(
                SmartBoardShapeType.ARC,
                SmartBoardShapeType.CURVE,
                SmartBoardShapeType.CLOSED_REGION,
            ) -> .84f
            else -> .68f
        }
        return candidate.confidence >= threshold
    }
}

class GeometricStrokePreprocessor(
    private val maxPointsPerStroke: Int = 192,
) : StrokePreprocessor {
    override fun normalize(strokes: List<StrokeElement>): List<List<SmartBoardPoint>> = strokes.map { stroke ->
        val distinct = stroke.points.map { it.position }.fold(mutableListOf<SmartBoardPoint>()) { out, point ->
            if (out.lastOrNull()?.distanceTo(point)?.let { it > .15f } != false) out += point
            out
        }
        val sampled = if (distinct.size <= maxPointsPerStroke) distinct else {
            val stride = distinct.lastIndex.toFloat() / (maxPointsPerStroke - 1)
            List(maxPointsPerStroke) { distinct[(it * stride).toInt().coerceIn(distinct.indices)] }
        }
        if (sampled.size < 3) sampled else {
            val tolerance = maxOf(0.65f, bounds(sampled).diagonal * .012f)
            val closed = sampled.size >= 4 && sampled.first().distanceTo(sampled.last()) <= bounds(sampled).diagonal * .1f
            if (!closed) douglasPeucker(sampled, tolerance) else {
                val open = sampled.dropLast(1)
                val pivot = open.indices.maxBy { open[it].distanceTo(open.first()) }
                val first = douglasPeucker(open.subList(0, pivot + 1), tolerance)
                val second = douglasPeucker(open.subList(pivot, open.size) + open.first(), tolerance)
                first.dropLast(1) + second
            }
        }
    }
}

object SmartBoardStrokeGrouper {
    fun recentRelated(
        all: List<StrokeElement>,
        newest: StrokeElement,
        maximumGapMillis: Long = 1_600,
        maximumStrokes: Int = 16,
    ): List<StrokeElement> {
        val result = mutableListOf(newest)
        var combined = newest.bounds
        all.asReversed().forEach { candidate ->
            if (candidate.id == newest.id || result.size >= maximumStrokes || candidate.hidden) return@forEach
            val gap = newest.createdAt - candidate.createdAt
            if (gap !in 0..maximumGapMillis) return@forEach
            val padding = max(18f, max(combined.width, combined.height) * .28f)
            if (!combined.expand(padding).intersects(candidate.bounds)) return@forEach
            result += candidate
            combined = union(combined, candidate.bounds)
        }
        return result.sortedBy(StrokeElement::createdAt)
    }
}

class DeterministicAutoShapeRecognizer(
    private val preprocessor: StrokePreprocessor = GeometricStrokePreprocessor(),
    private val confidenceEvaluator: ConfidenceEvaluator = DefaultShapeConfidenceEvaluator,
) : AutoShapeRecognizer {
    override fun recognize(strokes: List<StrokeElement>, forced: Boolean): List<AutoShapeCandidate> {
        val usable = strokes.filter { !it.hidden && it.points.size >= 2 }.takeLast(8)
        if (usable.isEmpty()) return emptyList()
        val paths = preprocessor.normalize(usable)
        val ids = usable.map(StrokeElement::id)
        val candidates = buildList {
            if (paths.size == 1) {
                val path = paths.single()
                line(path, ids)?.let(::add)
                closed(path, ids).forEach(::add)
                singleStrokeArrow(path, ids)?.let(::add)
                arcOrCurve(path, ids)?.let(::add)
            } else {
                multiLinePolygon(paths, ids)?.let(::add)
                arrow(paths, ids)?.let(::add)
                axesOrAngle(paths, ids).forEach(::add)
                numberLine(paths, ids)?.let(::add)
                graphGrid(paths, ids)?.let(::add)
            }
        }
        return candidates
            .filter { confidenceEvaluator.shouldSuggest(it, forced) }
            .sortedByDescending(AutoShapeCandidate::confidence)
            .distinctBy(AutoShapeCandidate::type)
            .take(4)
    }

    private fun line(path: List<SmartBoardPoint>, ids: List<String>): AutoShapeCandidate? {
        if (path.size < 2) return null
        val start = path.first()
        val end = path.last()
        val direct = start.distanceTo(end)
        val length = pathLength(path)
        val box = bounds(path)
        if (direct < 12f || length <= 0f) return null
        val straightness = (direct / length).coerceIn(0f, 1f)
        val deviation = path.maxOf { distanceToSegment(it, start, end) } / max(box.diagonal, 1f)
        if (straightness < .90f || deviation > .075f) return null
        val dx = abs(end.x - start.x)
        val dy = abs(end.y - start.y)
        val type = when {
            dy <= max(4f, dx * .12f) -> SmartBoardShapeType.HORIZONTAL_LINE
            dx <= max(4f, dy * .12f) -> SmartBoardShapeType.VERTICAL_LINE
            else -> SmartBoardShapeType.DIAGONAL_LINE
        }
        return candidate(type, listOf(start, end), .72f + straightness * .18f - deviation, "Straight fitted segment", ids)
    }

    private fun closed(path: List<SmartBoardPoint>, ids: List<String>): List<AutoShapeCandidate> {
        if (path.size < 4) return emptyList()
        val box = bounds(path)
        if (box.diagonal < 18f || path.first().distanceTo(path.last()) > box.diagonal * .20f) return emptyList()
        val centre = box.center
        val radii = path.dropLast(1).map { it.distanceTo(centre) }
        val radialMean = radii.average().toFloat().coerceAtLeast(.1f)
        val radialCv = sqrt(radii.sumOf { (it - radialMean) * (it - radialMean).toDouble() } / radii.size).toFloat() / radialMean
        val aspect = box.width / max(box.height, 1f)
        val simplified = polygonVertices(path, box.diagonal * .055f)
        val out = mutableListOf<AutoShapeCandidate>()
        if (radialCv < .22f && aspect in .72f..1.38f && simplified.size >= 6) {
            val type = if (aspect in .88f..1.14f) SmartBoardShapeType.CIRCLE else SmartBoardShapeType.ELLIPSE
            out += candidate(type, ellipsePoints(box), .91f - radialCv * .55f - abs(1f - aspect).coerceAtMost(.3f) * .16f, "Closed path with stable radius", ids)
        }
        if (simplified.size in 3..8) {
            val polygon = classifyPolygon(simplified, box)
            val regularity = polygonRegularity(simplified)
            out += candidate(polygon, simplified + simplified.first(), .75f + regularity * .16f, "${simplified.size}-corner closed polygon", ids)
        } else if (looksLikeStar(path, centre)) {
            out += candidate(SmartBoardShapeType.STAR, starPoints(box), .78f, "Alternating inner and outer corners", ids)
        } else {
            out += candidate(SmartBoardShapeType.CLOSED_REGION, path, .64f, "Closed freehand region", ids)
        }
        return out
    }

    private fun classifyPolygon(vertices: List<SmartBoardPoint>, box: SmartBoardBounds): SmartBoardShapeType = when (vertices.size) {
        3 -> {
            val angles = interiorAngles(vertices)
            val sides = cyclicSides(vertices)
            when {
                angles.any { abs(it - 90.0) < 13.0 } -> SmartBoardShapeType.RIGHT_TRIANGLE
                sides.maxOrNull()!! / sides.minOrNull()!!.coerceAtLeast(.1f) < 1.16f -> SmartBoardShapeType.EQUILATERAL_TRIANGLE
                else -> SmartBoardShapeType.TRIANGLE
            }
        }
        4 -> {
            val right = interiorAngles(vertices).all { abs(it - 90.0) < 16.0 }
            if (right && abs(box.width - box.height) / max(box.width, box.height).coerceAtLeast(1f) < .13f) SmartBoardShapeType.SQUARE
            else if (right) SmartBoardShapeType.RECTANGLE
            else SmartBoardShapeType.POLYGON
        }
        5 -> SmartBoardShapeType.PENTAGON
        6 -> SmartBoardShapeType.HEXAGON
        else -> SmartBoardShapeType.POLYGON
    }

    private fun singleStrokeArrow(path: List<SmartBoardPoint>, ids: List<String>): AutoShapeCandidate? {
        val box = bounds(path)
        val simple = douglasPeucker(path, box.diagonal * .045f)
        if (simple.size !in 4..7) return null
        val tip = simple.last()
        val shaftStart = simple.first()
        val shaftLength = shaftStart.distanceTo(tip)
        val head = simple.drop(1).dropLast(1).filter { it.distanceTo(tip) in (shaftLength * .08f)..(shaftLength * .38f) }
        if (head.size < 2 || shaftLength < 24f) return null
        return candidate(SmartBoardShapeType.ARROW, listOf(shaftStart, tip, head[0], tip, head[1]), .70f, "Shaft with two arrowhead arms", ids)
    }

    private fun arcOrCurve(path: List<SmartBoardPoint>, ids: List<String>): AutoShapeCandidate? {
        val box = bounds(path)
        if (box.diagonal < 18f || path.first().distanceTo(path.last()) < box.diagonal * .2f) return null
        val simple = douglasPeucker(path, box.diagonal * .045f)
        if (simple.size < 3) return null
        val verticalDirections = simple.zipWithNext().map { (a, b) -> (b.y - a.y).compareTo(0f) }.filterNot { it == 0 }
        val oscillations = verticalDirections.zipWithNext().count { (a, b) -> a != b }
        if (oscillations >= 4) return null
        val bend = pathLength(path) / path.first().distanceTo(path.last()).coerceAtLeast(1f)
        if (bend < 1.08f) return null
        val type = if (bend < 1.75f && simple.size <= 8) SmartBoardShapeType.ARC else SmartBoardShapeType.CURVE
        return candidate(type, simple, (.62f + (bend - 1f).coerceIn(0f, .2f)).coerceAtMost(.78f), "Smooth non-linear open path", ids)
    }

    private fun multiLinePolygon(paths: List<List<SmartBoardPoint>>, ids: List<String>): AutoShapeCandidate? {
        if (paths.size !in 3..8 || paths.any { line(it, ids) == null }) return null
        val endpoints = paths.flatMap { listOf(it.first(), it.last()) }
        val box = bounds(endpoints)
        val tolerance = max(10f, box.diagonal * .13f)
        val clusterGroups = cluster(endpoints, tolerance)
        if (clusterGroups.size !in 3..8 || clusterGroups.any { it.size < 2 }) return null
        val clusters = clusterGroups.map { group ->
            SmartBoardPoint(group.map { it.x }.average().toFloat(), group.map { it.y }.average().toFloat())
        }
        val centre = SmartBoardPoint(clusters.map { it.x }.average().toFloat(), clusters.map { it.y }.average().toFloat())
        val ordered = clusters.sortedBy { atan2((it.y - centre.y).toDouble(), (it.x - centre.x).toDouble()) }
        return candidate(classifyPolygon(ordered, bounds(ordered)), ordered + ordered.first(), .84f, "Connected multi-stroke polygon", ids)
    }

    private fun arrow(paths: List<List<SmartBoardPoint>>, ids: List<String>): AutoShapeCandidate? {
        val lines = paths.mapNotNull { path -> line(path, ids)?.let { path.first() to path.last() } }
        if (lines.size < 3) return null
        val shaft = lines.maxBy { it.first.distanceTo(it.second) }
        val length = shaft.first.distanceTo(shaft.second)
        val atEnd = lines.filterNot { it == shaft }.filter { pair ->
            min(pair.first.distanceTo(shaft.second), pair.second.distanceTo(shaft.second)) < max(9f, length * .14f)
        }
        if (atEnd.size < 2) return null
        val arms = atEnd.take(2).map { pair -> if (pair.first.distanceTo(shaft.second) < pair.second.distanceTo(shaft.second)) pair.second else pair.first }
        return candidate(SmartBoardShapeType.ARROW, listOf(shaft.first, shaft.second, arms[0], shaft.second, arms[1]), .89f, "Multi-stroke shaft and arrowhead", ids)
    }

    private fun axesOrAngle(paths: List<List<SmartBoardPoint>>, ids: List<String>): List<AutoShapeCandidate> {
        val lines = paths.mapNotNull { path -> line(path, ids)?.let { path.first() to path.last() } }
        if (lines.size < 2) return emptyList()
        val out = mutableListOf<AutoShapeCandidate>()
        for (i in 0 until lines.lastIndex) for (j in i + 1 until lines.size) {
            val a = lines[i]
            val b = lines[j]
            val angle = acuteAngle(a.second - a.first, b.second - b.first)
            if (angle in 78.0..102.0) {
                val intersection = segmentIntersection(a.first, a.second, b.first, b.second)
                if (intersection != null) {
                    val central = bounds(listOf(a.first, a.second)).expand(6f).contains(intersection) &&
                        bounds(listOf(b.first, b.second)).expand(6f).contains(intersection)
                    val type = if (central && endpointDistance(a, b) > 12f) SmartBoardShapeType.COORDINATE_AXES else SmartBoardShapeType.ANGLE
                    out += candidate(type, listOf(a.first, a.second, b.first, b.second), if (type == SmartBoardShapeType.COORDINATE_AXES) .88f else .80f, "Two perpendicular fitted lines", ids)
                }
            } else if (angle < 9.0) {
                out += candidate(SmartBoardShapeType.PARALLEL_LINES, listOf(a.first, a.second, b.first, b.second), .78f, "Two parallel fitted lines", ids)
            }
        }
        return out
    }

    private fun numberLine(paths: List<List<SmartBoardPoint>>, ids: List<String>): AutoShapeCandidate? {
        val lines = paths.mapNotNull { path -> line(path, ids)?.let { path.first() to path.last() } }
        if (lines.size < 4) return null
        val main = lines.maxBy { it.first.distanceTo(it.second) }
        val mainLength = main.first.distanceTo(main.second)
        val ticks = lines.filterNot { it == main }.filter {
            it.first.distanceTo(it.second) < mainLength * .25f &&
                acuteAngle(main.second - main.first, it.second - it.first) in 70.0..110.0
        }
        if (ticks.size < 3) return null
        return candidate(SmartBoardShapeType.NUMBER_LINE, listOf(main.first, main.second) + ticks.flatMap { listOf(it.first, it.second) }, .83f, "Baseline with perpendicular ticks", ids)
    }

    private fun graphGrid(paths: List<List<SmartBoardPoint>>, ids: List<String>): AutoShapeCandidate? {
        val lines = paths.mapNotNull { path -> line(path, ids)?.let { path.first() to path.last() } }
        if (lines.size < 6) return null
        val horizontal = lines.filter { abs(it.second.y - it.first.y) <= abs(it.second.x - it.first.x) * .15f }
        val vertical = lines.filter { abs(it.second.x - it.first.x) <= abs(it.second.y - it.first.y) * .15f }
        if (horizontal.size < 3 || vertical.size < 3) return null
        return candidate(SmartBoardShapeType.GRAPH_GRID, lines.flatMap { listOf(it.first, it.second) }, .86f, "Intersecting horizontal and vertical line families", ids)
    }

    private fun candidate(
        type: SmartBoardShapeType,
        points: List<SmartBoardPoint>,
        confidence: Float,
        rationale: String,
        ids: List<String>,
    ) = AutoShapeCandidate(type, points, bounds(points), confidence.coerceIn(0f, 1f), rationale, ids)
}

private fun polygonVertices(path: List<SmartBoardPoint>, tolerance: Float): List<SmartBoardPoint> {
    val open = if (path.first().distanceTo(path.last()) <= tolerance * 2f) path.dropLast(1) else path
    if (open.size < 3) return open
    val simplified = if (open.size <= 8) open else {
        val pivot = open.indices.maxBy { open[it].distanceTo(open.first()) }
        val first = douglasPeucker(open.subList(0, pivot + 1), tolerance)
        val second = douglasPeucker(open.subList(pivot, open.size) + open.first(), tolerance)
        (first.dropLast(1) + second).dropLast(1)
    }
    return simplified.removeNearDuplicates(tolerance * .7f)
}

private fun looksLikeStar(path: List<SmartBoardPoint>, centre: SmartBoardPoint): Boolean {
    val vertices = polygonVertices(path, bounds(path).diagonal * .035f)
    if (vertices.size !in 8..12) return false
    val radii = vertices.map { it.distanceTo(centre) }
    val even = radii.filterIndexed { index, _ -> index % 2 == 0 }.average()
    val odd = radii.filterIndexed { index, _ -> index % 2 == 1 }.average()
    return max(even, odd) / min(even, odd).coerceAtLeast(1.0) > 1.35
}

private fun ellipsePoints(box: SmartBoardBounds, count: Int = 48) = (0..count).map { index ->
    val angle = 2.0 * PI * index / count
    SmartBoardPoint(
        box.center.x + cos(angle).toFloat() * box.width / 2f,
        box.center.y + sin(angle).toFloat() * box.height / 2f,
    )
}

private fun starPoints(box: SmartBoardBounds) = (0..10).map { index ->
    val angle = -PI / 2 + index * PI / 5
    val radius = if (index % 2 == 0) .5f else .22f
    SmartBoardPoint(
        box.center.x + cos(angle).toFloat() * box.width * radius,
        box.center.y + sin(angle).toFloat() * box.height * radius,
    )
}

private fun polygonRegularity(vertices: List<SmartBoardPoint>): Float {
    val sides = cyclicSides(vertices)
    val mean = sides.average().toFloat().coerceAtLeast(.1f)
    val spread = sqrt(sides.sumOf { (it - mean) * (it - mean).toDouble() } / sides.size).toFloat() / mean
    return (1f - spread).coerceIn(0f, 1f)
}

private fun cyclicSides(vertices: List<SmartBoardPoint>) =
    vertices.indices.map { vertices[it].distanceTo(vertices[(it + 1) % vertices.size]) }

private fun interiorAngles(vertices: List<SmartBoardPoint>) = vertices.indices.map { index ->
    val previous = vertices[(index - 1 + vertices.size) % vertices.size] - vertices[index]
    val next = vertices[(index + 1) % vertices.size] - vertices[index]
    angle(previous, next)
}

private fun angle(a: SmartBoardPoint, b: SmartBoardPoint): Double {
    val denominator = hypot(a.x.toDouble(), a.y.toDouble()) * hypot(b.x.toDouble(), b.y.toDouble())
    if (denominator <= 1e-8) return 0.0
    return Math.toDegrees(acos(((a.x * b.x + a.y * b.y) / denominator).coerceIn(-1.0, 1.0)))
}

private fun acuteAngle(a: SmartBoardPoint, b: SmartBoardPoint): Double {
    val value = angle(a, b)
    return min(value, 180.0 - value)
}

private fun endpointDistance(a: Pair<SmartBoardPoint, SmartBoardPoint>, b: Pair<SmartBoardPoint, SmartBoardPoint>) =
    listOf(a.first.distanceTo(b.first), a.first.distanceTo(b.second), a.second.distanceTo(b.first), a.second.distanceTo(b.second)).min()

private fun segmentIntersection(a: SmartBoardPoint, b: SmartBoardPoint, c: SmartBoardPoint, d: SmartBoardPoint): SmartBoardPoint? {
    val denominator = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
    if (abs(denominator) < 1e-5f) return null
    val first = a.x * b.y - a.y * b.x
    val second = c.x * d.y - c.y * d.x
    return SmartBoardPoint(
        (first * (c.x - d.x) - (a.x - b.x) * second) / denominator,
        (first * (c.y - d.y) - (a.y - b.y) * second) / denominator,
    )
}

private fun cluster(points: List<SmartBoardPoint>, tolerance: Float): List<List<SmartBoardPoint>> {
    val groups = mutableListOf<MutableList<SmartBoardPoint>>()
    points.forEach { point ->
        val group = groups.firstOrNull { existing -> existing.any { it.distanceTo(point) <= tolerance } }
        if (group == null) groups += mutableListOf(point) else group += point
    }
    return groups
}

private fun List<SmartBoardPoint>.removeNearDuplicates(tolerance: Float) = fold(mutableListOf<SmartBoardPoint>()) { out, point ->
    if (out.lastOrNull()?.distanceTo(point)?.let { it > tolerance } != false) out += point
    out
}

private fun douglasPeucker(points: List<SmartBoardPoint>, tolerance: Float): List<SmartBoardPoint> {
    if (points.size <= 2) return points
    var farthest = 0f
    var index = 0
    for (i in 1 until points.lastIndex) {
        val distance = distanceToSegment(points[i], points.first(), points.last())
        if (distance > farthest) {
            farthest = distance
            index = i
        }
    }
    if (farthest <= tolerance) return listOf(points.first(), points.last())
    return (douglasPeucker(points.subList(0, index + 1), tolerance).dropLast(1) +
        douglasPeucker(points.subList(index, points.size), tolerance))
}

private fun distanceToSegment(point: SmartBoardPoint, start: SmartBoardPoint, end: SmartBoardPoint): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 1e-8f) return point.distanceTo(start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return point.distanceTo(SmartBoardPoint(start.x + t * dx, start.y + t * dy))
}

private fun pathLength(points: List<SmartBoardPoint>) = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b).toDouble() }.toFloat()

private fun bounds(points: List<SmartBoardPoint>) = SmartBoardBounds.from(points)

private fun union(a: SmartBoardBounds, b: SmartBoardBounds) =
    SmartBoardBounds(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

private val SmartBoardBounds.diagonal get() = hypot(width.toDouble(), height.toDouble()).toFloat()

private fun SmartBoardPoint.distanceTo(other: SmartBoardPoint) = hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()
