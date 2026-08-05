package com.indianservers.smartboard.smartboard

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.MainActivity
import com.indianservers.smartboard.smartboard.presentation.assistant.RoboAssistantMood
import com.indianservers.smartboard.smartboard.presentation.assistant.SmartBoardRoboAssistantFace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardRoboAssistantUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun vectorRobotExposesEveryAssistantMoodAccessibly() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                Row {
                    RoboAssistantMood.entries.forEach { mood ->
                        SmartBoardRoboAssistantFace(
                            mood = mood,
                            reducedMotion = true,
                            contentDescription = "SMART Board assistant ${mood.name}",
                        )
                    }
                }
            }
        }

        RoboAssistantMood.entries.forEach { mood ->
            composeRule
                .onNodeWithContentDescription("SMART Board assistant ${mood.name}")
                .assertExists()
        }
    }
}
