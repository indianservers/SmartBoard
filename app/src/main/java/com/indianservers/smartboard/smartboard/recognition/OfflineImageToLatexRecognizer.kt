package com.indianservers.smartboard.smartboard.recognition

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
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

enum class OfflineMathModelState {
    NOT_INSTALLED,
    INSTALLING,
    READY,
    INVALID,
}

data class OfflineMathModelStatus(
    val state: OfflineMathModelState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = OfflineMathOcrModelPack.TOTAL_BYTES,
    val message: String = "",
) {
    val progress: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

private data class ModelArtifact(
    val fileName: String,
    val relativeUrl: String,
    val expectedBytes: Long,
    val sha256: String,
)

/**
 * One-time installer for the Apache-2.0 TexTeller formula-recognition model.
 *
 * The weights live in private app storage and are usable without a network connection after
 * installation. They are intentionally not embedded in the base APK because the quantized model
 * is about 255 MB. Partial downloads are retained and resumed.
 */
class OfflineMathOcrModelPack(context: Context) {
    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "offline_models/texteller-q4-v2")

    @Volatile
    private var installing = false

    fun status(): OfflineMathModelStatus {
        if (installing) {
            val downloaded = Artifacts.sumOf { artifact ->
                File(modelDirectory, "${artifact.fileName}.part").length().coerceAtMost(artifact.expectedBytes)
            }
            return OfflineMathModelStatus(OfflineMathModelState.INSTALLING, downloaded, TOTAL_BYTES, "Installing offline Image-to-LaTeX model")
        }
        val existing = Artifacts.sumOf { File(modelDirectory, it.fileName).length().coerceAtMost(it.expectedBytes) }
        val missing = Artifacts.filterNot { artifact ->
            val file = File(modelDirectory, artifact.fileName)
            file.isFile && file.length() == artifact.expectedBytes
        }
        return when {
            missing.isEmpty() -> OfflineMathModelStatus(OfflineMathModelState.READY, TOTAL_BYTES, TOTAL_BYTES, "Offline Image-to-LaTeX ready")
            existing > 0L -> OfflineMathModelStatus(OfflineMathModelState.INVALID, existing, TOTAL_BYTES, "Offline model is incomplete")
            else -> OfflineMathModelStatus(OfflineMathModelState.NOT_INSTALLED, 0, TOTAL_BYTES, "Offline model is not installed")
        }
    }

    suspend fun install(onProgress: (OfflineMathModelStatus) -> Unit = {}): Result<OfflineMathModelStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(!installing) { "The offline mathematics model is already being installed." }
                installing = true
                modelDirectory.mkdirs()
                var completedBytes = Artifacts.sumOf { artifact ->
                    File(modelDirectory, artifact.fileName).takeIf { it.isFile && it.length() == artifact.expectedBytes }?.length() ?: 0L
                }
                onProgress(OfflineMathModelStatus(OfflineMathModelState.INSTALLING, completedBytes, TOTAL_BYTES, "Preparing model download"))
                Artifacts.forEach { artifact ->
                    val destination = File(modelDirectory, artifact.fileName)
                    if (destination.isFile && destination.length() == artifact.expectedBytes && sha256(destination) == artifact.sha256) {
                        return@forEach
                    }
                    val partial = File(modelDirectory, "${artifact.fileName}.part")
                    download(artifact, partial) { artifactBytes ->
                        onProgress(
                            OfflineMathModelStatus(
                                OfflineMathModelState.INSTALLING,
                                (completedBytes + artifactBytes).coerceAtMost(TOTAL_BYTES),
                                TOTAL_BYTES,
                                "Downloading ${artifact.fileName}",
                            ),
                        )
                    }
                    check(partial.length() == artifact.expectedBytes) { "${artifact.fileName} has an unexpected size." }
                    check(sha256(partial) == artifact.sha256) { "${artifact.fileName} failed integrity verification." }
                    if (destination.exists()) destination.delete()
                    check(partial.renameTo(destination)) { "Could not activate ${artifact.fileName}." }
                    completedBytes += artifact.expectedBytes
                }
                status().copy(state = OfflineMathModelState.READY, message = "Offline Image-to-LaTeX model installed")
            }.onFailure {
                onProgress(status().copy(state = OfflineMathModelState.INVALID, message = it.message ?: "Model installation failed"))
            }.also {
                installing = false
            }
        }

    fun remove(): Boolean {
        if (installing || !modelDirectory.exists()) return false
        return modelDirectory.deleteRecursively()
    }

    internal fun encoderFile() = File(modelDirectory, Encoder.fileName)
    internal fun decoderFile() = File(modelDirectory, Decoder.fileName)
    internal fun vocabularyFile() = File(modelDirectory, Vocabulary.fileName)

    private fun download(artifact: ModelArtifact, destination: File, onProgress: (Long) -> Unit) {
        var offset = destination.length().coerceAtMost(artifact.expectedBytes)
        if (offset == artifact.expectedBytes) return
        val connection = (URL("$RepositoryBase${artifact.relativeUrl}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            connection.connect()
            if (offset > 0L && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                destination.delete()
                offset = 0
            }
            check(connection.responseCode in 200..299) { "Model server returned HTTP ${connection.responseCode}." }
            connection.inputStream.use { input ->
                FileOutputStream(destination, offset > 0L).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var current = offset
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        current += count
                        onProgress(current)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

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

    companion object {
        private const val RepositoryBase = "https://huggingface.co/onnx-community/TexTeller-ONNX/resolve/main/"
        private val Encoder = ModelArtifact(
            "encoder_model_q4.onnx",
            "onnx/encoder_model_q4.onnx",
            56_848_921,
            "de5fe45294a00f45af907b783f3f4764dbdc95386676f4e20175d912cfe8e59a",
        )
        private val Decoder = ModelArtifact(
            "decoder_model_q4.onnx",
            "onnx/decoder_model_q4.onnx",
            198_619_422,
            "d937474a36f212cd704acc811b9eef32405f3aa20c5da812d7bf227abbc6004b",
        )
        private val Vocabulary = ModelArtifact(
            "vocab.json",
            "vocab.json",
            146_663,
            "6c5fdc7c688b7da8ca9f6abca73ff2c12c08e14386461daf9af1f0716b31e359",
        )
        private val Artifacts = listOf(Encoder, Decoder, Vocabulary)
        const val TOTAL_BYTES = 255_615_006L
    }
}

data class OfflineLatexPrediction(
    val latex: String,
    val confidence: Float,
    val tokenCount: Int,
    /** Exact text decoded from the model vocabulary, before SMART Board normalization. */
    val rawLatex: String = latex,
    val preprocessingMillis: Long = 0L,
    val inferenceMillis: Long = 0L,
    val decodingMillis: Long = 0L,
)

internal object OfflineMathImagePreprocessor {
    private const val Size = 448

    fun tensor(bytes: ByteArray): FloatBuffer {
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "The selected image is unreadable." }
        val crop = contentBounds(source)
        val output = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val margin = 18
        val scale = minOf((Size - 2 * margin).toFloat() / crop.width(), (Size - 2 * margin).toFloat() / crop.height())
        val width = max(1, (crop.width() * scale).toInt())
        val height = max(1, (crop.height() * scale).toInt())
        val left = (Size - width) / 2
        val top = (Size - height) / 2
        canvas.drawBitmap(source, crop, Rect(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(Size * Size)
        output.getPixels(pixels, 0, Size, 0, 0, Size, Size)
        val values = FloatArray(pixels.size)
        pixels.forEachIndexed { index, color ->
            val luminance = (.299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color)) / 255f
            values[index] = (luminance - .5f) / .5f
        }
        if (source !== output) source.recycle()
        output.recycle()
        return FloatBuffer.wrap(values)
    }

    private fun contentBounds(bitmap: Bitmap): Rect {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            val luminance = (.299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color))
            if (luminance < 242f || Color.alpha(color) < 240) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return Rect(0, 0, bitmap.width, bitmap.height)
        val padding = max(4, max(right - left, bottom - top) / 40)
        return Rect(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding + 1).coerceAtMost(bitmap.width),
            (bottom + padding + 1).coerceAtMost(bitmap.height),
        )
    }
}

internal class TexTellerVocabulary(file: File) {
    private val tokens: Map<Int, String> = JSONObject(file.readText()).let { json ->
        buildMap {
            json.keys().forEach { token -> put(json.getInt(token), token) }
        }
    }
    private val byteDecoder = gpt2ByteEncoder().entries.associate { (byte, character) -> character to byte }

    fun decode(ids: List<Int>): String {
        val merged = ids.asSequence()
            .filterNot { it in setOf(0, 1, 2, 3, 4) }
            .mapNotNull(tokens::get)
            .joinToString("")
        val bytes = ArrayList<Byte>(merged.length)
        merged.forEach { character ->
            val value = byteDecoder[character]
            if (value != null) bytes += value.toByte()
            else bytes += character.toString().encodeToByteArray().toList()
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
            .replace("Ġ", " ")
            .replace("Ċ", "\n")
            .trim()
    }

    private fun gpt2ByteEncoder(): Map<Int, Char> {
        val visible = ((33..126) + (161..172) + (174..255)).toMutableList()
        val characters = visible.map(Int::toChar).toMutableList()
        var extra = 0
        for (byte in 0..255) {
            if (byte !in visible) {
                visible += byte
                characters += (256 + extra).toChar()
                extra++
            }
        }
        return visible.zip(characters).toMap()
    }
}

internal fun normalizeTexTellerLatex(source: String): String {
    var value = source
        .filterNot { character ->
            character.category in setOf(
                CharCategory.CONTROL,
                CharCategory.FORMAT,
                CharCategory.PRIVATE_USE,
                CharCategory.SURROGATE,
                CharCategory.UNASSIGNED,
            )
        }
        .trim()
    val wrappers = listOf("\\[" to "\\]", "\\(" to "\\)", "$$" to "$$", "$" to "$")
    wrappers.firstOrNull { (start, end) ->
        value.length >= start.length + end.length &&
            value.startsWith(start) &&
            value.endsWith(end)
    }?.let { (start, end) ->
        value = value.substring(start.length, value.length - end.length).trim()
    }
    value = value.removePrefix("\\displaystyle").trim()
    // TexTeller occasionally confuses the open handwritten "a" in tan with "o".
    // Restrict the repair to a complete function token followed by an argument so
    // ordinary prose or variable names such as "ton" remain untouched.
    value = value.replace(
        Regex("""(?<![A-Za-z\\])\\?ton(?=\s*(?:\\left\s*)?\()""", RegexOption.IGNORE_CASE),
    ) { "\\tan" }
    return removeRedundantOuterLatexGroup(value)
}

internal fun removeRedundantOuterLatexGroup(source: String): String {
    if (!source.startsWith('{')) return source
    var depth = 0
    source.forEachIndexed { index, character ->
        when (character) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    val suffix = source.substring(index + 1)
                    val relation = suffix.trimStart().firstOrNull()
                    return if (relation == null || relation in "=<>") {
                        source.substring(1, index) + suffix
                    } else {
                        source
                    }
                }
            }
        }
        if (depth < 0) return source
    }
    return source
}

