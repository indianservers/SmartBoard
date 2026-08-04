package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.core.Graph3D
import com.indianservers.smartboard.core.TypedGraphEngine
import com.indianservers.smartboard.core.TypedGraphExpression
import com.indianservers.smartboard.core.TypedGraphExpressionParser
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardGraphAdapter
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardGraphKind
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionClassifier
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardLargeMathCorpusTest {
    @Test
    fun `fifteen trigonometric formulas classify and graph`() {
        val formulas = listOf(
            "Sin ( x )", "COS(x)", "tan (x)", "sin(x) + cos(x)", "2 * sin(x)",
            "sin( 2*x )", "sin(x) ^ 2", "cos(x)^2", "sin(x) / cos(x)",
            "asin( x/10 )", "acos(x / 10)", "atan ( x )", "sin(x + pi/4)",
            "cos(3 * x)", "sin(x) * cos(x)",
        )
        assertEquals(15, formulas.size)
        formulas.forEach { source ->
            assertEquals(source, MathExpressionType.FUNCTION, MathRecognitionClassifier.detect(source))
            val graph = SmartBoardGraphAdapter.prepare(source).getOrThrow()
            assertEquals(source, "graph2d", graph.route)
            assertGraphHasOutput(graph.expression)
        }
    }

    @Test
    fun `fifteen algebra formulas classify and parse structurally`() {
        val formulas = listOf(
            "x+1" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "2*x-3" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "x^2+5*x+6" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "(x+2)*(x-2)" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "a*x+b" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "x^3-4*x" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "x/2+7" to MathExpressionType.ALGEBRAIC_EXPRESSION,
            "x^2=9" to MathExpressionType.EQUATION,
            "2*x+5=15" to MathExpressionType.EQUATION,
            "x^2+y^2=25" to MathExpressionType.EQUATION,
            "a^2+b^2=c^2" to MathExpressionType.EQUATION,
            "y=3*x-1" to MathExpressionType.EQUATION,
            "x>=4" to MathExpressionType.INEQUALITY,
            "2*x+1<9" to MathExpressionType.INEQUALITY,
            "(x+1)/(x-1)" to MathExpressionType.ALGEBRAIC_EXPRESSION,
        )
        assertEquals(15, formulas.size)
        formulas.forEach { (source, expected) ->
            assertEquals(source, expected, MathRecognitionClassifier.detect(source))
            assertTrue("$source should be parser verified", SmartBoardExpressionAnalyzer.analyze(source).parserVerified)
        }
    }

    @Test
    fun `seventeen two and three dimensional shape constructions are detected`() {
        val recognizer = DeterministicAutoShapeRecognizer()
        fun detected(expected: SmartBoardShapeType, strokes: List<StrokeElement>, forced: Boolean = true) {
            val candidates = recognizer.recognize(strokes, forced)
            assertTrue(
                "$expected was not in ${candidates.map { candidate -> "${candidate.type}:${candidate.points}" }}",
                candidates.any { it.type == expected },
            )
        }

        detected(SmartBoardShapeType.HORIZONTAL_LINE, listOf(stroke("h", p(0f, 20f), p(100f, 20f))))
        detected(SmartBoardShapeType.VERTICAL_LINE, listOf(stroke("v", p(20f, 0f), p(20f, 100f))))
        detected(SmartBoardShapeType.DIAGONAL_LINE, listOf(stroke("d", p(0f, 0f), p(80f, 70f))))
        detected(SmartBoardShapeType.CIRCLE, listOf(roundStroke("circle", 60f, 60f, 40f, 40f)))
        detected(SmartBoardShapeType.ELLIPSE, listOf(roundStroke("ellipse", 70f, 50f, 60f, 28f)))
        detected(SmartBoardShapeType.SQUARE, listOf(polygonStroke("square", listOf(p(0f, 0f), p(70f, 0f), p(70f, 70f), p(0f, 70f)))))
        detected(SmartBoardShapeType.RECTANGLE, listOf(polygonStroke("rectangle", listOf(p(0f, 0f), p(110f, 0f), p(110f, 60f), p(0f, 60f)))))
        detected(SmartBoardShapeType.TRIANGLE, listOf(polygonStroke("triangle", listOf(p(0f, 80f), p(35f, 0f), p(100f, 80f)))))
        detected(SmartBoardShapeType.RIGHT_TRIANGLE, listOf(polygonStroke("right", listOf(p(0f, 0f), p(0f, 80f), p(110f, 80f)))))
        detected(SmartBoardShapeType.PENTAGON, listOf(regularPolygon("pentagon", 5)))
        detected(SmartBoardShapeType.HEXAGON, listOf(regularPolygon("hexagon", 6)))
        detected(
            SmartBoardShapeType.ARROW,
            listOf(
                stroke("shaft", p(0f, 40f), p(100f, 40f)),
                stroke("head1", p(100f, 40f), p(75f, 20f)),
                stroke("head2", p(100f, 40f), p(75f, 60f)),
            ),
        )
        detected(
            SmartBoardShapeType.COORDINATE_AXES,
            listOf(stroke("x-axis", p(0f, 60f), p(120f, 60f)), stroke("y-axis", p(60f, 0f), p(60f, 120f))),
        )
        detected(SmartBoardShapeType.NUMBER_LINE, numberLine())
        detected(SmartBoardShapeType.GRAPH_GRID, graphGrid())
        detected(SmartBoardShapeType.CUBE, cube())
        detected(SmartBoardShapeType.CYLINDER, cylinder())
    }

    @Test
    fun `twenty five graph equations produce drawable two or three dimensional output`() {
        val twoDimensional = listOf(
            "x", "x^2", "x^3-3*x", "sin(x)", "cos(x)", "tan(x)", "abs(x)",
            "sqrt(abs(x))", "exp(x/5)", "ln(abs(x)+1)", "1/(x-2)", "y=2*x+1",
            "x^2+y^2=9", "x^2-y^2=1", "y<=2*x+1", "x^2+y^2<16",
            "r=2+cos(theta)", "r=3*sin(2*theta)",
            "x(t)=cos(t); y(t)=sin(t)", "x(t)=t; y(t)=t^2",
            "piecewise{x<0:-x; x>=0:x}",
        )
        val threeDimensional = listOf("x^2+y^2", "sin(x)+cos(y)", "x*y", "sqrt(abs(x*y))")
        assertEquals(25, twoDimensional.size + threeDimensional.size)
        twoDimensional.forEach { source ->
            val prepared = SmartBoardGraphAdapter.prepare(source).getOrThrow()
            assertEquals(source, "graph2d", prepared.route)
            assertGraphHasOutput(prepared.expression)
        }
        threeDimensional.forEach { source ->
            val prepared = SmartBoardGraphAdapter.prepare(source, threeDimensional = true).getOrThrow()
            assertEquals(source, SmartBoardGraphKind.SURFACE_3D, prepared.kind)
            assertEquals(source, "graph3d", prepared.route)
            assertTrue(Graph3D().mesh(prepared.expression, density = 8).vertices.isNotEmpty())
        }
    }

    private fun assertGraphHasOutput(source: String) {
        val typed = TypedGraphExpressionParser.parse(source)
        val parameters = typed.parameters.associateWith { 1.0 }
        val sample = TypedGraphEngine().sample(typed, parameterValues = parameters, samples = 64)
        val count = sample.curves.sumOf { it.points.size } + sample.implicitSegments.size + sample.inequalityCells.count { it.satisfied }
        assertTrue("$source produced no drawable graph output from ${typed::class.simpleName}", count > 0)
    }

    private fun p(x: Float, y: Float) = SmartBoardPoint(x, y)

    private fun stroke(id: String, vararg points: SmartBoardPoint) = stroke(id, points.toList())

    private fun stroke(id: String, points: List<SmartBoardPoint>): StrokeElement {
        val humanPoints = humanize(points)
        var timestamp = 1L
        val samples = humanPoints.mapIndexed { index, point ->
            timestamp += 12L + (index % 5) * 5L
            StrokePoint(point.x, point.y, .72f + (index % 4) * .08f, timestamp)
        }
        return StrokeElement(
            id,
            samples,
            StrokeTool.PEN,
            3f,
            1f,
            0xff000000,
            SmartBoardBounds.from(humanPoints, 2f),
            1L,
        )
    }

    /**
     * Deterministic hand simulation: uneven timing/pressure plus small perpendicular tremor,
     * while keeping intended corners and stroke endpoints intact.
     */
    private fun humanize(points: List<SmartBoardPoint>): List<SmartBoardPoint> {
        if (points.size < 2) return points
        return buildList {
            points.zipWithNext().forEachIndexed { segmentIndex, (start, end) ->
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                val subdivisions = if (points.size > 20) 1 else 6
                for (step in 0..subdivisions) {
                    if (segmentIndex > 0 && step == 0) continue
                    val ratio = step.toFloat() / subdivisions
                    val tremor = sin((segmentIndex * 7 + step) * 1.37).toFloat() *
                        sin(PI * ratio).toFloat() * 1.15f
                    add(
                        p(
                            start.x + dx * ratio - dy / length * tremor,
                            start.y + dy * ratio + dx / length * tremor,
                        ),
                    )
                }
            }
        }
    }

    private fun polygonStroke(id: String, vertices: List<SmartBoardPoint>) = stroke(id, vertices + vertices.first())

    private fun roundStroke(id: String, cx: Float, cy: Float, rx: Float, ry: Float) = stroke(
        id,
        (0..48).map { index ->
            val angle = 2.0 * PI * index / 48
            p(cx + cos(angle).toFloat() * rx, cy + sin(angle).toFloat() * ry)
        },
    )

    private fun regularPolygon(id: String, sides: Int): StrokeElement {
        val vertices = (0 until sides).map { index ->
            val angle = -PI / 2 + 2 * PI * index / sides
            p(60f + cos(angle).toFloat() * 55f, 60f + sin(angle).toFloat() * 55f)
        }
        return polygonStroke(id, vertices)
    }

    private fun numberLine() = buildList {
        add(stroke("baseline", p(0f, 50f), p(140f, 50f)))
        listOf(25f, 55f, 85f, 115f).forEachIndexed { index, x ->
            add(stroke("tick-$index", p(x, 40f), p(x, 60f)))
        }
    }

    private fun graphGrid() = buildList {
        listOf(20f, 50f, 80f).forEachIndexed { index, y ->
            add(stroke("row-$index", p(0f, y), p(100f, y)))
        }
        listOf(20f, 50f, 80f).forEachIndexed { index, x ->
            add(stroke("column-$index", p(x, 0f), p(x, 100f)))
        }
    }

    private fun cube(): List<StrokeElement> {
        val a = p(0f, 25f); val b = p(60f, 25f); val c = p(60f, 85f); val d = p(0f, 85f)
        val e = p(25f, 0f); val f = p(85f, 0f); val g = p(85f, 60f); val h = p(25f, 60f)
        return listOf(
            a to b, b to c, c to d, d to a, e to f, f to g, g to h, h to e,
            a to e, b to f, c to g, d to h,
        ).mapIndexed { index, (start, end) -> stroke("cube-$index", start, end) }
    }

    private fun cylinder() = listOf(
        roundStroke("top", 60f, 20f, 40f, 12f),
        roundStroke("bottom", 60f, 100f, 40f, 12f),
        stroke("left", p(20f, 20f), p(20f, 100f)),
        stroke("right", p(100f, 20f), p(100f, 100f)),
    )
}
