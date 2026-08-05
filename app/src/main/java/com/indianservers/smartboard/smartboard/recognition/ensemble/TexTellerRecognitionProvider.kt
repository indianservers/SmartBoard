package com.indianservers.smartboard.smartboard.recognition.ensemble

import android.content.Context
import com.indianservers.smartboard.smartboard.recognition.OfflineLatexPrediction
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelState
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import com.indianservers.smartboard.smartboard.recognition.TexTellerOnnxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Long-lived TexTeller adapter for the recognition ensemble.
 *
 * Production routing remains unchanged until ENSEMBLE_RECOGNITION is enabled. The provider owns
 * one pair of ONNX sessions, serializes access because the decoder is autoregressive, and checks
 * cancellation both before and after native inference so a superseded request cannot update UI.
 */
class TexTellerRecognitionProvider(
    context: Context,
    private val pack: OfflineMathOcrModelPack = OfflineMathOcrModelPack(context),
) : MathRecognitionProvider {
    override val providerId: String = ProviderId
    override val capabilities = RecognitionCapabilities(
        simpleExpressions = true,
        superscripts = true,
        subscripts = true,
        fractions = true,
        radicals = true,
        matrices = true,
        multilineExpressions = true,
        setsAndLogic = true,
        probabilityNotation = true,
        imageOnly = true,
        expectedDeviceCost = RecognitionDeviceCost.HIGH,
    )

    private val sessionLock = Any()
    private val cancelledRequests = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var runtime: TexTellerOnnxRuntime? = null

    override suspend fun recognize(
        input: RecognitionInput,
        context: RecognitionContext,
    ): ProviderRecognitionResult = withContext(Dispatchers.Default) {
        require(context.requestId == input.requestId) { "Recognition request IDs do not match." }
        require(input.rasterPng.isNotEmpty()) { "TexTeller requires a raster PNG." }
        coroutineContext.ensureActive()
        terminalResultIfNeeded(input, context)?.let { return@withContext it }

        val prediction = synchronized(sessionLock) {
            terminalResultIfNeeded(input, context)?.let { return@synchronized it }
            val model = runtime ?: createRuntime().also { runtime = it }
            model.recognize(input.rasterPng)
        }
        if (prediction is ProviderRecognitionResult) return@withContext prediction

        coroutineContext.ensureActive()
        terminalResultIfNeeded(input, context)?.let { return@withContext it }
        @Suppress("UNCHECKED_CAST")
        mapTexTellerPrediction(input, prediction as OfflineLatexPrediction)
    }

    override suspend fun warmUp() {
        withContext(Dispatchers.Default) {
            synchronized(sessionLock) {
                if (runtime == null) runtime = createRuntime()
            }
        }
    }

    override fun cancel(requestId: String) {
        cancelledRequests += requestId
    }

    override fun release() {
        synchronized(sessionLock) {
            runtime?.close()
            runtime = null
            cancelledRequests.clear()
        }
    }

    private fun createRuntime(): TexTellerOnnxRuntime {
        check(pack.status().state == OfflineMathModelState.READY) {
            "TexTeller model pack is not ready."
        }
        return TexTellerOnnxRuntime(pack)
    }

    private fun terminalResultIfNeeded(
        input: RecognitionInput,
        context: RecognitionContext,
    ): ProviderRecognitionResult? {
        val cancelled = cancelledRequests.remove(input.requestId)
        val timedOut = !cancelled &&
            context.deadlineEpochMillis > 0L &&
            System.currentTimeMillis() >= context.deadlineEpochMillis
        if (!cancelled && !timedOut) return null
        return ProviderRecognitionResult(
            providerId = providerId,
            rawOutput = null,
            normalizedOutput = null,
            overallConfidence = null,
            timing = ProviderRecognitionTiming(0L, 0L, 0L),
            timedOut = timedOut,
            cancelled = cancelled,
            warnings = listOf(if (cancelled) "TexTeller request was superseded." else "TexTeller deadline expired."),
            modelVersion = ModelVersion,
            requestFingerprint = input.requestFingerprint,
        )
    }

    companion object {
        const val ProviderId = "texteller-q4"
        const val ModelVersion = "TexTeller-ONNX-Q4-v2"
    }
}

internal fun mapTexTellerPrediction(
    input: RecognitionInput,
    prediction: OfflineLatexPrediction,
): ProviderRecognitionResult = ProviderRecognitionResult(
    providerId = TexTellerRecognitionProvider.ProviderId,
    rawOutput = prediction.rawLatex,
    normalizedOutput = prediction.latex,
    tokenCandidates = listOf(
        RecognitionTokenCandidate(
            rawToken = prediction.rawLatex,
            normalizedToken = prediction.latex,
            confidence = prediction.confidence,
        ),
    ),
    overallConfidence = prediction.confidence,
    timing = ProviderRecognitionTiming(
        preprocessingMillis = prediction.preprocessingMillis,
        inferenceMillis = prediction.inferenceMillis,
        decodingMillis = prediction.decodingMillis,
    ),
    timedOut = false,
    cancelled = false,
    warnings = listOf("Offline TexTeller primary candidate."),
    modelVersion = TexTellerRecognitionProvider.ModelVersion,
    requestFingerprint = input.requestFingerprint,
)
