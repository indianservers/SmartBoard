package com.indianservers.smartboard.smartboard

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardHandDrawnShapeAccuracyTest {
    private data class ShapeCase(
        val number: Int,
        val dimension: String,
        val name: String,
        val exactTypes: Set<SmartBoardShapeType>,
        val familyTypes: Set<SmartBoardShapeType>,
        val ink: (Int) -> List<StrokeElement>,
    )

    private val cases = buildList {
        fun add2(
            name: String,
            exact: Set<SmartBoardShapeType>,
            family: Set<SmartBoardShapeType> = exact,
            ink: (Int) -> List<StrokeElement>,
        ) = add(ShapeCase(size + 1, "2D", name, exact, family, ink))
        fun add3(
            name: String,
            exact: Set<SmartBoardShapeType>,
            family: Set<SmartBoardShapeType> = exact,
            ink: (Int) -> List<StrokeElement>,
        ) = add(ShapeCase(size - 29, "3D", name, exact, family, ink))

        add2("Circle", setOf(SmartBoardShapeType.CIRCLE)) { s -> HumanShapeInk.round(s, 52f, 52f) }
        add2("Oval", setOf(SmartBoardShapeType.ELLIPSE)) { s -> HumanShapeInk.round(s, 68f, 40f) }
        add2("Triangle", setOf(SmartBoardShapeType.TRIANGLE)) { s -> HumanShapeInk.polygon(s, listOf(p(16, 90), p(48, 12), p(108, 90))) }
        add2("Right triangle", setOf(SmartBoardShapeType.RIGHT_TRIANGLE)) { s -> HumanShapeInk.polygon(s, listOf(p(15, 15), p(15, 92), p(112, 92))) }
        add2("Equilateral triangle", setOf(SmartBoardShapeType.EQUILATERAL_TRIANGLE)) { s -> HumanShapeInk.regularPolygon(s, 3, 54f, 52f, -.5f * PI.toFloat()) }
        add2("Isosceles triangle", emptySet(), setOf(SmartBoardShapeType.TRIANGLE, SmartBoardShapeType.EQUILATERAL_TRIANGLE)) { s ->
            HumanShapeInk.polygon(s, listOf(p(15, 92), p(62, 10), p(109, 92)))
        }
        add2("Scalene triangle", setOf(SmartBoardShapeType.TRIANGLE)) { s -> HumanShapeInk.polygon(s, listOf(p(12, 89), p(42, 8), p(116, 82))) }
        add2("Square", setOf(SmartBoardShapeType.SQUARE)) { s -> HumanShapeInk.polygon(s, listOf(p(15, 15), p(100, 15), p(100, 100), p(15, 100))) }
        add2("Rectangle", setOf(SmartBoardShapeType.RECTANGLE)) { s -> HumanShapeInk.polygon(s, listOf(p(8, 25), p(120, 25), p(120, 90), p(8, 90))) }
        add2("Parallelogram", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polygon(s, listOf(p(30, 20), p(120, 20), p(98, 92), p(8, 92))) }
        add2("Rhombus", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polygon(s, listOf(p(62, 8), p(112, 58), p(62, 108), p(12, 58))) }
        add2("Trapezium", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polygon(s, listOf(p(35, 18), p(95, 18), p(120, 95), p(8, 95))) }
        add2("Trapezoid", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polygon(s, listOf(p(25, 15), p(105, 25), p(118, 95), p(8, 95))) }
        add2("Kite", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polygon(s, listOf(p(62, 5), p(101, 49), p(62, 116), p(26, 49))) }
        add2("Pentagon", setOf(SmartBoardShapeType.PENTAGON)) { s -> HumanShapeInk.regularPolygon(s, 5) }
        add2("Hexagon", setOf(SmartBoardShapeType.HEXAGON)) { s -> HumanShapeInk.regularPolygon(s, 6) }
        add2("Heptagon", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.regularPolygon(s, 7) }
        add2("Octagon", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.regularPolygon(s, 8) }
        add2("Nonagon", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.regularPolygon(s, 9) }
        add2("Decagon", emptySet(), setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.regularPolygon(s, 10) }
        add2("Star (5-point)", setOf(SmartBoardShapeType.STAR)) { s -> HumanShapeInk.star(s, 5) }
        add2("Star (6-point)", setOf(SmartBoardShapeType.STAR)) { s -> HumanShapeInk.doubleTriangleStar(s) }
        add2("Crescent", emptySet(), setOf(SmartBoardShapeType.CURVE, SmartBoardShapeType.CLOSED_REGION)) { s -> HumanShapeInk.crescent(s) }
        add2("Semicircle", setOf(SmartBoardShapeType.SEMICIRCLE), setOf(SmartBoardShapeType.SEMICIRCLE, SmartBoardShapeType.ARC)) { s -> HumanShapeInk.semicircle(s) }
        add2("Annulus", emptySet(), setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ELLIPSE)) { s -> HumanShapeInk.annulus(s) }
        add2("Sector", emptySet(), setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ANGLE, SmartBoardShapeType.CLOSED_REGION)) { s -> HumanShapeInk.sector(s) }
        add2("Segment", emptySet(), setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ARC, SmartBoardShapeType.CLOSED_REGION)) { s -> HumanShapeInk.segment(s) }
        add2("Chord", emptySet(), setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.LINE_SEGMENT, SmartBoardShapeType.HORIZONTAL_LINE)) { s -> HumanShapeInk.chord(s) }
        add2("Tangent", emptySet(), setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.LINE, SmartBoardShapeType.DIAGONAL_LINE)) { s -> HumanShapeInk.tangent(s) }
        add2("Regular polygon (n sides)", setOf(SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.regularPolygon(s, 8) }

        add3("Cube", setOf(SmartBoardShapeType.CUBE)) { s -> HumanShapeInk.box3d(s, square = true) }
        add3("Cuboid", setOf(SmartBoardShapeType.CUBOID)) { s -> HumanShapeInk.box3d(s, square = false) }
        add3("Sphere", setOf(SmartBoardShapeType.SPHERE)) { s -> HumanShapeInk.sphere(s, false) }
        add3("Hemisphere", emptySet(), setOf(SmartBoardShapeType.SPHERE, SmartBoardShapeType.ARC)) { s -> HumanShapeInk.hemisphere(s) }
        add3("Cone", setOf(SmartBoardShapeType.CONE)) { s -> HumanShapeInk.cone(s, frustum = false) }
        add3("Cylinder", setOf(SmartBoardShapeType.CYLINDER)) { s -> HumanShapeInk.cylinder(s, hollow = false) }
        add3("Triangular prism", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.CUBE, SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.prism(s, 3) }
        add3("Square prism", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.CUBE)) { s -> HumanShapeInk.prism(s, 4) }
        add3("Rectangular prism", setOf(SmartBoardShapeType.CUBOID)) { s -> HumanShapeInk.box3d(s, square = false) }
        add3("Pentagonal prism", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.prism(s, 5) }
        add3("Hexagonal prism", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.prism(s, 6) }
        add3("Pyramid (square base)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 4) }
        add3("Pyramid (triangular base)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 3) }
        add3("Pyramid (pentagonal base)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 5) }
        add3("Cylinder (hollow)", emptySet(), setOf(SmartBoardShapeType.CYLINDER)) { s -> HumanShapeInk.cylinder(s, hollow = true) }
        add3("Cone (frustum)", emptySet(), setOf(SmartBoardShapeType.CONE, SmartBoardShapeType.CYLINDER)) { s -> HumanShapeInk.cone(s, frustum = true) }
        add3("Triangular pyramid (tetrahedron)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 3) }
        add3("Octahedron", emptySet(), setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.octahedron(s) }
        add3("Dodecahedron", emptySet(), setOf(SmartBoardShapeType.SPHERE, SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polyhedron(s, 5) }
        add3("Icosahedron", emptySet(), setOf(SmartBoardShapeType.SPHERE, SmartBoardShapeType.POLYGON)) { s -> HumanShapeInk.polyhedron(s, 6) }
        add3("Torus (ring)", emptySet(), setOf(SmartBoardShapeType.ELLIPSE, SmartBoardShapeType.CYLINDER)) { s -> HumanShapeInk.torus(s) }
        add3("Ellipsoid (oval sphere)", emptySet(), setOf(SmartBoardShapeType.SPHERE, SmartBoardShapeType.ELLIPSE)) { s -> HumanShapeInk.sphere(s, true) }
        add3("Capsule", emptySet(), setOf(SmartBoardShapeType.CYLINDER, SmartBoardShapeType.CLOSED_REGION)) { s -> HumanShapeInk.capsule(s) }
        add3("Pyramid (hexagonal base)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 6) }
        add3("Pyramid (octagonal base)", setOf(SmartBoardShapeType.PYRAMID)) { s -> HumanShapeInk.pyramid(s, 8) }
        add3("Prism (oblique)", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.CUBE)) { s -> HumanShapeInk.obliqueBox(s) }
        add3("Rhombohedron", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.CUBE)) { s -> HumanShapeInk.obliqueBox(s) }
        add3("Sphere (with axis)", emptySet(), setOf(SmartBoardShapeType.SPHERE)) { s -> HumanShapeInk.sphereWithAxis(s) }
        add3("Frustum (square base)", emptySet(), setOf(SmartBoardShapeType.PYRAMID, SmartBoardShapeType.CUBOID)) { s -> HumanShapeInk.squareFrustum(s) }
        add3("Composite shape", emptySet(), setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.PYRAMID, SmartBoardShapeType.CLOSED_REGION)) { s -> HumanShapeInk.composite(s) }
    }

    @Test
    fun reportExpectedAndDetectedForAllHandDrawnShapes() {
        val recognizer = DeterministicAutoShapeRecognizer()
        var exact = 0
        var family = 0
        var detected = 0
        val rows = mutableListOf<String>()
        cases.forEachIndexed { index, case ->
            val strokes = case.ink(1_000 + index)
            val candidates = recognizer.recognize(strokes, forced = true)
            val primary = candidates.firstOrNull()?.type
            val exactMatch = primary != null && primary in case.exactTypes
            val familyMatch = exactMatch || (primary != null && primary in case.familyTypes)
            if (primary != null) detected++
            if (exactMatch) exact++
            if (familyMatch) family++
            val outcome = when {
                exactMatch -> "EXACT"
                familyMatch -> "FAMILY"
                primary == null -> "NONE"
                else -> "MISS"
            }
            val alternatives = candidates.drop(1).joinToString(",") { it.type.name }
            val row = "ROW|${case.dimension}|${case.number}|${case.name}|${case.exactTypes.joinToString(",") { it.name }}|" +
                "${primary?.name ?: "NONE"}|$outcome|$alternatives"
            rows += row
            Log.i(TAG, row)
        }
        Log.i(TAG, "SUMMARY|exact=$exact/${cases.size}|family=$family/${cases.size}|detected=$detected/${cases.size}")
        assertEquals(60, rows.size)
    }

    private companion object {
        const val TAG = "SHAPE_ACCURACY"
        fun p(x: Int, y: Int) = SmartBoardPoint(x.toFloat(), y.toFloat())
    }
}

private object HumanShapeInk {
    fun round(seed: Int, rx: Float, ry: Float, cx: Float = 65f, cy: Float = 62f) =
        listOf(stroke(seed, 0, sampledArc(cx, cy, rx, ry, 0f, 2f * PI.toFloat(), 48, closed = true)))

    fun regularPolygon(seed: Int, sides: Int, rx: Float = 52f, ry: Float = 52f, rotation: Float = -.5f * PI.toFloat()) =
        polygon(seed, List(sides) { index ->
            val angle = rotation + 2f * PI.toFloat() * index / sides
            SmartBoardPoint(65f + cos(angle) * rx, 62f + sin(angle) * ry)
        })

    fun polygon(seed: Int, vertices: List<SmartBoardPoint>) =
        listOf(stroke(seed, 0, vertices + vertices.first()))

    fun star(seed: Int, points: Int): List<StrokeElement> {
        val vertices = List(points * 2) { index ->
            val angle = -.5f * PI.toFloat() + PI.toFloat() * index / points
            val radius = if (index % 2 == 0) 54f else 22f
            SmartBoardPoint(65f + cos(angle) * radius, 62f + sin(angle) * radius)
        }
        return polygon(seed, vertices)
    }

    fun doubleTriangleStar(seed: Int) =
        polygon(seed, listOf(p(65, 8), p(112, 92), p(18, 92))) +
            polygon(seed + 1, listOf(p(65, 112), p(18, 28), p(112, 28)))

    fun crescent(seed: Int): List<StrokeElement> {
        val outer = sampledArc(63f, 62f, 48f, 50f, -.7f * PI.toFloat(), .7f * PI.toFloat(), 24)
        val inner = sampledArc(79f, 62f, 37f, 40f, .7f * PI.toFloat(), -.7f * PI.toFloat(), 24)
        return listOf(stroke(seed, 0, outer + inner + outer.first()))
    }

    fun semicircle(seed: Int): List<StrokeElement> {
        val arc = sampledArc(65f, 75f, 52f, 48f, PI.toFloat(), 2f * PI.toFloat(), 28)
        return listOf(stroke(seed, 0, arc + listOf(arc.first())))
    }

    fun annulus(seed: Int) = round(seed, 52f, 52f) + round(seed + 1, 23f, 23f)

    fun sector(seed: Int): List<StrokeElement> {
        val arc = sampledArc(65f, 62f, 52f, 52f, -.65f, .65f, 18)
        return listOf(
            stroke(seed, 0, arc),
            stroke(seed, 1, listOf(p(65, 62), arc.first())),
            stroke(seed, 2, listOf(p(65, 62), arc.last())),
        )
    }

    fun segment(seed: Int): List<StrokeElement> {
        val arc = sampledArc(65f, 62f, 52f, 52f, .25f, PI.toFloat() + .25f, 24)
        return listOf(stroke(seed, 0, arc + arc.first()))
    }

    fun chord(seed: Int) = round(seed, 52f, 52f) +
        listOf(stroke(seed, 1, listOf(p(14, 62), p(116, 62))))

    fun tangent(seed: Int) = round(seed, 45f, 45f, 60f, 57f) +
        listOf(stroke(seed, 1, listOf(p(10, 104), p(126, 85))))

    fun box3d(seed: Int, square: Boolean): List<StrokeElement> {
        val w = if (square) 67f else 92f
        val h = 67f
        val a = SmartBoardPoint(12f, 35f)
        val b = SmartBoardPoint(12f + w, 35f)
        val c = SmartBoardPoint(12f + w, 35f + h)
        val d = SmartBoardPoint(12f, 35f + h)
        val offset = SmartBoardPoint(27f, -22f)
        val e = a + offset
        val f = b + offset
        val g = c + offset
        val hPoint = d + offset
        return edges(seed, listOf(a to b, b to c, c to d, d to a, e to f, f to g, g to hPoint, hPoint to e, a to e, b to f, c to g, d to hPoint))
    }

    fun sphere(seed: Int, elongated: Boolean): List<StrokeElement> {
        val rx = if (elongated) 58f else 50f
        val ry = if (elongated) 40f else 50f
        return round(seed, rx, ry) +
            listOf(stroke(seed, 1, sampledArc(65f, 62f, rx, ry * .28f, 0f, 2f * PI.toFloat(), 36, true))) +
            listOf(stroke(seed, 2, sampledArc(65f, 62f, rx * .28f, ry, 0f, 2f * PI.toFloat(), 36, true)))
    }

    fun hemisphere(seed: Int): List<StrokeElement> {
        val dome = sampledArc(65f, 70f, 52f, 48f, PI.toFloat(), 2f * PI.toFloat(), 28)
        return listOf(stroke(seed, 0, dome), stroke(seed, 1, sampledArc(65f, 70f, 52f, 13f, 0f, 2f * PI.toFloat(), 28, true)))
    }

    fun cylinder(seed: Int, hollow: Boolean): List<StrokeElement> {
        val output = mutableListOf<StrokeElement>()
        output += stroke(seed, 0, sampledArc(65f, 25f, 42f, 14f, 0f, 2f * PI.toFloat(), 28, true))
        output += stroke(seed, 1, sampledArc(65f, 97f, 42f, 14f, 0f, 2f * PI.toFloat(), 28, true))
        output += stroke(seed, 2, listOf(p(23, 25), p(23, 97)))
        output += stroke(seed, 3, listOf(p(107, 25), p(107, 97)))
        if (hollow) output += stroke(seed, 4, sampledArc(65f, 25f, 23f, 7f, 0f, 2f * PI.toFloat(), 24, true))
        return output
    }

    fun cone(seed: Int, frustum: Boolean): List<StrokeElement> {
        val base = stroke(seed, 0, sampledArc(65f, 100f, 43f, 13f, 0f, 2f * PI.toFloat(), 28, true))
        return if (!frustum) listOf(
            base,
            stroke(seed, 1, listOf(p(65, 10), p(22, 100))),
            stroke(seed, 2, listOf(p(65, 10), p(108, 100))),
        ) else listOf(
            base,
            stroke(seed, 1, sampledArc(65f, 25f, 25f, 8f, 0f, 2f * PI.toFloat(), 24, true)),
            stroke(seed, 2, listOf(p(40, 25), p(22, 100))),
            stroke(seed, 3, listOf(p(90, 25), p(108, 100))),
        )
    }

    fun prism(seed: Int, sides: Int): List<StrokeElement> {
        val front = polygonPoints(sides, 48f, 66f, 37f, 42f)
        val offset = SmartBoardPoint(30f, -20f)
        val back = front.map { it + offset }
        val pairs = mutableListOf<Pair<SmartBoardPoint, SmartBoardPoint>>()
        front.indices.forEach { i ->
            pairs += front[i] to front[(i + 1) % front.size]
            pairs += back[i] to back[(i + 1) % back.size]
            pairs += front[i] to back[i]
        }
        return edges(seed, pairs.take(16))
    }

    fun pyramid(seed: Int, sides: Int): List<StrokeElement> {
        val base = polygonPoints(sides, 65f, 89f, 48f, 24f, .5f * PI.toFloat())
        val apex = p(65, 8)
        val pairs = base.indices.map { base[it] to base[(it + 1) % base.size] } +
            base.map { it to apex }
        return edges(seed, pairs.take(16))
    }

    fun octahedron(seed: Int): List<StrokeElement> {
        val top = p(65, 5)
        val bottom = p(65, 116)
        val ring = listOf(p(10, 60), p(65, 38), p(120, 60), p(65, 82))
        return edges(seed, ring.indices.flatMap { i ->
            listOf(ring[i] to ring[(i + 1) % ring.size], ring[i] to top, ring[i] to bottom)
        })
    }

    fun polyhedron(seed: Int, sides: Int) =
        regularPolygon(seed, sides, 52f, 52f) +
            regularPolygon(seed + 1, sides, 28f, 28f, -.5f * PI.toFloat() + .2f) +
            edges(seed + 2, polygonPoints(sides, 65f, 62f, 52f, 52f).zip(polygonPoints(sides, 65f, 62f, 28f, 28f)).take(14))

    fun torus(seed: Int) =
        listOf(
            stroke(seed, 0, sampledArc(65f, 62f, 57f, 33f, 0f, 2f * PI.toFloat(), 40, true)),
            stroke(seed, 1, sampledArc(65f, 62f, 25f, 12f, 0f, 2f * PI.toFloat(), 30, true)),
            stroke(seed, 2, sampledArc(65f, 70f, 50f, 14f, 0f, PI.toFloat(), 20)),
        )

    fun capsule(seed: Int): List<StrokeElement> {
        val outline = sampledArc(35f, 62f, 25f, 42f, .5f * PI.toFloat(), 1.5f * PI.toFloat(), 20) +
            sampledArc(95f, 62f, 25f, 42f, 1.5f * PI.toFloat(), 2.5f * PI.toFloat(), 20)
        return listOf(stroke(seed, 0, outline + outline.first()))
    }

    fun obliqueBox(seed: Int): List<StrokeElement> {
        val front = listOf(p(18, 38), p(83, 38), p(72, 103), p(7, 103))
        val offset = SmartBoardPoint(34f, -24f)
        val back = front.map { it + offset }
        return edges(seed, front.indices.flatMap { i ->
            listOf(front[i] to front[(i + 1) % 4], back[i] to back[(i + 1) % 4], front[i] to back[i])
        })
    }

    fun sphereWithAxis(seed: Int) = sphere(seed, false) +
        listOf(stroke(seed, 3, listOf(p(65, 0), p(65, 124))))

    fun squareFrustum(seed: Int): List<StrokeElement> {
        val lower = listOf(p(12, 102), p(118, 102), p(100, 62), p(30, 62))
        val upper = listOf(p(42, 18), p(88, 18), p(82, 42), p(48, 42))
        return edges(seed, lower.indices.flatMap { i ->
            listOf(lower[i] to lower[(i + 1) % 4], upper[i] to upper[(i + 1) % 4], lower[i] to upper[i])
        })
    }

    fun composite(seed: Int) = box3d(seed, false).take(10) +
        edges(seed + 1, listOf(p(12, 35) to p(58, 5), p(104, 13) to p(58, 5), p(39, 13) to p(58, 5)))

    private fun edges(seed: Int, pairs: List<Pair<SmartBoardPoint, SmartBoardPoint>>) =
        pairs.mapIndexed { index, pair -> stroke(seed, index, listOf(pair.first, pair.second)) }

    private fun stroke(seed: Int, index: Int, vertices: List<SmartBoardPoint>): StrokeElement {
        val points = interpolate(vertices).mapIndexed { pointIndex, point ->
            val jitterX = wobble(seed, index, pointIndex) * 1.15f
            val jitterY = wobble(seed + 37, index, pointIndex) * 1.25f
            StrokePoint(
                point.x + jitterX,
                point.y + jitterY,
                (.64f + wobble(seed + 11, index, pointIndex) * .09f).coerceIn(.3f, .95f),
                1_000L + seed * 10_000L + index * 2_000L + pointIndex * 13L,
            )
        }
        return StrokeElement(
            "human-shape-$seed-$index",
            points,
            StrokeTool.PEN,
            3.2f,
            1f,
            0xFFF4F7FF,
            SmartBoardBounds.from(points.map(StrokePoint::position)),
            points.last().timestampMillis,
        )
    }

    private fun interpolate(vertices: List<SmartBoardPoint>): List<SmartBoardPoint> = buildList {
        vertices.zipWithNext().forEachIndexed { segmentIndex, (start, end) ->
            repeat(7) { step ->
                if (segmentIndex > 0 && step == 0) return@repeat
                val t = step / 6f
                add(SmartBoardPoint(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t))
            }
        }
    }

    private fun sampledArc(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        start: Float,
        end: Float,
        count: Int,
        closed: Boolean = false,
    ) = List(count + if (closed) 1 else 0) { index ->
        val denominator = if (closed) count else count - 1
        val angle = start + (end - start) * index / denominator.coerceAtLeast(1)
        SmartBoardPoint(cx + cos(angle) * rx, cy + sin(angle) * ry)
    }

    private fun polygonPoints(
        sides: Int,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        rotation: Float = -.5f * PI.toFloat(),
    ) = List(sides) { index ->
        val angle = rotation + 2f * PI.toFloat() * index / sides
        SmartBoardPoint(cx + cos(angle) * rx, cy + sin(angle) * ry)
    }

    private fun wobble(seed: Int, stroke: Int, point: Int): Float {
        val value = (seed * 73 + stroke * 37 + point * 17) % 19
        return (value - 9) / 9f
    }

    private fun p(x: Int, y: Int) = SmartBoardPoint(x.toFloat(), y.toFloat())
}
