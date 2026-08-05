package com.indianservers.smartboard.smartboard.audit

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.smartboard.smartboard.HumanInkWriter
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardMathGraphIntelligenceEngine
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.DedicatedOfflineImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInputRenderer
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MlKitImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MultimodalMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelState
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import java.util.Locale
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartBoardComprehensiveAuditTest {
    @Test
    fun executeGeneratedAuditAgainstProductionRecognitionPipeline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val allCases = SmartBoardAuditDataset.cases
        validateDataset(allCases)

        val requestedCategory = arguments.getString("auditCategory")
            ?.let { name -> AuditCategory.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val start = arguments.getString("caseStart")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val limit = arguments.getString("caseLimit")?.toIntOrNull()?.coerceAtLeast(1) ?: Int.MAX_VALUE
        val selected = allCases
            .filter { requestedCategory == null || it.category == requestedCategory }
            .drop(start - 1)
            .take(limit)
        assertTrue("No audit cases selected", selected.isNotEmpty())

        val modelPack = OfflineMathOcrModelPack(context)
        if (arguments.getString("installFormulaModel").toBoolean() &&
            modelPack.status().state != OfflineMathModelState.READY
        ) {
            val installed = withTimeout(600_000) { modelPack.install() }
            assertTrue("Offline formula model installation failed", installed.isSuccess)
        }
        val math = MultimodalMathRecognitionEngine(
            MlKitMathRecognitionAdapter(),
            DedicatedOfflineImageMathRecognitionAdapter(context, modelPack, MlKitImageMathRecognitionAdapter()),
        )
        val graphs = SmartBoardMathGraphIntelligenceEngine()
        val shapes = DeterministicAutoShapeRecognizer()
        val exporter = SmartBoardAuditExporter(
            context,
            arguments.getString("auditRunId")?.takeIf(String::isNotBlank)
                ?: "audit-${System.currentTimeMillis()}",
        )
        val processMemoryStartKb = Debug.getPss()
        var processMemoryPeakKb = processMemoryStartKb
        val startedAt = System.currentTimeMillis()
        val outputs = mutableListOf<SmartBoardAuditResult>()

        selected.forEachIndexed { index, case ->
            val input = AuditStrokeFactory.create(case, seed = 10_000 + allCases.indexOf(case))
            val png = if (input.strokes.isEmpty()) blankPng() else MathRecognitionInputRenderer.render(input.strokes, input.bounds)
            val evidence = exporter.saveInput(case, input.strokes, png)
            val started = System.currentTimeMillis()
            val result = if (input.strokes.isEmpty()) {
                terminalResult(
                    case,
                    AuditStatus.MANUAL_REVIEW_REQUIRED,
                    error = null,
                    time = 0,
                    evidence = evidence,
                    note = "The automated stroke alphabet cannot represent this case faithfully; execute in manual Mode B.",
                )
            } else try {
                when (case.category) {
                    AuditCategory.GRAPHS -> recognizeGraph(case, input.strokes, graphs, started)
                    AuditCategory.GEOMETRY_DIAGRAMS -> recognizeShape(case, input.strokes, shapes, started)
                    else -> recognizeMath(case, input, png, math, started)
                }
            } catch (_: TimeoutCancellationException) {
                terminalResult(case, AuditStatus.TIMEOUT, AuditErrorType.TIMEOUT, System.currentTimeMillis() - started, evidence)
            } catch (error: Throwable) {
                terminalResult(
                    case,
                    AuditStatus.CRASH,
                    AuditErrorType.CRASH,
                    System.currentTimeMillis() - started,
                    evidence,
                    "${error::class.java.simpleName}: ${error.message}",
                )
            }
            val finalized = if (result.status !in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION)) {
                result.copy(evidencePath = exporter.saveFailure(case, result, png))
            } else {
                result.copy(evidencePath = evidence)
            }
            outputs += finalized
            processMemoryPeakKb = maxOf(processMemoryPeakKb, Debug.getPss())
            Log.i(TAG, "ROW|${case.id}|${case.expectedPlainText}|${finalized.rawRecognitionOutput}|${finalized.status}|${finalized.overallScore}")
            if ((index + 1) % 10 == 0 || index == selected.lastIndex) {
                Log.i(TAG, "PROGRESS|${index + 1}/${selected.size}|pass=${outputs.count(::passed)}")
            }
        }

        val device = linkedMapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString(),
            "abi" to Build.SUPPORTED_ABIS.joinToString("|"),
            "app_version" to "1.0 (1)",
            "recognition_model" to modelPack.status().let { "TexTeller-Q4-v2:${it.state}" },
            "input_mode" to "automated-stroke-replay",
            "process_pss_start_kb" to processMemoryStartKb.toString(),
            "process_pss_peak_kb" to processMemoryPeakKb.toString(),
        )
        exporter.finish(allCases, outputs, device, System.currentTimeMillis() - startedAt)
        Log.i(TAG, "OUTPUT|${exporter.root.absolutePath}")
        Log.i(
            TAG,
            "SUMMARY|generated=${allCases.size}|executed=${outputs.size}|pass=${outputs.count(::passed)}|" +
                "crash=${outputs.count { it.status == AuditStatus.CRASH }}|timeout=${outputs.count { it.status == AuditStatus.TIMEOUT }}",
        )
        assertEquals(selected.size, outputs.size)
    }

    private suspend fun recognizeMath(
        case: SmartBoardAuditCase,
        input: AuditInput,
        png: ByteArray,
        engine: MultimodalMathRecognitionEngine,
        started: Long,
    ): SmartBoardAuditResult {
        val request = MathRecognitionRequestBuilder.build(case.id, input.strokes, started)
        val result = withTimeout(TIMEOUT_MS) {
            engine.recognize(
                MathRecognitionInput(input.strokes, input.bounds, png, MathRecognitionRequestBuilder.fingerprint(request)),
                MathRecognitionOptions(languageTag = "en-US", maximumAlternatives = 8),
            ).result
        }
        val comparison = SmartBoardAuditScoring.compare(case, result.latex, result.confidence)
        return result(case, result.latex, result.normalizedExpression, result.latex, result.confidence,
            System.currentTimeMillis() - started, comparison, notes = result.warnings.joinToString("; "))
    }

    private fun recognizeGraph(
        case: SmartBoardAuditCase,
        strokes: List<StrokeElement>,
        engine: SmartBoardMathGraphIntelligenceEngine,
        started: Long,
    ): SmartBoardAuditResult {
        val suggestion = engine.analyzeInk(strokes, emptyList(), emptyList(), started)
        val primary = suggestion?.candidates?.firstOrNull()
        val objects = primary?.let { listOf("GRAPH:${it.family}") }.orEmpty()
        val base = SmartBoardAuditScoring.compare(case, primary?.expression, primary?.confidence, objects)
        val typeMatch = primary != null && graphFamilyMatches(case.expectedGraph?.type.orEmpty(), primary.family)
        val comparison = base.copy(
            semantic = typeMatch,
            structure = typeMatch,
            layout = primary != null,
            structureScore = if (typeMatch) 1.0 else if (primary == null) 0.0 else .45,
            spatialScore = if (primary == null) 0.0 else 1.0,
            semanticScore = if (typeMatch) 1.0 else .35,
            overallScore = (
                .15 * base.symbolScore + .35 * (if (typeMatch) 1.0 else .45) +
                    .30 * (if (primary == null) 0.0 else 1.0) + .15 * (if (typeMatch) 1.0 else .35) +
                    .05 * (primary?.confidence ?: 0f)
                ).coerceIn(0.0, 1.0),
            status = when {
                primary == null -> AuditStatus.NOT_DETECTED
                typeMatch -> AuditStatus.PASS_WITH_NORMALIZATION
                else -> AuditStatus.WRONG_STRUCTURE
            },
            errors = when {
                primary == null -> setOf(AuditErrorType.GRAPH_NOT_DETECTED)
                typeMatch -> emptySet()
                else -> setOf(AuditErrorType.GRAPH_TYPE_WRONG)
            },
        )
        return result(
            case, primary?.expression, primary?.expression, primary?.expression, primary?.confidence,
            System.currentTimeMillis() - started, comparison, objects,
            "fit_error=${primary?.fitError}; candidates=${suggestion?.candidates?.joinToString { it.family }}",
        )
    }

    private fun recognizeShape(
        case: SmartBoardAuditCase,
        strokes: List<StrokeElement>,
        engine: DeterministicAutoShapeRecognizer,
        started: Long,
    ): SmartBoardAuditResult {
        val candidates = engine.recognize(strokes, forced = true)
        val primary = candidates.firstOrNull()
        val objects = primary?.let { listOf("SHAPE:${it.type.name}") }.orEmpty()
        val base = SmartBoardAuditScoring.compare(case, primary?.type?.name, primary?.confidence, objects)
        val expected = case.expectedDiagram?.shapeType.orEmpty()
        val typeMatch = primary != null && shapeFamilyMatches(expected, primary.type)
        val labelsMatch = case.expectedDiagram?.labels.isNullOrEmpty()
        val comparison = base.copy(
            semantic = typeMatch && labelsMatch,
            structure = typeMatch,
            layout = primary != null && labelsMatch,
            structureScore = if (typeMatch) 1.0 else if (primary == null) 0.0 else .45,
            spatialScore = if (primary == null) 0.0 else if (labelsMatch) 1.0 else .5,
            semanticScore = if (typeMatch && labelsMatch) 1.0 else if (typeMatch) .7 else .35,
            overallScore = (
                .15 * base.symbolScore + .35 * (if (typeMatch) 1.0 else .45) +
                    .30 * (if (primary == null) 0.0 else if (labelsMatch) 1.0 else .5) +
                    .15 * (if (typeMatch && labelsMatch) 1.0 else if (typeMatch) .7 else .35) +
                    .05 * (primary?.confidence ?: 0f)
                ).coerceIn(0.0, 1.0),
            status = when {
                primary == null -> AuditStatus.NOT_DETECTED
                typeMatch && labelsMatch -> AuditStatus.PASS_WITH_NORMALIZATION
                typeMatch -> AuditStatus.WRONG_LAYOUT
                else -> AuditStatus.WRONG_STRUCTURE
            },
            errors = when {
                primary == null -> setOf(AuditErrorType.SHAPE_NOT_DETECTED)
                typeMatch && labelsMatch -> emptySet()
                typeMatch -> setOf(AuditErrorType.DIAGRAM_LABEL_MISREAD)
                else -> setOf(AuditErrorType.SHAPE_TYPE_WRONG)
            },
        )
        return result(
            case, primary?.type?.name, primary?.type?.name, primary?.type?.name, primary?.confidence,
            System.currentTimeMillis() - started, comparison, objects,
            candidates.joinToString { "${it.type}:${"%.3f".format(Locale.US, it.confidence)}" },
        )
    }

    private fun result(
        case: SmartBoardAuditCase,
        raw: String?,
        normalized: String?,
        latex: String?,
        confidence: Float?,
        time: Long,
        comparison: SmartBoardAuditScoring.Comparison,
        objects: List<String> = emptyList(),
        notes: String? = null,
    ) = SmartBoardAuditResult(
        case.id, raw, normalized ?: comparison.normalizedDetected, latex, objects, confidence, time,
        comparison.exact, comparison.semantic, comparison.structure, comparison.layout,
        comparison.symbolScore, comparison.structureScore, comparison.spatialScore,
        comparison.semanticScore, comparison.overallScore, comparison.status, comparison.errors,
        evidencePath = null, notes = notes,
    )

    private fun terminalResult(
        case: SmartBoardAuditCase,
        status: AuditStatus,
        error: AuditErrorType?,
        time: Long,
        evidence: String,
        note: String? = null,
    ) = SmartBoardAuditResult(
        case.id, null, null, null, emptyList(), null, time,
        exactMatch = false, semanticMatch = false, structureMatch = false, layoutMatch = false,
        symbolScore = 0.0, structureScore = 0.0, spatialScore = 0.0, semanticScore = 0.0,
        overallScore = 0.0, status = status, errorTypes = setOfNotNull(error), evidencePath = evidence, notes = note,
    )

    private fun validateDataset(cases: List<SmartBoardAuditCase>) {
        assertEquals(560, cases.size)
        AuditCategory.entries.forEach { category ->
            val rows = cases.filter { it.category == category }
            assertEquals("$category case count", 40, rows.size)
            assertEquals(10, rows.count { it.difficulty == AuditDifficulty.EASY })
            assertEquals(12, rows.count { it.difficulty == AuditDifficulty.MEDIUM })
            assertEquals(12, rows.count { it.difficulty == AuditDifficulty.HARD })
            assertEquals(6, rows.count { it.difficulty == AuditDifficulty.EXTREME })
        }
        assertTrue(cases.map { it.expectedPlainText }.distinct().size >= 500)
        assertTrue(cases.map { it.handwritingProfile }.toSet().containsAll(HandwritingProfile.entries))
        assertTrue(cases.map { it.canvasRegion }.toSet().containsAll(CanvasRegion.entries))
        assertTrue(cases.count { "reverse" in it.strokeVariant || "fraction-bar" in it.strokeVariant || "delayed" in it.strokeVariant } >= 100)
        assertTrue(cases.count { "venn" in it.tags } >= 9)
    }

    private fun passed(value: SmartBoardAuditResult) =
        value.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION)

    private fun blankPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }.also { bitmap.recycle() }
    }

    private fun graphFamilyMatches(expected: String, detected: String): Boolean {
        val actual = detected.lowercase()
        return when (expected.lowercase()) {
            "line", "vertical-line", "horizontal-line" -> "line" in actual
            "quadratic", "quadratic-grid" -> "quadratic" in actual || "parabola" in actual
            "sine", "cosine", "tangent" -> "sine" in actual
            "exponential", "exponential-decay" -> "exponential" in actual
            else -> actual.contains(expected.lowercase())
        }
    }

    private fun shapeFamilyMatches(expected: String, detected: SmartBoardShapeType): Boolean {
        if (expected == detected.name) return true
        return when (expected) {
            "POINT" -> detected in setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.CLOSED_REGION)
            "RIGHT_ANGLE" -> detected in setOf(SmartBoardShapeType.RIGHT_ANGLE_MARKER, SmartBoardShapeType.PERPENDICULAR_LINES)
            "POLYGON" -> detected in setOf(
                SmartBoardShapeType.POLYGON, SmartBoardShapeType.SQUARE, SmartBoardShapeType.RECTANGLE,
                SmartBoardShapeType.PENTAGON, SmartBoardShapeType.HEXAGON,
            )
            "CIRCLE" -> detected in setOf(SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ELLIPSE)
            "SPHERE" -> detected in setOf(SmartBoardShapeType.SPHERE, SmartBoardShapeType.CIRCLE, SmartBoardShapeType.ELLIPSE)
            "CUBOID" -> detected in setOf(SmartBoardShapeType.CUBOID, SmartBoardShapeType.CUBE, SmartBoardShapeType.POLYGON)
            "CONE" -> detected in setOf(SmartBoardShapeType.CONE, SmartBoardShapeType.CYLINDER)
            else -> false
        }
    }

    private companion object {
        const val TAG = "SMART_BOARD_AUDIT"
        const val TIMEOUT_MS = 10_000L
    }
}

