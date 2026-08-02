package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.domain.EditMathExpressionCommand
import com.indianservers.smartboard.smartboard.domain.ReplaceElementCommand
import com.indianservers.smartboard.smartboard.domain.SmartBoardCommandHistory
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.recognition.OfflineFormulaIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardOfflineFormulaAndEditingTest {
    @Test
    fun `identifies canonical mathematics and bundled physics formulas offline`() {
        val pythagorean = OfflineFormulaIdentifier.identify("a^2 + b^2 = c^2")
        assertEquals("math.pythagorean", pythagorean?.id)
        assertEquals(SmartBoardSubject.MATHEMATICS, pythagorean?.subject)

        val force = OfflineFormulaIdentifier.identify("F = ma")
        assertEquals("physics-newton-second-law", force?.id)
        assertEquals(SmartBoardSubject.PHYSICS, force?.subject)
        assertTrue(force?.explanation?.contains("bundled") == true)

        assertNull(OfflineFormulaIdentifier.identify("this is not a formula"))
    }

    @Test
    fun `saved board reopens with expression and graph configuration still editable`() {
        val bounds = SmartBoardBounds(10f, 20f, 300f, 120f)
        val expression = MathExpressionElement(
            "math", "x^2", null, "x^2", emptyList(), 1f, bounds, 2,
        )
        val graph = GraphConfigurationElement(
            "graph", SmartBoardGraphKind.EXPLICIT_2D, listOf("x^2"), listOf("math"),
            "graph2d", bounds, 3,
        )
        val saved = SmartBoardDocument.new("editable", 1).copy(elements = listOf(expression, graph))
        val reopened = requireNotNull(SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(saved)).document)
        val history = SmartBoardCommandHistory()
        val reopenedExpression = reopened.elements.filterIsInstance<MathExpressionElement>().single()
        val edited = history.execute(
            reopened,
            EditMathExpressionCommand(reopenedExpression, reopenedExpression.copy(correctedLatex = "x^3")),
            4,
        )
        val reopenedGraph = edited.elements.filterIsInstance<GraphConfigurationElement>().single()
        val graphEdited = history.execute(
            edited,
            ReplaceElementCommand(
                reopenedGraph,
                reopenedGraph.copy(expressions = listOf("x^3")),
                "Edit graph configuration",
            ),
            5,
        )
        assertEquals("x^3", graphEdited.elements.filterIsInstance<MathExpressionElement>().single().displayLatex)
        assertEquals(listOf("x^3"), graphEdited.elements.filterIsInstance<GraphConfigurationElement>().single().expressions)
        assertTrue(history.canUndo)
    }
}
