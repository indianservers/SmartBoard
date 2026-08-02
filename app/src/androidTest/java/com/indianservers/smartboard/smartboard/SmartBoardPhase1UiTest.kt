package com.indianservers.smartboard.smartboard

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.indianservers.smartboard.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Device-run Phase 1 acceptance scenarios. These intentionally exercise the real custom
 * Android canvas hosted by Compose rather than replacing it with a test-only fake.
 */
class SmartBoardPhase1UiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSmartBoard() {
        compose.onNodeWithText("Smart Board · Mathematics · integrated workspace").assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("New Maths Board").performClick()
    }

    @Test
    fun openingSmartBoardKeepsStandaloneNavigationAvailable() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Graph Editor").assertExists()
        compose.onNodeWithText("Input & Display Settings").assertExists()
    }

    @Test
    fun drawingAStrokeCommitsVectorInk() {
        drawStroke()
        compose.onNodeWithText("Stroke added").assertExists()
    }

    @Test
    fun switchingToolsShowsSelectedState() {
        compose.onNodeWithContentDescription("Pencil tool").performClick()
        compose.onNodeWithContentDescription("Pencil tool, selected").assertIsSelected()
    }

    @Test
    fun undoAndRedoOperateOnCommittedStroke() {
        drawStroke()
        compose.onNodeWithContentDescription("Undo tool").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Redo tool").performClick()
        compose.onNodeWithText("Redo complete", substring = true).assertExists()
    }

    @Test
    fun rectangleSelectionExposesEditingActions() {
        drawStroke()
        compose.onNodeWithContentDescription("Box tool").performClick()
        compose.onNodeWithContentDescription("Box tool, selected").assertIsSelected()
        canvas().performTouchInput {
            swipe(Offset(2f, 2f), Offset(width - 2f, height - 2f), 350L)
        }
        compose.onNodeWithContentDescription("Duplicate").assertExists()
    }

    @Test
    fun recognitionOpensReviewWithoutDeletingInk() {
        drawStroke()
        compose.onAllNodesWithText("Recognize")[0].performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Recognition Review").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("On-device recognition", substring = true).assertExists()
    }

    @Test
    fun recognizedLatexCanBeEdited() {
        drawStroke()
        compose.onAllNodesWithText("Recognize")[0].performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Recognition Review").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("\\frac{1}{2}")
        compose.onNodeWithText("\\frac{1}{2}").assertExists()
    }

    @Test
    fun confirmingRecognitionInsertsExpression() {
        drawStroke()
        compose.onAllNodesWithText("Recognize")[0].performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Insert expression").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Insert expression").performClick()
        compose.onNodeWithText("Recognized expression inserted; source strokes preserved").assertExists()
    }

    @Test
    fun boardCanBeSaved() {
        drawStroke()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved Untitled Board").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun savedBoardAppearsInRecentBoards() {
        drawStroke()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.onNodeWithContentDescription("Open").performClick()
        compose.onNodeWithText("Open Smart Board").assertExists()
        compose.onAllNodesWithText("Untitled Board", substring = true)[0].assertExists()
    }

    @Test
    fun savedBoardCanBeOpenedAndEditedAgain() {
        drawStroke()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved Untitled Board").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Open").performClick()
        compose.onAllNodesWithText("Open & edit")[0].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("select any object to continue editing", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        drawStroke()
        compose.onNodeWithText("Stroke added").assertExists()
    }

    private fun drawStroke() {
        canvas().performTouchInput { swipe(centerLeft, centerRight, 300L) }
        compose.waitForIdle()
    }

    private fun canvas() = compose.onNode(hasContentDescription("Vector Smart Board canvas", substring = true))
}
