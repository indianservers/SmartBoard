package com.indianservers.smartboard.smartboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.indianservers.smartboard.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class SmartBoardMathOnlyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openBoardSettings() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("New Maths Board").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Input & Display Settings").performClick()
        compose.onNodeWithText("Mathematics board").assertExists()
    }

    @Test
    fun boardDoesNotOfferOtherSubjects() {
        listOf("Auto Detect", "Physics", "Chemistry", "English", "Biology", "General").forEach {
            assertTrue(compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty())
        }
        compose.onNodeWithText(
            "Recognition, tutoring and workspace actions are focused on mathematics.",
        ).assertExists()
    }

    @Test
    fun autoShapeSuggestionsUseTheExistingSettingsPanel() {
        compose.onNodeWithText("Auto-shape suggestions").assertExists()
        compose.onNodeWithText("Suggestions never replace ink until you accept them.").assertExists()
    }
}
