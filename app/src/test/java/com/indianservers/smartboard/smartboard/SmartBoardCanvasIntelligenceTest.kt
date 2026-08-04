package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.CanvasStrokeIntent
import com.indianservers.smartboard.smartboard.recognition.CanvasTeachingProfileCodec
import com.indianservers.smartboard.smartboard.recognition.SmartBoardCanvasIntelligenceEngine
import com.indianservers.smartboard.smartboard.shapes.AutoShapeCandidate
import com.indianservers.smartboard.smartboard.shapes.AutoShapeRecognizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardCanvasIntelligenceTest {
    @Test
    fun `intent grouping separates distant formulas while preserving related strokes`() {
        val engine = SmartBoardCanvasIntelligenceEngine(emptyRecognizer)
        val strokes = listOf(
            stroke("a", 1_000, p(0f, 0f), p(20f, 20f)),
            stroke("b", 1_100, p(18f, 0f), p(18f, 25f)),
            stroke("c", 1_200, p(300f, 0f), p(330f, 20f)),
        )

        val snapshot = engine.analyze(strokes, SmartBoardSubject.MATHEMATICS, now = 2_000)

        assertEquals(2, snapshot.groups.size)
        assertTrue(snapshot.groups.all { it.intent == CanvasStrokeIntent.FORMULA })
        assertEquals(setOf("a", "b"), snapshot.groups.first { it.strokeIds.size == 2 }.strokeIds.toSet())
    }

    @Test
    fun `partial circle produces ranked ghost completion and stroke uncertainty`() {
        val points = (0..40).map { index ->
            val angle = PI * 1.72 * index / 40
            p(60f + cos(angle).toFloat() * 42f, 60f + sin(angle).toFloat() * 42f)
        }
        val snapshot = SmartBoardCanvasIntelligenceEngine(emptyRecognizer).analyze(
            listOf(stroke("arc", 1_000, *points.toTypedArray())),
            SmartBoardSubject.MATHEMATICS,
            now = 2_000,
        )

        assertTrue(snapshot.hypotheses.any { it.shapeType == SmartBoardShapeType.CIRCLE && it.incomplete })
        assertTrue(snapshot.hypotheses.any { it.shapeType == SmartBoardShapeType.ELLIPSE && it.incomplete })
        assertNotNull(snapshot.ghostCompletion)
        assertTrue(snapshot.uncertaintyRegions.any { "arc" in it.strokeIds })
    }

    @Test
    fun `partial cube graph and circuit receive optional completions`() {
        val cube = listOf(
            stroke("c1", 1_000, p(0f, 20f), p(60f, 20f)),
            stroke("c2", 1_050, p(60f, 20f), p(60f, 80f)),
            stroke("c3", 1_100, p(60f, 80f), p(0f, 80f)),
            stroke("c4", 1_150, p(0f, 80f), p(0f, 20f)),
            stroke("c5", 1_200, p(0f, 20f), p(20f, 0f)),
        )
        val graph = listOf(
            stroke("gx", 8_000, p(200f, 50f), p(300f, 50f)),
            stroke("gy", 8_050, p(250f, 0f), p(250f, 100f)),
        )
        val circuit = listOf(
            stroke(
                "resistor",
                15_000,
                p(400f, 50f), p(410f, 35f), p(420f, 65f), p(430f, 35f),
                p(440f, 65f), p(450f, 35f), p(460f, 50f),
            ),
        )

        val snapshot = SmartBoardCanvasIntelligenceEngine(emptyRecognizer).analyze(
            cube + graph + circuit,
            SmartBoardSubject.PHYSICS,
            now = 16_000,
        )

        assertTrue(snapshot.hypotheses.any { it.shapeType == SmartBoardShapeType.CUBE && it.incomplete })
        assertTrue(snapshot.hypotheses.any { it.shapeType == SmartBoardShapeType.COORDINATE_AXES && it.incomplete })
        assertTrue(snapshot.hypotheses.any { it.shapeType == SmartBoardShapeType.RESISTOR && it.incomplete })
    }

    @Test
    fun `teaching profile persists and promotes matching object hypothesis`() {
        val candidateRecognizer = object : AutoShapeRecognizer {
            override fun recognize(strokes: List<StrokeElement>, forced: Boolean) = listOf(
                AutoShapeCandidate(
                    SmartBoardShapeType.RECTANGLE,
                    listOf(p(0f, 0f), p(30f, 0f), p(30f, 20f), p(0f, 20f), p(0f, 0f)),
                    SmartBoardBounds(0f, 0f, 30f, 20f),
                    .62f,
                    "rectangle fit",
                    strokes.map(StrokeElement::id),
                ),
                AutoShapeCandidate(
                    SmartBoardShapeType.CIRCLE,
                    listOf(p(0f, 0f), p(30f, 20f)),
                    SmartBoardBounds(0f, 0f, 30f, 20f),
                    .60f,
                    "circle fit",
                    strokes.map(StrokeElement::id),
                ),
            )
        }
        val engine = SmartBoardCanvasIntelligenceEngine(candidateRecognizer)
        val input = listOf(stroke("sample", 1_000, p(0f, 0f), p(30f, 20f)))
        val taught = engine.teach(
            com.indianservers.smartboard.smartboard.recognition.CanvasTeachingProfile.Empty,
            input,
            "circle",
            SmartBoardShapeType.CIRCLE,
            2_000,
        )
        val restored = CanvasTeachingProfileCodec.decode(CanvasTeachingProfileCodec.encode(taught))

        val snapshot = engine.analyze(input, SmartBoardSubject.MATHEMATICS, restored, 3_000)

        assertEquals("circle", snapshot.hypotheses.first().label)
        assertEquals(1, restored.examples.size)
        assertTrue(snapshot.hypotheses.first().confidence > .70f)
    }

    private val emptyRecognizer = object : AutoShapeRecognizer {
        override fun recognize(strokes: List<StrokeElement>, forced: Boolean) = emptyList<AutoShapeCandidate>()
    }

    private fun p(x: Float, y: Float) = SmartBoardPoint(x, y)

    private fun stroke(id: String, createdAt: Long, vararg points: SmartBoardPoint): StrokeElement {
        val samples = points.mapIndexed { index, point ->
            StrokePoint(point.x, point.y, .8f + index % 2 * .1f, createdAt + index * 16)
        }
        return StrokeElement(
            id = id,
            points = samples,
            tool = StrokeTool.PEN,
            width = 3f,
            opacity = 1f,
            argbColor = 0xff000000,
            bounds = SmartBoardBounds.from(points.toList(), 1f),
            createdAt = createdAt,
        )
    }
}
