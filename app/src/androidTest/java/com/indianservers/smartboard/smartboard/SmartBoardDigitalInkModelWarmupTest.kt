package com.indianservers.smartboard.smartboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit emulator/CI provisioning check. It is intentionally separate from accuracy tests so a
 * missing downloadable ML Kit model cannot be confused with recognition quality.
 */
@RunWith(AndroidJUnit4::class)
class SmartBoardDigitalInkModelWarmupTest {
    @Test
    fun ensureEnglishDigitalInkModelIsAvailable() = runBlocking {
        val strokes = HumanInkWriter.write("x+1", seed = 42)
        val bounds = SmartBoardBounds.from(
            strokes.flatMap { stroke -> stroke.points.map { it.position } },
        ).expand(16f)
        val result = withTimeout(300_000L) {
            MlKitMathRecognitionAdapter().recognize(
                MathRecognitionInput(strokes, bounds, byteArrayOf(), "model-warmup"),
                MathRecognitionOptions(languageTag = "en-US", maximumAlternatives = 3),
            )
        }
        assertTrue(result.latex.isNotBlank())
    }
}