private object AuditStrokeFactory {
    fun create(case: SmartBoardAuditCase, seed: Int): AuditInput {
        val base = when (case.category) {
            AuditCategory.GRAPHS -> graph(case, seed)
            AuditCategory.GEOMETRY_DIAGRAMS -> diagram(case, seed)
            else -> HumanInkWriter.write(case.expectedPlainText.orEmpty(), seed)
        }
        val profile = transformProfile(base, case.handwritingProfile, case.strokeVariant)
        val positioned = position(profile, case.canvasRegion)
        val bounds = SmartBoardBounds.from(positioned.flatMap { stroke -> stroke.points.map { it.position } }).expand(16f)
        return AuditInput(case, positioned, bounds)
    }

    private fun transformProfile(
        source: List<StrokeElement>,
        profile: HandwritingProfile,
        variant: String,
    ): List<StrokeElement> {
        val sourceBounds = SmartBoardBounds.from(source.flatMap { stroke -> stroke.points.map { it.position } })
        val scale = when (profile) {
            HandwritingProfile.SMALL_COMPACT -> .58f
            HandwritingProfile.LARGE_BOARD -> 1.55f
            HandwritingProfile.CROWDED -> .78f
            HandwritingProfile.WIDELY_SPACED -> 1.25f
            else -> 1f
        }
        val slant = when (profile) {
            HandwritingProfile.RIGHT_SLANTED -> .18f
            HandwritingProfile.LEFT_SLANTED -> -.18f
            else -> 0f
        }
        val pressureScale = when (profile) {
            HandwritingProfile.HEAVY_PRESSURE -> 1.45f
            HandwritingProfile.LIGHT_BROKEN -> .45f
            else -> 1f
        }
        val transformed = source.mapIndexed { strokeIndex, stroke ->
            val points = stroke.points.mapIndexedNotNull { pointIndex, point ->
                if (profile == HandwritingProfile.LIGHT_BROKEN && pointIndex % 7 == 3) return@mapIndexedNotNull null
                val localX = point.x - sourceBounds.left
                val localY = point.y - sourceBounds.top
                val baseline = when (profile) {
                    HandwritingProfile.UNEVEN_BASELINE -> sin(localX / 32f) * 5f
                    HandwritingProfile.SLIGHTLY_SHAKY -> sin(pointIndex * 2.1f) * 2.2f
                    else -> 0f
                }
                StrokePoint(
                    x = sourceBounds.left + localX * scale + slant * localY,
                    y = sourceBounds.top + localY * scale + baseline,
                    pressure = (point.pressure * pressureScale).coerceAtLeast(.05f),
                    timestampMillis = point.timestampMillis + if ("delayed" in variant && strokeIndex == source.lastIndex) 500 else 0,
                )
            }.let { if (it.size >= 2) it else stroke.points.take(2) }
            stroke.copy(
                points = points,
                width = when (profile) {
                    HandwritingProfile.HEAVY_PRESSURE -> stroke.width * 1.6f
                    HandwritingProfile.LIGHT_BROKEN -> stroke.width * .65f
                    else -> stroke.width
                },
                bounds = SmartBoardBounds.from(points.map(StrokePoint::position)),
            )
        }.toMutableList()
        if ("reverse-symbol-strokes" in variant) transformed.reverse()
        if (profile == HandwritingProfile.OVERWRITTEN_CORRECTION && transformed.isNotEmpty()) {
            val bounds = SmartBoardBounds.from(transformed.flatMap { it.points.map(StrokePoint::position) })
            transformed += stroke("correction", listOf(
                SmartBoardPoint(bounds.left + bounds.width * .25f, bounds.top + bounds.height * .25f),
                SmartBoardPoint(bounds.left + bounds.width * .42f, bounds.bottom),
            ), 99_000L)
        }
        return transformed
    }

