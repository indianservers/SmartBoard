package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.recognition.OfflineLatexPrediction
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionInput
import com.indianservers.smartboard.smartboard.recognition.ensemble.TexTellerRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.ensemble.mapTexTellerPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TexTellerRecognitionProviderTest {
    @Test
    fun `provider preserves raw output separately from normalized latex and timings`() {
        val input = RecognitionInput(
            requestId = "request-1",
            requestFingerprint = "fingerprint-1",
            rasterPng = byteArrayOf(1),
            canvasWidth = 1920f,
            canvasHeight = 1080f,
        )
        val result = mapTexTellerPrediction(
            input = input,
            prediction = OfflineLatexPrediction(
                latex = "x^{2}",
                confidence = .91f,
                tokenCount = 4,
                rawLatex = "\\[x^{2}\\]",
                preprocessingMillis = 12,
                inferenceMillis = 340,
                decodingMillis = 3,
            ),
        )

        assertEquals(TexTellerRecognitionProvider.ProviderId, result.providerId)
        assertEquals("\\[x^{2}\\]", result.rawOutput)
        assertEquals("x^{2}", result.normalizedOutput)
        assertEquals("fingerprint-1", result.requestFingerprint)
        assertEquals(12L, result.timing.preprocessingMillis)
        assertEquals(340L, result.timing.inferenceMillis)
        assertEquals(3L, result.timing.decodingMillis)
        assertEquals(355L, result.timing.totalMillis)
        assertFalse(result.cancelled)
        assertFalse(result.timedOut)
    }
}