internal class TexTellerOnnxRuntime(private val pack: OfflineMathOcrModelPack) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setInterOpNumThreads(1)
        setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors().coerceAtMost(4)))
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val encoder = environment.createSession(pack.encoderFile().absolutePath, options)
    private val decoder = environment.createSession(pack.decoderFile().absolutePath, options)
    private val vocabulary = TexTellerVocabulary(pack.vocabularyFile())

    fun recognize(bytes: ByteArray, maximumTokens: Int = 96): OfflineLatexPrediction {
        require(bytes.isNotEmpty()) { "The selected image is empty." }
        val preprocessingStarted = System.nanoTime()
        val pixelBuffer = OfflineMathImagePreprocessor.tensor(bytes)
        val preprocessingMillis = (System.nanoTime() - preprocessingStarted) / 1_000_000L
        val pixels = OnnxTensor.createTensor(environment, pixelBuffer, longArrayOf(1, 1, 448, 448))
        pixels.use { input ->
            val inferenceStarted = System.nanoTime()
            encoder.run(mapOf(encoder.inputNames.first() to input)).use { encoded ->
                val hidden = encoded[0] as OnnxTensor
                val ids = mutableListOf(2L)
                var logProbability = 0.0
                var predicted = 0
                while (ids.size <= maximumTokens) {
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong())).use { idTensor ->
                        decoder.run(
                            mapOf(
                                decoder.inputNames.first { it.contains("input_ids") } to idTensor,
                                decoder.inputNames.first { it.contains("encoder") } to hidden,
                            ),
                        ).use { decoded ->
                            val logits = decoded[0].value as Array<Array<FloatArray>>
                            val last = logits[0].last()
                            val next = last.indices.maxByOrNull(last::get) ?: 2
                            if (next == 2) break
                            ids += next.toLong()
                            logProbability += ln(softmaxProbability(last, next).coerceAtLeast(1e-7f).toDouble())
                            predicted++
                        }
                    }
                }
                val inferenceMillis = (System.nanoTime() - inferenceStarted) / 1_000_000L
                val decodingStarted = System.nanoTime()
                val rawLatex = vocabulary.decode(ids.drop(1).map(Long::toInt))
                val latex = normalizeTexTellerLatex(rawLatex)
                require(latex.isNotBlank()) { "The formula model returned an empty expression." }
                require(SafeLatexPreview.validate(latex).isSuccess) {
                    "The formula model returned malformed or unsupported notation."
                }
                val confidence = if (predicted == 0) .35f else exp(logProbability / predicted).toFloat().coerceIn(.35f, .995f)
                val decodingMillis = (System.nanoTime() - decodingStarted) / 1_000_000L
                return OfflineLatexPrediction(
                    latex = latex,
                    confidence = confidence,
                    tokenCount = predicted,
                    rawLatex = rawLatex,
                    preprocessingMillis = preprocessingMillis,
                    inferenceMillis = inferenceMillis,
                    decodingMillis = decodingMillis,
                )
            }
        }
    }

    private fun softmaxProbability(logits: FloatArray, selected: Int): Float {
        val maximum = logits.maxOrNull() ?: return 0f
        var denominator = 0.0
        logits.forEach { denominator += exp((it - maximum).toDouble()) }
        return (exp((logits[selected] - maximum).toDouble()) / denominator.coerceAtLeast(1e-12)).toFloat()
    }

    override fun close() {
        encoder.close()
        decoder.close()
        options.close()
    }
}

