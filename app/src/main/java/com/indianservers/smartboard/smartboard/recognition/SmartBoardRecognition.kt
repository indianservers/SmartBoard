package com.indianservers.smartboard.smartboard.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.indianservers.smartboard.input.CasHandwritingRecognizer
import com.indianservers.smartboard.input.MathInkPoint
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardAction
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionInput
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectAnalysis
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectHandler
import com.indianservers.smartboard.smartboard.models.StrokeElement
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class MathRecognitionInput(
    val strokes: List<StrokeElement>,
    val bounds: SmartBoardBounds,
    val rasterPng: ByteArray,
    val requestFingerprint: String,
)

data class MathRecognitionOptions(
    val languageTag: String = "en-IN",
    val maximumAlternatives: Int = 6,
    val preferLatex: Boolean = true,
)

interface MathHandwritingRecognitionProvider {
    val id: String
    val productionReady: Boolean
    suspend fun recognize(input: MathRecognitionInput, options: MathRecognitionOptions): MathRecognitionResult
}

object MathRecognitionRequestBuilder {
    fun build(
        documentId: String,
        strokes: List<StrokeElement>,
        now: Long,
        padding: Float = 20f,
        rasterPng: ByteArray = byteArrayOf(),
        subject: SmartBoardSubject = SmartBoardSubject.MATHEMATICS,
    ): SmartBoardRecognitionInput {
        require(documentId.isNotBlank())
        require(strokes.isNotEmpty())
        val bounds = SmartBoardBounds.from(strokes.flatMap { stroke -> stroke.points.map { it.position } }).expand(padding)
        return SmartBoardRecognitionInput(
            documentId = documentId,
            subject = subject,
            strokeIds = strokes.map(StrokeElement::id),
            strokes = strokes,
            bounds = bounds,
            rasterPng = rasterPng,
            requestedAt = now,
        )
    }

