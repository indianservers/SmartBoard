package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.intelligence.CanvasCommandKind
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardCanvasCommandEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardCanvasCommandTest {
    @Test
    fun `meaning selection search graph hint and mistake commands are understood`() {
        assertEquals(
            CanvasCommandKind.SELECT_BY_MEANING,
            SmartBoardCanvasCommandEngine.parse("Select every denominator").kind,
        )
        assertEquals(
            CanvasCommandKind.SEARCH_CANVAS,
            SmartBoardCanvasCommandEngine.parse("Where did I use the quadratic formula?").kind,
        )
        assertEquals(
            CanvasCommandKind.GRAPH_FROM_INK,
            SmartBoardCanvasCommandEngine.parse("Graph this hand drawn curve").kind,
        )
        assertEquals(
            CanvasCommandKind.SHOW_HINT,
            SmartBoardCanvasCommandEngine.parse("Show a next step hint").kind,
        )
        assertEquals(
            CanvasCommandKind.LOCALIZE_MISTAKE,
            SmartBoardCanvasCommandEngine.parse("Check my work for mistakes").kind,
        )
    }

    @Test
    fun `teach mode labels and parameter values are extracted`() {
        val teach = SmartBoardCanvasCommandEngine.parse("Teach this as right triangle")
        assertEquals(CanvasCommandKind.TEACH_EXAMPLE, teach.kind)
        assertEquals("right triangle", teach.argument)

        val parameter = SmartBoardCanvasCommandEngine.parse("Set parameter amplitude to 2.5")
        assertEquals(CanvasCommandKind.SET_GRAPH_PARAMETER, parameter.kind)
        assertEquals("amplitude", parameter.argument)
        assertEquals(2.5, parameter.numericValue ?: 0.0, 0.0)

        assertEquals(
            CanvasCommandKind.ENABLE_TEACH_MODE,
            SmartBoardCanvasCommandEngine.parse("Enable handwriting adaptation").kind,
        )
        assertEquals(
            CanvasCommandKind.DISABLE_TEACH_MODE,
            SmartBoardCanvasCommandEngine.parse("Turn off Teach mode").kind,
        )
    }

    @Test
    fun `destructive commands require confirmation while reversible commands do not`() {
        val clear = SmartBoardCanvasCommandEngine.parse("Clear the board")
        val delete = SmartBoardCanvasCommandEngine.parse("Delete selection")
        val undo = SmartBoardCanvasCommandEngine.parse("Undo")

        assertTrue(clear.requiresConfirmation)
        assertTrue(delete.requiresConfirmation)
        assertFalse(undo.requiresConfirmation)
    }

    @Test
    fun `unknown prose is not treated as an executable action`() {
        val command = SmartBoardCanvasCommandEngine.parse("The mitochondria is the powerhouse of the cell")
        assertEquals(CanvasCommandKind.UNKNOWN, command.kind)
        assertFalse(command.requiresConfirmation)
    }
}
