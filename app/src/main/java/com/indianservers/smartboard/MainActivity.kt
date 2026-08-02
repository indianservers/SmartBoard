package com.indianservers.smartboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.indianservers.smartboard.core.Graph3D
import com.indianservers.smartboard.services.StandaloneSmartBoardGraphService
import com.indianservers.smartboard.services.StandaloneSmartBoardPhysicsFormulaService
import com.indianservers.smartboard.services.StandaloneSmartBoardStatisticsService
import com.indianservers.smartboard.services.StandaloneSmartBoardSubjectCatalogue
import com.indianservers.smartboard.smartboard.presentation.SmartBoardFeatureRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color(0xFF071018))) {
                SmartBoardStandaloneApp(onExit = ::finish)
            }
        }
    }
}

private data class ToolDestination(val route: String, val payload: String)

@Composable
private fun SmartBoardStandaloneApp(onExit: () -> Unit) {
    var destination by remember { mutableStateOf<ToolDestination?>(null) }
    val open: (String, String) -> Unit = { route, payload -> destination = ToolDestination(route, payload) }
    if (destination == null) {
        SmartBoardFeatureRoot(
            onExit = onExit,
            onOpenGraph2D = { open("graph2d", it) },
            onOpenGraph3D = { open("graph3d", it) },
            onOpenGeometry2D = { open("geometry2d", "") },
            onOpenGeometry3D = { open("geometry3d", "") },
            onOpenPhysicsWorkspace = { open(it, "") },
        )
    } else {
        BackHandler { destination = null }
        StandaloneToolScreen(destination!!, onBack = { destination = null })
    }
}

@Composable
private fun StandaloneToolScreen(destination: ToolDestination, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF071018)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBack) { Text("Back to board") }
                Text(destination.route.replace(':', ' ').replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.headlineSmall)
            }
            when (destination.route) {
                "graph2d" -> Graph2DTool(destination.payload)
                "graph3d" -> Graph3DTool(destination.payload)
                "geometry2d" -> Geometry2DTool()
                "geometry3d" -> Geometry3DTool()
                "physics:circuit", "physics:wave", "physics:optics" -> PhysicsTool(destination.route)
                else -> Text("Unsupported internal route: ${destination.route}")
            }
        }
    }
}

@Composable
private fun Graph2DTool(initialExpression: String) {
    val service = remember { StandaloneSmartBoardGraphService() }
    var expression by remember(initialExpression) { mutableStateOf(initialExpression.ifBlank { "sin(x)" }) }
    var plotted by remember { mutableStateOf(expression) }
    val result = remember(plotted) { service.sample(plotted) }
    OutlinedTextField(
        value = expression,
        onValueChange = { expression = it },
        label = { Text("Expression") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { plotted = expression }, enabled = expression.isNotBlank()) { Text("Plot") }
    result.fold(
        onSuccess = { curves ->
            Text("${curves.sumOf(List<*>::size)} sampled points")
            Canvas(
                Modifier.fillMaxWidth().height(420.dp).background(Color(0xFF101E2A)).semantics {
                    contentDescription = "Plot of $plotted with ${curves.size} curve segments"
                },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawLine(Color.Gray, Offset(0f, center.y), Offset(size.width, center.y))
                drawLine(Color.Gray, Offset(center.x, 0f), Offset(center.x, size.height))
                curves.forEach { points ->
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = center.x + (point.x / 10.0 * center.x).toFloat()
                        val y = center.y - (point.y / 10.0 * center.y).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Color(0xFF43D9F5), style = Stroke(3f))
                }
            }
        },
        onFailure = { Text(it.message ?: "The expression could not be graphed.", color = MaterialTheme.colorScheme.error) },
    )
}