    private fun position(source: List<StrokeElement>, region: CanvasRegion): List<StrokeElement> {
        val bounds = SmartBoardBounds.from(source.flatMap { it.points.map(StrokePoint::position) })
        val target = when (region) {
            CanvasRegion.TOP_LEFT -> SmartBoardPoint(24f, 24f)
            CanvasRegion.TOP_CENTER -> SmartBoardPoint(500f, 24f)
            CanvasRegion.TOP_RIGHT -> SmartBoardPoint(1000f, 24f)
            CanvasRegion.CENTER_LEFT -> SmartBoardPoint(24f, 280f)
            CanvasRegion.CENTER -> SmartBoardPoint(500f, 280f)
            CanvasRegion.CENTER_RIGHT -> SmartBoardPoint(1000f, 280f)
            CanvasRegion.BOTTOM_LEFT -> SmartBoardPoint(24f, 580f)
            CanvasRegion.BOTTOM_CENTER -> SmartBoardPoint(500f, 580f)
            CanvasRegion.BOTTOM_RIGHT -> SmartBoardPoint(1000f, 580f)
            CanvasRegion.NEAR_BOUNDARY -> SmartBoardPoint(2f, 2f)
            CanvasRegion.NEAR_TOOLBAR -> SmartBoardPoint(40f, 690f)
        }
        val dx = target.x - bounds.left
        val dy = target.y - bounds.top
        return source.map { element ->
            val points = element.points.map { it.copy(x = it.x + dx, y = it.y + dy) }
            element.copy(points = points, bounds = SmartBoardBounds.from(points.map(StrokePoint::position)))
        }
    }

