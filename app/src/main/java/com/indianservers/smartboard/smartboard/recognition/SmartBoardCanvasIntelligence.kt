package com.indianservers.smartboard.smartboard.recognition

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.shapes.AutoShapeCandidate
import com.indianservers.smartboard.smartboard.shapes.AutoShapeRecognizer
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import java.util.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class CanvasStrokeIntent { FORMULA, WORD, GRAPH, DIAGRAM, OBJECT, UNKNOWN }

data class IntentAwareStrokeGroup(
    val id: String,
    val strokeIds: List<String>,
    val intent: CanvasStrokeIntent,
    val confidence: Float,
    val bounds: SmartBoardBounds,
    val rationale: String,
)

data class CanvasObjectHypothesis(
    val label: String,
    val shapeType: SmartBoardShapeType?,
    val confidence: Float,
    val sourceStrokeIds: List<String>,
    val completionPoints: List<SmartBoardPoint> = emptyList(),
    val incomplete: Boolean = false,
    val rationale: String,
)

data class CanvasUncertaintyRegion(
    val id: String,
    val strokeIds: List<String>,
    val bounds: SmartBoardBounds,
    val confidence: Float,
    val alternatives: List<CanvasObjectHypothesis>,
)

data class SmartBoardCanvasIntelligenceSnapshot(
    val groups: List<IntentAwareStrokeGroup>,
    val hypotheses: List<CanvasObjectHypothesis>,
    val uncertaintyRegions: List<CanvasUncertaintyRegion>,
    val createdAt: Long,
) {
    val ghostCompletion: CanvasObjectHypothesis?
        get() = hypotheses.filter(CanvasObjectHypothesis::incomplete).maxByOrNull(CanvasObjectHypothesis::confidence)

    companion object {
        val Empty = SmartBoardCanvasIntelligenceSnapshot(emptyList(), emptyList(), emptyList(), 0L)
    }
}

data class TaughtCanvasExample(
    val label: String,
    val shapeType: SmartBoardShapeType?,
    val features: List<Float>,
    val confirmations: Int,
    val updatedAt: Long,
)

data class CanvasTeachingProfile(
    val examples: List<TaughtCanvasExample> = emptyList(),
) {
    companion object { val Empty = CanvasTeachingProfile() }
}

object CanvasTeachingProfileCodec {
    fun encode(profile: CanvasTeachingProfile): String = profile.examples.joinToString("\n") { example ->
        listOf(
            Base64.getUrlEncoder().withoutPadding().encodeToString(example.label.toByteArray()),
            example.shapeType?.name.orEmpty(),
            example.features.joinToString(","),
            example.confirmations,
            example.updatedAt,
        ).joinToString("|")
    }

    fun decode(source: String): CanvasTeachingProfile {
        if (source.isBlank()) return CanvasTeachingProfile.Empty
        return CanvasTeachingProfile(
            source.lineSequence().mapNotNull { line ->
                runCatching {
                    val fields = line.split('|')
                    TaughtCanvasExample(
                        label = String(Base64.getUrlDecoder().decode(fields[0])),
                        shapeType = fields[1].takeIf(String::isNotBlank)?.let(SmartBoardShapeType::valueOf),
                        features = fields[2].split(',').map(String::toFloat),
                        confirmations = fields[3].toInt().coerceIn(1, 10_000),
                        updatedAt = fields[4].toLong().coerceAtLeast(0L),
                    )
                }.getOrNull()
            }.take(256).toList(),
        )
    }
}