@Composable
private fun Graph3DTool(initialExpression: String) {
    var expression by remember(initialExpression) { mutableStateOf(initialExpression.ifBlank { "x^2+y^2" }) }
    var submitted by remember { mutableStateOf(expression) }
    val result = remember(submitted) {
        runCatching { Graph3D().mesh(submitted, -4.0, 4.0, 36) }
    }
    OutlinedTextField(expression, { expression = it }, label = { Text("z =") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { submitted = expression }, enabled = expression.isNotBlank()) { Text("Render surface") }
    result.fold(
        onSuccess = { mesh ->
            Text("Surface generated: ${mesh.vertices.size} vertices, ${mesh.rows} × ${mesh.columns} grid")
            Canvas(
                Modifier.fillMaxWidth().height(420.dp).background(Color(0xFF101E2A)).semantics {
                    contentDescription = "Wireframe surface for z equals $submitted"
                },
            ) {
                val scale = size.minDimension / 12f
                mesh.vertices.chunked(mesh.columns).forEach { row ->
                    val path = Path()
                    row.forEachIndexed { index, point ->
                        val px = size.width / 2f + ((point.x - point.y) * .7 * scale).toFloat()
                        val py = size.height / 2f + ((point.x + point.y) * .25 * scale - point.z * .12 * scale).toFloat()
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, Color(0xFF9A7BFF), style = Stroke(1.5f))
                }
            }
        },
        onFailure = { Text(it.message ?: "The surface could not be generated.", color = MaterialTheme.colorScheme.error) },
    )
}

@Composable
private fun Geometry2DTool() {
    var width by remember { mutableFloatStateOf(5f) }
    var height by remember { mutableFloatStateOf(3f) }
    Text("Interactive rectangle: width ${"%.1f".format(width)}, height ${"%.1f".format(height)}")
    Text("Area ${"%.2f".format(width * height)} · Perimeter ${"%.2f".format(2 * (width + height))}")
    Text("Width"); Slider(width, { width = it }, valueRange = 1f..10f)
    Text("Height"); Slider(height, { height = it }, valueRange = 1f..10f)
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Rectangle geometry diagram" }) {
            val w = size.width * width / 12f
            val h = size.height * height / 12f
            drawRect(Color(0xFF43D9F5), Offset((size.width - w) / 2f, (size.height - h) / 2f), androidx.compose.ui.geometry.Size(w, h), style = Stroke(5f))
        }
    }
}

@Composable
private fun Geometry3DTool() {
    var side by remember { mutableFloatStateOf(4f) }
    Text("Interactive cube: side ${"%.1f".format(side)}")
    Text("Volume ${"%.2f".format(side * side * side)} · Surface area ${"%.2f".format(6 * side * side)}")
    Slider(side, { side = it }, valueRange = 1f..10f)
}

@Composable
private fun PhysicsTool(route: String) {
    var first by remember { mutableStateOf("10") }
    var second by remember { mutableStateOf("2") }
    val a = first.toDoubleOrNull()
    val b = second.toDoubleOrNull()
    val (labels, result) = when (route) {
        "physics:circuit" -> Pair("Voltage (V), Resistance (Ω)", if (a != null && b != null && b != 0.0) "${a / b} A" else "Enter valid non-zero resistance")
        "physics:wave" -> Pair("Frequency (Hz), Wavelength (m)", if (a != null && b != null) "${a * b} m/s" else "Enter valid values")
        else -> Pair("Object distance, Focal length", if (a != null && b != null && a != b) "${1.0 / (1.0 / b - 1.0 / a)} image distance" else "Enter valid unequal values")
    }
    Text(labels)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(first, { first = it }, label = { Text("First value") }, modifier = Modifier.weight(1f))
        OutlinedTextField(second, { second = it }, label = { Text("Second value") }, modifier = Modifier.weight(1f))
    }
    Text(result, style = MaterialTheme.typography.headlineSmall)
    Text(
        "${StandaloneSmartBoardPhysicsFormulaService.formulaCount} bundled physics formulas · " +
            "${StandaloneSmartBoardSubjectCatalogue.biologyTopicCount} biology topics · " +
            "${StandaloneSmartBoardSubjectCatalogue.chemistryElementCount} chemistry elements available offline",
    )
    val enteredValues = listOfNotNull(a, b)
    if (enteredValues.isNotEmpty()) {
        val stats = remember(enteredValues) { StandaloneSmartBoardStatisticsService.summarize(enteredValues) }
        Text("Entered-value mean: ${stats.mean}")
    }
}
