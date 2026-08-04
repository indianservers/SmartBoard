package com.indianservers.smartboard.input

import android.graphics.BitmapFactory
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

data class MathInkPoint(val x: Float, val y: Float, val timestampMillis: Long)
data class LocalRecognitionResult(val candidates: List<String>, val confidence: Double, val message: String) {
    init { require(confidence in 0.0..1.0); require(candidates.none { it.isBlank() }) }
}

object CasPhotoMathRecognizer {
    fun recognize(bytes: ByteArray, onSuccess: (LocalRecognitionResult) -> Unit, onFailure: (String) -> Unit) {
        if (bytes.isEmpty()) { onFailure("The selected image is empty."); return }
        if (bytes.size > 20_000_000) { onFailure("Use an image smaller than 20 MB."); return }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) { onFailure("The selected file is not a readable image."); return }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val candidates = buildList {
                    result.text.takeIf(String::isNotBlank)?.let(::add)
                    result.textBlocks.flatMap { block -> block.lines.map { it.text } }.filter(String::isNotBlank).forEach(::add)
                }.map(::normalizeMathText).distinct().filter(String::isNotBlank).take(8)
                if (candidates.isEmpty()) onFailure("No readable text was found. Crop tightly around the mathematics and try again.")
                else onSuccess(LocalRecognitionResult(candidates, confidence(result.text, candidates.first()), "Recognized locally from the image; confirm symbols before evaluation."))
            }
            .addOnFailureListener { onFailure(it.message ?: "Local image recognition failed.") }
            .addOnCompleteListener { recognizer.close() }
    }

    private fun confidence(raw: String, normalized: String): Double {
        val useful = normalized.count { it.isLetterOrDigit() || it in "+-*/^=()[]{}.,<>" }
        val ratio = useful.toDouble() / normalized.length.coerceAtLeast(1)
        val bracketPenalty = if (normalized.count { it == '(' } == normalized.count { it == ')' }) 0.0 else .12
        return (.55 + .4 * ratio - bracketPenalty - if (raw.lines().size > 8) .05 else 0.0).coerceIn(.35, .96)
    }
}

class CasHandwritingRecognizer(languageTag: String = "en-US") : AutoCloseable {
    private val model: DigitalInkRecognitionModel
    private val recognizer: DigitalInkRecognizer
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()

    init {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            ?: error("No digital-ink model is available for $languageTag.")
        model = DigitalInkRecognitionModel.builder(identifier).build()
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    }

    fun recognize(
        strokes: List<List<MathInkPoint>>,
        width: Float,
        height: Float,
        preContext: String,
        onSuccess: (LocalRecognitionResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val clean = strokes.filter { it.size >= 2 }
        if (closed.get()) { onFailure("The handwriting recognition request was cancelled."); return }
        if (clean.isEmpty()) { onFailure("Write at least one complete stroke."); return }
        val ink = Ink.builder().apply {
            clean.forEach { points -> addStroke(Ink.Stroke.builder().apply { points.forEach { addPoint(Ink.Point.create(it.x, it.y, it.timestampMillis)) } }.build()) }
        }.build()
        val context = RecognitionContext.builder()
            .setWritingArea(WritingArea(width.coerceAtLeast(1f), height.coerceAtLeast(1f)))
            .setPreContext(preContext.takeLast(20))
            .build()
        val manager = RemoteModelManager.getInstance()
        manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
            if (closed.get()) return@addOnSuccessListener
            if (downloaded) runRecognition(ink, context, onSuccess, onFailure)
            else manager.download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    if (!closed.get()) runRecognition(ink, context, onSuccess, onFailure)
                }
                .addOnFailureListener { onFailure(it.message ?: "The offline handwriting model could not be downloaded.") }
        }.addOnFailureListener { onFailure(it.message ?: "Could not check the handwriting model.") }
    }

    private fun runRecognition(ink: Ink, context: RecognitionContext, onSuccess: (LocalRecognitionResult) -> Unit, onFailure: (String) -> Unit) {
        val task = synchronized(lifecycleLock) {
            if (closed.get()) return
            runCatching { recognizer.recognize(ink, context) }.getOrElse {
                onFailure(it.message ?: "Handwriting recognition was cancelled.")
                return
            }
        }
        task.addOnSuccessListener { result ->
            if (closed.get()) return@addOnSuccessListener
            val candidates = result.candidates.map { normalizeMathText(it.text) }.distinct().filter(String::isNotBlank).take(8)
            if (candidates.isEmpty()) onFailure("No handwriting candidate was found.")
            else onSuccess(LocalRecognitionResult(candidates, if (candidates.size == 1) .9 else (.88 - .05 * (candidates.size - 1)).coerceAtLeast(.55), "Recognized on device; choose or edit a candidate."))
        }.addOnFailureListener { onFailure(it.message ?: "Handwriting recognition failed.") }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed.compareAndSet(false, true)) recognizer.close()
        }
    }
}

private fun normalizeMathText(source: String): String = source
    .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')
    .replace("π", "pi").replace(Regex("(?i)\\bpi\\b"), "pi")
    .replace(Regex("(?<=\\d)[Oo](?=\\d)"), "0")
    .replace(Regex("(?<=\\d)[lI](?=\\d)"), "1")
    .replace(Regex("\\s*([+\\-*/^=(),;])\\s*"), "$1")
    .replace(Regex("\\s+"), " ").trim()
