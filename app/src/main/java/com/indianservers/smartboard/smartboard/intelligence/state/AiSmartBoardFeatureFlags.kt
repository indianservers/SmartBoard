package com.indianservers.smartboard.smartboard.intelligence.state

enum class AiSmartBoardFeature {
    BOARD_INTELLIGENCE_STATE,
    STRUCTURED_BOARD_OBJECTS,
    INCREMENTAL_REGION_PROCESSING,
    ROBO_ASSISTANT,
    ASSISTANT_SUGGESTION_RAIL,
    GRAPH_MODE,
    LIVE_BOARD_GRAPH_SYNC,
    GRAPH_PARAMETER_SLIDERS,
    GRAPH_INTELLIGENCE,
    MATHEMATICAL_VALIDATOR,
    LESSON_MEMORY,
}

/**
 * Experimental AI behavior is opt-in. Phase 1 deliberately leaves every flag
 * disabled so existing board behavior and persistence remain unchanged.
 */
data class AiSmartBoardFeatureFlags(
    private val enabled: Set<AiSmartBoardFeature> = emptySet(),
) {
    fun isEnabled(feature: AiSmartBoardFeature): Boolean = feature in enabled

    fun with(feature: AiSmartBoardFeature, value: Boolean): AiSmartBoardFeatureFlags =
        copy(enabled = if (value) enabled + feature else enabled - feature)

    fun enabledFeatures(): Set<AiSmartBoardFeature> = enabled.toSet()

    companion object {
        val ProductionDefault = AiSmartBoardFeatureFlags()
    }
}
