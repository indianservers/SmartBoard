package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.intelligence.SmartBoardMathGraphIntelligenceEngine
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSemanticExpressionBuilder
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.tools.SemanticComponentRole
import com.indianservers.smartboard.smartboard.tools.SmartBoardSemanticToolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardMathGraphIntelligenceTest {
    private val engine = SmartBoardMathGraphIntelligenceEngine()

    @Test
    fun `semantic targets expose exponents matrix cells terms and function arguments`() {
        val algebra = SmartBoardSemanticExpressionBuilder.build("sin(x+1)+a/(b^2)")
        val roles = SmartBoardSemanticToolEngine.targets(algebra).mapTo(hashSetOf()) { it.role }
        assertTrue(SemanticComponentRole.EXPONENT in roles)
        assertTrue(SemanticComponentRole.DENOMINATOR in roles)
        assertTrue(SemanticComponentRole.TERM in roles)
        assertTrue(SemanticComponentRole.FUNCTION_ARGUMENT in roles)

        val matrix = SmartBoardSemanticExpressionBuilder.build("[1,2;3,4]")
        assertTrue(SmartBoardSemanticToolEngine.targets(matrix).any { it.role == SemanticComponentRole.MATRIX_CELL })

        val exponent = SmartBoardSemanticToolEngine.targets(algebra).first { it.role == SemanticComponentRole.EXPONENT }
        val replaced = SmartBoardSemanticToolEngine.replace(algebra, exponent.nodeId, "3").getOrThrow()
        assertTrue("3" in replaced.expressionAfter)
    }

    @Test
    fun `equivalent expressions are proven and invalid alternatives return a counterexample`() {
        val equivalent = engine.equivalent("(x+1)^2", "x^2+2*x+1")
        assertTrue(equivalent.equivalent)

        val invalid = engine.equivalent("x^2", "2*x")
        assertFalse(invalid.equivalent)
        assertTrue(invalid.counterexample != null || invalid.explanation.isNotBlank())
    }

    @Test
    fun `parameter discovery names amplitude frequency and offset`() {
        val parameters = engine.discoverParameters("y=a*sin(b*x)+c")
        assertEquals(listOf("a", "b", "c"), parameters.map { it.symbol })
        assertEquals("amplitude", parameters[0].semanticName)
        assertEquals("frequency / horizontal scale", parameters[1].semanticName)
        assertEquals("offset", parameters[2].semanticName)
    }

    @Test
    fun `hand drawn parabola produces ranked editable quadratic candidate`() {
        val axes = shape("axes", SmartBoardShapeType.COORDINATE_AXES, SmartBoardBounds(0f, 0f, 400f, 400f))
        val points = (-20..20).mapIndexed { index, step ->
            val x = step / 8.0
            StrokePoint(
                x = (200.0 + x * 40.0).toFloat(),
                y = (200.0 - .7 * x * x * 40.0).toFloat(),
                pressure = 1f,
                timestampMillis = index.toLong(),
            )
        }
        val curve = StrokeElement(
            "curve", points, StrokeTool.PEN, 3f, 1f, 0xFFFFFFFF,
            SmartBoardBounds.from(points.map(StrokePoint::position)), 1L,
        )
        val suggestion = engine.analyzeInk(listOf(curve), listOf(axes), emptyList(), 2L)

        assertTrue(suggestion != null)
        assertTrue(suggestion!!.candidates.any { it.family.contains("quadratic") && "x^2" in it.expression })
        assertEquals(setOf("curve"), suggestion.sourceStrokeIds)
        assertEquals(setOf("axes"), suggestion.axisElementIds)
    }

    @Test
    fun `first invalid algebra transformation is localized and receives spatial hint`() {
        val lines = listOf(
            expression("one", "x+2=5", 20f),
            expression("two", "x=3", 90f),
            expression("three", "x=-3", 160f),
        )
        val mistake = engine.localizeMistake(lines)

        assertEquals(2, mistake?.invalidStepIndex)
        assertEquals(lines[2].bounds, mistake?.bounds)
        val hint = engine.mistakeHint(requireNotNull(mistake), lines.mapTo(linkedSetOf()) { it.id })
        assertTrue(hint.warning)
        assertEquals(lines[2].bounds, hint.anchorBounds)
    }

    @Test
    fun `next step hint is anchored beside the selected line`() {
        val line = expression("trig", "sin(x)^2+cos(x)^2=1", 80f)
        val hint = engine.nextStepHint(line)
        assertEquals(line.bounds, hint.anchorBounds)
        assertTrue(hint.text.contains("identity"))
    }

    @Test
    fun `graph parameter slider values survive board persistence`() {
        val graph = GraphConfigurationElement(
            "graph", SmartBoardGraphKind.EXPLICIT_2D, listOf("a*sin(b*x)"), emptyList(), "graph2d",
            SmartBoardBounds(0f, 0f, 300f, 200f), 2L, parameterValues = mapOf("a" to 2.5, "b" to .75),
        )
        val board = SmartBoardDocument.new("board", 1L, "Parameters", SmartBoardSubject.MATHEMATICS)
            .copy(elements = listOf(graph), updatedAt = 2L)
        val decoded = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(board)).document
        val restored = decoded?.elements?.filterIsInstance<GraphConfigurationElement>()?.single()
        assertEquals(mapOf("a" to 2.5, "b" to .75), restored?.parameterValues)
    }

    private fun expression(id: String, source: String, top: Float) = MathExpressionElement(
        id = id,
        rawLatex = source,
        correctedLatex = null,
        normalizedExpression = source,
        sourceStrokeIds = emptyList(),
        recognitionConfidence = .95f,
        bounds = SmartBoardBounds(20f, top, 320f, top + 50f),
        createdAt = top.toLong(),
        semanticTree = SmartBoardSemanticExpressionBuilder.build(source),
    )

    private fun shape(id: String, type: SmartBoardShapeType, bounds: SmartBoardBounds) = ShapeElement(
        id = id,
        shapeType = type,
        points = listOf(SmartBoardPoint(bounds.left, bounds.center.y), SmartBoardPoint(bounds.right, bounds.center.y)),
        sourceStrokeIds = emptyList(),
        recognitionConfidence = .95f,
        strokeWidth = 3f,
        argbColor = 0xFFFFFFFF,
        bounds = bounds,
        createdAt = 1L,
    )
}