    private fun graph(case: SmartBoardAuditCase, seed: Int): List<StrokeElement> {
        val family = case.expectedGraph?.type.orEmpty()
        val origin = SmartBoardPoint(180f, 145f)
        val axes = listOf(
            stroke("$seed-axis-x", listOf(SmartBoardPoint(30f, origin.y), SmartBoardPoint(330f, origin.y)), seed.toLong()),
            stroke("$seed-axis-y", listOf(SmartBoardPoint(origin.x, 20f), SmartBoardPoint(origin.x, 270f)), seed + 100L),
        )
        val points = when {
            family == "vertical-line" -> List(24) { i -> SmartBoardPoint(origin.x + 42f, 30f + i * 9f) }
            family == "horizontal-line" -> List(32) { i -> SmartBoardPoint(30f + i * 9f, origin.y - 60f) }
            "quadratic" in family -> sample(-3.0, 3.0, 48) { x -> x * x - if ("shifted" in case.subcategory) 1.0 else 0.0 }
            family == "cubic" -> sample(-2.2, 2.2, 48) { x -> x.pow(3) }
            family in setOf("sine", "cosine", "tangent", "hyperbolic") -> sample(-3.2, 3.2, 64) { x ->
                when (family) {
                    "cosine" -> cos(x)
                    "tangent" -> kotlin.math.tan(x).coerceIn(-3.0, 3.0)
                    "hyperbolic" -> kotlin.math.cosh(x / 2).coerceAtMost(4.0)
                    else -> sin(if ("frequency" in case.subcategory) 2 * x else x) * if ("scaled" in case.subcategory) 2 else 1
                }
            }
            family in setOf("exponential", "exponential-decay", "logistic", "gaussian", "logarithmic", "log-rational") ->
                sample(-3.0, 3.0, 52) { x ->
                    when (family) {
                        "exponential-decay" -> .5.pow(x)
                        "logistic" -> 1 / (1 + exp(-x))
                        "gaussian" -> exp(-x * x)
                        "logarithmic", "log-rational" -> if (x > .1) kotlin.math.ln(x) else Double.NaN
                        else -> exp(x / 1.5)
                    }
                }
            family in setOf("circle", "ellipse", "parametric-circle", "polar-cardioid") -> List(72) { i ->
                val t = 2 * PI * i / 71
                val rx = if (family == "ellipse") 3.0 else if (family == "polar-cardioid") 2 * (1 + cos(t)) else 2.5
                val ry = if (family == "ellipse") 1.7 else rx
                graphPoint(rx * cos(t), ry * sin(t))
            }
            family == "absolute" -> sample(-3.0, 3.0, 48) { kotlin.math.abs(it) }
            family in setOf("reciprocal", "reciprocal-squared", "hyperbola", "rational", "rational-hole") ->
                sample(-3.0, 3.0, 56) { x -> if (kotlin.math.abs(x) < .18) Double.NaN else if (family == "reciprocal-squared") 1 / (x * x) else 1 / x }
            family in setOf("square-root", "cube-root") -> sample(-3.0, 3.0, 48) { x ->
                if (family == "square-root") if (x >= 0) kotlin.math.sqrt(x) else Double.NaN else kotlin.math.sign(x) * kotlin.math.abs(x).pow(1.0 / 3.0)
            }
            family in setOf("step", "signum") -> sample(-3.0, 3.0, 48) { x -> if (family == "step") kotlin.math.floor(x) else kotlin.math.sign(x) }
            else -> sample(-3.0, 3.0, 48) { x -> x }
        }
        val finiteRuns = points.fold(mutableListOf<MutableList<SmartBoardPoint>>()) { runs, point ->
            if (!point.x.isFinite() || !point.y.isFinite()) {
                if (runs.lastOrNull()?.isNotEmpty() == true) runs.add(mutableListOf())
            } else {
                if (runs.isEmpty()) runs.add(mutableListOf())
                runs.last() += point
            }
            runs
        }.filter { it.size >= 2 }
        return axes + finiteRuns.mapIndexed { index, run -> stroke("$seed-curve-$index", run, seed + 200L + index) }
    }

