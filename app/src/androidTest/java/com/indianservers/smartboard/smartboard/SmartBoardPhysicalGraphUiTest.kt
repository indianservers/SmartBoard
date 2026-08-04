package com.indianservers.smartboard.smartboard

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.indianservers.smartboard.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SmartBoardPhysicalGraphUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun showTestActivityAboveKeyguard() {
        compose.activity.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
        compose.waitForIdle()
    }

    @Test
    fun graphEquationOpensAndDisplaysAPlotCanvas() {
        compose.onNodeWithContentDescription("More").performClick()
        if (compose.onAllNodesWithText("Graph Editor").fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("Neither a board nor its Graph Editor was available", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
            compose.onNodeWithContentDescription("More").performClick()
        }
        compose.onNodeWithText("Graph Editor").performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("sin(x)")
        compose.onNodeWithText("Open full Graph Editor").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("sampled points", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Plot of sin(x)", substring = true)).assertExists()
    }

    @Test
    fun bottomToolbarCanPageToRecognitionAndGraphButtons() {
        if (compose.onAllNodes(hasContentDescription("Next toolbar tools")).fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("The mathematics board could not be opened", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
        }
        fun displayedDescription(description: String): Boolean =
            compose.onAllNodes(hasContentDescription(description, substring = true)).let { matches ->
                matches.fetchSemanticsNodes().indices.any { index ->
                    runCatching { matches[index].assertIsDisplayed() }.isSuccess
                }
            }
        fun graphControlDisplayed(): Boolean = displayedDescription("Graph")

        var recognizeSeen = displayedDescription("Recognize")
        var graphSeen = graphControlDisplayed()
        repeat(6) {
            if (!recognizeSeen || !graphSeen) {
                compose.onNodeWithContentDescription("Next toolbar tools").performClick()
                compose.waitForIdle()
                recognizeSeen = recognizeSeen || displayedDescription("Recognize")
                graphSeen = graphSeen || graphControlDisplayed()
            }
        }
        assertTrue("Recognize was not reachable using the toolbar paging button", recognizeSeen)
        assertTrue("The Graph AI mode control was not reachable using the toolbar paging button", graphSeen)
        compose.onNodeWithContentDescription("Previous toolbar tools").performClick()
    }

    @Test
    fun clearBoardRemovesCanvasContentWithOneTap() {
        val canvases = compose.onAllNodes(hasContentDescription("Vector Smart Board canvas", substring = true))
        if (canvases.fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("The mathematics board could not be opened", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
        }

        runCatching { compose.onNodeWithContentDescription("Clear Board").performClick() }
        compose.onNode(hasContentDescription("Vector Smart Board canvas", substring = true)).performTouchInput {
            swipe(centerLeft, centerRight, 300L)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("with 1 elements", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Clear Board").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("with 0 elements", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
