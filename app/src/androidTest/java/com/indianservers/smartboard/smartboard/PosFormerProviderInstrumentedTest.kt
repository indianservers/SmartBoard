package com.indianservers.smartboard.smartboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.smartboard.smartboard.recognition.ensemble.PosFormerRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionContext
import com.indianservers.smartboard.smartboard.recognition.ensemble.RecognitionInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PosFormerProviderInstrumentedTest {
    @Test
    fun strokeDrawnPowerExpressionProducesSpecialistCandidate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = PosFormerRecognitionProvider(context)
        try {
            val input = RecognitionInput(
                requestId = "posformer-device-smoke",
                requestFingerprint = "stroke-x-power-2-plus-1",
                rasterPng = strokeDrawnPowerExpression(),
                canvasWidth = 640f,
                canvasHeight = 200f,
            )
            val result = provider.recognize(
                input,
                RecognitionContext(
                    requestId = input.requestId,
                    deadlineEpochMillis = System.currentTimeMillis() + 120_000L,
                    debugDiagnostics = true,
                ),
            )
            println("POSFORMER_EXPECTED=x^{2}+1")
            println("POSFORMER_DETECTED=${result.normalizedOutput}")
            println("POSFORMER_CONFIDENCE=${result.overallConfidence}")
            println("POSFORMER_TIMING_MS=${result.timing.totalMillis}")
            assertFalse(result.cancelled)
            assertFalse(result.timedOut)
            assertNotNull(result.rawOutput)
            assertTrue(result.normalizedOutput.orEmpty().isNotBlank())
        } finally {
            provider.release()
        }
    }

    private fun strokeDrawnPowerExpression(): ByteArray {
        val bitmap = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Human-style x: independent, slightly curved pen strokes.
        canvas.drawPath(Path().apply {
            moveTo(70f, 78f)
            cubicTo(90f, 88f, 125f, 132f, 150f, 154f)
        }, ink)
        canvas.drawPath(Path().apply {
            moveTo(150f, 76f)
            cubicTo(128f, 96f, 96f, 136f, 74f, 157f)
        }, ink)

        // Raised 2, deliberately smaller and above the baseline.
        canvas.drawPath(Path().apply {
            moveTo(166f, 62f)
            cubicTo(180f, 42f, 216f, 45f, 218f, 64f)
            cubicTo(219f, 77f, 189f, 91f, 170f, 108f)
            cubicTo(187f, 105f, 207f, 104f, 224f, 105f)
        }, ink)

        canvas.drawLine(270f, 119f, 350f, 119f, ink)
        canvas.drawLine(310f, 82f, 310f, 158f, ink)
        canvas.drawPath(Path().apply {
            moveTo(416f, 91f)
            cubicTo(426f, 86f, 435f, 78f, 444f, 70f)
            cubicTo(443f, 98f, 442f, 127f, 442f, 158f)
        }, ink)

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
