package com.indianservers.smartboard.smartboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.indianservers.smartboard.MainActivity
import org.junit.Rule
import org.junit.Test

class SmartBoardShareUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun shareActionIsVisibleInTheBoardHeader() {
        compose.onNodeWithContentDescription("Share app").assertExists()
    }
}
