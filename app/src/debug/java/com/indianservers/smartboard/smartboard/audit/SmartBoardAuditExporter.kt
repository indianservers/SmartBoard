package com.indianservers.smartboard.smartboard.audit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.indianservers.smartboard.smartboard.models.StrokeElement
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class SmartBoardAuditExporter(
    context: Context,
    runId: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()),
) {
    val root: File = File(context.getExternalFilesDir(null), "audit-output/$runId")
    private val report = File(root, "report")
    private val results = File(root, "results")
    private val strokes = File(root, "strokes")
    private val rendered = File(root, "rendered-inputs")
    private val overlays = File(root, "overlays")
    private val failures = File(root, "failures")
    private val screenshots = File(root, "screenshots")

    init {
        listOf(report, results, strokes, rendered, overlays, failures, screenshots).forEach(File::mkdirs)
    }

    fun saveInput(case: SmartBoardAuditCase, input: List<StrokeElement>, png: ByteArray): String {
        val strokeFile = File(strokes, "${case.id}.json")
        strokeFile.writeText(
            JSONArray().apply {
                input.forEach { stroke ->
                    put(JSONObject().apply {
                        put("id", stroke.id)
                        put("width", stroke.width)
                        put("opacity", stroke.opacity)
                        put("color", stroke.argbColor)
                        put("points", JSONArray().apply {
                            stroke.points.forEach {
                                put(JSONObject().apply {
                                    put("x", it.x)
                                    put("y", it.y)
                                    put("pressure", it.pressure)
                                    put("timestamp_ms", it.timestampMillis)
                                })
                            }
                        })
                    })
                }
            }.toString(2),
        )
        File(rendered, "${case.id}.png").writeBytes(png)
        return rendered.relativeTo(root).invariantSeparatorsPath
    }

    fun saveFailure(case: SmartBoardAuditCase, result: SmartBoardAuditResult, png: ByteArray): String {
        val input = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)
        val width = maxOf(input.width, 1100)
        val rowHeight = 180
        val output = Bitmap.createBitmap(width, input.height + rowHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(input, 0f, 0f, null)
        val divider = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
        canvas.drawLine(width / 3f, input.height.toFloat(), width / 3f, output.height.toFloat(), divider)
        canvas.drawLine(width * 2 / 3f, input.height.toFloat(), width * 2 / 3f, output.height.toFloat(), divider)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 40, 75)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 21f }
        canvas.drawText("EXPECTED", 16f, input.height + 30f, title)
        canvas.drawText(case.expectedPlainText.orEmpty().take(42), 16f, input.height + 68f, body)
        canvas.drawText("DETECTED", width / 3f + 16f, input.height + 30f, title)
        canvas.drawText(result.rawRecognitionOutput.orEmpty().take(42), width / 3f + 16f, input.height + 68f, body)
        canvas.drawText("AUDIT", width * 2 / 3f + 16f, input.height + 30f, title)
        canvas.drawText(result.status.name, width * 2 / 3f + 16f, input.height + 68f, body)
        canvas.drawText(result.errorTypes.joinToString(",").take(42), width * 2 / 3f + 16f, input.height + 104f, body)
        canvas.drawText("confidence=${result.confidence ?: "n/a"}", width * 2 / 3f + 16f, input.height + 140f, body)
        val file = File(failures, "${case.id}.png")
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.relativeTo(root).invariantSeparatorsPath
    }

    fun finish(
        cases: List<SmartBoardAuditCase>,
        auditedResults: List<SmartBoardAuditResult>,
        device: Map<String, String>,
        elapsedMs: Long,
    ) {
        val byId = cases.associateBy(SmartBoardAuditCase::id)
        writeResultsCsv(auditedResults, byId)
        writeFailuresCsv(auditedResults, byId)
        writeCategoryCsv(auditedResults, byId)
        writePerformanceCsv(auditedResults, byId)
        writeConfusionCsv(auditedResults, byId)
        writeSummaryJson(cases, auditedResults, device, elapsedMs)
        writeReport(cases, auditedResults, device, elapsedMs)
        writeRecommendations(auditedResults, byId)
    }

    private fun writeResultsCsv(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        val header = listOf(
            "case_id", "category", "subcategory", "difficulty", "handwriting_profile",
            "stroke_variant", "canvas_region", "expected_plain_text", "expected_latex",
            "raw_detected_output", "normalized_detected_output", "detected_latex", "confidence",
            "recognition_time_ms", "exact_match", "semantic_match", "structure_match", "layout_match",
            "symbol_score", "structure_score", "spatial_score", "overall_score", "status",
            "error_types", "evidence_path", "notes",
        )
        File(results, "SMART_BOARD_AUDIT_RESULTS.csv").bufferedWriter().use { writer ->
            writer.appendLine(header.joinToString(","))
            values.forEach { value ->
                val case = cases.getValue(value.caseId)
                writer.appendLine(
                    listOf(
                        value.caseId, case.category, case.subcategory, case.difficulty, case.handwritingProfile,
                        case.strokeVariant, case.canvasRegion, case.expectedPlainText, case.expectedLatex,
                        value.rawRecognitionOutput, value.normalizedRecognitionOutput, value.detectedLatex,
                        value.confidence, value.recognitionTimeMs, value.exactMatch, value.semanticMatch,
                        value.structureMatch, value.layoutMatch, value.symbolScore, value.structureScore,
                        value.spatialScore, value.overallScore, value.status,
                        value.errorTypes.joinToString("|"), value.evidencePath, value.notes,
                    ).joinToString(",") { csv(it) },
                )
            }
        }
    }

    private fun writeFailuresCsv(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        File(results, "SMART_BOARD_AUDIT_FAILURES.csv").bufferedWriter().use { writer ->
            writer.appendLine("case_id,category,expected,detected,status,error_types,evidence_path,notes")
            values.filterNot { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }.forEach {
                val case = cases.getValue(it.caseId)
                writer.appendLine(
                    listOf(it.caseId, case.category, case.expectedPlainText, it.rawRecognitionOutput, it.status,
                        it.errorTypes.joinToString("|"), it.evidencePath, it.notes).joinToString(",") { value -> csv(value) },
                )
            }
        }
    }

    private fun writeCategoryCsv(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        File(results, "SMART_BOARD_CATEGORY_METRICS.csv").bufferedWriter().use { writer ->
            writer.appendLine("category,total,passed,normalized,partial,failed,not_detected,crashed,pass_rate,exact_rate,semantic_rate,avg_symbol,avg_structure,avg_spatial,avg_confidence,median_ms,p95_ms,most_frequent_error,hardest_profile,worst_region")
            AuditCategory.entries.forEach { category ->
                val rows = values.filter { cases[it.caseId]?.category == category }
                if (rows.isEmpty()) return@forEach
                val pass = rows.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }
                val normalized = rows.count { it.status == AuditStatus.PASS_WITH_NORMALIZATION }
                val notDetected = rows.count { it.status == AuditStatus.NOT_DETECTED }
                val crashes = rows.count { it.status == AuditStatus.CRASH }
                val failed = rows.size - pass - rows.count { it.status == AuditStatus.PARTIAL }
                val durations = rows.map(SmartBoardAuditResult::recognitionTimeMs).sorted()
                val mostError = rows.flatMap { it.errorTypes }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val hardestProfile = rows.groupBy { cases.getValue(it.caseId).handwritingProfile }
                    .minByOrNull { (_, profileRows) -> profileRows.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }.toDouble() / profileRows.size }?.key
                val worstRegion = rows.groupBy { cases.getValue(it.caseId).canvasRegion }
                    .minByOrNull { (_, regionRows) -> regionRows.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }.toDouble() / regionRows.size }?.key
                writer.appendLine(listOf(
                    category, rows.size, pass, normalized, rows.count { it.status == AuditStatus.PARTIAL }, failed,
                    notDetected, crashes, ratio(pass, rows.size), ratio(rows.count { it.exactMatch }, rows.size),
                    ratio(rows.count { it.semanticMatch }, rows.size), average(rows.map { it.symbolScore }),
                    average(rows.map { it.structureScore }), average(rows.map { it.spatialScore }),
                    average(rows.mapNotNull { it.confidence?.toDouble() }), percentile(durations, .5),
                    percentile(durations, .95), mostError, hardestProfile, worstRegion,
                ).joinToString(",") { value -> csv(value) })
            }
        }
    }

    private fun writePerformanceCsv(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        File(results, "SMART_BOARD_PERFORMANCE_METRICS.csv").bufferedWriter().use { writer ->
            writer.appendLine("scope,count,median_ms,p90_ms,p95_ms,p99_ms,mean_ms,max_ms,timeouts,crashes")
            fun row(scope: String, rows: List<SmartBoardAuditResult>) {
                if (rows.isEmpty()) return
                val times = rows.map(SmartBoardAuditResult::recognitionTimeMs).sorted()
                writer.appendLine(listOf(
                    scope, rows.size, percentile(times, .5), percentile(times, .9), percentile(times, .95),
                    percentile(times, .99), average(times.map(Long::toDouble)), times.last(),
                    rows.count { it.status == AuditStatus.TIMEOUT }, rows.count { it.status == AuditStatus.CRASH },
                ).joinToString(",") { value -> csv(value) })
            }
            row("overall", values)
            AuditCategory.entries.forEach { category ->
                row(category.name, values.filter { cases[it.caseId]?.category == category })
            }
        }
    }

    private fun writeConfusionCsv(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        val pairs = mutableMapOf<Pair<Char, Char>, Int>()
        values.forEach { value ->
            val expected = SmartBoardAuditScoring.normalize(cases.getValue(value.caseId).expectedPlainText.orEmpty())
            val actual = value.normalizedRecognitionOutput.orEmpty()
            val limit = minOf(expected.length, actual.length)
            repeat(limit) { index ->
                if (expected[index] != actual[index]) pairs[expected[index] to actual[index]] =
                    pairs.getOrDefault(expected[index] to actual[index], 0) + 1
            }
        }
        val requested = listOf(
            '0' to 'O', '0' to 'θ', '1' to 'l', '1' to 'i', '2' to 'z', '3' to '8',
            '5' to 'S', '6' to 'b', '9' to 'q', 'x' to '×', '-' to '/', '=' to '≠',
            '<' to '≤', '>' to '≥', '∪' to 'U', '∩' to 'n', 'π' to 'n', '∞' to '8',
            'θ' to '0', 'α' to 'a', 'β' to 'B', 'μ' to 'u', 'σ' to '6', '∂' to 'd', '∫' to 'S',
        )
        File(results, "SMART_BOARD_SYMBOL_CONFUSION_MATRIX.csv").bufferedWriter().use { writer ->
            writer.appendLine("expected_symbol,detected_symbol,frequency,impact")
            (requested + pairs.keys).distinct().sortedByDescending { pairs.getOrDefault(it, 0) }.forEach { pair ->
                val frequency = pairs.getOrDefault(pair, 0)
                val impact = when {
                    frequency >= 10 -> "HIGH"
                    frequency >= 3 -> "MEDIUM"
                    else -> "LOW"
                }
                writer.appendLine(listOf(pair.first, pair.second, frequency, impact).joinToString(",") { value -> csv(value) })
            }
        }
    }

    private fun writeSummaryJson(
        cases: List<SmartBoardAuditCase>,
        values: List<SmartBoardAuditResult>,
        device: Map<String, String>,
        elapsedMs: Long,
    ) {
        val pass = values.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }
        val json = JSONObject().apply {
            put("schema_version", 1)
            put("generated_cases", cases.size)
            put("executed_cases", values.size)
            put("pass_count", pass)
            put("pass_rate", ratio(pass, values.size))
            put("exact_match_rate", ratio(values.count { it.exactMatch }, values.size))
            put("semantic_match_rate", ratio(values.count { it.semanticMatch }, values.size))
            put("average_symbol_score", average(values.map { it.symbolScore }))
            put("average_structure_score", average(values.map { it.structureScore }))
            put("average_spatial_score", average(values.map { it.spatialScore }))
            put("crashes", values.count { it.status == AuditStatus.CRASH })
            put("timeouts", values.count { it.status == AuditStatus.TIMEOUT })
            put("elapsed_ms", elapsedMs)
            put("device", JSONObject(device))
            put("category_counts", JSONObject().apply {
                AuditCategory.entries.forEach { category ->
                    put(category.name, cases.count { it.category == category })
                }
            })
        }
        File(report, "SMART_BOARD_AUDIT_SUMMARY.json").writeText(json.toString(2))
    }

    private fun writeReport(
        cases: List<SmartBoardAuditCase>,
        values: List<SmartBoardAuditResult>,
        device: Map<String, String>,
        elapsedMs: Long,
    ) {
        val byId = cases.associateBy(SmartBoardAuditCase::id)
        val passed = values.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }
        val passRate = ratio(passed, values.size)
        val conclusion = when {
            values.any { it.status == AuditStatus.CRASH } -> "NOT READY"
            passRate >= .90 -> "READY WITH MINOR FIXES"
            passRate >= .80 -> "CONDITIONALLY READY"
            else -> "NOT READY"
        }
        val times = values.map(SmartBoardAuditResult::recognitionTimeMs).sorted()
        File(report, "SMART_BOARD_AUDIT_REPORT.md").bufferedWriter().use { out ->
            out.appendLine("# Smart Board Handwriting Recognition Audit")
            out.appendLine()
            out.appendLine("## 1. Executive summary")
            out.appendLine()
            out.appendLine("| Measure | Result |")
            out.appendLine("|---|---:|")
            out.appendLine("| Generated | ${cases.size} |")
            out.appendLine("| Executed | ${values.size} |")
            out.appendLine("| Pass rate | ${formatPercent(passRate)} |")
            out.appendLine("| Exact match | ${formatPercent(ratio(values.count { it.exactMatch }, values.size))} |")
            out.appendLine("| Semantic match | ${formatPercent(ratio(values.count { it.semanticMatch }, values.size))} |")
            out.appendLine("| Average structural score | ${formatPercent(average(values.map { it.structureScore }))} |")
            out.appendLine("| Average spatial score | ${formatPercent(average(values.map { it.spatialScore }))} |")
            out.appendLine("| Crashes | ${values.count { it.status == AuditStatus.CRASH }} |")
            out.appendLine("| Timeouts | ${values.count { it.status == AuditStatus.TIMEOUT }} |")
            out.appendLine("| Conclusion | **$conclusion** |")
            out.appendLine()
            out.appendLine("## 2. Scope and methodology")
            out.appendLine()
            out.appendLine("The corpus contains 14 mandatory categories with 40 unique cases each. Automated cases replay stroke-level human-style ink through the production multimodal pipeline. Graph and geometry cases draw curves/diagrams and use the production graph/shape engines. Literal, semantic, structural and spatial scores are retained independently.")
            out.appendLine()
            out.appendLine("Device: ${device.entries.joinToString { "${it.key}=${it.value}" }}. Total elapsed time: ${elapsedMs} ms.")
            out.appendLine()
            out.appendLine("## 3. Overall results")
            out.appendLine()
            out.appendLine("| Category | Tests | Pass | Partial | Fail | Pass rate | Avg confidence | P95 time |")
            out.appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
            AuditCategory.entries.forEach { category ->
                val rows = values.filter { byId[it.caseId]?.category == category }
                if (rows.isEmpty()) return@forEach
                val pass = rows.count { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }
                val partial = rows.count { it.status == AuditStatus.PARTIAL }
                out.appendLine("| ${category.name} | ${rows.size} | $pass | $partial | ${rows.size - pass - partial} | ${formatPercent(ratio(pass, rows.size))} | ${"%.3f".format(Locale.US, average(rows.mapNotNull { it.confidence?.toDouble() }))} | ${percentile(rows.map { it.recognitionTimeMs }.sorted(), .95)} ms |")
            }
            out.appendLine()
            out.appendLine("## 4. Category analysis")
            out.appendLine()
            AuditCategory.entries.forEach { category ->
                val rows = values.filter { byId[it.caseId]?.category == category }
                if (rows.isEmpty()) return@forEach
                val error = rows.flatMap { it.errorTypes }.groupingBy { it }.eachCount().maxByOrNull { it.value }
                out.appendLine("### ${category.name}")
                out.appendLine()
                out.appendLine("- Strength: ${rows.maxByOrNull { it.overallScore }?.caseId ?: "not measured"}.")
                out.appendLine("- Weakness: ${rows.minByOrNull { it.overallScore }?.caseId ?: "not measured"}.")
                out.appendLine("- Most frequent error: ${error?.key ?: "none"} (${error?.value ?: 0} cases).")
                out.appendLine("- Recommended correction: see the evidence-linked recommendation file.")
                out.appendLine()
            }
            out.appendLine("## 5. Expected versus detected examples")
            out.appendLine()
            val examples =
                values.filter { it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION) }.take(20) +
                values.filter { it.status == AuditStatus.PARTIAL }.take(30) +
                values.filterNot {
                    it.status in setOf(AuditStatus.PASS, AuditStatus.PASS_WITH_NORMALIZATION, AuditStatus.PARTIAL)
                }.take(50)
            examples.forEach { value ->
                val case = byId.getValue(value.caseId)
                out.appendLine("- **${value.caseId}** — Expected `${case.expectedPlainText}`; detected `${value.rawRecognitionOutput}`; normalized `${value.normalizedRecognitionOutput}`; confidence `${value.confidence}`; status **${value.status}**; errors `${value.errorTypes.joinToString()}`; evidence `${value.evidencePath}`.")
            }
            out.appendLine()
            out.appendLine("## 6. Symbol confusion analysis")
            out.appendLine()
            out.appendLine("See `../results/SMART_BOARD_SYMBOL_CONFUSION_MATRIX.csv`; pairs are ranked by observed frequency and impact.")
            out.appendLine()
            out.appendLine("## 7. Layout-recognition analysis")
            out.appendLine()
            out.appendLine("Independent spatial scores cover superscripts, subscripts, fraction bars, root scope, matrices, multiline grouping, graph classification and diagram classification. Failures retain layout errors even where conservative semantic normalization matches.")
            out.appendLine()
            out.appendLine("## 8. Environmental sensitivity")
            out.appendLine()
            out.appendLine("Profiles, regions and stroke variants are recorded per case in the results CSV. Physical stylus/finger and multi-device conclusions require Mode B manual runs and are not inferred from automated replay.")
            out.appendLine()
            out.appendLine("## 9. Performance results")
            out.appendLine()
            out.appendLine("Median ${percentile(times, .5)} ms; P90 ${percentile(times, .9)} ms; P95 ${percentile(times, .95)} ms; P99 ${percentile(times, .99)} ms. Detailed category metrics are in `../results/SMART_BOARD_PERFORMANCE_METRICS.csv`.")
            out.appendLine()
            out.appendLine("## 10. Root-cause analysis")
            out.appendLine()
            out.appendLine("Observed failures are classified by symbol, spatial hierarchy, grouping, graph/shape classification and environment sensitivity. The raw result is never modified before persistence.")
            out.appendLine()
            out.appendLine("## 11. Prioritized recommendations")
            out.appendLine()
            out.appendLine("See `SMART_BOARD_RECOMMENDATIONS.md` for evidence-linked P0-P4 actions.")
            out.appendLine()
            out.appendLine("## 12. Production-readiness conclusion")
            out.appendLine()
            out.appendLine("**$conclusion** based on the measured automated run. Manual handwriting, stylus/finger, phone/tablet and multi-Android-version coverage remain separate evidence requirements.")
        }
    }

    private fun writeRecommendations(values: List<SmartBoardAuditResult>, cases: Map<String, SmartBoardAuditCase>) {
        val grouped = values.flatMap { result -> result.errorTypes.map { it to result } }.groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }
        File(report, "SMART_BOARD_RECOMMENDATIONS.md").bufferedWriter().use { out ->
            out.appendLine("# Smart Board Audit Recommendations")
            out.appendLine()
            grouped.take(10).forEachIndexed { index, (error, rows) ->
                val priority = when {
                    error in setOf(AuditErrorType.CRASH, AuditErrorType.TIMEOUT) -> "P0"
                    error in setOf(AuditErrorType.FRACTION_SCOPE_ERROR, AuditErrorType.MATRIX_ROW_ERROR, AuditErrorType.GRAPH_NOT_DETECTED) -> "P1"
                    index < 5 -> "P2"
                    else -> "P3"
                }
                val ids = rows.take(12).joinToString { it.caseId }
                out.appendLine("## $priority — ${error.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)}")
                out.appendLine()
                out.appendLine("- Problem: `${error.name}` affected ${rows.size} executed cases.")
                out.appendLine("- Evidence: $ids${if (rows.size > 12) " and ${rows.size - 12} more" else ""}.")
                out.appendLine("- Likely root cause: ${rootCause(error)}.")
                out.appendLine("- Proposed fix: ${proposedFix(error)}.")
                out.appendLine("- Likely modules: ${likelyModules(error)}.")
                out.appendLine("- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.")
                out.appendLine("- Tests required: replay the original saved strokes plus adjacent passing cases from ${rows.map { cases.getValue(it.caseId).category }.distinct().joinToString()}.")
                out.appendLine("- Expected improvement: recover the ${rows.size} directly affected cases without changing unrelated categories.")
                out.appendLine()
            }
        }
    }

    private fun rootCause(error: AuditErrorType): String = when (error) {
        AuditErrorType.TIMEOUT -> "the blocking recognition provider continues inference after coroutine cancellation, so the requested deadline is not a hard execution boundary"
        AuditErrorType.SUPERSCRIPT_MISSED, AuditErrorType.SUBSCRIPT_MISSED,
        AuditErrorType.SUPERSCRIPT_WRONG_PARENT, AuditErrorType.SUBSCRIPT_WRONG_PARENT -> "weak vertical-zone assignment or premature baseline grouping"
        AuditErrorType.FRACTION_MISREAD, AuditErrorType.FRACTION_SCOPE_ERROR -> "horizontal-bar scope was not associated with numerator and denominator groups"
        AuditErrorType.MATRIX_ROW_ERROR, AuditErrorType.MATRIX_COLUMN_ERROR, AuditErrorType.MATRIX_BRACKET_ERROR -> "dense row/column segmentation and bracket ownership are ambiguous"
        AuditErrorType.GRAPH_NOT_DETECTED, AuditErrorType.GRAPH_TYPE_WRONG -> "axes/curve separation or fitted-family coverage is incomplete"
        AuditErrorType.SHAPE_NOT_DETECTED, AuditErrorType.SHAPE_TYPE_WRONG -> "component strokes were ranked above the composite diagram"
        AuditErrorType.LOCATION_SENSITIVE -> "canvas/document coordinate normalization differs near an edge or toolbar"
        AuditErrorType.STROKE_ORDER_SENSITIVE -> "grouping is finalized before later structural strokes arrive"
        else -> "symbol classifier ambiguity or insufficient structural evidence"
    }

    private fun proposedFix(error: AuditErrorType): String = when (error) {
        AuditErrorType.TIMEOUT -> "enforce the deadline at the provider/session boundary, abandon late fusion results by request fingerprint, and add cancellation tests for every saved timeout stroke"
        AuditErrorType.FRACTION_MISREAD, AuditErrorType.FRACTION_SCOPE_ERROR -> "re-run local grouping when a horizontal stroke crosses symbol boxes and preserve bar-first/bar-last alternatives"
        AuditErrorType.MATRIX_ROW_ERROR, AuditErrorType.MATRIX_COLUMN_ERROR, AuditErrorType.MATRIX_BRACKET_ERROR -> "score bracket-constrained row and column partitions before flattening cells"
        AuditErrorType.GRAPH_NOT_DETECTED, AuditErrorType.GRAPH_TYPE_WRONG -> "expand graph-family fitting and require crossing-axis evidence before treating curves as text noise"
        AuditErrorType.SHAPE_NOT_DETECTED, AuditErrorType.SHAPE_TYPE_WRONG -> "rank composite candidates using shared endpoints and hidden-edge topology"
        AuditErrorType.SUPERSCRIPT_MISSED, AuditErrorType.SUBSCRIPT_MISSED -> "delay parent assignment until the local vertical neighborhood stabilizes"
        else -> "add evidence-gated candidate ranking trained against the saved failing strokes"
    }

    private fun likelyModules(error: AuditErrorType): String = when (error) {
        AuditErrorType.GRAPH_NOT_DETECTED, AuditErrorType.GRAPH_TYPE_WRONG -> "SmartBoardMathGraphIntelligenceEngine"
        AuditErrorType.SHAPE_NOT_DETECTED, AuditErrorType.SHAPE_TYPE_WRONG -> "DeterministicAutoShapeRecognizer"
        AuditErrorType.MATRIX_ROW_ERROR, AuditErrorType.MATRIX_COLUMN_ERROR -> "StructureAwareRecognitionEnhancer, SmartBoardSemanticRecognitionEngine"
        else -> "StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine"
    }

    private fun csv(value: Any?): String {
        val text = value?.toString().orEmpty()
        return "\"${text.replace("\"", "\"\"").replace("\r", " ").replace("\n", "\\n")}\""
    }

    private fun ratio(numerator: Int, denominator: Int) = if (denominator == 0) 0.0 else numerator.toDouble() / denominator
    private fun average(values: List<Double>) = if (values.isEmpty()) 0.0 else values.average()
    private fun percentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0
        return values[(ceil(percentile * values.size).toInt() - 1).coerceIn(0, values.lastIndex)]
    }
    private fun formatPercent(value: Double) = String.format(Locale.US, "%.1f%%", value * 100.0)
}
