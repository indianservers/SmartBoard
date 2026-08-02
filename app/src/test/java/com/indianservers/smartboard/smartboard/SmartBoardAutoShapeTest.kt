package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.domain.InsertRecognizedExpressionCommand
import com.indianservers.smartboard.smartboard.domain.MoveElementsCommand
import com.indianservers.smartboard.smartboard.domain.SmartBoardCommandHistory
import com.indianservers.smartboard.smartboard.domain.duplicateElements
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import com.indianservers.smartboard.smartboard.shapes.SmartBoardStrokeGrouper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardAutoShapeTest {
    private val recognizer = DeterministicAutoShapeRecognizer()

    @Test
    fun `fits horizontal line without replacing ink`() {
        val line = stroke("line", listOf(p(0f, 20f), p(30f, 20.5f), p(80f, 20f)))
        val candidate = recognizer.recognize(listOf(line)).first()
        assertEquals(SmartBoardShapeType.HORIZONTAL_LINE, candidate.type)
        assertTrue(candidate.confidence >= .68f)
        assertEquals(listOf(line.id), candidate.sourceStrokeIds)
    }

    @Test
    fun `fits circle rectangle and triangle as ranked closed shapes`() {
        val circlePoints = (0..64).map { index ->
            val angle = 2.0 * PI * index / 64
            p(80f + cos(angle).toFloat() * 40f, 80f + sin(angle).toFloat() * 40f)
        }
        assertEquals(SmartBoardShapeType.CIRCLE, recognizer.recognize(listOf(stroke("circle", circlePoints))).first().type)

        val rectangle = stroke("rectangle", listOf(p(0f, 0f), p(100f, 0f), p(100f, 60f), p(0f, 60f), p(0f, 0f)))
        assertEquals(SmartBoardShapeType.RECTANGLE, recognizer.recognize(listOf(rectangle)).first().type)

        val triangle = stroke("triangle", listOf(p(0f, 80f), p(45f, 0f), p(90f, 80f), p(0f, 80f)))
        assertTrue(recognizer.recognize(listOf(triangle)).first().type in setOf(
            SmartBoardShapeType.TRIANGLE,
            SmartBoardShapeType.EQUILATERAL_TRIANGLE,
        ))
    }

    @Test
    fun `fits multi stroke arrow and coordinate axes`() {
        val arrow = listOf(
            stroke("shaft", listOf(p(0f, 30f), p(100f, 30f)), 1),
            stroke("head-a", listOf(p(100f, 30f), p(78f, 16f)), 2),
            stroke("head-b", listOf(p(100f, 30f), p(78f, 44f)), 3),
        )
        assertEquals(SmartBoardShapeType.ARROW, recognizer.recognize(arrow).first().type)

        val axes = listOf(
            stroke("x", listOf(p(0f, 50f), p(100f, 50f)), 1),
            stroke("y", listOf(p(50f, 0f), p(50f, 100f)), 2),
        )
        assertEquals(SmartBoardShapeType.COORDINATE_AXES, recognizer.recognize(axes).first().type)
    }

    @Test
    fun `fits polygon and rejects low confidence handwriting like ink`() {
        val pentagon = stroke(
            "pentagon",
            listOf(p(50f, 0f), p(98f, 35f), p(80f, 92f), p(20f, 92f), p(2f, 35f), p(50f, 0f)),
        )
        assertEquals(SmartBoardShapeType.PENTAGON, recognizer.recognize(listOf(pentagon)).first().type)

        val handwritingLike = stroke(
            "word",
            listOf(p(0f, 30f), p(8f, 5f), p(16f, 30f), p(24f, 5f), p(32f, 30f), p(40f, 8f), p(48f, 30f)),
        )
        assertTrue(recognizer.recognize(listOf(handwritingLike)).isEmpty())
    }

    @Test
    fun `ambiguous handwriting curves require an explicit shape request`() {
        val arcLikeInk = stroke(
            "arc-like-ink",
            (0..12).map { index ->
                val x = index * 8f
                p(x, 45f - sin(index / 12.0 * PI).toFloat() * 32f)
            },
        )

        assertTrue(recognizer.recognize(listOf(arcLikeInk)).isEmpty())
        assertTrue(
            recognizer.recognize(listOf(arcLikeInk), forced = true)
                .any { it.type in setOf(SmartBoardShapeType.ARC, SmartBoardShapeType.CURVE) },
        )
    }

    @Test
    fun `recent grouping uses time and spatial proximity`() {
        val nearby = stroke("nearby", listOf(p(0f, 0f), p(40f, 0f)), 1_500)
        val newest = stroke("newest", listOf(p(40f, 0f), p(40f, 40f)), 2_000)
        val old = stroke("old", listOf(p(0f, 0f), p(0f, 40f)), 1)
        val far = stroke("far", listOf(p(900f, 900f), p(940f, 900f)), 1_950)
        val grouped = SmartBoardStrokeGrouper.recentRelated(listOf(old, nearby, far, newest), newest)
        assertEquals(setOf("nearby", "newest"), grouped.mapTo(linkedSetOf(), StrokeElement::id))
    }

    @Test
    fun `recent grouping keeps moderately paced mathematical handwriting together`() {
        val first = stroke("first", listOf(p(0f, 20f), p(24f, 0f), p(40f, 20f)), 1_000)
        val newest = stroke("newest", listOf(p(44f, 10f), p(66f, 10f)), 2_400)

        val grouped = SmartBoardStrokeGrouper.recentRelated(listOf(first, newest), newest)

        assertEquals(setOf("first", "newest"), grouped.mapTo(linkedSetOf(), StrokeElement::id))
    }

    @Test
    fun `shape round trips through schema six and schema five boards migrate`() {
        val source = stroke("source", listOf(p(0f, 0f), p(80f, 0f)))
        val shape = ShapeElement(
            "shape", SmartBoardShapeType.LINE, listOf(SmartBoardPoint(0f, 0f), SmartBoardPoint(80f, 0f)),
            listOf(source.id), .91f, 3f, 0xFFFFFFFF, bounds = SmartBoardBounds(0f, 0f, 80f, 0f), createdAt = 2,
        )
        val document = SmartBoardDocument.new("board", 1).copy(elements = listOf(source, shape))
        val decoded = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(document)).document
        assertEquals(shape, decoded?.elements?.filterIsInstance<ShapeElement>()?.single())

        val legacy = SmartBoardDocumentCodec.encode(SmartBoardDocument.new("legacy", 1))
            .replaceFirst("SB|${SmartBoardDocument.CurrentSchemaVersion}|", "SB|5|")
        val migrated = SmartBoardDocumentCodec.decode(legacy)
        assertEquals(5, migrated.sourceSchemaVersion)
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, migrated.document?.schemaVersion)
    }

    @Test
    fun `accept conversion is one undoable command with recoverable source ink`() {
        val source = stroke("source", listOf(p(0f, 0f), p(80f, 0f)))
        val shape = ShapeElement(
            "shape", SmartBoardShapeType.LINE, listOf(SmartBoardPoint(0f, 0f), SmartBoardPoint(80f, 0f)),
            listOf(source.id), .9f, 3f, 0xFFFFFFFF, bounds = SmartBoardBounds(0f, 0f, 80f, 0f), createdAt = 2,
        )
        val relationship = SmartBoardRelationship(
            "relation", SmartBoardRelationshipType.RECOGNIZED_FROM, listOf(shape.id, source.id), 2,
        )
        val command = InsertRecognizedExpressionCommand(shape, relationship, mapOf(source.id to false), true)
        val history = SmartBoardCommandHistory()
        val original = SmartBoardDocument.new("board", 1).copy(elements = listOf(source))
        val converted = history.execute(original, command, 2)
        assertTrue(converted.elements.filterIsInstance<StrokeElement>().single().hidden)
        assertNotNull(converted.elements.filterIsInstance<ShapeElement>().singleOrNull())
        val undone = history.undo(converted, 3)
        assertFalse(undone.elements.filterIsInstance<StrokeElement>().single().hidden)
        assertTrue(undone.elements.none { it is ShapeElement })
        val redone = history.redo(undone, 4)
        assertTrue(redone.elements.filterIsInstance<StrokeElement>().single().hidden)
        assertNotNull(redone.elements.filterIsInstance<ShapeElement>().singleOrNull())
    }

    @Test
    fun `structured shape supports existing move and duplicate operations`() {
        val source = stroke("source", listOf(p(0f, 0f), p(80f, 0f)))
        val shape = ShapeElement(
            "shape", SmartBoardShapeType.LINE, listOf(SmartBoardPoint(0f, 0f), SmartBoardPoint(80f, 0f)),
            listOf(source.id), .9f, 3f, 0xFFFFFFFF, bounds = SmartBoardBounds(0f, 0f, 80f, 0f), createdAt = 2,
        )
        val original = SmartBoardDocument.new("board", 1).copy(elements = listOf(source, shape))
        val moved = MoveElementsCommand(setOf(shape.id), SmartBoardPoint(10f, 20f)).apply(original, 3)
            .elements.filterIsInstance<ShapeElement>().single()
        assertEquals(SmartBoardPoint(10f, 20f), moved.points.first())
        val copy = duplicateElements(original, setOf(shape.id), { "copy" }, 4).single() as ShapeElement
        assertEquals("copy", copy.id)
        assertEquals(SmartBoardPoint(24f, 24f), copy.points.first())
        assertEquals(shape.sourceStrokeIds, copy.sourceStrokeIds)
    }

    private fun p(x: Float, y: Float) = SmartBoardPoint(x, y)

    private fun stroke(id: String, points: List<SmartBoardPoint>, createdAt: Long = 1) = StrokeElement(
        id = id,
        points = points.mapIndexed { index, point -> StrokePoint(point.x, point.y, 1f, createdAt + index) },
        tool = StrokeTool.PEN,
        width = 3f,
        opacity = 1f,
        argbColor = 0xFFFFFFFF,
        bounds = SmartBoardBounds.from(points, 1.5f),
        createdAt = createdAt,
    )
}