    fun fingerprint(input: SmartBoardRecognitionInput): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(input.documentId.toByteArray())
        input.strokes.forEach { stroke ->
            digest.update(stroke.id.toByteArray())
            stroke.points.forEach { point ->
                digest.update("${point.x},${point.y},${point.timestampMillis};".toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** High-contrast request bitmap. Vector strokes remain in the request and document. */
object MathRecognitionInputRenderer {
    fun render(strokes: List<StrokeElement>, bounds: SmartBoardBounds, scale: Float = 2f, maximumDimension: Int = 2048): ByteArray {
        require(strokes.isNotEmpty())
        val width = (bounds.width * scale).toInt().coerceIn(64, maximumDimension)
        val height = (bounds.height * scale).toInt().coerceIn(64, maximumDimension)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val sx = width / bounds.width.coerceAtLeast(1f)
            val sy = height / bounds.height.coerceAtLeast(1f)
            strokes.forEach { stroke ->
                paint.strokeWidth = (stroke.width * (sx + sy) / 2f).coerceAtLeast(2f)
                val path = Path()
                stroke.points.forEachIndexed { index, point ->
                    val x = (point.x - bounds.left) * sx
                    val y = (point.y - bounds.top) * sy
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Production provider backed by the application's existing ML Kit digital-ink integration.
 * The language model is downloaded through ML Kit and recognition subsequently runs on-device.
 */
class MlKitMathRecognitionAdapter : MathHandwritingRecognitionProvider {
    override val id = "existing-mlkit-digital-ink"
    override val productionReady = true

    override suspend fun recognize(input: MathRecognitionInput, options: MathRecognitionOptions): MathRecognitionResult =
        suspendCancellableCoroutine { continuation ->
            val recognizer = runCatching { CasHandwritingRecognizer(options.languageTag) }.getOrElse {
                continuation.resumeWithException(IllegalStateException("On-device handwriting recognition is unavailable."))
                return@suspendCancellableCoroutine
            }
            var completed = false
            fun finish() {
                if (!completed) {
                    completed = true
                    recognizer.close()
                }
            }
            continuation.invokeOnCancellation { finish() }
            recognizer.recognize(
                strokes = input.strokes.map { stroke ->
                    stroke.points.map { point -> MathInkPoint(point.x - input.bounds.left, point.y - input.bounds.top, point.timestampMillis) }
                },
                width = input.bounds.width,
                height = input.bounds.height,
                preContext = "",
                onSuccess = { recognized ->
                    if (!continuation.isActive) {
                        finish()
                        return@recognize
                    }
                    val candidates = recognized.candidates.take(options.maximumAlternatives)
                    val primary = candidates.first()
                    continuation.resume(
                        MathRecognitionResult(
                            latex = primary,
                            normalizedExpression = primary,
                            plainText = primary,
                            confidence = recognized.confidence.toFloat(),
                            alternatives = candidates.drop(1).mapIndexed { index, candidate ->
                                MathRecognitionAlternative(candidate, (recognized.confidence - .05 * (index + 1)).toFloat().coerceIn(0f, 1f))
                            },
                            detectedType = MathRecognitionClassifier.detect(primary),
                            warnings = buildList {
                                if (recognized.confidence < .65) add("Low confidence; correct the expression before using a mathematics engine.")
                                add(recognized.message)
                            },
                        ),
                    )
                    finish()
                },
                onFailure = { message ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message.take(240)))
                    finish()
                },
            )
        }
}

class MathematicsSubjectHandler(
    private val provider: MathHandwritingRecognitionProvider = MlKitMathRecognitionAdapter(),
) : SmartBoardSubjectHandler {
    override val subject = SmartBoardSubject.MATHEMATICS

    override suspend fun analyze(input: SmartBoardRecognitionInput): SmartBoardSubjectAnalysis {
        require(input.subject == subject)
        val recognitionInput = MathRecognitionInput(
            input.strokes,
            input.bounds,
            input.rasterPng,
            MathRecognitionRequestBuilder.fingerprint(input),
        )
        val result = provider.recognize(recognitionInput, MathRecognitionOptions())
        return SmartBoardSubjectAnalysis(subject, "Mathematical handwriting recognized locally; review notation before insertion.", result)
    }

    override fun supportedActions(analysis: SmartBoardSubjectAnalysis) =
        if (analysis.recognition == null) listOf(SmartBoardAction.RetryRecognition)
        else listOf(SmartBoardAction.InsertExpression, SmartBoardAction.EditLatex, SmartBoardAction.RetryRecognition)
}

object SmartBoardSubjectRouter {
    fun handler(subject: SmartBoardSubject, mathematics: SmartBoardSubjectHandler): SmartBoardSubjectHandler =
        when (subject) {
            SmartBoardSubject.MATHEMATICS -> mathematics
            else -> error("${subject.name.lowercase()} support is not installed.")
        }
}

class SmartBoardSubjectRegistry(handlers: List<SmartBoardSubjectHandler>) {
    private val bySubject = handlers.associateBy(SmartBoardSubjectHandler::subject)
    init { require(bySubject.size == handlers.size) { "Only one handler may be registered per subject." } }
    fun handler(subject: SmartBoardSubject): SmartBoardSubjectHandler =
        bySubject[subject] ?: error("${subject.name.lowercase()} support is not installed.")
    fun supportedSubjects(): Set<SmartBoardSubject> = bySubject.keys
}

object MathRecognitionClassifier {
    fun detect(source: String): MathExpressionType = when {
        source.matches(Regex("[+-]?\\d+(\\.\\d+)?")) -> MathExpressionType.NUMBER
        Regex("\\b(lim|int|sum|prod|d/d|partial)\\b|[∫ΣΠ∂]").containsMatchIn(source) -> MathExpressionType.CALCULUS
        source.contains("<=") || source.contains(">=") || source.contains('<') || source.contains('>') -> MathExpressionType.INEQUALITY
        source.contains('=') -> MathExpressionType.EQUATION
        source.startsWith("[") || source.startsWith("\\begin{matrix}") -> MathExpressionType.MATRIX
        Regex("\\([^)]+,[^)]+\\)").matches(source) -> MathExpressionType.COORDINATE
        Regex("\\b(sin|cos|tan|log|ln)\\b", RegexOption.IGNORE_CASE).containsMatchIn(source) -> MathExpressionType.FUNCTION
        source.any(Char::isLetter) -> MathExpressionType.ALGEBRAIC_EXPRESSION
        source.any { it in "+-*/^√" } -> MathExpressionType.ARITHMETIC
        else -> MathExpressionType.UNKNOWN
    }
}

object SafeLatexPreview {
    private val dangerous = Regex(
        """\\(write|write18|input|include|includeonly|openout|read|catcode|csname|newcommand|renewcommand|def|edef|gdef|xdef|usepackage|documentclass|href|url|html)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val allowedCommands = setOf(
        "frac", "dfrac", "tfrac", "sqrt", "left", "right",
        "sin", "cos", "tan", "sec", "csc", "cot", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "log", "ln", "exp", "min", "max",
        "cdot", "times", "div", "pm", "mp", "le", "leq", "ge", "geq", "ne", "neq",
        "approx", "equiv", "in", "notin", "subset", "subseteq", "cup", "cap",
        "pi", "theta", "phi", "lambda", "mu", "sigma", "delta", "alpha", "beta", "gamma",
        "omega", "rho", "tau", "epsilon", "varepsilon", "infty", "partial", "nabla",
        "int", "iint", "iiint", "oint", "sum", "prod", "lim",
        "mathrm", "mathbf", "mathit", "mathbb", "mathcal", "operatorname", "text",
        "begin", "end", "overline", "underline", "vec", "hat", "bar",
    )
    private val allowedEnvironments = setOf("matrix", "pmatrix", "bmatrix", "vmatrix", "cases", "aligned")

    fun validate(source: String): Result<String> = runCatching {
        val value = source.trim()
        require(value.isNotBlank()) { "Mathematical notation is empty." }
        require(value.length <= 4_000) { "Mathematical notation is too long." }
        require(!dangerous.containsMatchIn(value)) { "Unsupported LaTeX command." }
        require(balanced(value, '{', '}') && balanced(value, '[', ']') && balanced(value, '(', ')')) { "Unbalanced mathematical delimiters." }
        Regex("""\\([A-Za-z]+)""").findAll(value).forEach { match ->
            require(match.groupValues[1].lowercase() in allowedCommands) {
                "Unsupported LaTeX command: \\${match.groupValues[1]}"
            }
        }
        Regex("""\\(?:begin|end)\{([^}]+)}""").findAll(value).forEach { match ->
            require(match.groupValues[1].lowercase() in allowedEnvironments) {
                "Unsupported LaTeX environment: ${match.groupValues[1]}"
            }
        }
        value
    }

    fun accessibleSummary(source: String): String = source
        .replace("\\frac", " fraction ")
        .replace("\\sqrt", " square root ")
        .replace("\\int", " integral ")
        .replace("\\sum", " sum ")
        .replace(Regex("\\\\[A-Za-z]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun balanced(source: String, open: Char, close: Char): Boolean {
        var depth = 0
        source.forEach { character ->
            if (character == open) depth++
            if (character == close && --depth < 0) return false
        }
        return depth == 0
    }
}
