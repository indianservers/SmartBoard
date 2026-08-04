package com.indianservers.smartboard.smartboard

import android.content.res.Configuration
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.indianservers.smartboard.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        compose.waitUntil(8_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
        compose.onNodeWithText("Graph Editor").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Open full Graph Editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("sin(x)")
        compose.onNodeWithText("Open full Graph Editor").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("sampled points", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Plot of sin(x)", substring = true)).assertExists()
    }

    @Test
    fun activityStartsInLandscape() {
        assertEquals(
            Configuration.ORIENTATION_LANDSCAPE,
            compose.activity.resources.configuration.orientation,
        )
    }

    @Test
    fun toolbarKeepsRecognitionGraphAndCommandsReachable() {
        if (compose.onAllNodes(hasContentDescription("Vector Smart Board canvas", substring = true)).fetchSemanticsNodes().isEmpty()) {
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
        var commandSeen = displayedDescription("AI Command")
        val horizontalPaging = compose.onAllNodes(hasContentDescription("Next toolbar tools")).fetchSemanticsNodes().isNotEmpty()
        if (horizontalPaging) {
            repeat(8) {
                if (!recognizeSeen || !graphSeen || !commandSeen) {
                    compose.onNodeWithContentDescription("Next toolbar tools").performClick()
                    compose.waitForIdle()
                    recognizeSeen = recognizeSeen || displayedDescription("Recognize")
                    graphSeen = graphSeen || graphControlDisplayed()
                    commandSeen = commandSeen || displayedDescription("AI Command")
                }
            }
            repeat(8) {
                if (!displayedDescription("AI Command")) {
                    compose.onNodeWithContentDescription("Next toolbar tools").performClick()
                    compose.waitForIdle()
                }
            }
        } else {
            compose.onNode(hasContentDescription("Recognize", substring = true)).performScrollTo().assertIsDisplayed()
            compose.onNode(hasContentDescription("Graph AI", substring = true)).performScrollTo().assertIsDisplayed()
            compose.onNode(hasContentDescription("AI Command", substring = true)).performScrollTo().assertIsDisplayed()
            recognizeSeen = true
            graphSeen = true
            commandSeen = true
        }
        assertTrue("Recognize was not reachable using the toolbar paging button", recognizeSeen)
        assertTrue("The Graph AI mode control was not reachable using the toolbar paging button", graphSeen)
        assertTrue("Natural-language canvas commands were not reachable using toolbar paging", commandSeen)
        compose.onNode(hasContentDescription("AI Command", substring = true)).performClick()
        compose.onNodeWithText("Natural-language canvas commands").assertIsDisplayed()
        compose.onNodeWithText("Teach SMART Board mode", substring = true).assertIsDisplayed()
    }

    @Test
    fun humanEquationStrokesConvertToEditableGraphWithOneButton() {
        if (compose.onAllNodes(hasContentDescription("Vector Smart Board canvas", substring = true)).fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("The mathematics board could not be opened", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
        }
        runCatching { compose.onNodeWithContentDescription("Clear Board").performClick() }
        val canvas = compose.onNode(hasContentDescription("Vector Smart Board canvas", substring = true))
        canvas.performTouchInput {
            fun humanStroke(points: List<Offset>) {
                down(points.first())
                points.drop(1).forEach { moveTo(it, 42L) }
                up()
            }
            humanStroke(
                listOf(
                    Offset(width * .12f, height * .25f),
                    Offset(width * .15f, height * .35f),
                    Offset(width * .18f, height * .25f),
                ),
            )
            humanStroke(
                listOf(
                    Offset(width * .15f, height * .35f),
                    Offset(width * .145f, height * .42f),
                    Offset(width * .135f, height * .49f),
                ),
            )
            humanStroke(listOf(Offset(width * .22f, height * .31f), Offset(width * .29f, height * .31f)))
            humanStroke(listOf(Offset(width * .22f, height * .37f), Offset(width * .29f, height * .37f)))
            humanStroke(listOf(Offset(width * .33f, height * .25f), Offset(width * .40f, height * .43f)))
            humanStroke(listOf(Offset(width * .40f, height * .25f), Offset(width * .33f, height * .43f)))
        }

        fun equationGraphButtonDisplayed(): Boolean {
            val nodes = compose.onAllNodes(hasContentDescription("Eq → Graph", substring = true))
            return nodes.fetchSemanticsNodes().indices.any { index ->
                runCatching { nodes[index].assertIsDisplayed() }.isSuccess
            }
        }
        val hasToolbarPaging =
            compose.onAllNodes(hasContentDescription("Next toolbar tools")).fetchSemanticsNodes().isNotEmpty()
        if (hasToolbarPaging) {
            repeat(10) {
                if (!equationGraphButtonDisplayed()) {
                    compose.onNodeWithContentDescription("Next toolbar tools").performClick()
                    compose.waitForIdle()
                }
            }
        } else {
            compose.onNode(hasContentDescription("Eq → Graph", substring = true)).performScrollTo()
            compose.waitForIdle()
        }
        assertTrue("Equation to Graph was not reachable from the landscape toolbar", equationGraphButtonDisplayed())
        compose.waitUntil(120_000) {
            runCatching {
                compose.onNode(hasContentDescription("Eq → Graph", substring = true)).assertIsEnabled()
            }.isSuccess
        }
        compose.onNode(hasContentDescription("Eq → Graph", substring = true)).performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("sampled points", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasContentDescription("Plot of", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Plot of", substring = true)).assertExists()
    }

    @Test
    fun workspaceHudQuickControlsAndFocusModeStayUsable() {
        if (compose.onAllNodes(hasContentDescription("Vector Smart Board canvas", substring = true)).fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("The mathematics board could not be opened", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
        }

        compose.onNodeWithText("Controls").assertIsDisplayed().performClick()
        compose.onNodeWithText("Quick Controls").assertIsDisplayed()
        compose.onNodeWithText("Ink style").assertIsDisplayed()
        compose.onNode(hasContentDescription("Cyan ink colour", substring = true)).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Bold").performScrollTo().performClick()
        compose.onNodeWithContentDescription("65%").performScrollTo().performClick()
        compose.onNodeWithText("+ Zoom").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Dots").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close").performScrollTo().performClick()

        compose.onNodeWithText("Focus").assertIsDisplayed().performClick()
        compose.onNodeWithText("Exit focus").assertIsDisplayed()
        assertTrue("The top navigation should be hidden in Focus Mode", compose.onAllNodesWithText("Back").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Exit focus").performClick()
        compose.onNodeWithText("Back").assertIsDisplayed()
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

    @Test
    fun canvasIntelligenceGroupsGraphStrokesAndOffersTeaching() {
        val canvases = compose.onAllNodes(hasContentDescription("Vector Smart Board canvas", substring = true))
        if (canvases.fetchSemanticsNodes().isEmpty()) {
            val newBoard = compose.onAllNodesWithText("New Mathematics Board")
            assertTrue("The mathematics board could not be opened", newBoard.fetchSemanticsNodes().isNotEmpty())
            newBoard[0].performClick()
        }
        runCatching { compose.onNodeWithContentDescription("Clear Board").performClick() }
        val canvas = compose.onNode(hasContentDescription("Vector Smart Board canvas", substring = true))
        canvas.performTouchInput {
            swipe(Offset(width * .18f, height * .50f), Offset(width * .82f, height * .50f), 280L)
        }
        canvas.performTouchInput {
            swipe(Offset(width * .50f, height * .22f), Offset(width * .50f, height * .78f), 280L)
        }
        canvas.performTouchInput {
            down(Offset(width * .28f, height * .35f))
            moveTo(Offset(width * .38f, height * .54f), 45L)
            moveTo(Offset(width * .50f, height * .62f), 45L)
            moveTo(Offset(width * .62f, height * .54f), 45L)
            moveTo(Offset(width * .72f, height * .35f), 45L)
            up()
        }

        compose.waitUntil(8_000) {
            compose.onAllNodes(hasContentDescription("result groups ready", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("result groups ready", substring = true)).performClick()
        compose.onNodeWithText("Canvas intelligence").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ranked object hypotheses").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Teach example").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("uncertain stroke region", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Semantic Canvas Graph").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Meaning-based selection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Canvas-wide semantic search").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mathematics and graph intelligence").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Graph-from-ink", substring = true).performScrollTo().assertIsDisplayed()
    }
}