    private fun diagram(case: SmartBoardAuditCase, seed: Int): List<StrokeElement> {
        val expected = runCatching { SmartBoardShapeType.valueOf(case.expectedDiagram?.shapeType.orEmpty()) }.getOrNull()
        return when (expected) {
            SmartBoardShapeType.CIRCLE, SmartBoardShapeType.SPHERE -> listOf(round(seed, 65f, 65f, 50f, 50f))
            SmartBoardShapeType.ELLIPSE -> listOf(round(seed, 65f, 65f, 56f, 34f))
            SmartBoardShapeType.TRIANGLE, SmartBoardShapeType.EQUILATERAL_TRIANGLE ->
                polygon(seed, listOf(SmartBoardPoint(65f, 8f), SmartBoardPoint(116f, 108f), SmartBoardPoint(14f, 108f)))
            SmartBoardShapeType.RIGHT_TRIANGLE ->
                polygon(seed, listOf(SmartBoardPoint(15f, 15f), SmartBoardPoint(15f, 108f), SmartBoardPoint(116f, 108f)))
            SmartBoardShapeType.SQUARE ->
                polygon(seed, listOf(SmartBoardPoint(15f, 15f), SmartBoardPoint(110f, 15f), SmartBoardPoint(110f, 110f), SmartBoardPoint(15f, 110f)))
            SmartBoardShapeType.RECTANGLE ->
                polygon(seed, listOf(SmartBoardPoint(8f, 28f), SmartBoardPoint(122f, 28f), SmartBoardPoint(122f, 98f), SmartBoardPoint(8f, 98f)))
            SmartBoardShapeType.PENTAGON -> regular(seed, 5)
            SmartBoardShapeType.HEXAGON -> regular(seed, 6)
            SmartBoardShapeType.POLYGON -> regular(seed, 8)
            SmartBoardShapeType.CUBE, SmartBoardShapeType.CUBOID -> box(seed)
            SmartBoardShapeType.CYLINDER -> cylinder(seed)
            SmartBoardShapeType.CONE -> cone(seed)
            SmartBoardShapeType.PYRAMID -> pyramid(seed)
            SmartBoardShapeType.LINE, SmartBoardShapeType.LINE_SEGMENT, SmartBoardShapeType.RAY,
            SmartBoardShapeType.HORIZONTAL_LINE, SmartBoardShapeType.VERTICAL_LINE ->
                listOf(stroke("$seed-line", listOf(SmartBoardPoint(15f, 65f), SmartBoardPoint(120f, 65f)), seed.toLong()))
            SmartBoardShapeType.PARALLEL_LINES -> listOf(
                stroke("$seed-p1", listOf(SmartBoardPoint(15f, 40f), SmartBoardPoint(120f, 40f)), seed.toLong()),
                stroke("$seed-p2", listOf(SmartBoardPoint(15f, 85f), SmartBoardPoint(120f, 85f)), seed + 1L),
            )
            SmartBoardShapeType.PERPENDICULAR_LINES, SmartBoardShapeType.RIGHT_ANGLE_MARKER -> listOf(
                stroke("$seed-h", listOf(SmartBoardPoint(15f, 65f), SmartBoardPoint(120f, 65f)), seed.toLong()),
                stroke("$seed-v", listOf(SmartBoardPoint(65f, 15f), SmartBoardPoint(65f, 115f)), seed + 1L),
            )
            SmartBoardShapeType.ANGLE -> listOf(
                stroke("$seed-a1", listOf(SmartBoardPoint(65f, 65f), SmartBoardPoint(120f, 30f)), seed.toLong()),
                stroke("$seed-a2", listOf(SmartBoardPoint(65f, 65f), SmartBoardPoint(115f, 105f)), seed + 1L),
            )
            SmartBoardShapeType.ARC -> listOf(round(seed, 65f, 65f, 50f, 50f, half = true))
            else -> polygon(seed, listOf(SmartBoardPoint(15f, 20f), SmartBoardPoint(118f, 25f), SmartBoardPoint(105f, 105f), SmartBoardPoint(20f, 110f)))
        }
    }

