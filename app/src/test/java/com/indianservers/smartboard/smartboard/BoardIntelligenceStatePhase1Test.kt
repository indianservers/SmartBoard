package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.intelligence.state.AiSmartBoardFeature
import com.indianservers.smartboard.smartboard.intelligence.state.AiSmartBoardFeatureFlags
import com.indianservers.smartboard.smartboard.intelligence.state.BoardIntelligenceState
import com.indianservers.smartboard.smartboard.intelligence.state.BoardPageState
import com.indianservers.smartboard.smartboard.intelligence.state.BoardProcessingState
import com.indianservers.smartboard.smartboard.intelligence.state.VersionedBoardResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardIntelligenceStatePhase1Test {
    @Test
    fun productionDefaultsKeepEveryExperimentalFeatureDisabled() {
        AiSmartBoardFeature.entries.forEach { feature ->
            assertFalse(AiSmartBoardFeatureFlags.ProductionDefault.isEnabled(feature))
        }
    }

    @Test
    fun aFeatureCanBeEnabledWithoutMutatingTheDefaultSet() {
        val enabled = AiSmartBoardFeatureFlags.ProductionDefault.with(
            AiSmartBoardFeature.ROBO_ASSISTANT,
            true,
        )

        assertTrue(enabled.isEnabled(AiSmartBoardFeature.ROBO_ASSISTANT))
        assertFalse(
            AiSmartBoardFeatureFlags.ProductionDefault.isEnabled(
                AiSmartBoardFeature.ROBO_ASSISTANT,
            ),
        )
    }

    @Test
    fun staleRegionResultsCannotReplaceNewBoardContent() {
        val state = BoardIntelligenceState(
            documentId = "lesson-1",
            pages = listOf(BoardPageState("page-1", "document-page-1")),
            activePageId = "page-1",
            processingState = BoardProcessingState(
                contentVersion = 7,
                regionVersions = mapOf("region-a" to 3),
            ),
        )

        assertTrue(
            state.accepts(
                VersionedBoardResult("lesson-1", "page-1", "region-a", 7, 3),
            ),
        )
        assertFalse(
            state.accepts(
                VersionedBoardResult("lesson-1", "page-1", "region-a", 6, 3),
            ),
        )
        assertFalse(
            state.accepts(
                VersionedBoardResult("lesson-1", "page-1", "region-a", 7, 2),
            ),
        )
    }
}
