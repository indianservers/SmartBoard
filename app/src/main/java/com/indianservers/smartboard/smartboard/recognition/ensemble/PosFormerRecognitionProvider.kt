package com.indianservers.smartboard.smartboard.recognition.ensemble

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

enum class PosFormerModelState { NOT_INSTALLED, READY, INVALID }

data class PosFormerModelStatus(
    val state: PosFormerModelState,
    val message: String,
    val installedBytes: Long,
    val totalBytes: Long = PosFormerModelPack.TotalBytes,
    val usageRestriction: String = PosFormerModelPack.UsageRestriction,
)

/**
 * Optional specialist pack derived from the official PosFormer CROHME checkpoint.
 *
 * PosFormer is enabled only for the confirmed education deployment. Files are imported instead
 * of downloaded silently so the academic-use restriction remains visible at provisioning time.
 */
class PosFormerModelPack(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DirectoryName)

    fun status(): PosFormerModelStatus {
        val valid = Artifacts.all { artifact ->
            File(directory, artifact.name).let { it.isFile && it.length() == artifact.bytes }
        }
        val installed = Artifacts.sumOf { artifact ->
            File(directory, artifact.name).length().coerceAtMost(artifact.bytes)
        }
        return when {
            valid -> PosFormerModelStatus(
                PosFormerModelState.READY,
                "PosFormer academic specialist ready",
                installed,
            )
            installed > 0L -> PosFormerModelStatus(
                PosFormerModelState.INVALID,
                "PosFormer specialist files are incomplete",
                installed,
            )
            else -> PosFormerModelStatus(
                PosFormerModelState.NOT_INSTALLED,
                "PosFormer academic specialist is not installed",
                0L,
            )
        }
    }

    suspend fun importFrom(sourceDirectory: File): Result<PosFormerModelStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(sourceDirectory.isDirectory) { "PosFormer source directory does not exist." }
                directory.mkdirs()
                Artifacts.forEach { artifact ->
                    val source = File(sourceDirectory, artifact.name)
                    require(source.isFile && source.length() == artifact.bytes) {
                        "${artifact.name} has an unexpected size."
                    }
                    require(sha256(source) == artifact.sha256) {
                        "${artifact.name} failed integrity verification."
                    }
                    source.copyTo(File(directory, artifact.name), overwrite = true)
                }
                status().also { check(it.state == PosFormerModelState.READY) }
            }
        }

    internal fun encoderFile() = File(directory, Encoder.name)
    internal fun decoderFile() = File(directory, Decoder.name)
    internal fun vocabularyFile() = File(directory, Vocabulary.name)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Artifact(val name: String, val bytes: Long, val sha256: String)

    companion object {
        const val UsageRestriction = "Education and academic research only"
        private const val DirectoryName = "offline_models/posformer-crohme-v1"
        private val Encoder = Artifact(
            "posformer_encoder.onnx",
            12_671_986,
            "16696cded9aa9b867620d0c751d9f739dbb558825093c75bf61eff4cecf8fa6d",
        )
        private val Decoder = Artifact(
            "posformer_decoder.onnx",
            13_674_143,
            "75c0cbd5dfce8697fdeb4f0dfd37090e278b5bd25eebd30cf7d6ac49e15302bd",
        )
        private val Vocabulary = Artifact(
            "dictionary.txt",
            478,
            "0f11c42ea5075319f5b8664c7f5c18ab65b59e340d7e8fdfe85a79bd2d7c20bf",
        )
        private val Artifacts = listOf(Encoder, Decoder, Vocabulary)
        const val TotalBytes = 26_346_607L
    }
}

internal data class PosFormerPrediction(
    val rawLatex: String,
    val latex: String,
    val confidence: Float,
    val tokenCount: Int,
    val preprocessingMillis: Long,
    val inferenceMillis: Long,
    val decodingMillis: Long,
)

private data class PosFormerImageTensor(
    val pixels: FloatBuffer,
    val mask: Array<Array<BooleanArray>>,
    val height: Int,
    val width: Int,
)