    private fun sample(start: Double, end: Double, count: Int, function: (Double) -> Double): List<SmartBoardPoint> =
        List(count) { index ->
            val x = start + (end - start) * index / (count - 1)
            val y = function(x)
            if (y.isFinite()) graphPoint(x, y.coerceIn(-5.0, 5.0)) else SmartBoardPoint(Float.NaN, Float.NaN)
        }

    private fun graphPoint(x: Double, y: Double) = SmartBoardPoint((180 + x * 42).toFloat(), (145 - y * 30).toFloat())

    private fun regular(seed: Int, sides: Int): List<StrokeElement> = polygon(
        seed,
        List(sides) { index ->
            val angle = -PI / 2 + 2 * PI * index / sides
            SmartBoardPoint((65 + 52 * cos(angle)).toFloat(), (65 + 52 * sin(angle)).toFloat())
        },
    )

    private fun polygon(seed: Int, points: List<SmartBoardPoint>) =
        listOf(stroke("$seed-polygon", points + points.first(), seed.toLong()))

    private fun round(seed: Int, cx: Float, cy: Float, rx: Float, ry: Float, half: Boolean = false): StrokeElement {
        val count = if (half) 32 else 56
        val max = if (half) PI else 2 * PI
        val points = List(count) { index ->
            val angle = max * index / (count - 1)
            SmartBoardPoint(cx + rx * cos(angle).toFloat(), cy + ry * sin(angle).toFloat())
        }
        return stroke("$seed-round", points, seed.toLong())
    }

