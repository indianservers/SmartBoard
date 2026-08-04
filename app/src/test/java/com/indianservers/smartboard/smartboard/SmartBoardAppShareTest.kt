package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.presentation.SmartBoardAppShareContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardAppShareTest {
    @Test
    fun shareMessageContainsDescriptionAndRequestedPlayStoreLink() {
        assertEquals("Try AI Biology", SmartBoardAppShareContent.SUBJECT)
        assertTrue(SmartBoardAppShareContent.DESCRIPTION.contains("students and teachers"))
        assertTrue(SmartBoardAppShareContent.message.contains(SmartBoardAppShareContent.DESCRIPTION))
        assertTrue(
            SmartBoardAppShareContent.message.endsWith(
                "https://play.google.com/store/apps/details?id=com.indianservers.AIbiology",
            ),
        )
    }
}