private object PosFormerImagePreprocessor {
    fun tensor(bytes: ByteArray): PosFormerImageTensor {
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "The selected image is unreadable."
        }
        val crop = contentBounds(source)
        val scaleDown = minOf(1f, MaximumHeight.toFloat() / crop.height(), MaximumWidth.toFloat() / crop.width())
        val downWidth = max(1, (crop.width() * scaleDown).toInt())
        val downHeight = max(1, (crop.height() * scaleDown).toInt())
        val scaleUp = maxOf(1f, MinimumHeight.toFloat() / downHeight, MinimumWidth.toFloat() / downWidth)
        val width = max(1, (downWidth * scaleUp).toInt()).coerceAtMost(MaximumWidth)
        val height = max(1, (downHeight * scaleUp).toInt()).coerceAtMost(MaximumHeight)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, crop, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        val colors = IntArray(width * height)
        output.getPixels(colors, 0, width, 0, 0, width, height)
        val values = FloatArray(colors.size)
        colors.forEachIndexed { index, color ->
            // CROHME/PosFormer training tensors use a black background (0) with
            // white ink (1), while SMART Board renders black ink on white.
            values[index] = 1f - (
                .299f * Color.red(color) +
                    .587f * Color.green(color) +
                    .114f * Color.blue(color)
                ) / 255f
        }
        if (source !== output) source.recycle()
        output.recycle()
        return PosFormerImageTensor(
            pixels = FloatBuffer.wrap(values),
            mask = arrayOf(Array(height) { BooleanArray(width) }),
            height = height,
            width = width,
        )
    }

    private fun contentBounds(bitmap: Bitmap): Rect {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        colors.forEachIndexed { index, color ->
            val luminance = .299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color)
            if (luminance < 242f) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return Rect(0, 0, bitmap.width, bitmap.height)
        val padding = max(3, max(right - left, bottom - top) / 45)
        return Rect(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding + 1).coerceAtMost(bitmap.width),
            (bottom + padding + 1).coerceAtMost(bitmap.height),
        )
    }

    private const val MinimumHeight = 16
    private const val MinimumWidth = 16
    private const val MaximumHeight = 256
    private const val MaximumWidth = 1024
}

internal class PosFormerOnnxRuntime(private val pack: PosFormerModelPack) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setInterOpNumThreads(1)
        setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors().coerceAtMost(4)))
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val encoder = environment.createSession(pack.encoderFile().absolutePath, options)
    private val decoder = environment.createSession(pack.decoderFile().absolutePath, options)
    private val vocabulary = buildMap {
        put(0, "<pad>")
        put(1, "<sos>")
        put(2, "<eos>")
        pack.vocabularyFile().readLines().forEachIndexed { index, token -> put(index + 3, token) }
    }

    fun recognize(bytes: ByteArray, maximumTokens: Int = 96): PosFormerPrediction {
        val preprocessingStarted = System.nanoTime()
        val image = PosFormerImagePreprocessor.tensor(bytes)
        val preprocessingMillis = elapsedMillis(preprocessingStarted)
        OnnxTensor.createTensor(
            environment,
            image.pixels,
            longArrayOf(1, 1, image.height.toLong(), image.width.toLong()),
        ).use { imageTensor ->
            OnnxTensor.createTensor(environment, image.mask).use { maskTensor ->
                val inferenceStarted = System.nanoTime()
                encoder.run(mapOf("image" to imageTensor, "image_mask" to maskTensor)).use { encoded ->
                    val feature = encoded[0] as OnnxTensor
                    val featureMask = encoded[1] as OnnxTensor
                    val ids = mutableListOf(1L)
                    var logProbability = 0.0
                    var predicted = 0
                    while (ids.size <= maximumTokens) {
                        OnnxTensor.createTensor(
                            environment,
                            LongBuffer.wrap(ids.toLongArray()),
                            longArrayOf(1, ids.size.toLong()),
                        ).use { tokenTensor ->
                            decoder.run(
                                mapOf(
                                    "feature" to feature,
                                    "feature_mask" to featureMask,
                                    "token_ids" to tokenTensor,
                                ),
                            ).use { decoded ->
                                @Suppress("UNCHECKED_CAST")
                                val logits = decoded[0].value as Array<Array<FloatArray>>
                                val last = logits[0].last()
                                val next = last.indices.maxByOrNull(last::get) ?: EndToken
                                if (next == EndToken) break
                                ids += next.toLong()
                                logProbability += ln(softmaxProbability(last, next).coerceAtLeast(1e-7f).toDouble())
                                predicted++
                            }
                        }
                    }
                    val inferenceMillis = elapsedMillis(inferenceStarted)
                    val decodingStarted = System.nanoTime()
                    val raw = ids.drop(1).mapNotNull { vocabulary[it.toInt()] }.joinToString(" ").trim()
                    require(raw.isNotBlank()) { "PosFormer returned an empty expression." }
                    val normalized = raw.replace(Regex("""\s+"""), " ").trim()
                    val confidence = if (predicted == 0) .2f else {
                        exp(logProbability / predicted).toFloat().coerceIn(.2f, .995f)
                    }
                    return PosFormerPrediction(
                        rawLatex = raw,
                        latex = normalized,
                        confidence = confidence,
                        tokenCount = predicted,
                        preprocessingMillis = preprocessingMillis,
                        inferenceMillis = inferenceMillis,
                        decodingMillis = elapsedMillis(decodingStarted),
                    )
                }
            }
        }
    }

    private fun softmaxProbability(logits: FloatArray, selected: Int): Float {
        val maximum = logits.maxOrNull() ?: return 0f
        var denominator = 0.0
        logits.forEach { denominator += exp((it - maximum).toDouble()) }
        return (exp((logits[selected] - maximum).toDouble()) / denominator.coerceAtLeast(1e-12)).toFloat()
    }

    private fun elapsedMillis(started: Long) = (System.nanoTime() - started) / 1_000_000L

    override fun close() {
        encoder.close()
        decoder.close()
        options.close()
    }

    private companion object {
        const val EndToken = 2
    }
}

