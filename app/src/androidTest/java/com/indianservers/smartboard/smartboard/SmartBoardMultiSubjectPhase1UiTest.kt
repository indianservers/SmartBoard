package com.indianservers.smartboard.smartboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.indianservers.smartboard.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SmartBoardMultiSubjectPhase1UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openBoardSettings() {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("New Mathematics Board").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Input & Display Settings").performClick()
        compose.onNodeWithText("Classroom subject").assertExists()
    }

    @Test
    fun boardOffersTheFourPhaseOneSubjectsAndAutoDetection() {
        listOf("Auto Detect", "Physics", "Chemistry", "Biology").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText(
            "Auto Detect routes each selection locally.",
            substring = true,
        ).assertExists()
    }

    @Test
    fun mathematicsCanSelectGraphRecognitionMode() {
        compose.onNodeWithText("Graph mode").performScrollTo().performClick()
        compose.onNodeWithText("Graph mode recognizes mathematical handwriting", substring = true).assertExists()
    }
}
