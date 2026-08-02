package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSemanticExpressionBuilder
import com.indianservers.smartboard.smartboard.tools.SemanticToolOperation
import com.indianservers.smartboard.smartboard.tools.SmartBoardClassroomToolFactory
import com.indianservers.smartboard.smartboard.tools.SmartBoardEditableReconstructionEngine
import com.indianservers.smartboard.smartboard.tools.SmartBoardReconstructionKind
import com.indianservers.smartboard.smartboard.tools.SmartBoardSemanticToolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardFinalToolsTest {
    @Test
    fun semanticSubexpressionOperationRebuildsAnEditableTree() {
        val tree = SmartBoardSemanticExpressionBuilder.build("(x+1)+(2+3)")
        val numericSum = SmartBoardSemanticToolEngine.targets(tree)
            .first { it.expression.contains("2") && it.expression.contains("3") && it.depth > 0 }

        val result = SmartBoardSemanticToolEngine.apply(tree, numericSum.nodeId, SemanticToolOperation.SIMPLIFY).getOrThrow()

        assertTrue(result.expressionAfter.contains("5"))
        assertTrue(result.tree.parserVerified)
        assertFalse(SmartBoardSemanticToolEngine.targets(result.tree).isEmpty())
    }

    @Test
    fun matrixRecognitionReconstructsAFirstClassEditableTable() {
        val expression = expression("[1,2;3,4]")

        val suggestions = SmartBoardEditableReconstructionEngine.suggestions(expression)
        val table = SmartBoardEditableReconstructionEngine.tableFrom(expression, "table", 3L).getOrThrow()

        assertTrue(suggestions.any { it.kind == SmartBoardReconstructionKind.TABLE })
        assertEquals(listOf("1", "2"), table.rows.first())
        assertEquals(listOf("3", "4"), table.rows.last())
        assertEquals(listOf(expression.id), table.sourceElementIds)
    }

    @Test
    fun tablesRoundTripInCurrentBoardSchema() {
        val table = SmartBoardClassroomToolFactory.blankTable(SmartBoardBounds(10f, 20f, 410f, 260f), 2L, 4, 5)
        val original = SmartBoardDocument.new("board", 1L).copy(elements = listOf(table))

        val decoded = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(original))

        assertFalse(decoded.recovered)
        assertEquals(SmartBoardDocument.CurrentSchemaVersion, decoded.document?.schemaVersion)
        assertEquals(table, decoded.document?.elements?.single() as TableElement)
    }

    @Test
    fun directClassroomShapesAreValidWithoutRecognitionSourceStrokes() {
        val bounds = SmartBoardBounds(20f, 30f, 220f, 170f)
        listOf(
            SmartBoardShapeType.CIRCLE,
            SmartBoardShapeType.LINE_SEGMENT,
            SmartBoardShapeType.ARROW,
            SmartBoardShapeType.RIGHT_ANGLE_MARKER,
            SmartBoardShapeType.COORDINATE_AXES,
        ).forEach { type ->
            val shape = SmartBoardClassroomToolFactory.shape(type, bounds, 2L)
            assertEquals(type, shape.shapeType)
            assertTrue(shape.sourceStrokeIds.isEmpty())
            assertTrue(shape.points.size >= 2)
        }
    }

    private fun expression(source: String): MathExpressionElement = MathExpressionElement(
        id = "math",
        rawLatex = source,
        correctedLatex = null,
        normalizedExpression = source,
        sourceStrokeIds = listOf("stroke"),
        recognitionConfidence = .98f,
        bounds = SmartBoardBounds(10f, 10f, 220f, 90f),
        createdAt = 2L,
        semanticTree = SmartBoardSemanticExpressionBuilder.build(source, source, listOf("stroke"), .98f),
    )
}