class PosFormerRecognitionProvider(
    context: Context,
    private val pack: PosFormerModelPack = PosFormerModelPack(context),
) : MathRecognitionProvider {
    override val providerId = ProviderId
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
    @Volatile private var runtime: PosFormerOnnxRuntime? = null

    override suspend fun recognize(input: RecognitionInput, context: RecognitionContext): ProviderRecognitionResult =
        withContext(Dispatchers.Default) {
            require(context.requestId == input.requestId)
            require(input.rasterPng.isNotEmpty())
            coroutineContext.ensureActive()
            terminalResult(input, context)?.let { return@withContext it }
            val prediction = synchronized(sessionLock) {
                terminalResult(input, context)?.let { return@synchronized it }
                val model = runtime ?: createRuntime().also { runtime = it }
                model.recognize(input.rasterPng)
            }
            if (prediction is ProviderRecognitionResult) return@withContext prediction
            coroutineContext.ensureActive()
            terminalResult(input, context)?.let { return@withContext it }
            mapPrediction(input, prediction as PosFormerPrediction)
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

    private fun createRuntime(): PosFormerOnnxRuntime {
        check(pack.status().state == PosFormerModelState.READY) { "PosFormer model pack is not ready." }
        return PosFormerOnnxRuntime(pack)
    }

    private fun terminalResult(input: RecognitionInput, context: RecognitionContext): ProviderRecognitionResult? {
        val cancelled = cancelledRequests.remove(input.requestId)
        val timedOut = !cancelled && context.deadlineEpochMillis > 0 &&
            System.currentTimeMillis() >= context.deadlineEpochMillis
        if (!cancelled && !timedOut) return null
        return ProviderRecognitionResult(
            providerId = providerId,
            rawOutput = null,
            normalizedOutput = null,
            overallConfidence = null,
            timing = ProviderRecognitionTiming(0, 0, 0),
            timedOut = timedOut,
            cancelled = cancelled,
            warnings = listOf(if (cancelled) "PosFormer request was superseded." else "PosFormer deadline expired."),
            modelVersion = ModelVersion,
            requestFingerprint = input.requestFingerprint,
        )
    }

    private fun mapPrediction(input: RecognitionInput, prediction: PosFormerPrediction) =
        ProviderRecognitionResult(
            providerId = providerId,
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
                prediction.preprocessingMillis,
                prediction.inferenceMillis,
                prediction.decodingMillis,
            ),
            timedOut = false,
            cancelled = false,
            warnings = listOf(
                "PosFormer academic specialist candidate.",
                "Usage restriction: ${PosFormerModelPack.UsageRestriction}.",
                "Initial Android decoder uses deterministic greedy search; official bidirectional beam parity is pending.",
            ),
            modelVersion = ModelVersion,
            requestFingerprint = input.requestFingerprint,
        )

    companion object {
        const val ProviderId = "posformer-crohme"
        const val ModelVersion = "PosFormer-CROHME-epoch206-onnx-v1"
    }
}