    private fun box(seed: Int): List<StrokeElement> {
        val front = listOf(SmartBoardPoint(15f, 35f), SmartBoardPoint(90f, 35f), SmartBoardPoint(90f, 110f), SmartBoardPoint(15f, 110f))
        val back = front.map { SmartBoardPoint(it.x + 28f, it.y - 22f) }
        val edges = buildList {
            repeat(4) { index ->
                add(front[index] to front[(index + 1) % 4])
                add(back[index] to back[(index + 1) % 4])
                add(front[index] to back[index])
            }
        }
        return edges.mapIndexed { index, (a, b) -> stroke("$seed-box-$index", listOf(a, b), seed + index.toLong()) }
    }

    private fun cylinder(seed: Int) = listOf(
        round(seed, 65f, 28f, 42f, 14f),
        round(seed + 1, 65f, 100f, 42f, 14f),
        stroke("$seed-cyl-l", listOf(SmartBoardPoint(23f, 28f), SmartBoardPoint(23f, 100f)), seed + 2L),
        stroke("$seed-cyl-r", listOf(SmartBoardPoint(107f, 28f), SmartBoardPoint(107f, 100f)), seed + 3L),
    )

    private fun cone(seed: Int) = listOf(
        round(seed, 65f, 102f, 44f, 13f),
        stroke("$seed-cone-l", listOf(SmartBoardPoint(65f, 10f), SmartBoardPoint(21f, 102f)), seed + 1L),
        stroke("$seed-cone-r", listOf(SmartBoardPoint(65f, 10f), SmartBoardPoint(109f, 102f)), seed + 2L),
    )

