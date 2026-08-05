package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.recognition.ensemble.ProviderRecognitionResult
import com.indianservers.smartboard.smartboard.recognition.ensemble.ProviderRecognitionTiming
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionEnsembleFeature
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionEnsembleFeatureFlags
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionRequestGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionProviderContractTest {
    @Test
    fun productionDefaultsDoNotChangeTheRecognitionPipeline() {
        RecognitionEnsembleFeature.entries.forEach { feature ->
            assertFalse(RecognitionEnsembleFeatureFlags.ProductionDefault.isEnabled(feature))
        }
    }

    @Test
    fun providerResultKeepsRawAndNormalizedOutputsSeparate() {
        val result = ProviderRecognitionResult(
            providerId = "fixture",
            rawOutput = "\\frac 1 2",
            normalizedOutput = "\\frac{1}{2}",
            overallConfidence = .8f,
            timing = ProviderRecognitionTiming(4, 8, 2),
            timedOut = false,
            cancelled = false,
            modelVersion = "fixture-1",
            requestFingerprint = "fingerprint-1",
        )

        assertEquals("\\frac 1 2", result.rawOutput)
        assertEquals("\\frac{1}{2}", result.normalizedOutput)
        assertEquals(14L, result.timing.totalMillis)
    }

    @Test
    fun lateOrCancelledProviderResultsAreRejected() {
        val gate = RecognitionRequestGate()
        val first = gate.begin("recognize", "ink-v1")
        val second = gate.begin("recognize", "ink-v2")

        assertFalse(gate.accepts("recognize", "ink-v1", first))
        assertTrue(gate.accepts("recognize", "ink-v2", second))

        gate.cancel("recognize")
        assertFalse(gate.accepts("recognize", "ink-v2", second))
    }
}