/**
 * Dedicated mathematical image recognizer. Generic ML Kit OCR remains a controlled fallback only
 * when the optional model pack is unavailable or a device cannot execute the model.
 */
class DedicatedOfflineImageMathRecognitionAdapter(
    context: Context,
    private val pack: OfflineMathOcrModelPack = OfflineMathOcrModelPack(context),
    private val fallback: MathImageRecognitionProvider = MlKitImageMathRecognitionAdapter(),
) : MathImageRecognitionProvider {
    override val id = "texteller-q4-offline-image-to-latex"

    override suspend fun recognize(png: ByteArray, maximumAlternatives: Int): MathRecognitionResult =
        withContext(Dispatchers.Default) {
            if (pack.status().state != OfflineMathModelState.READY) {
                return@withContext fallback.recognize(png, maximumAlternatives).copy(
                    warnings = listOf("Dedicated offline Image-to-LaTeX is not installed; using generic image text recognition."),
                )
            }
            runCatching {
                TexTellerOnnxRuntime(pack).use { runtime ->
                    val prediction = runtime.recognize(png)
                    MathRecognitionResult(
                        latex = prediction.latex,
                        normalizedExpression = runCatching { SmartBoardLatexAdapter.toEngineExpression(prediction.latex) }.getOrDefault(prediction.latex),
                        plainText = prediction.latex,
                        confidence = prediction.confidence,
                        alternatives = emptyList<MathRecognitionAlternative>(),
                        detectedType = MathRecognitionClassifier.detect(prediction.latex),
                        warnings = listOf("Recognized completely offline with the dedicated TexTeller mathematical vision model."),
                    )
                }
            }.getOrElse { failure ->
                fallback.recognize(png, maximumAlternatives).copy(
                    warnings = listOf(
                        "Dedicated Image-to-LaTeX could not run: ${failure.message ?: "unknown model error"}.",
                        "Generic image text recognition supplied this fallback candidate.",
                    ),
                )
            }
        }
}
