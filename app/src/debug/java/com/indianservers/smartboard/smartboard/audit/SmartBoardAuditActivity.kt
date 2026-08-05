package com.indianservers.smartboard.smartboard.audit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
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
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import kotlinx.coroutines.launch

class SmartBoardAuditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = MultimodalMathRecognitionEngine(
            MlKitMathRecognitionAdapter(),
            DedicatedOfflineImageMathRecognitionAdapter(
                this,
                OfflineMathOcrModelPack(this),
                MlKitImageMathRecognitionAdapter(),
            ),
        )
        setContent {
            MaterialTheme {
                ManualAuditScreen(engine)
            }
        }
    }
}

@Composable
private fun ManualAuditScreen(engine: MultimodalMathRecognitionEngine) {
    val cases = remember { SmartBoardAuditDataset.cases }
    var index by remember { mutableIntStateOf(0) }
    var expectedVisible by remember { mutableStateOf(true) }
    var raw by remember { mutableStateOf("") }
    var normalized by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf<Float?>(null) }
    var testerStatus by remember { mutableStateOf<AuditStatus?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val scope = rememberCoroutineScope()
    val case = cases[index]

    Row(Modifier.fillMaxSize().background(Color(0xFFF4F6FA)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(.29f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SMART Board Recognition Audit", style = MaterialTheme.typography.titleLarge)
            Text("DEBUG ONLY • Mode B: manual handwriting")
            Text("Case ${index + 1}/${cases.size}: ${case.id}")
            Text("Category: ${case.category}")
            Text("Difficulty: ${case.difficulty}")
            Text("Profile prompt: ${case.handwritingProfile}")
            Text("Stroke variant: ${case.strokeVariant}")
            Text("Canvas region: ${case.canvasRegion}")
            Row {
                Checkbox(expectedVisible, { expectedVisible = it })
                Text("Show expected prompt", Modifier.padding(top = 12.dp))
            }
            if (expectedVisible) Card(Modifier.fillMaxWidth()) {
                Text(case.expectedPlainText.orEmpty(), Modifier.padding(12.dp), style = MaterialTheme.typography.headlineSmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    index = (index - 1).coerceAtLeast(0)
                    strokes.clear(); raw = ""; normalized = ""; confidence = null; testerStatus = null
                }) { Text("Previous") }
                Button(onClick = {
                    index = (index + 1).coerceAtMost(cases.lastIndex)
                    strokes.clear(); raw = ""; normalized = ""; confidence = null; testerStatus = null
                }) { Text("Next") }
                Button(onClick = { expectedVisible = false }) { Text("Blind") }
            }
            Text("Category, difficulty and handwriting-profile selectors are represented by the corpus navigation and may be filtered with the case search field below.")
            OutlinedTextField(
                value = "",
                onValueChange = { query ->
                    val found = cases.indexOfFirst { it.id.contains(query, true) || it.category.name.contains(query, true) }
                    if (found >= 0) index = found
                },
                label = { Text("Find category or case") },
            )
        }

        Column(Modifier.weight(.46f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Write naturally below. Do not trace the prompt.", style = MaterialTheme.typography.titleMedium)
            ManualInkCanvas(strokes, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { strokes.clear(); raw = ""; normalized = ""; confidence = null }) { Text("Clear") }
                Button(onClick = { strokes.clear(); raw = "Reference replay is available in automated/hybrid mode." }) { Text("Replay reference") }
                Button(enabled = strokes.isNotEmpty() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val elements = manualElements(strokes)
                        val bounds = SmartBoardBounds.from(elements.flatMap { it.points.map(StrokePoint::position) }).expand(16f)
                        val request = MathRecognitionRequestBuilder.build(case.id, elements, System.currentTimeMillis())
                        val result = runCatching {
                            engine.recognize(
                                MathRecognitionInput(
                                    elements,
                                    bounds,
                                    MathRecognitionInputRenderer.render(elements, bounds),
                                    MathRecognitionRequestBuilder.fingerprint(request),
                                ),
                                MathRecognitionOptions(languageTag = "en-US", maximumAlternatives = 8),
                            ).result
                        }
                        result.onSuccess {
                            raw = it.latex
                            normalized = SmartBoardAuditScoring.normalize(it.normalizedExpression ?: it.latex)
                            confidence = it.confidence
                        }.onFailure { raw = "ERROR: ${it.message}" }
                        busy = false
                    }
                }) { Text(if (busy) "Recognizing…" else "Recognize") }
            }
        }

        Column(
            Modifier.weight(.31f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Recognition result", style = MaterialTheme.typography.titleLarge)
            Text("Expected")
            Text(case.expectedPlainText.orEmpty())
            Text("Raw detection")
            Text(raw.ifBlank { "Not recognized yet" })
            Text("Normalized detection")
            Text(normalized.ifBlank { "—" })
            Text("Confidence: ${confidence ?: "—"}")
            val comparison = remember(case.id, raw, confidence) {
                if (raw.isBlank()) null else SmartBoardAuditScoring.compare(case, raw, confidence)
            }
            comparison?.let {
                Text("Symbol: ${"%.3f".format(it.symbolScore)}")
                Text("Structure: ${"%.3f".format(it.structureScore)}")
                Text("Spatial: ${"%.3f".format(it.spatialScore)}")
                Text("Overall: ${"%.3f".format(it.overallScore)}")
                Text("Automatic status: ${it.status}")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = { testerStatus = AuditStatus.PASS }) { Text("Pass") }
                Button(onClick = { testerStatus = AuditStatus.PARTIAL }) { Text("Partial") }
                Button(onClick = { testerStatus = AuditStatus.WRONG_SYMBOL }) { Text("Fail") }
            }
            Text("Tester decision: ${testerStatus ?: "not set"}")
            OutlinedTextField(note, { note = it }, label = { Text("Add note") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { raw = "$raw\nEvidence marked for save; use Export report after completing the run." }) { Text("Save evidence") }
            Button(onClick = { raw = "$raw\nAutomated exports are produced by SmartBoardComprehensiveAuditTest." }) { Text("Export report") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ManualInkCanvas(strokes: MutableList<List<Offset>>, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Box(modifier.background(Color.White).pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { active = listOf(it) },
            onDrag = { change, _ -> active = active + change.position },
            onDragEnd = {
                if (active.size >= 2) strokes.add(active)
                active = emptyList()
            },
            onDragCancel = { active = emptyList() },
        )
    }) {
        Canvas(Modifier.fillMaxSize()) {
            (strokes + listOf(active)).filter { it.size >= 2 }.forEach { points ->
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, Color(0xFF102040), style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun manualElements(source: List<List<Offset>>): List<StrokeElement> {
    var timestamp = System.currentTimeMillis()
    return source.mapIndexed { index, points ->
        val strokePoints = points.map { point ->
            StrokePoint(point.x, point.y, .7f, timestamp.also { timestamp += 8 })
        }
        StrokeElement(
            id = "manual-$index-$timestamp",
            points = strokePoints,
            tool = StrokeTool.PEN,
            width = 4f,
            opacity = 1f,
            argbColor = 0xFF102040,
            bounds = SmartBoardBounds.from(strokePoints.map(StrokePoint::position)),
            createdAt = strokePoints.first().timestampMillis,
        )
    }
}