    private fun pyramid(seed: Int): List<StrokeElement> {
        val base = listOf(SmartBoardPoint(20f, 88f), SmartBoardPoint(65f, 112f), SmartBoardPoint(112f, 88f), SmartBoardPoint(65f, 68f))
        val apex = SmartBoardPoint(65f, 8f)
        val edges = base.indices.map { base[it] to base[(it + 1) % base.size] } + base.map { it to apex }
        return edges.mapIndexed { index, (a, b) -> stroke("$seed-pyr-$index", listOf(a, b), seed + index.toLong()) }
    }

    private fun stroke(id: String, points: List<SmartBoardPoint>, time: Long): StrokeElement {
        val expanded = if (points.size == 2) {
            List(10) { index ->
                val t = index / 9f
                SmartBoardPoint(
                    points[0].x + (points[1].x - points[0].x) * t,
                    points[0].y + (points[1].y - points[0].y) * t,
                )
            }
        } else points
        val strokePoints = expanded.mapIndexed { index, point ->
            StrokePoint(point.x, point.y, .66f, (time.coerceAtLeast(0) * 10 + index * 8).coerceAtLeast(0))
        }
        return StrokeElement(
            id = id,
            points = strokePoints,
            tool = StrokeTool.PEN,
            width = 3.2f,
            opacity = 1f,
            argbColor = 0xFF111111,
            bounds = SmartBoardBounds.from(expanded),
            createdAt = time.coerceAtLeast(0),
        )
    }
}
