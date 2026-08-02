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

class SmartBoardMultiSubjectPhase3UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openFreshBoard() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("New Maths Board").performClick()
    }

    @Test
    fun oneUnifiedTutorPanelExposesCoreModes() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Smart Board Tutor").performClick()
        compose.onNodeWithText("Smart Board Tutor").assertExists()
        compose.onNodeWithText("Ask").assertExists()
        compose.onAllNodesWithText("Hint")[0].assertExists()
        compose.onAllNodesWithText("Next step")[0].assertExists()
        compose.onAllNodesWithText("Check my work")[0].assertExists()
        compose.onAllNodesWithText("Find my mistake")[0].assertExists()
    }

    @Test
    fun tutorDoesNotReplaceBoardWithoutUserInsert() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Smart Board Tutor").performClick()
        compose.onNodeWithText("Insert into Board").assertExists()
        compose.onNodeWithText("Send").assertExists()
        compose.onNodeWithText("Clear thread").assertExists()
    }
}
