package com.indianservers.smartboard.smartboard.recognition.ensemble

enum class RecognitionEnsembleFeature {
    ENSEMBLE_RECOGNITION,
    STRUCTURE_SPECIALIST,
    SYMBOL_RERANKER,
    STRUCTURAL_CONSENSUS,
    MATRIX_SPECIALIST,
    ADVANCED_GRAPH_ROUTING,
    MODEL_PACK_DOWNLOADS,
    DETAILED_RECOGNITION_DIAGNOSTICS,
}
data class RecognitionEnsembleFeatureFlags(
    private val enabled: Set<RecognitionEnsembleFeature> = emptySet(),
) {
    fun isEnabled(feature: RecognitionEnsembleFeature): Boolean = feature in enabled

    fun with(
        feature: RecognitionEnsembleFeature,
        value: Boolean,
    ): RecognitionEnsembleFeatureFlags =
        copy(enabled = if (value) enabled + feature else enabled - feature)

    fun enabledFeatures(): Set<RecognitionEnsembleFeature> = enabled.toSet()

    companion object {
        val ProductionDefault = RecognitionEnsembleFeatureFlags()
    }
}
