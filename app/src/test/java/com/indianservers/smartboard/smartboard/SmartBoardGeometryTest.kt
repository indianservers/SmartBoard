package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.canvas.SmartBoardCoordinates
import com.indianservers.smartboard.smartboard.canvas.SmartBoardSelection
import com.indianservers.smartboard.smartboard.canvas.SmartBoardStrokeGeometry
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardViewport
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardGeometryTest {
    @Test
    fun coordinateConversionsRoundTripAcrossDensityPanAndZoom() {
        val viewport = SmartBoardViewport(panX = 24f, panY = -8f, zoom = 2.5f)
        val document = SmartBoardPoint(31.5f, -12.25f)
        val screen = SmartBoardCoordinates.documentToScreen(document, density = 3f, viewport)
        val restored = SmartBoardCoordinates.screenToDocument(screen, density = 3f, viewport)

        assertEquals(document.x, restored.x, 1e-4f)
        assertEquals(document.y, restored.y, 1e-4f)
    }

    @Test
    fun strokeBoundsIncludeWidthAndSimplificationPreservesShapeEndpoints() {
        val points = (0..100).map { index ->
            StrokePoint(index.toFloat(), if (index == 50) 20f else index * .01f, 1f, index.toLong())
        }
        val bounds = SmartBoardStrokeGeometry.bounds(points, width = 4f)
        val simplified = SmartBoardStrokeGeometry.simplify(points, tolerance = .5f)

        assertEquals(-2f, bounds.left, 0f)
        assertEquals(102f, bounds.right, 0f)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue(simplified.size < points.size)
        assertTrue(simplified.any { it.y == 20f })
    }

    @Test
    fun rectangleLassoAndTapSelectionUseVectorGeometry() {
        val first = stroke("a", 0f, 0f, 20f, 20f)
        val second = stroke("b", 80f, 80f, 100f, 100f)

        assertEquals(setOf("a"), SmartBoardSelection.rectangle(listOf(first, second), SmartBoardBounds(-2f, -2f, 30f, 30f)))
        assertEquals(
            setOf("a"),
            SmartBoardSelection.lasso(
                listOf(first, second),
                listOf(SmartBoardPoint(-5f, -5f), SmartBoardPoint(40f, -5f), SmartBoardPoint(40f, 40f), SmartBoardPoint(-5f, 40f)),
            ),
        )
        assertEquals("b", SmartBoardSelection.tap(listOf(first, second), SmartBoardPoint(90f, 90f), 3f))
    }

    private fun stroke(id: String, x1: Float, y1: Float, x2: Float, y2: Float): StrokeElement {
        val points = listOf(StrokePoint(x1, y1, 1f, 1L), StrokePoint(x2, y2, 1f, 2L))
        return StrokeElement(id, points, StrokeTool.PEN, 3f, 1f, 0xff000000, SmartBoardStrokeGeometry.bounds(points, 3f), 1L)
    }
}