class SmartBoardCanvasIntelligenceEngine(
    private val shapeRecognizer: AutoShapeRecognizer = DeterministicAutoShapeRecognizer(),
) {
    fun analyze(
        strokes: List<StrokeElement>,
        subject: SmartBoardSubject,
        teaching: CanvasTeachingProfile = CanvasTeachingProfile.Empty,
        now: Long,
    ): SmartBoardCanvasIntelligenceSnapshot {
        val visible = strokes.filterNot(StrokeElement::hidden).takeLast(96)
        if (visible.isEmpty()) return SmartBoardCanvasIntelligenceSnapshot.Empty
        val grouped = groupStrokes(visible)
        val groups = mutableListOf<IntentAwareStrokeGroup>()
        val hypotheses = mutableListOf<CanvasObjectHypothesis>()

        grouped.forEach { related ->
            val rawCandidates = shapeRecognizer.recognize(related, forced = true)
            val incomplete = incompleteCandidates(related)
            val ranked = (rawCandidates.map(::asHypothesis) + incomplete)
                .map { personalize(it, featureVector(related), teaching) }
                .sortedByDescending(CanvasObjectHypothesis::confidence)
                .distinctBy { it.shapeType to it.label.lowercase() }
                .take(5)
            val intent = inferIntent(ranked, related, subject)
            val confidence = when {
                ranked.isNotEmpty() -> ranked.first().confidence
                intent == CanvasStrokeIntent.FORMULA -> .69f
                else -> .58f
            }
            groups += IntentAwareStrokeGroup(
                id = "group-${related.joinToString("-") { it.id }.hashCode()}",
                strokeIds = related.map(StrokeElement::id),
                intent = intent,
                confidence = confidence,
                bounds = unionBounds(related.map(StrokeElement::bounds)),
                rationale = intentRationale(intent, ranked, related),
            )
            hypotheses += ranked
        }

        val uncertainty = buildList {
            groups.forEach { group ->
                val ranked = hypotheses.filter { hypothesis ->
                    hypothesis.sourceStrokeIds.any(group.strokeIds::contains)
                }.sortedByDescending(CanvasObjectHypothesis::confidence)
                val ambiguous = ranked.size >= 2 && ranked[0].confidence - ranked[1].confidence < .20f
                if (ranked.isNotEmpty() && (ambiguous || ranked[0].confidence < .78f)) {
                    group.strokeIds.forEach { strokeId ->
                        visible.firstOrNull { it.id == strokeId }?.let { stroke ->
                            add(
                                CanvasUncertaintyRegion(
                                    id = "uncertain-${group.id}-$strokeId",
                                    strokeIds = listOf(strokeId),
                                    bounds = stroke.bounds.expand(5f),
                                    confidence = ranked[0].confidence,
                                    alternatives = ranked.take(4),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return SmartBoardCanvasIntelligenceSnapshot(groups, hypotheses, uncertainty, now)
    }

    fun teach(
        profile: CanvasTeachingProfile,
        strokes: List<StrokeElement>,
        label: String,
        shapeType: SmartBoardShapeType?,
        now: Long,
    ): CanvasTeachingProfile {
        require(label.isNotBlank())
        val features = featureVector(strokes)
        val nearest = profile.examples.firstOrNull {
            it.label.equals(label.trim(), true) && featureDistance(it.features, features) < .08f
        }
        val replacement = TaughtCanvasExample(
            label = label.trim().take(80),
            shapeType = shapeType,
            features = features,
            confirmations = ((nearest?.confirmations ?: 0) + 1).coerceAtMost(10_000),
            updatedAt = now,
        )
        return CanvasTeachingProfile(
            (profile.examples.filterNot { it === nearest } + replacement)
                .sortedByDescending(TaughtCanvasExample::updatedAt)
                .take(256),
        )
    }

    private fun groupStrokes(strokes: List<StrokeElement>): List<List<StrokeElement>> {
        val remaining = strokes.toMutableList()
        val groups = mutableListOf<List<StrokeElement>>()
        while (remaining.isNotEmpty()) {
            val group = mutableListOf(remaining.removeAt(0))
            var changed = true
            while (changed) {
                changed = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (group.any { belongsTogether(it, candidate) }) {
                        group += candidate
                        iterator.remove()
                        changed = true
                    }
                }
            }
            groups += group.sortedBy(StrokeElement::createdAt)
        }
        return groups
    }

    private fun belongsTogether(first: StrokeElement, second: StrokeElement): Boolean {
        val timeGap = abs(first.createdAt - second.createdAt)
        if (timeGap > 6_500) return false
        val combinedScale = max(
            20f,
            max(max(first.bounds.width, first.bounds.height), max(second.bounds.width, second.bounds.height)),
        )
        return boundsDistance(first.bounds, second.bounds) <= combinedScale * .72f
    }

    private fun inferIntent(
        hypotheses: List<CanvasObjectHypothesis>,
        strokes: List<StrokeElement>,
        subject: SmartBoardSubject,
    ): CanvasStrokeIntent {
        val types = hypotheses.mapNotNull(CanvasObjectHypothesis::shapeType).toSet()
        return when {
            types.any { it in GraphTypes } -> CanvasStrokeIntent.GRAPH
            types.any { it in DiagramTypes } -> CanvasStrokeIntent.DIAGRAM
            subject == SmartBoardSubject.MATHEMATICS &&
                types.isNotEmpty() && types.all { it in FormulaPrimitiveTypes } -> CanvasStrokeIntent.FORMULA
            hypotheses.firstOrNull()?.confidence?.let { it >= .64f } == true -> CanvasStrokeIntent.OBJECT
            subject == SmartBoardSubject.MATHEMATICS || subject == SmartBoardSubject.PHYSICS -> CanvasStrokeIntent.FORMULA
            strokes.size <= 12 -> CanvasStrokeIntent.WORD
            else -> CanvasStrokeIntent.UNKNOWN
        }
    }

    private fun incompleteCandidates(strokes: List<StrokeElement>): List<CanvasObjectHypothesis> {
        val out = mutableListOf<CanvasObjectHypothesis>()
        val ids = strokes.map(StrokeElement::id)
        if (strokes.size == 1) {
            val points = strokes.single().points.map { it.position }
            val box = strokes.single().bounds
            val diagonal = hypot(box.width.toDouble(), box.height.toDouble()).toFloat().coerceAtLeast(1f)
            val closure = points.first().distanceTo(points.last()) / diagonal
            val travelled = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b).toDouble() }.toFloat()
            if (closure in 0.10f..0.65f && travelled > diagonal * 1.65f && box.width > 15f && box.height > 15f) {
                val aspect = box.width / box.height.coerceAtLeast(1f)
                val circleConfidence = (.81f - abs(1f - aspect) * .28f - closure * .22f).coerceIn(.54f, .83f)
                out += CanvasObjectHypothesis(
                    "circle", SmartBoardShapeType.CIRCLE, circleConfidence, ids,
                    ellipsePoints(box), true, "Open curved stroke is close to a circular closure",
                )
                out += CanvasObjectHypothesis(
                    "ellipse", SmartBoardShapeType.ELLIPSE, (circleConfidence - .05f + abs(1f - aspect) * .12f).coerceIn(.5f, .81f),
                    ids, ellipsePoints(box), true, "Open curved stroke can be completed as an ellipse",
                )
            }
        }
        val straightCount = strokes.count(::isMostlyStraight)
        val allBounds = unionBounds(strokes.map(StrokeElement::bounds))
        if (strokes.size in 5..11 && straightCount >= strokes.size - 1 && allBounds.width > 25f && allBounds.height > 25f) {
            out += CanvasObjectHypothesis(
                "cube", SmartBoardShapeType.CUBE, .72f, ids, cubePoints(allBounds), true,
                "Several connected straight edges suggest an unfinished cube",
            )
            out += CanvasObjectHypothesis(
                "cuboid", SmartBoardShapeType.CUBOID, .64f, ids, cubePoints(allBounds), true,
                "The same partial wireframe can represent a cuboid",
            )
        }
        if (strokes.size in 2..6 && straightCount >= 2) {
            val segments = strokes.map { it.points.first().position to it.points.last().position }
            val horizontal = segments.any { (a, b) -> abs(b.y - a.y) <= abs(b.x - a.x) * .18f }
            val vertical = segments.any { (a, b) -> abs(b.x - a.x) <= abs(b.y - a.y) * .18f }
            if (horizontal && vertical) {
                out += CanvasObjectHypothesis(
                    "coordinate graph", SmartBoardShapeType.COORDINATE_AXES, .70f, ids,
                    coordinateAxesPoints(allBounds.expand(12f)), true,
                    "Perpendicular construction strokes suggest unfinished graph axes",
                )
            }
        }
        if (strokes.size == 1) {
            val points = strokes.single().points.map { it.position }
            val directionChanges = points.zipWithNext()
                .map { (a, b) -> (b.y - a.y).compareTo(0f) }
                .filterNot { it == 0 }
                .zipWithNext().count { (a, b) -> a != b }
            if (directionChanges >= 4 && allBounds.width > allBounds.height * 1.5f) {
                out += CanvasObjectHypothesis(
                    "resistor", SmartBoardShapeType.RESISTOR, .68f, ids,
                    resistorPoints(allBounds.expand(8f)), true,
                    "Alternating zigzag segments suggest an unfinished circuit resistor",
                )
                out += CanvasObjectHypothesis(
                    "circuit wire", SmartBoardShapeType.CIRCUIT_WIRE, .58f, ids,
                    listOf(
                        SmartBoardPoint(allBounds.left - 12f, allBounds.center.y),
                        SmartBoardPoint(allBounds.right + 12f, allBounds.center.y),
                    ),
                    true,
                    "The component can be completed with circuit leads",
                )
            }
        }
        return out
    }

    private fun personalize(
        hypothesis: CanvasObjectHypothesis,
        features: List<Float>,
        teaching: CanvasTeachingProfile,
    ): CanvasObjectHypothesis {
        val similarity = teaching.examples
            .filter {
                it.label.equals(hypothesis.label, true) ||
                    (hypothesis.shapeType != null && it.shapeType == hypothesis.shapeType)
            }
            .maxOfOrNull { (1f - featureDistance(it.features, features)).coerceIn(0f, 1f) }
            ?: 0f
        return hypothesis.copy(confidence = (hypothesis.confidence + similarity * .16f).coerceAtMost(.98f))
    }

    companion object {
        private val GraphTypes = setOf(
            SmartBoardShapeType.COORDINATE_AXES, SmartBoardShapeType.GRAPH_GRID, SmartBoardShapeType.NUMBER_LINE,
        )
        private val DiagramTypes = setOf(
            SmartBoardShapeType.CUBE, SmartBoardShapeType.CUBOID, SmartBoardShapeType.CYLINDER,
            SmartBoardShapeType.CONE, SmartBoardShapeType.SPHERE, SmartBoardShapeType.PYRAMID,
            SmartBoardShapeType.RESISTOR, SmartBoardShapeType.CIRCUIT_WIRE, SmartBoardShapeType.NODE,
            SmartBoardShapeType.LAB_CONTAINER,
        )
        private val FormulaPrimitiveTypes = setOf(
            SmartBoardShapeType.LINE, SmartBoardShapeType.HORIZONTAL_LINE, SmartBoardShapeType.VERTICAL_LINE,
            SmartBoardShapeType.DIAGONAL_LINE, SmartBoardShapeType.LINE_SEGMENT, SmartBoardShapeType.ARC,
            SmartBoardShapeType.CURVE, SmartBoardShapeType.BRACKET, SmartBoardShapeType.BRACE,
        )
    }
}

private fun asHypothesis(candidate: AutoShapeCandidate) = CanvasObjectHypothesis(
    label = candidate.type.name.lowercase().replace('_', ' '),
    shapeType = candidate.type,
    confidence = candidate.confidence,
    sourceStrokeIds = candidate.sourceStrokeIds,
    completionPoints = candidate.points,
    incomplete = false,
    rationale = candidate.rationale,
)

private fun intentRationale(
    intent: CanvasStrokeIntent,
    ranked: List<CanvasObjectHypothesis>,
    strokes: List<StrokeElement>,
) = ranked.firstOrNull()?.let { "${it.label}: ${it.rationale}" }
    ?: "${strokes.size} spatially and temporally related stroke(s) classified as ${intent.name.lowercase()}"

private fun featureVector(strokes: List<StrokeElement>): List<Float> {
    if (strokes.isEmpty()) return List(8) { 0f }
    val box = unionBounds(strokes.map(StrokeElement::bounds))
    val diagonal = hypot(box.width.toDouble(), box.height.toDouble()).toFloat().coerceAtLeast(1f)
    val points = strokes.flatMap { it.points }.map { it.position }
    val totalLength = strokes.sumOf { stroke ->
        stroke.points.map { it.position }.zipWithNext().sumOf { (a, b) -> a.distanceTo(b).toDouble() }
    }.toFloat()
    val closure = strokes.map { it.points.first().position.distanceTo(it.points.last().position) / diagonal }.average().toFloat()
    val straight = strokes.count(::isMostlyStraight).toFloat() / strokes.size
    return listOf(
        (strokes.size / 16f).coerceAtMost(1f),
        (box.width / diagonal).coerceIn(0f, 1f),
        (box.height / diagonal).coerceIn(0f, 1f),
        (totalLength / (diagonal * 8f)).coerceIn(0f, 1f),
        closure.coerceIn(0f, 1f),
        straight,
        (points.size / 256f).coerceAtMost(1f),
        strokes.map { it.points.map { point -> point.pressure }.average() }.average().toFloat().coerceIn(0f, 1.5f) / 1.5f,
    )
}

private fun featureDistance(a: List<Float>, b: List<Float>): Float {
    if (a.size != b.size || a.isEmpty()) return 1f
    return sqrt(a.indices.sumOf { index -> ((a[index] - b[index]) * (a[index] - b[index])).toDouble() } / a.size).toFloat()
        .coerceIn(0f, 1f)
}

private fun boundsDistance(a: SmartBoardBounds, b: SmartBoardBounds): Float {
    val dx = max(0f, max(a.left - b.right, b.left - a.right))
    val dy = max(0f, max(a.top - b.bottom, b.top - a.bottom))
    return hypot(dx.toDouble(), dy.toDouble()).toFloat()
}

private fun unionBounds(values: List<SmartBoardBounds>): SmartBoardBounds {
    if (values.isEmpty()) return SmartBoardBounds.Empty
    return SmartBoardBounds(
        values.minOf(SmartBoardBounds::left),
        values.minOf(SmartBoardBounds::top),
        values.maxOf(SmartBoardBounds::right),
        values.maxOf(SmartBoardBounds::bottom),
    )
}

private fun SmartBoardPoint.distanceTo(other: SmartBoardPoint) =
    hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()

private fun isMostlyStraight(stroke: StrokeElement): Boolean {
    val points = stroke.points.map { it.position }
    val direct = points.first().distanceTo(points.last())
    val length = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b).toDouble() }.toFloat()
    return length > 0f && direct / length > .90f
}

private fun ellipsePoints(box: SmartBoardBounds, count: Int = 48) = (0..count).map { index ->
    val angle = 2.0 * PI * index / count
    SmartBoardPoint(
        box.center.x + cos(angle).toFloat() * box.width / 2f,
        box.center.y + sin(angle).toFloat() * box.height / 2f,
    )
}

private fun cubePoints(box: SmartBoardBounds): List<SmartBoardPoint> {
    val insetX = box.width * .24f
    val insetY = box.height * .22f
    val a = SmartBoardPoint(box.left, box.top + insetY)
    val b = SmartBoardPoint(box.right - insetX, box.top + insetY)
    val c = SmartBoardPoint(box.right - insetX, box.bottom)
    val d = SmartBoardPoint(box.left, box.bottom)
    val e = SmartBoardPoint(box.left + insetX, box.top)
    val f = SmartBoardPoint(box.right, box.top)
    val g = SmartBoardPoint(box.right, box.bottom - insetY)
    val h = SmartBoardPoint(box.left + insetX, box.bottom - insetY)
    return listOf(a, b, b, c, c, d, d, a, e, f, f, g, g, h, h, e, a, e, b, f, c, g, d, h)
}

private fun coordinateAxesPoints(box: SmartBoardBounds) = listOf(
    SmartBoardPoint(box.left, box.center.y),
    SmartBoardPoint(box.right, box.center.y),
    SmartBoardPoint(box.center.x, box.top),
    SmartBoardPoint(box.center.x, box.bottom),
)

private fun resistorPoints(box: SmartBoardBounds): List<SmartBoardPoint> {
    val left = box.left
    val right = box.right
    val centreY = box.center.y
    val step = box.width / 8f
    return buildList {
        add(SmartBoardPoint(left, centreY))
        for (index in 1..7) {
            add(
                SmartBoardPoint(
                    left + step * index,
                    centreY + if (index % 2 == 0) box.height * .32f else -box.height * .32f,
                ),
            )
        }
        add(SmartBoardPoint(right, centreY))
    }
}
