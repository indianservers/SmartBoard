package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.canvas.SmartBoardStrokeGeometry
import com.indianservers.smartboard.smartboard.domain.AddElementCommand
import com.indianservers.smartboard.smartboard.domain.GroupCommand
import com.indianservers.smartboard.smartboard.domain.InsertRecognizedExpressionCommand
import com.indianservers.smartboard.smartboard.domain.MoveElementsCommand
import com.indianservers.smartboard.smartboard.domain.SmartBoardCommandHistory
import com.indianservers.smartboard.smartboard.domain.groupRelationship
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardHistoryPersistenceTest {
    @Test
    fun commandHistorySupportsAddMoveGroupUndoAndRedo() {
        val history = SmartBoardCommandHistory()
        val stroke = stroke("s1")
        var document = SmartBoardDocument.new("board", 1L)
        document = history.execute(document, AddElementCommand(stroke), 2L)
        document = history.execute(document, MoveElementsCommand(setOf("s1"), SmartBoardPoint(10f, 5f)), 3L)
        document = history.execute(document, GroupCommand(groupRelationship(setOf("s1"), "g1", 4L)), 4L)
        assertEquals(1, document.relationships.size)
        assertEquals(10f, (document.elements.single() as StrokeElement).points.first().x, 0f)

        document = history.undo(document, 5L)
        document = history.undo(document, 6L)
        assertEquals(0f, (document.elements.single() as StrokeElement).points.first().x, 0f)
        document = history.redo(document, 7L)
        assertEquals(10f, (document.elements.single() as StrokeElement).points.first().x, 0f)
    }

    @Test
    fun recognizedInsertionUndoRestoresSourceVisibilityAtomically() {
        val source = stroke("source")
        val expression = MathExpressionElement(
            "math",
            "x",
            null,
            "x",
            listOf("source"),
            .8f,
            SmartBoardBounds(0f, 0f, 100f, 40f),
            2L,
        )
        val relation = SmartBoardRelationship("recognized", SmartBoardRelationshipType.RECOGNIZED_FROM, listOf("math", "source"), 2L)
        val history = SmartBoardCommandHistory()
        var document = SmartBoardDocument.new("board", 1L).copy(elements = listOf(source))
        document = history.execute(document, InsertRecognizedExpressionCommand(expression, relation, mapOf("source" to false), hideSources = true), 2L)
        assertTrue((document.elements.first { it.id == "source" } as StrokeElement).hidden)
        assertNotNull(document.elements.firstOrNull { it.id == "math" })

        document = history.undo(document, 3L)
        assertFalse((document.elements.single() as StrokeElement).hidden)
        assertTrue(document.relationships.isEmpty())
    }

    @Test
    fun serializationRoundTripsAllPhase1ElementTypesAndRelationships() {
        val stroke = stroke("s1")
        val math = MathExpressionElement("m1", "\\frac{1}{2}", "1/2", "1/2", listOf("s1"), .91f, SmartBoardBounds(0f, 0f, 80f, 40f), 3L)
        val document = SmartBoardDocument.new("board", 1L, "Algebra").copy(
            updatedAt = 4L,
            elements = listOf(stroke, math),
            relationships = listOf(SmartBoardRelationship("r1", SmartBoardRelationshipType.RECOGNIZED_FROM, listOf("m1", "s1"), 4L)),
        )

        val result = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(document))
        assertFalse(result.recovered)
        assertEquals(document, result.document)
    }

    @Test
    fun schemaZeroPayloadMigratesToCurrentVersion() {
        val encoded = SmartBoardDocumentCodec.encode(SmartBoardDocument.new("board", 1L))
            .replaceFirst("SB|${SmartBoardDocument.CurrentSchemaVersion}|", "SB|0|")
        val result = SmartBoardDocumentCodec.decode(encoded)

        assertTrue(result.recovered)
        assertEquals(0, result.sourceSchemaVersion)
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, result.document?.schemaVersion)
    }

    private fun stroke(id: String): StrokeElement {
        val points = listOf(StrokePoint(0f, 0f, .8f, 1L), StrokePoint(20f, 20f, 1f, 2L))
        return StrokeElement(id, points, StrokeTool.PEN, 3f, 1f, 0xff112233, SmartBoardStrokeGeometry.bounds(points, 3f), 1L)
    }
}
