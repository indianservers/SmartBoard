package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.intelligence.SemanticCanvasNodeKind
import com.indianservers.smartboard.smartboard.intelligence.SemanticCanvasRelationKind
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSemanticCanvasEngine
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardSemanticCanvasTest {
    private val engine = SmartBoardSemanticCanvasEngine()

    @Test
    fun `equation controls graph and meaning selection keeps the pair together`() {
        val equation = equation("eq", "y=x^2", SmartBoardBounds(20f, 20f, 170f, 70f))
        val graph = GraphConfigurationElement(
            id = "graph",
            graphKind = SmartBoardGraphKind.EXPLICIT_2D,
            expressions = listOf("y=x^2"),
            sourceElementIds = listOf(equation.id),
            moduleRoute = "graph2d",
            bounds = SmartBoardBounds(220f, 20f, 520f, 260f),
            createdAt = 2L,
        )
        val board = board("page-1", "Quadratics", listOf(equation, graph))
        val snapshot = engine.analyze(board, emptyList(), 10L)

        assertTrue(snapshot.edges.any { it.kind == SemanticCanvasRelationKind.CONTROLS_GRAPH })
        assertEquals(
            setOf("eq", "graph"),
            engine.selectByMeaning(snapshot, board.id, "select this equation and its graph", setOf("eq")),
        )
    }

    @Test
    fun `labels forces axes and circuit components receive semantic names and relationships`() {
        val body = shape("body", SmartBoardShapeType.RECTANGLE, SmartBoardBounds(100f, 100f, 220f, 190f))
        val force = shape("force", SmartBoardShapeType.FORCE_ARROW, SmartBoardBounds(150f, 30f, 150f, 100f))
        val axes = shape("axes", SmartBoardShapeType.COORDINATE_AXES, SmartBoardBounds(320f, 80f, 560f, 300f))
        val resistor = shape("resistor", SmartBoardShapeType.RESISTOR, SmartBoardBounds(50f, 300f, 180f, 340f))
        val label = TextElement("label", "Block A", SmartBoardBounds(110f, 195f, 190f, 225f), 3L)
        val board = board("page-1", "Forces", listOf(body, force, axes, resistor, label))
        val snapshot = engine.analyze(board, emptyList(), 10L)

        assertTrue(snapshot.edges.any { it.kind == SemanticCanvasRelationKind.FORCE_ON })
        assertTrue(snapshot.edges.any { it.kind == SemanticCanvasRelationKind.LABELS })
        assertTrue(snapshot.nodes.any { it.kind == SemanticCanvasNodeKind.AXIS && "x-axis and y-axis" in it.proposedNames })
        assertTrue(snapshot.nodes.any { it.kind == SemanticCanvasNodeKind.CIRCUIT_COMPONENT && "resistor R" in it.proposedNames })
        assertEquals(setOf("force"), engine.selectByMeaning(snapshot, board.id, "select all forces", emptySet()))
    }

    @Test
    fun `semantic search and cross-page reasoning find related quadratic content`() {
        val first = board(
            "page-1",
            "Derivation",
            listOf(equation("derivation", "x=(-b+sqrt(b^2-4ac))/(2a)", SmartBoardBounds(20f, 20f, 360f, 80f))),
        )
        val secondEquation = equation("application", "x=(-b+sqrt(b^2-4ac))/(2a)", SmartBoardBounds(30f, 30f, 370f, 90f))
        val second = board("page-2", "Worked example", listOf(secondEquation))
        val snapshot = engine.analyze(first, listOf(second), 10L)
        val results = engine.search(snapshot, listOf(first, second), "Where did I use the quadratic formula?")

        assertTrue(snapshot.edges.any { it.kind == SemanticCanvasRelationKind.CROSS_PAGE })
        assertEquals(setOf("page-1", "page-2"), results.mapTo(linkedSetOf()) { it.boardId })
    }

    @Test
    fun `semantic lasso expands a label to its meaningful object and snapping aligns equations`() {
        val objectShape = shape("triangle", SmartBoardShapeType.TRIANGLE, SmartBoardBounds(80f, 80f, 180f, 180f))
        val label = TextElement("label", "Triangle ABC", SmartBoardBounds(85f, 184f, 180f, 215f), 2L)
        val eq1 = equation("eq1", "a^2+b^2=c^2", SmartBoardBounds(300f, 40f, 500f, 90f))
        val eq2 = equation("eq2", "c=sqrt(a^2+b^2)", SmartBoardBounds(333f, 120f, 533f, 170f))
        val board = board("page-1", "Geometry", listOf(objectShape, label, eq1, eq2))
        val snapshot = engine.analyze(board, emptyList(), 10L)

        val selected = engine.semanticLasso(
            snapshot,
            board.id,
            setOf("label"),
            SmartBoardBounds(70f, 170f, 200f, 225f),
        )
        assertTrue("triangle" in selected && "label" in selected)

        val snap = engine.snap(snapshot, board.id, setOf("eq2"), SmartBoardPoint(-30f, 0f))
        assertTrue(snap.snapped)
        assertEquals(-33f, snap.delta.x, .001f)
        assertTrue(snap.rationale.orEmpty().contains("Equation"))
    }

    private fun board(id: String, title: String, elements: List<com.indianservers.smartboard.smartboard.models.SmartBoardElement>) =
        SmartBoardDocument.new(id, 1L, title, SmartBoardSubject.MATHEMATICS).copy(elements = elements, updatedAt = 2L)

    private fun equation(id: String, source: String, bounds: SmartBoardBounds) = MathExpressionElement(
        id = id,
        rawLatex = source,
        correctedLatex = null,
        normalizedExpression = source,
        sourceStrokeIds = emptyList(),
        recognitionConfidence = .92f,
        bounds = bounds,
        createdAt = 2L,
    )

    private fun shape(id: String, type: SmartBoardShapeType, bounds: SmartBoardBounds) = ShapeElement(
        id = id,
        shapeType = type,
        points = listOf(SmartBoardPoint(bounds.left, bounds.top), SmartBoardPoint(bounds.right, bounds.bottom)),
        sourceStrokeIds = emptyList(),
        recognitionConfidence = .9f,
        strokeWidth = 3f,
        argbColor = 0xFFFFFFFF,
        bounds = bounds,
        createdAt = 2L,
    )
}
