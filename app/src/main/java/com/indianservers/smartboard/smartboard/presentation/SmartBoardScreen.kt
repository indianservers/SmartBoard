package com.indianservers.smartboard.smartboard.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indianservers.smartboard.smartboard.canvas.SmartBoardCanvasView
import com.indianservers.smartboard.smartboard.canvas.SmartBoardStrokeStyle
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyResultElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.ChemistryResultElement
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.EnglishResultElement
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.integration.SmartBoardMathAction
import com.indianservers.smartboard.smartboard.export.SmartBoardExportFormat
import com.indianservers.smartboard.smartboard.models.SmartBoardBackground
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardInputMode
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionMode
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionTarget
import com.indianservers.smartboard.smartboard.models.RecognitionQualityTier
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.tools.SemanticToolOperation
import com.indianservers.smartboard.smartboard.tools.SmartBoardEditableReconstructionEngine
import com.indianservers.smartboard.smartboard.tools.SmartBoardReconstructionKind
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardClassroomSubjects
import com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardRecommendation
import com.indianservers.smartboard.smartboard.intelligence.WorkflowStepStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardTool
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import com.indianservers.smartboard.smartboard.recognition.OfflineFormulaIdentifier
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelState
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.latexStyleFormula
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorVerificationStatus
import com.indianservers.smartboard.smartboard.tutor.UnifiedTutorMode

private val BoardBackground = Color(0xFF071018)
private val BoardPanel = Color(0xF2142230)
private val BoardCyan = Color(0xFF43D9F5)
private val BoardViolet = Color(0xFF9A7BFF)
private val BoardInk = Color(0xFFF2F7FF)
private val BoardMuted = Color(0xFFA6B7C8)
private val BoardWarning = Color(0xFFFFBF5A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBoardFeatureRoot(
    onExit: () -> Unit,
    onOpenGraph2D: (String) -> Unit = {},
    onOpenGraph3D: (String) -> Unit = {},
    onOpenGeometry2D: () -> Unit = {},
    onOpenGeometry3D: () -> Unit = {},
    onOpenPhysicsWorkspace: (String) -> Unit = {},
    vm: SmartBoardViewModel = viewModel(),
) {
    var moreOpen by remember { mutableStateOf(false) }
    var recentOpen by remember { mutableStateOf(false) }
    var elementsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var intelligenceOpen by remember { mutableStateOf(false) }
    var tutorOpen by remember { mutableStateOf(false) }
    var latexEditorOpen by remember { mutableStateOf(false) }
    var graphEditorOpen by remember { mutableStateOf(false) }
    var toolboxOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf(SmartBoardExportFormat.STRUCTURED) }
    var eraserRadius by remember { mutableStateOf(18f) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importImage)
    }
    val boardImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBoard)
    }
    val boardExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { vm.export(pendingExport, it) }
    }
    DisposableEffect(Unit) {
        onDispose { vm.save() }
    }
    LaunchedEffect(vm.pendingGraphLaunch) {
        vm.pendingGraphLaunch?.let { launch ->
            if (launch.route == "graph3d") onOpenGraph3D(launch.expression) else onOpenGraph2D(launch.expression)
            vm.consumePendingGraphLaunch()
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(BoardBackground)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && event.key == Key.S -> {
                        vm.save()
                        true
                    }
                    event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z -> {
                        vm.redo()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Z -> {
                        vm.undo()
                        true
                    }
                    event.key == Key.Delete || event.key == Key.Backspace -> {
                        vm.deleteSelection()
                        true
                    }
                    event.key == Key.Escape -> {
                        vm.select(emptySet())
                        true
                    }
                    else -> false
                }
            },
    ) {
        val wide = maxWidth >= 800.dp
        val compact = maxWidth < 560.dp
        Column(Modifier.fillMaxSize()) {
            SmartBoardTopBar(
                title = vm.document.title,
                subject = vm.document.subjectMode.selection,
                onTitle = vm::rename,
                onExit = {
                    vm.save()
                    onExit()
                },
                onSave = vm::save,
                onOpen = { recentOpen = true },
                moreOpen = moreOpen,
                onMore = { moreOpen = !moreOpen },
                onDismissMore = { moreOpen = false },
                onNew = { subject ->
                    vm.newBoard(subject)
                    moreOpen = false
                },
                onRecent = {
                    recentOpen = true
                    moreOpen = false
                },
                onElements = {
                    elementsOpen = true
                    moreOpen = false
                },
                onSettings = {
                    settingsOpen = true
                    moreOpen = false
                },
                onIntelligence = {
                    intelligenceOpen = true
                    tutorOpen = false
                    moreOpen = false
                    vm.refreshIntelligence(explicit = true)
                },
                onTutor = {
                    tutorOpen = true
                    intelligenceOpen = false
                    moreOpen = false
                    vm.refreshTutorContext()
                },
                onLatex = {
                    latexEditorOpen = true
                    moreOpen = false
                },
                onGraph = {
                    graphEditorOpen = true
                    moreOpen = false
                },
                onUnderstandBoard = {
                    moreOpen = false
                    vm.analyzeWholeBoard()
                },
                onImportImage = {
                    moreOpen = false
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportBoard = {
                    moreOpen = false
                    boardImporter.launch(arrayOf("application/octet-stream", "application/json", "text/plain"))
                },
                onExport = { format ->
                    moreOpen = false
                    pendingExport = format
                    val safeTitle = vm.document.title.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .take(64).ifBlank { "smart-board" }
                    boardExporter.launch("$safeTitle.${format.extension}")
                },
            )
            Row(Modifier.weight(1f)) {
                if (wide) {
                    SmartBoardToolbar(vm, vertical = true, onToolbox = { toolboxOpen = true }, modifier = Modifier.width(78.dp).fillMaxHeight())
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        factory = {
                            SmartBoardCanvasView(context).apply {
                                onStrokeCommitted = vm::addStroke
                                onSelectionChanged = vm::select
                                onErase = vm::erase
                                onMoveSelection = vm::moveSelection
                                onViewportChanged = vm::updateViewport
                            }
                        },
                        update = { canvas ->
                            canvas.document = vm.document
                            canvas.selectedIds = vm.selectedIds
                            canvas.activeTool = vm.activeTool
                            canvas.preferences = vm.preferences
                            canvas.strokeStyle = SmartBoardStrokeStyle()
                            canvas.eraserRadius = eraserRadius
                        },
                        modifier = Modifier.fillMaxSize().semantics {
                            contentDescription = "Vector Smart Board canvas with ${vm.document.elements.size} elements and ${vm.selectedIds.size} selected"
                        },
                    )
                    if (vm.selectedIds.isNotEmpty()) {
                        SelectionActions(
                            vm,
                            onTutor = {
                                tutorOpen = true
                                intelligenceOpen = false
                                vm.refreshTutorContext()
                            },
                            onHandoff = { route, expression ->
                                when (route) {
                                    "graph2d" -> onOpenGraph2D(expression)
                                    "graph3d" -> onOpenGraph3D(expression)
                                    "geometry2d" -> onOpenGeometry2D()
                                    "geometry3d" -> onOpenGeometry3D()
                                    "physics:circuit", "physics:wave", "physics:optics" -> onOpenPhysicsWorkspace(route)
                                }
                            },
                            onEditLatex = { latexEditorOpen = true },
                            onEditGraph = { graphEditorOpen = true },
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        )
                    }
                    if (vm.activeTool == SmartBoardTool.ERASER) {
                        EraserQuickControl(
                            radius = eraserRadius,
                            canUndo = vm.canUndo,
                            onRadius = { eraserRadius = it },
                            onUndo = vm::undo,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                    if (vm.shapeSuggestion != null || vm.streamingRecognitionSuggestion != null || vm.correctionGestureSuggestion != null) {
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            vm.correctionGestureSuggestion?.let { CorrectionGestureCard(vm) }
                            vm.streamingRecognitionSuggestion?.let { StreamingRecognitionCard(vm) }
                            vm.shapeSuggestion?.let { ShapeSuggestionCard(vm) }
                        }
                    }
                    vm.wholeBoardUnderstanding?.let {
                        WholeBoardUnderstandingCard(
                            vm,
                            Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 520.dp),
                        )
                    }
                    Text(
                        vm.status,
                        color = BoardInk,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(BoardPanel, RoundedCornerShape(9.dp))
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
                if (wide && vm.recognitionReview != null) {
                    RecognitionPanel(vm, Modifier.widthIn(min = 310.dp, max = 390.dp).fillMaxHeight())
                }
                if (wide && intelligenceOpen) {
                    IntelligencePanel(
                        vm,
                        onClose = { intelligenceOpen = false },
                        onHandoff = { route, expression ->
                            when (route) {
                                "graph2d" -> onOpenGraph2D(expression)
                                "graph3d" -> onOpenGraph3D(expression)
                                "geometry2d" -> onOpenGeometry2D()
                                "geometry3d" -> onOpenGeometry3D()
                                "physics:circuit", "physics:wave", "physics:optics" -> onOpenPhysicsWorkspace(route)
                            }
                        },
                        modifier = Modifier.widthIn(min = 330.dp, max = 430.dp).fillMaxHeight(),
                    )
                }
                if (wide && tutorOpen) {
                    SmartBoardTutorPanel(
                        vm = vm,
                        onClose = { tutorOpen = false },
                        modifier = Modifier.widthIn(min = 340.dp, max = 460.dp).fillMaxHeight(),
                    )
                }
            }
            if (!wide) {
                SmartBoardToolbar(vm, vertical = false, onToolbox = { toolboxOpen = true }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (!wide && vm.recognitionReview != null) {
            ModalBottomSheet(onDismissRequest = vm::cancelRecognition, containerColor = BoardPanel) {
                RecognitionPanel(vm, Modifier.fillMaxWidth().heightIn(max = if (compact) 620.dp else 720.dp))
            }
        }
        if (!wide && intelligenceOpen) {
            ModalBottomSheet(onDismissRequest = { intelligenceOpen = false }, containerColor = BoardPanel) {
                IntelligencePanel(
                    vm,
                    onClose = { intelligenceOpen = false },
                    onHandoff = { route, expression ->
                        when (route) {
                            "graph2d" -> onOpenGraph2D(expression)
                            "graph3d" -> onOpenGraph3D(expression)
                            "geometry2d" -> onOpenGeometry2D()
                            "geometry3d" -> onOpenGeometry3D()
                            "physics:circuit", "physics:wave", "physics:optics" -> onOpenPhysicsWorkspace(route)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
                )
            }
        }
        if (!wide && tutorOpen) {
            ModalBottomSheet(onDismissRequest = { tutorOpen = false }, containerColor = BoardPanel) {
                SmartBoardTutorPanel(
                    vm = vm,
                    onClose = { tutorOpen = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp),
                )
            }
        }
        if (recentOpen) {
            OverlayPanel("Open Smart Board", onDismiss = { recentOpen = false }) {
                if (vm.recentBoards.isEmpty()) Text("No saved boards yet.", color = BoardMuted)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.recentBoards, key = { it.id }) { board ->
                        Row(
                            Modifier.fillMaxWidth().background(Color.White.copy(.04f), RoundedCornerShape(10.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).clickable {
                                vm.openBoard(board.id)
                                recentOpen = false
                            }) {
                                Text(board.title, color = BoardInk, fontWeight = FontWeight.Bold)
                                Text("${board.elements.size} elements · ${board.subject.displayName()}", color = BoardMuted, fontSize = 11.sp)
                            }
                            BoardButton("Open & edit") {
                                vm.openBoard(board.id)
                                recentOpen = false
                            }
                            BoardButton("Delete", warning = true) { vm.deleteBoard(board.id) }
                        }
                    }
                }
            }
        }
        if (elementsOpen) {
            OverlayPanel("Accessible Element List", onDismiss = { elementsOpen = false }) {
                StructuredElementList(vm)
            }
        }
        if (toolboxOpen) {
            OverlayPanel("Classroom Toolbox", onDismiss = { toolboxOpen = false }) {
                Text("Presentation tools", color = BoardInk, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    BoardButton("Laser pointer") {
                        vm.setTool(SmartBoardTool.LASER_POINTER)
                        toolboxOpen = false
                    }
                    BoardButton("Spotlight") {
                        vm.setTool(SmartBoardTool.SPOTLIGHT)
                        toolboxOpen = false
                    }
                }
                Text("Quick construction tools", color = BoardInk, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        SmartBoardShapeType.CIRCLE to "Circle",
                        SmartBoardShapeType.TRIANGLE to "Triangle",
                        SmartBoardShapeType.RECTANGLE to "Rectangle",
                        SmartBoardShapeType.LINE_SEGMENT to "Line",
                        SmartBoardShapeType.ARROW to "Arrow",
                        SmartBoardShapeType.RIGHT_ANGLE_MARKER to "Right angle",
                        SmartBoardShapeType.COORDINATE_AXES to "Axes",
                        SmartBoardShapeType.NUMBER_LINE to "Number line",
                    ).forEach { (shape, label) ->
                        BoardButton(label) { vm.insertQuickShape(shape) }
                    }
                    BoardButton("Blank table", onClick = vm::insertBlankTable)
                }
                var noteText by remember(vm.selectedText?.id) {
                    mutableStateOf(vm.selectedText?.text ?: "")
                }
                Text(if (vm.selectedText == null) "Sticky note" else "Edit selected note", color = BoardInk, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(8_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("Note text") },
                )
                BoardButton(if (vm.selectedText == null) "Insert note" else "Update note", enabled = noteText.isNotBlank()) {
                    if (vm.selectedText == null) vm.insertStickyNote(noteText) else vm.updateSelectedText(noteText)
                }
                vm.selectedTable?.let { table ->
                    Text("Edit selected table", color = BoardInk, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        table.columnHeaders.take(4).forEachIndexed { columnIndex, header ->
                            OutlinedTextField(
                                value = header,
                                onValueChange = { vm.updateSelectedTableHeader(columnIndex, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Header ${columnIndex + 1}") },
                            )
                        }
                    }
                    Text("${table.columnHeaders.size} columns · ${table.rows.size} rows. Showing the first 6 rows.", color = BoardMuted, fontSize = 10.sp)
                    table.rows.take(6).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.take(4).forEachIndexed { columnIndex, cell ->
                                OutlinedTextField(
                                    value = cell,
                                    onValueChange = { vm.updateSelectedTableCell(rowIndex, columnIndex, it) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(table.columnHeaders[columnIndex].take(18)) },
                                )
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        BoardButton("+ Row", onClick = vm::addSelectedTableRow)
                        BoardButton("+ Column", onClick = vm::addSelectedTableColumn)
                        BoardButton("- Last row", enabled = table.rows.isNotEmpty(), onClick = vm::removeSelectedTableLastRow)
                        BoardButton("- Last column", enabled = table.columnHeaders.size > 1, onClick = vm::removeSelectedTableLastColumn)
                    }
                }
                Text("Class timer", color = BoardInk, fontWeight = FontWeight.Bold)
                val minutes = vm.classroomTimerRemainingSeconds / 60
                val seconds = vm.classroomTimerRemainingSeconds % 60
                Text("%02d:%02d".format(minutes, seconds), color = if (vm.classroomTimerRemainingSeconds == 0) BoardWarning else BoardCyan, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    BoardButton(if (vm.classroomTimerRunning) "Pause" else "Start", onClick = vm::startOrPauseClassroomTimer)
                    BoardButton("5 min") { vm.resetClassroomTimer(300) }
                    BoardButton("10 min") { vm.resetClassroomTimer(600) }
                    BoardButton("20 min") { vm.resetClassroomTimer(1_200) }
                }
                vm.selectedExpression?.let { expression ->
                    val suggestions = SmartBoardEditableReconstructionEngine.suggestions(expression)
                    if (suggestions.isNotEmpty()) {
                        Text("Editable reconstruction", color = BoardInk, fontWeight = FontWeight.Bold)
                        suggestions.forEach { suggestion ->
                            Text("${suggestion.title} · ${(suggestion.confidence * 100).toInt()}%", color = BoardViolet)
                            Text(suggestion.explanation, color = BoardMuted, fontSize = 10.sp)
                            when (suggestion.kind) {
                                SmartBoardReconstructionKind.TABLE -> BoardButton("Create editable table", onClick = vm::reconstructSelectedTable)
                                SmartBoardReconstructionKind.GRAPH_2D -> BoardButton("Open in editable 2D graph") {
                                    onOpenGraph2D(expression.normalizedExpression ?: expression.displayLatex)
                                }
                                SmartBoardReconstructionKind.GRAPH_3D -> BoardButton("Open in editable 3D graph") {
                                    onOpenGraph3D(expression.normalizedExpression ?: expression.displayLatex)
                                }
                                SmartBoardReconstructionKind.GEOMETRY_2D -> BoardButton("Open in editable geometry", onClick = onOpenGeometry2D)
                            }
                        }
                    }
                }
            }
        }
        if (settingsOpen) {
            OverlayPanel("Board Settings", onDismiss = { settingsOpen = false }) {
                Text("Touch and stylus input", color = BoardInk, fontWeight = FontWeight.Bold)
                SmartBoardInputMode.entries.forEach { mode ->
                    BoardButton(
                        if (vm.preferences.inputMode == mode) "✓ ${mode.label()}" else mode.label(),
                        onClick = { vm.setInputMode(mode) },
                    )
                }
                SettingSwitch("Pressure-sensitive width", vm.preferences.pressureSensitivity) {
                    vm.updatePreferences(vm.preferences.copy(pressureSensitivity = it))
                }
                SettingSwitch("High-contrast board", vm.preferences.highContrast) {
                    vm.updatePreferences(vm.preferences.copy(highContrast = it))
                }
                SettingSwitch("Reduced motion", vm.preferences.reducedMotion) {
                    vm.updatePreferences(vm.preferences.copy(reducedMotion = it))
                }
                Text("Classroom subject", color = BoardInk, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartBoardClassroomSubjects.selectable.forEach { subject ->
                        BoardButton(
                            if (vm.document.subjectMode.selection == subject) "✓ ${subject.displayName()}" else subject.displayName(),
                            onClick = { vm.setBoardSubject(subject) },
                        )
                    }
                }
                SettingSwitch("Lock subject for this board", vm.document.subjectMode.locked, vm::setSubjectLock)
                Text(
                    "Auto Detect routes each selection locally. A subject lock gives formulas, diagrams and terminology the chosen classroom context.",
                    color = BoardMuted,
                )
                Text("Recognition result", color = BoardInk, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BoardButton(
                        if (vm.recognitionTarget == SmartBoardRecognitionTarget.CONTENT) "✓ Subject content" else "Subject content",
                    ) { vm.updateRecognitionTarget(SmartBoardRecognitionTarget.CONTENT) }
                    BoardButton(
                        if (vm.recognitionTarget == SmartBoardRecognitionTarget.GRAPH_2D) "✓ Graph mode" else "Graph mode",
                        enabled = vm.document.subjectMode.selection in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.MATHEMATICS),
                    ) { vm.updateRecognitionTarget(SmartBoardRecognitionTarget.GRAPH_2D) }
                }
                Text(
                    "Graph mode recognizes mathematical handwriting, keeps its source ink, creates an editable graph object and opens the graph immediately.",
                    color = BoardCyan,
                    fontSize = 10.sp,
                )
                Text("Recognition mode", color = BoardInk, fontWeight = FontWeight.Bold)
                SmartBoardRecognitionMode.entries.forEach { mode ->
                    BoardButton(if (vm.preferences.recognitionMode == mode) "✓ ${mode.name.lowercase().replace('_', ' ')}" else mode.name.lowercase().replace('_', ' ')) {
                        vm.updatePreferences(vm.preferences.copy(recognitionMode = mode))
                    }
                }
                if (vm.preferences.recognitionMode == SmartBoardRecognitionMode.AUTOMATIC) {
                    Text(
                        "Live classroom mode fuses digital ink, a rendered image pass and subject evidence. Suggestions never replace ink automatically.",
                        color = BoardCyan,
                        fontSize = 10.sp,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(BoardCyan.copy(alpha = .06f), RoundedCornerShape(14.dp))
                        .border(1.dp, BoardCyan.copy(alpha = .32f), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Offline Image-to-LaTeX", color = BoardCyan, fontWeight = FontWeight.Bold)
                    Text(
                        when (vm.offlineMathModelStatus.state) {
                            OfflineMathModelState.READY -> "Dedicated mathematical vision is installed. Images and rendered ink are recognized fully offline."
                            OfflineMathModelState.INSTALLING -> vm.offlineMathModelStatus.message
                            OfflineMathModelState.INVALID -> "The model pack is incomplete. Resume installation to verify and repair it."
                            OfflineMathModelState.NOT_INSTALLED -> "Install the 244 MB quantized mathematical model once. Generic text OCR remains available until then."
                        },
                        color = if (vm.offlineMathModelStatus.state == OfflineMathModelState.INVALID) BoardWarning else BoardMuted,
                        fontSize = 10.sp,
                    )
                    if (vm.offlineMathModelStatus.state == OfflineMathModelState.INSTALLING) {
                        LinearProgressIndicator(
                            progress = { vm.offlineMathModelStatus.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = BoardCyan,
                        )
                        Text(
                            "${(vm.offlineMathModelStatus.progress * 100).toInt()}%",
                            color = BoardInk,
                            fontSize = 10.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        when (vm.offlineMathModelStatus.state) {
                            OfflineMathModelState.READY ->
                                BoardButton("Remove model", warning = true, onClick = vm::removeOfflineMathRecognitionModel)
                            OfflineMathModelState.INSTALLING ->
                                BoardButton("Installing…", enabled = false, onClick = {})
                            OfflineMathModelState.INVALID ->
                                BoardButton("Resume and repair", onClick = vm::installOfflineMathRecognitionModel)
                            OfflineMathModelState.NOT_INSTALLED ->
                                BoardButton("Install offline model", onClick = vm::installOfflineMathRecognitionModel)
                        }
                    }
                    Text("TexTeller ONNX · Apache-2.0 · private app storage", color = BoardMuted, fontSize = 9.sp)
                }
                Text("Recognition quality", color = BoardInk, fontWeight = FontWeight.Bold)
                RecognitionQualityTier.entries.forEach { tier ->
                    BoardButton(
                        if (vm.preferences.recognitionQualityTier == tier) "✓ ${tier.name.lowercase()}"
                        else tier.name.lowercase(),
                    ) { vm.updatePreferences(vm.preferences.copy(recognitionQualityTier = tier)) }
                }
                SettingSwitch(
                    "Learn from my confirmed corrections on this device",
                    vm.preferences.recognitionPersonalizationEnabled,
                ) {
                    vm.updatePreferences(vm.preferences.copy(recognitionPersonalizationEnabled = it))
                }
                Text(
                    "${vm.recognitionPersonalizationProfile.totalConfirmedCorrections} confirmed corrections stored locally. Ink and images are never stored in this profile.",
                    color = BoardMuted,
                    fontSize = 10.sp,
                )
                if (vm.recognitionPersonalizationProfile.totalConfirmedCorrections > 0) {
                    BoardButton("Clear recognition learning", warning = true, onClick = vm::clearRecognitionPersonalization)
                }
                SettingSwitch(
                    "Keep content-free recognition diagnostics",
                    vm.preferences.recognitionDiagnosticsEnabled,
                ) {
                    vm.updatePreferences(vm.preferences.copy(recognitionDiagnosticsEnabled = it))
                }
                val recognitionHealth = vm.recognitionRuntimeHealth
                Text(
                    "Runtime health: ${recognitionHealth.sampleCount} local samples · ${(recognitionHealth.slowRate * 100).toInt()}% slow · ${(recognitionHealth.correctionRate * 100).toInt()}% corrected",
                    color = if (recognitionHealth.rollbackRecommended) BoardWarning else BoardCyan,
                    fontSize = 10.sp,
                )
                if (recognitionHealth.sampleCount > 0) {
                    BoardButton("Clear recognition diagnostics", warning = true, onClick = vm::clearRecognitionDiagnostics)
                }
                SettingSwitch("Auto-shape suggestions", vm.preferences.autoShapeEnabled, vm::setAutoShapeEnabled)
                if (vm.preferences.autoShapeEnabled) {
                    Text("Shape pause ${vm.preferences.autoShapeDelayMillis} ms", color = BoardMuted)
                    Slider(
                        value = vm.preferences.autoShapeDelayMillis.toFloat(),
                        onValueChange = {
                            vm.updatePreferences(vm.preferences.copy(autoShapeDelayMillis = it.toInt().coerceIn(300, 3_000)))
                        },
                        valueRange = 300f..3_000f,
                    )
                    Text("Suggestions never replace ink until you accept them.", color = BoardWarning, fontSize = 10.sp)
                }
                Text("Intelligence mode", color = BoardInk, fontWeight = FontWeight.Bold)
                SmartBoardIntelligenceMode.entries.forEach { mode ->
                    BoardButton(
                        if (vm.preferences.intelligenceMode == mode) "✓ ${mode.name.lowercase().replace('_', ' ')}"
                        else mode.name.lowercase().replace('_', ' '),
                    ) { vm.setIntelligenceMode(mode) }
                }
                SettingSwitch("Smart suggestions", vm.preferences.intelligenceSuggestionsEnabled) {
                    vm.updatePreferences(vm.preferences.copy(intelligenceSuggestionsEnabled = it))
                }
                Text("Smoothing level ${vm.preferences.smoothingLevel}", color = BoardMuted)
                Slider(
                    value = vm.preferences.smoothingLevel.toFloat(),
                    onValueChange = { vm.updatePreferences(vm.preferences.copy(smoothingLevel = it.toInt().coerceIn(0, 4))) },
                    valueRange = 0f..4f,
                    steps = 3,
                )
                Text("Palm rejection depends on device hardware. Stylus contacts are prioritised and likely palms are ignored where Android reports contact size.", color = BoardWarning, fontSize = 11.sp)
            }
        }
        if (latexEditorOpen) {
            LatexEditorPanel(
                vm = vm,
                onDismiss = { latexEditorOpen = false },
                onOpenGraph = {
                    latexEditorOpen = false
                    graphEditorOpen = true
                },
            )
        }
        if (graphEditorOpen) {
            GraphEditorPanel(
                vm = vm,
                onDismiss = { graphEditorOpen = false },
                onOpenGraph = { route, expression ->
                    graphEditorOpen = false
                    if (route == "graph3d") onOpenGraph3D(expression) else onOpenGraph2D(expression)
                },
            )
        }
    }
}

@Composable
private fun SmartBoardTopBar(
    title: String,
    subject: SmartBoardSubject,
    onTitle: (String) -> Unit,
    onExit: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    moreOpen: Boolean,
    onMore: () -> Unit,
    onDismissMore: () -> Unit,
    onNew: (SmartBoardSubject) -> Unit,
    onRecent: () -> Unit,
    onElements: () -> Unit,
    onSettings: () -> Unit,
    onIntelligence: () -> Unit,
    onTutor: () -> Unit,
    onLatex: () -> Unit,
    onGraph: () -> Unit,
    onUnderstandBoard: () -> Unit,
    onImportImage: () -> Unit,
    onImportBoard: () -> Unit,
    onExport: (SmartBoardExportFormat) -> Unit,
) {
    val moreMenu: @Composable () -> Unit = {
        Box {
            BoardButton("More", onClick = onMore)
            DropdownMenu(expanded = moreOpen, onDismissRequest = onDismissMore) {
                SmartBoardClassroomSubjects.selectable.forEach { subject ->
                    DropdownMenuItem(
                        text = { Text("New ${subject.displayName()} Board") },
                        onClick = { onNew(subject) },
                    )
                }
                DropdownMenuItem(text = { Text("Open saved board") }, onClick = onRecent)
                DropdownMenuItem(text = { Text("Accessible Elements") }, onClick = onElements)
                DropdownMenuItem(text = { Text("Input & Display Settings") }, onClick = onSettings)
                DropdownMenuItem(text = { Text("Intelligence Panel") }, onClick = onIntelligence)
                DropdownMenuItem(text = { Text("Smart Board Tutor") }, onClick = onTutor)
                DropdownMenuItem(text = { Text("Insert LaTeX / identify formula") }, onClick = onLatex)
                DropdownMenuItem(text = { Text("Graph Editor") }, onClick = onGraph)
                DropdownMenuItem(text = { Text("Understand whole board") }, onClick = onUnderstandBoard)
                DropdownMenuItem(text = { Text("Import board document") }, onClick = onImportBoard)
                DropdownMenuItem(text = { Text("Import image") }, onClick = onImportImage)
                SmartBoardExportFormat.entries.forEach { format ->
                    DropdownMenuItem(text = { Text("Export ${format.name.lowercase()}") }, onClick = { onExport(format) })
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(BoardPanel)) {
        if (maxWidth < 600.dp) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardButton("Back", onClick = onExit)
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitle,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Board title") },
                    )
                    moreMenu()
                }
                Text(
                    "Smart Board · ${subject.displayName()} · integrated workspace",
                    color = BoardCyan,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    BoardButton("Save", onClick = onSave)
                    BoardButton("Open", onClick = onOpen)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoardButton("Back", onClick = onExit)
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitle,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Board title") },
                    )
                    Text("Smart Board · ${subject.displayName()} · integrated workspace", color = BoardCyan, fontSize = 10.sp)
                }
                BoardButton("Save", onClick = onSave)
                BoardButton("Open", onClick = onOpen)
                moreMenu()
            }
        }
    }
}

@Composable
private fun SmartBoardToolbar(
    vm: SmartBoardViewModel,
    vertical: Boolean,
    onToolbox: () -> Unit,
    modifier: Modifier,
) {
    val tools = listOf(
        Triple(SmartBoardTool.PEN, "Pen", "✎"),
        Triple(SmartBoardTool.PENCIL, "Pencil", "✐"),
        Triple(SmartBoardTool.HIGHLIGHTER, "Highlight", "▰"),
        Triple(SmartBoardTool.ERASER, "Eraser", "⌫"),
        Triple(SmartBoardTool.LASSO, "Lasso", "◯"),
        Triple(SmartBoardTool.RECTANGLE_SELECT, "Box", "□"),
        Triple(SmartBoardTool.PAN, "Pan", "✥"),
    )
    val content: @Composable () -> Unit = {
        tools.forEach { (tool, label, icon) ->
            ToolButton(label, icon = icon, selected = vm.activeTool == tool) { vm.setTool(tool) }
        }
        ToolButton(
            when (vm.activeTool) {
                SmartBoardTool.LASER_POINTER -> "Laser ✓"
                SmartBoardTool.SPOTLIGHT -> "Spot ✓"
                else -> "Toolbox"
            },
            onClick = onToolbox,
        )
        ToolButton("Undo", enabled = vm.canUndo) { vm.undo() }
        ToolButton("Redo", enabled = vm.canRedo) { vm.redo() }
        ToolButton("Clear", enabled = vm.document.elements.isNotEmpty(), warning = true) { vm.clearBoard() }
        ToolButton(if (vm.recognizing) "Reading…" else "Recognize", enabled = !vm.recognizing) { vm.recognizeSelection() }
        ToolButton(
            "Graph AI",
            selected = vm.recognitionTarget == SmartBoardRecognitionTarget.GRAPH_2D,
            enabled = vm.document.subjectMode.selection in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.MATHEMATICS),
        ) {
            vm.updateRecognitionTarget(
                if (vm.recognitionTarget == SmartBoardRecognitionTarget.GRAPH_2D) {
                    SmartBoardRecognitionTarget.CONTENT
                } else {
                    SmartBoardRecognitionTarget.GRAPH_2D
                },
            )
        }
        ToolButton("Reset", onClick = vm::resetZoom)
        ToolButton("Grid") {
            val entries = SmartBoardBackground.entries
            vm.changeBackground(entries[(entries.indexOf(vm.document.background) + 1) % entries.size])
        }
    }
    if (vertical) {
        Column(
            modifier.background(BoardPanel).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) { content() }
    } else {
        Row(
            modifier.background(BoardPanel).horizontalScroll(rememberScrollState()).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) { content() }
    }
}

@Composable
private fun EraserQuickControl(
    radius: Float,
    canUndo: Boolean,
    onRadius: (Float) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 310.dp)
            .background(BoardPanel, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFFF6688).copy(alpha = .75f), RoundedCornerShape(14.dp))
            .padding(8.dp)
            .semantics { contentDescription = "Eraser active. Radius ${radius.toInt()} pixels" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("⌫ Eraser active", color = Color(0xFFFF8AA3), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${radius.toInt()} px", color = BoardInk, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(10f to "Small", 18f to "Medium", 32f to "Large").forEach { (value, label) ->
                BoardButton(if (radius == value) "✓ $label" else label) { onRadius(value) }
            }
            BoardButton("Undo", enabled = canUndo, onClick = onUndo)
        }
        Text("Drag across ink to erase complete strokes. Undo restores the last erased stroke.", color = BoardMuted, fontSize = 9.sp)
    }
}

@Composable
private fun SelectionActions(
    vm: SmartBoardViewModel,
    onTutor: () -> Unit,
    onHandoff: (String, String) -> Unit,
    onEditLatex: () -> Unit,
    onEditGraph: () -> Unit,
    modifier: Modifier,
) {
    FlowRow(
        modifier.background(BoardPanel, RoundedCornerShape(12.dp)).border(1.dp, BoardCyan.copy(.45f), RoundedCornerShape(12.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (vm.selectedSubjectComposition.isNotEmpty()) {
            Text(
                vm.selectedSubjectComposition.entries.joinToString(" + ") { "${it.key.displayName()} ${it.value}" },
                color = BoardCyan,
                fontSize = 10.sp,
                modifier = Modifier.semantics {
                    contentDescription = "Selected subject composition. " +
                        vm.selectedSubjectComposition.entries.joinToString { "${it.value} ${it.key.displayName()} elements" }
                },
            )
        }
        BoardButton("Delete", warning = true, onClick = vm::deleteSelection)
        BoardButton("Duplicate", onClick = vm::duplicateSelection)
        BoardButton("Group", enabled = vm.selectedIds.size >= 2, onClick = vm::groupSelection)
        BoardButton("Ungroup", onClick = vm::ungroupSelection)
        BoardButton("Forward", onClick = vm::bringForward)
        BoardButton("Backward", onClick = vm::sendBackward)
        BoardButton("Recognize", onClick = { vm.recognizeSelection() })
        BoardButton("Recognize shape", onClick = vm::recognizeShapeSelection)
        vm.selectedShape?.let { shape ->
            BoardButton("Rotate 15°", enabled = !shape.locked, onClick = vm::rotateSelectedShape)
            BoardButton(if (shape.locked) "Unlock shape" else "Lock shape", onClick = vm::toggleSelectedShapeLock)
            BoardButton("Shape style", enabled = !shape.locked, onClick = vm::cycleSelectedShapeStyle)
            if (shape.shapeType in setOf(
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.COORDINATE_AXES,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.GRAPH_GRID,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.NUMBER_LINE,
                )
            ) {
                BoardButton("Open Graph 2D") { onHandoff("graph2d", "") }
            }
            if (shape.shapeType in setOf(
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.TRIANGLE,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.RIGHT_TRIANGLE,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.EQUILATERAL_TRIANGLE,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.CIRCLE,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.RECTANGLE,
                    com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.POLYGON,
                )
            ) {
                BoardButton("Open Geometry 2D") { onHandoff("geometry2d", "") }
            }
        }
        BoardButton("Tutor", onClick = onTutor)
        vm.selectedExpressionAnalysis?.actions?.forEach { action ->
            BoardButton(action.label(), enabled = !vm.runningAction) { vm.runMathAction(action, onHandoff) }
        }
        if (vm.selectedExpression != null) {
            BoardButton("Edit LaTeX", onClick = onEditLatex)
            BoardButton("Hint") { vm.requestTutorHint() }
            BoardButton("Next step") { vm.requestTutorHint(nextStepOnly = true) }
            vm.semanticToolTargets.take(8).forEach { target ->
                BoardButton(
                    if (vm.selectedSemanticNodeId == target.nodeId) "✓ ${target.expression.take(18)}" else target.expression.take(18),
                ) { vm.selectSemanticNode(target.nodeId) }
            }
            SemanticToolOperation.entries.forEach { operation ->
                BoardButton(
                    "${operation.name.lowercase().replaceFirstChar(Char::uppercase)} part",
                    enabled = vm.selectedSemanticNodeId != null,
                ) { vm.applySemanticTool(operation) }
            }
            if (vm.selectedExpression?.semanticTree?.root?.kind ==
                com.indianservers.smartboard.smartboard.models.SemanticMathNodeKind.MATRIX
            ) {
                BoardButton("Matrix → table", onClick = vm::reconstructSelectedTable)
            }
        }
        vm.selectedGraph?.let { graph ->
            BoardButton("Edit graph", onClick = onEditGraph)
            BoardButton("Open graph") { onHandoff(graph.moduleRoute, graph.expressions.first()) }
        }
    }
}

@Composable
private fun StreamingRecognitionCard(vm: SmartBoardViewModel, modifier: Modifier = Modifier) {
    val suggestion = vm.streamingRecognitionSuggestion ?: return
    Surface(
        modifier.widthIn(max = 620.dp),
        color = BoardPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BoardCyan.copy(.55f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Live multimodal recognition", color = BoardInk, fontWeight = FontWeight.Bold)
                Text("${suggestion.snapshot.latencyMillis} ms", color = BoardMuted, fontSize = 10.sp)
            }
            Text(
                "Stroke + image + parser evidence · stability ${(suggestion.snapshot.stability * 100).toInt()}%",
                color = BoardMuted,
                fontSize = 10.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestion.snapshot.candidates.take(4).forEachIndexed { index, candidate ->
                    BoardButton("${candidate.text} · ${(candidate.confidence * 100).toInt()}%") {
                        vm.chooseStreamingCandidate(index)
                    }
                }
                BoardButton("Keep writing", onClick = vm::dismissStreamingRecognition)
            }
            Text("No candidate replaces ink until you open it for review and confirm.", color = BoardWarning, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CorrectionGestureCard(vm: SmartBoardViewModel, modifier: Modifier = Modifier) {
    val suggestion = vm.correctionGestureSuggestion ?: return
    Surface(
        modifier.widthIn(max = 520.dp),
        color = BoardPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BoardWarning.copy(.65f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${suggestion.type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)} detected",
                color = BoardInk,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${suggestion.targetStrokeIds.size} stroke(s) · ${(suggestion.confidence * 100).toInt()}% confidence",
                color = BoardMuted,
                fontSize = 10.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoardButton("Erase strokes", warning = true, onClick = vm::acceptCorrectionGesture)
                BoardButton("Keep as ink", onClick = vm::keepCorrectionGesture)
            }
        }
    }
}

@Composable
private fun LatexEditorPanel(
    vm: SmartBoardViewModel,
    onDismiss: () -> Unit,
    onOpenGraph: () -> Unit,
) {
    val existing = vm.selectedExpression
    var source by remember(existing?.id) { mutableStateOf(existing?.displayLatex.orEmpty()) }
    val validation = SafeLatexPreview.validate(source)
    val preparation = SmartBoardLatexAdapter.prepare(source).getOrNull()
    val analysis = preparation?.analysis
    val formula = validation.getOrNull()?.let(OfflineFormulaIdentifier::identify)
    OverlayPanel(if (existing == null) "Insert LaTeX" else "Edit LaTeX", onDismiss) {
        Text(
            "Offline editor · safe preview · formula identification",
            color = BoardCyan,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("LaTeX or mathematical expression") },
            minLines = 3,
            supportingText = {
                Text(
                    validation.exceptionOrNull()?.message
                        ?: "${analysis?.type?.name?.lowercase()?.replace('_', ' ') ?: "expression"} · " +
                            if (analysis?.parserVerified == true) "engine-readable" else "preview-only until confirmed",
                )
            },
        )
        Surface(
            Modifier.fillMaxWidth(),
            color = Color.White.copy(.05f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                preparation?.engineExpression?.let(::latexStyleFormula).orEmpty().ifBlank { "Live formula preview" },
                modifier = Modifier.padding(14.dp),
                color = BoardInk,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
            )
        }
        preparation?.warnings?.forEach { warning ->
            Text(warning, color = BoardWarning, fontSize = 10.sp)
        }
        if (formula != null) {
            Column(
                Modifier.fillMaxWidth()
                    .background(BoardViolet.copy(.14f), RoundedCornerShape(12.dp))
                    .border(1.dp, BoardViolet.copy(.55f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Identified offline: ${formula.title}", color = BoardInk, fontWeight = FontWeight.Bold)
                Text(formula.canonicalForm, color = BoardCyan, fontFamily = FontFamily.Serif)
                Text(
                    "${formula.subject.displayName()} · ${(formula.confidence * 100).toInt()}% · variables ${formula.variables.joinToString()}",
                    color = BoardMuted,
                    fontSize = 11.sp,
                )
                Text(formula.explanation, color = BoardMuted, fontSize = 11.sp)
            }
        } else if (source.isNotBlank() && validation.isSuccess) {
            Text("No named formula match. The expression remains editable and can still use supported CAS/graph actions.", color = BoardMuted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardButton(if (existing == null) "Insert editable object" else "Update object", enabled = validation.isSuccess) {
                if (vm.insertOrUpdateLatex(source, existing)) onDismiss()
            }
            BoardButton("Continue in Graph Editor", enabled = analysis?.actions?.contains(SmartBoardMathAction.PLOT_2D) == true) {
                if (vm.insertOrUpdateLatex(source, existing)) onOpenGraph()
            }
        }
        Text("All identification above is on-device. Nothing is uploaded.", color = BoardWarning, fontSize = 10.sp)
    }
}

@Composable
private fun GraphEditorPanel(
    vm: SmartBoardViewModel,
    onDismiss: () -> Unit,
    onOpenGraph: (String, String) -> Unit,
) {
    val existing = vm.selectedGraph
    var source by remember(existing?.id) {
        mutableStateOf(
            existing?.expressions?.firstOrNull()
                ?: vm.selectedExpression?.normalizedExpression
                ?: vm.selectedExpression?.displayLatex.orEmpty(),
        )
    }
    var threeDimensional by remember(existing?.id) {
        mutableStateOf(existing?.moduleRoute == "graph3d")
    }
    val analysis = SmartBoardExpressionAnalyzer.analyze(source)
    OverlayPanel(if (existing == null) "Graph Editor" else "Edit Graph", onDismiss) {
        Text("Reuse the full Graph engine while the configuration stays editable on this board.", color = BoardCyan)
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Expression") },
            minLines = 2,
            supportingText = {
                Text("${analysis.type.name.lowercase().replace('_', ' ')} · ${if (analysis.parserVerified) "validated" else "check syntax"}")
            },
        )
        SettingSwitch("3D graph", threeDimensional) { threeDimensional = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardButton(if (existing == null) "Insert graph object" else "Update graph object", enabled = source.isNotBlank()) {
                if (vm.insertOrUpdateGraph(source, threeDimensional, existing)) onDismiss()
            }
            BoardButton("Open full Graph Editor", enabled = source.isNotBlank()) {
                if (vm.insertOrUpdateGraph(source, threeDimensional, existing)) {
                    val graph = vm.selectedGraph
                    if (graph != null) onOpenGraph(graph.moduleRoute, graph.expressions.first())
                }
            }
        }
        Text("The Smart Board stores the graph configuration; rendering, handles and direct manipulation remain owned by the existing Graph engine.", color = BoardMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ShapeSuggestionCard(vm: SmartBoardViewModel, modifier: Modifier = Modifier) {
    val suggestion = vm.shapeSuggestion ?: return
    val selected = suggestion.selected
    Surface(
        modifier = modifier
            .widthIn(max = 620.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Shape suggestion ${selected.type.shapeLabel()}, ${(selected.confidence * 100).toInt()} percent confidence. Original ink remains unchanged."
            },
        color = BoardPanel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BoardCyan.copy(.65f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Clean shape suggestion", color = BoardInk, fontWeight = FontWeight.Bold)
            Text(
                "${selected.type.shapeLabel()} · ${(selected.confidence * 100).toInt()}% · ${selected.rationale}",
                color = BoardCyan,
                fontSize = 11.sp,
            )
            if (suggestion.candidates.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    suggestion.candidates.forEachIndexed { index, candidate ->
                        BoardButton(
                            if (index == suggestion.selectedIndex) "✓ ${candidate.type.shapeLabel()}" else candidate.type.shapeLabel(),
                        ) { vm.chooseShapeCandidate(index) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoardButton("Accept clean shape", onClick = vm::acceptShapeSuggestion)
                BoardButton("Keep original", onClick = vm::dismissShapeSuggestion)
                BoardButton("Dismiss", warning = true, onClick = vm::dismissShapeSuggestion)
            }
        }
    }
}

@Composable
private fun RecognitionPanel(vm: SmartBoardViewModel, modifier: Modifier) {
    val review = vm.recognitionReview ?: return
    Column(
        modifier.background(BoardPanel).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Recognition Review", color = BoardInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("On-device subject-aware recognition · handwriting is never replaced automatically", color = BoardWarning, fontSize = 10.sp)
        Surface(
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Recognized content preview. ${SafeLatexPreview.accessibleSummary(review.editableLatex)}"
            },
            color = Color.White.copy(.06f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                SafeLatexPreview.validate(review.editableLatex).getOrElse { "Invalid notation" },
                color = BoardInk,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.padding(16.dp),
            )
        }
        OutlinedTextField(
            review.editableLatex,
            vm::editRecognitionLatex,
            Modifier.fillMaxWidth(),
            label = { Text("Editable recognized content") },
        )
        Text("Plain text: ${review.result.plainText.orEmpty()}", color = BoardMuted)
        review.semanticTree?.let { tree ->
            Text(
                "Semantic structure: ${tree.root.kind.name.lowercase().replace('_', ' ')} · ${if (tree.parserVerified) "parser verified" else "editable fallback"}",
                color = BoardCyan,
                fontWeight = FontWeight.Bold,
            )
            Text("Spoken: ${tree.spokenForm}", color = BoardMuted, fontSize = 10.sp)
        }
        if (review.specialistInterpretations.isNotEmpty()) {
            Text("Specialist interpretations", color = BoardInk, fontWeight = FontWeight.Bold)
            review.specialistInterpretations.take(3).forEach { interpretation ->
                Text(
                    "${interpretation.specialist.name.lowercase().replace('_', ' ')} · ${(interpretation.confidence * 100).toInt()}% · ${interpretation.objectIntent}",
                    color = BoardViolet,
                    fontSize = 11.sp,
                )
                Text(interpretation.supportedActions.take(4).joinToString(" · "), color = BoardMuted, fontSize = 9.sp)
            }
        }
        if (review.contextEvidence.isNotEmpty()) {
            Text("Why this interpretation ranked first", color = BoardInk, fontWeight = FontWeight.Bold)
            review.contextEvidence.forEach { evidence ->
                Text(
                    "${if (evidence.scoreDelta >= 0f) "+" else ""}${"%.3f".format(evidence.scoreDelta)} · ${evidence.explanation}",
                    color = if (evidence.scoreDelta >= 0f) BoardCyan else BoardWarning,
                    fontSize = 10.sp,
                )
            }
            Text("Context only reorders candidates returned by on-device recognizers.", color = BoardMuted, fontSize = 9.sp)
        }
        review.subjectDetection?.let { detection ->
            val subject = review.selectedSubject ?: detection.primarySubject
            Text(
                "Recognition subject: ${subject?.displayName() ?: "Needs confirmation"} · ${detection.confidenceLevel.name.lowercase()} confidence",
                color = BoardCyan,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Recognition subject ${subject?.displayName() ?: "needs confirmation"}, ${detection.confidenceLevel.name.lowercase()} confidence."
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmartBoardClassroomSubjects.academic.forEach { candidate ->
                    BoardButton(
                        if (review.selectedSubject == candidate) "✓ ${candidate.displayName()}" else candidate.displayName(),
                    ) { vm.chooseRecognitionSubject(candidate) }
                }
            }
        }
        if (review.result.alternatives.isNotEmpty()) {
            Text("Alternatives", color = BoardInk, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                review.result.alternatives.forEach { alternative ->
                    BoardButton(alternative.latex) { vm.chooseAlternative(alternative.latex) }
                }
            }
        }
        review.result.warnings.forEach { Text("• $it", color = BoardWarning, fontSize = 11.sp) }
        review.validationMessage?.let { Text(it, color = Color(0xFFFF718A)) }
        SettingSwitch("Hide source handwriting after insertion", review.hideSourceHandwriting, vm::setHideSourceHandwriting)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardButton(
                if (vm.recognitionTarget == SmartBoardRecognitionTarget.GRAPH_2D) "Insert & show graph" else "Insert recognized content",
                onClick = vm::confirmRecognition,
            )
            BoardButton("Retry", onClick = { vm.recognizeSelection(force = true) })
            BoardButton("Cancel", warning = true, onClick = vm::cancelRecognition)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WholeBoardUnderstandingCard(vm: SmartBoardViewModel, modifier: Modifier = Modifier) {
    val understanding = vm.wholeBoardUnderstanding ?: return
    Surface(
        modifier.semantics {
            contentDescription = "Whole board understanding review. ${understanding.summary}. ${understanding.relationshipSuggestions.size} suggestions."
        },
        color = BoardPanel,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Whole-board understanding", color = BoardInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(understanding.summary, color = BoardCyan)
            understanding.relationshipSuggestions.take(6).forEach { suggestion ->
                Text(
                    "${suggestion.relationship.type.name.lowercase().replace('_', ' ')} · ${(suggestion.confidence * 100).toInt()}%",
                    color = BoardViolet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Text(suggestion.explanation, color = BoardMuted, fontSize = 10.sp)
            }
            if (understanding.relationshipSuggestions.size > 6) {
                Text("+${understanding.relationshipSuggestions.size - 6} more suggestions", color = BoardMuted, fontSize = 10.sp)
            }
            understanding.warnings.forEach { Text(it, color = BoardWarning, fontSize = 10.sp) }
            Text("No relationship is added until you confirm. The result is undoable.", color = BoardWarning, fontSize = 10.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardButton("Add relationships", enabled = understanding.relationshipSuggestions.isNotEmpty(), onClick = vm::acceptWholeBoardRelationships)
                BoardButton("Dismiss", warning = true, onClick = vm::dismissWholeBoardUnderstanding)
            }
        }
    }
}

@Composable
private fun SmartBoardTutorPanel(
    vm: SmartBoardViewModel,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var message by remember(vm.document.id) { mutableStateOf("") }
    val context = vm.tutorContext
    val subject = context?.primarySubject
    Column(
        modifier
            .background(BoardPanel)
            .semantics {
                contentDescription = "Smart Board Tutor. Active subject ${subject?.displayName() ?: "not confirmed"}."
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Smart Board Tutor", color = BoardInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${subject?.displayName() ?: "Confirm subject"} · " +
                        if (context?.serviceAvailability?.aiAvailable == true) "full intelligence" else "deterministic tutor",
                    color = BoardCyan,
                    fontSize = 11.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (context?.supportingSubjects?.isNotEmpty() == true) {
                    Text(
                        "Supporting: ${context.supportingSubjects.joinToString { it.displayName() }}",
                        color = BoardViolet,
                        fontSize = 10.sp,
                    )
                }
            }
            BoardButton("Close", onClick = onClose)
        }
        Surface(
            color = Color.White.copy(.05f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(9.dp)) {
                Text("Selected context", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    context?.selectedElements?.joinToString(" · ") { it.kind.removeSuffix("Element") }
                        ?.ifBlank { "Select Board content" } ?: "Select Board content",
                    color = BoardMuted,
                    fontSize = 10.sp,
                )
                Text(
                    "${context?.selectedElements?.size ?: 0} element(s) · selection-scoped",
                    color = BoardWarning,
                    fontSize = 9.sp,
                )
            }
        }
        Text("Tutor mode", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                UnifiedTutorMode.ASK,
                UnifiedTutorMode.HINT,
                UnifiedTutorMode.NEXT_STEP,
                UnifiedTutorMode.CHECK_MY_WORK,
                UnifiedTutorMode.FIND_MY_MISTAKE,
                UnifiedTutorMode.EXPLAIN_CONCEPT,
            ).forEach { mode ->
                BoardButton(
                    if (vm.tutorConversation.activeMode == mode) "✓ ${mode.tutorLabel()}" else mode.tutorLabel(),
                ) { vm.setUnifiedTutorMode(mode) }
            }
        }
        if (vm.tutorSuggestedPrompts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                vm.tutorSuggestedPrompts.take(5).forEach { prompt ->
                    BoardButton(prompt.label) {
                        vm.setUnifiedTutorMode(prompt.mode)
                        vm.sendTutorMessage(prompt.label)
                    }
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 100.dp, max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(vm.tutorConversation.messages, key = { it.id }) { item ->
                val tutorMessage = item.role == "tutor"
                Surface(
                    color = if (tutorMessage) BoardViolet.copy(.13f) else BoardCyan.copy(.10f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = if (tutorMessage) {
                            "Tutor response. ${item.verificationStatus?.spokenStatus().orEmpty()}. ${item.text}"
                        } else "Your tutor question. ${item.text}"
                    },
                ) {
                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (tutorMessage) "Tutor" else "You", color = if (tutorMessage) BoardViolet else BoardCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(item.text, color = BoardInk, fontSize = 12.sp)
                        item.verificationStatus?.let {
                            Text(it.spokenStatus(), color = it.statusColor(), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(2_000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask about selected work") },
            placeholder = { Text("Give me one hint") },
            enabled = !vm.tutorBusy,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoardButton(if (vm.tutorBusy) "Working…" else "Send", enabled = !vm.tutorBusy && vm.selectedIds.isNotEmpty()) {
                vm.sendTutorMessage(message)
                message = ""
            }
            BoardButton("Stop", enabled = vm.tutorBusy, warning = true, onClick = vm::stopTutor)
            BoardButton("Retry", enabled = !vm.tutorBusy && vm.tutorConversation.messages.isNotEmpty()) {
                vm.sendTutorMessage(vm.tutorConversation.messages.lastOrNull { it.role == "user" }?.text.orEmpty())
            }
            BoardButton("Insert into Board", enabled = vm.tutorLastResponse != null, onClick = vm::insertLastTutorResponse)
            BoardButton("Clear thread", warning = true, onClick = vm::clearTutorConversation)
        }
        vm.tutorLastResponse?.warnings?.take(2)?.forEach { warning ->
            Text("• $warning", color = BoardWarning, fontSize = 10.sp)
        }
    }
}

@Composable
private fun IntelligencePanel(
    vm: SmartBoardViewModel,
    onClose: () -> Unit,
    onHandoff: (String, String) -> Unit,
    modifier: Modifier,
) {
    var command by remember(vm.document.id) { mutableStateOf("") }
    var showMore by remember(vm.document.id) { mutableStateOf(false) }
    Column(
        modifier
            .background(BoardPanel)
            .semantics {
                contentDescription = "Smart Board intelligence panel. Active subject ${vm.document.subject.displayName()}."
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Board Intelligence", color = BoardInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${vm.preferences.intelligenceMode.name.lowercase().replace('_', ' ')} · deterministic local mode",
                    color = BoardCyan,
                    fontSize = 10.sp,
                )
            }
            BoardButton("Close", onClick = onClose)
        }
        OutlinedTextField(
            command,
            { command = it.take(500) },
            Modifier.fillMaxWidth(),
            label = { Text("Ask about the selection") },
            placeholder = { Text("Solve this and graph it") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoardButton(if (vm.intelligenceBusy) "Analyzing…" else "Understand", enabled = !vm.intelligenceBusy) {
                vm.refreshIntelligence(command.takeIf(String::isNotBlank), explicit = true)
            }
            BoardButton("Plan workflow", enabled = command.isNotBlank() && !vm.intelligenceBusy) {
                vm.planIntelligenceWorkflow(command)
            }
            listOf("Solve this", "Check my answer", "Give one hint", "Show this visually").forEach { quick ->
                BoardButton(quick) { command = quick; vm.refreshIntelligence(quick, explicit = true) }
            }
            BoardButton("Snooze 30 min") { vm.snoozeIntelligenceSuggestions() }
            BoardButton(
                if (vm.boardIntelligenceSuggestionsEnabled()) "Disable for Board" else "Enable for Board",
                warning = vm.boardIntelligenceSuggestionsEnabled(),
            ) {
                vm.setBoardIntelligenceSuggestionsEnabled(!vm.boardIntelligenceSuggestionsEnabled())
            }
        }
        vm.intelligenceUnderstanding?.let { understanding ->
            Surface(color = Color.White.copy(.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What I understand", color = BoardInk, fontWeight = FontWeight.Bold)
                    Text(understanding.subjectUnderstanding.summary, color = BoardMuted)
                    Text(
                        "Goal: ${understanding.problemState.goal?.type?.name?.lowercase()?.replace('_', ' ') ?: "not confirmed"} · " +
                            understanding.subjectUnderstanding.confidence.overallDisplayLevel.name.lowercase().replace('_', ' '),
                        color = BoardViolet,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    understanding.clarification?.let { Text("Clarification: $it", color = BoardWarning) }
                    Text(
                        "Context: ${understanding.context.metrics.includedElementCount}/${understanding.context.metrics.candidateElementCount} relevant elements, " +
                            "${understanding.context.metrics.includedCharacters} characters",
                        color = BoardMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        if (vm.intelligenceContext?.pendingAmbiguities?.isNotEmpty() == true) {
            Text("Needs confirmation", color = BoardWarning, fontWeight = FontWeight.Bold)
            vm.intelligenceContext?.pendingAmbiguities?.take(3)?.forEach { ambiguity ->
                var resolution by remember(ambiguity.id) { mutableStateOf("") }
                Text(ambiguity.prompt, color = BoardMuted)
                OutlinedTextField(resolution, { resolution = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Meaning for this problem") })
                BoardButton("Remember for this problem", enabled = resolution.isNotBlank()) {
                    vm.resolveIntelligenceAmbiguity(ambiguity.id, resolution)
                }
            }
        }
        vm.activeIntelligenceWorkflow?.let { workflow ->
            Surface(color = BoardViolet.copy(.10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(workflow.title, color = BoardInk, fontWeight = FontWeight.Bold)
                    Text("Review each step. Nothing runs without approval.", color = BoardWarning, fontSize = 10.sp)
                    workflow.steps.sortedBy { it.order }.forEach { step ->
                        Text(
                            "${step.order + 1}. ${step.title} · ${step.status.name.lowercase()}",
                            color = if (step.status == WorkflowStepStatus.COMPLETED) Color(0xFF79E2A8) else BoardMuted,
                            modifier = Modifier.semantics {
                                contentDescription = "Workflow step ${step.order + 1}, ${step.title}, ${step.status.name.lowercase()}"
                            },
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BoardButton("Approve next step", enabled = !vm.intelligenceBusy) { vm.executeNextWorkflowStep(onHandoff) }
                        BoardButton("Cancel workflow", warning = true) { vm.cancelIntelligenceWorkflow() }
                    }
                }
            }
        }
        if (vm.intelligenceRecommendations.isNotEmpty()) {
            Text("Recommended next actions", color = BoardInk, fontWeight = FontWeight.Bold)
            val visible = if (showMore) vm.intelligenceRecommendations else vm.intelligenceRecommendations.take(5)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visible, key = SmartBoardRecommendation::id) { recommendation ->
                    RecommendationCard(recommendation, vm, onHandoff)
                }
            }
            if (vm.intelligenceRecommendations.size > 5) {
                BoardButton(if (showMore) "Fewer suggestions" else "More suggestions") { showMore = !showMore }
            }
        } else if (!vm.intelligenceBusy) {
            Text("Select a recognized expression, result, dataset or diagram, then choose Understand.", color = BoardMuted)
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: SmartBoardRecommendation,
    vm: SmartBoardViewModel,
    onHandoff: (String, String) -> Unit,
) {
    Surface(
        color = Color.White.copy(.05f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${recommendation.title}. ${recommendation.reason}. " +
                (recommendation.disabledReason ?: "Available with confirmation.")
        },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(recommendation.title, color = BoardInk, fontWeight = FontWeight.Bold)
            Text(recommendation.reason, color = BoardMuted, fontSize = 11.sp)
            Text(
                "${recommendation.category.name.lowercase().replace('_', ' ')} · " +
                    if (recommendation.confidence >= .8f) "high confidence" else "review recommended",
                color = BoardCyan,
                fontSize = 10.sp,
            )
            recommendation.disabledReason?.let { Text(it, color = BoardWarning, fontSize = 10.sp) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoardButton("Approve", enabled = recommendation.disabledReason == null && !vm.intelligenceBusy) {
                    vm.executeRecommendation(recommendation, onHandoff)
                }
                BoardButton("Dismiss") { vm.dismissRecommendation(recommendation.id) }
            }
        }
    }
}

@Composable
private fun StructuredElementList(vm: SmartBoardViewModel) {
    if (vm.document.elements.isEmpty()) {
        Text("The board has no elements.", color = BoardMuted)
        return
    }
    val outlineElements = vm.document.elements.sortedWith(
        compareBy<SmartBoardElement>({ it.bounds.top }, { it.bounds.left }, { it.createdAt }, { it.id }),
    )
    Text(
        "Board Outline · logical top-to-bottom reading order",
        color = BoardCyan,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics {
            contentDescription = "Board Outline. ${outlineElements.size} elements in logical top to bottom reading order."
        },
    )
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(outlineElements, key = SmartBoardElement::id) { element ->
            val selected = element.id in vm.selectedIds
            val elementSubject = vm.document.elementSubjectClassifications[element.id]?.primarySubject
                ?: element.subjectClassification?.primarySubject
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) BoardCyan.copy(.14f) else Color.White.copy(.04f), RoundedCornerShape(10.dp))
                    .border(1.dp, if (selected) BoardCyan else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { vm.select(if (selected) vm.selectedIds - element.id else vm.selectedIds + element.id) }
                    .semantics {
                        this.selected = selected
                        contentDescription = "${elementSubject?.displayName() ?: "General"} content. ${element.accessibleDescription()}"
                    }
                    .padding(10.dp),
            ) {
                Text(elementSubject?.displayName() ?: "General", color = BoardViolet, fontSize = 10.sp)
                Text(element.accessibleDescription(), color = BoardInk, fontWeight = FontWeight.Bold)
                Text("Bounds ${element.bounds.left.toInt()}, ${element.bounds.top.toInt()} to ${element.bounds.right.toInt()}, ${element.bounds.bottom.toInt()}", color = BoardMuted, fontSize = 10.sp)
                if (element is MathExpressionElement) {
                    var edit by remember(element.id, element.displayLatex) { mutableStateOf(element.displayLatex) }
                    OutlinedTextField(edit, { edit = it }, Modifier.fillMaxWidth(), label = { Text("Edit notation") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BoardButton("Apply notation") { vm.editExpression(element, edit) }
                        BoardButton("Show source") { vm.setSourceHandwritingVisibility(element, hidden = false) }
                        BoardButton("Hide source") { vm.setSourceHandwritingVisibility(element, hidden = true) }
                    }
                }
                if (element is PhysicsDiagramElement) {
                    Text("Detected as ${element.diagramType.name.lowercase().replace('_', ' ')}. Inferred relationships require confirmation.", color = BoardCyan)
                    element.inferredRelations.forEach { inference ->
                        Text("${(inference.confidence * 100).toInt()}% · ${inference.description}", color = BoardWarning, fontSize = 11.sp)
                    }
                }
                if (element is ImageElement) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BoardButton("Rotate left") { vm.select(setOf(element.id)); vm.rotateSelectedImage(clockwise = false) }
                        BoardButton("Rotate right") { vm.select(setOf(element.id)); vm.rotateSelectedImage(clockwise = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayPanel(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.52f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(min = 300.dp, max = 560.dp)
                .fillMaxWidth(.9f)
                .fillMaxHeight(.92f)
                .background(BoardPanel, RoundedCornerShape(18.dp))
                .border(1.dp, BoardCyan.copy(.45f), RoundedCornerShape(18.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = BoardInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                BoardButton("Close", onClick = onDismiss)
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$label, ${if (value) "on" else "off"}" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = BoardInk)
        Switch(value, onChange)
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    warning: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick,
        Modifier.size(width = 74.dp, height = 48.dp).semantics {
            this.selected = selected
            contentDescription = "$label tool${if (selected) ", selected" else ""}"
        },
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                selected -> BoardCyan
                warning -> Color(0xFF6B2A3B)
                else -> Color(0xFF223244)
            },
            contentColor = if (selected) Color(0xFF041017) else BoardInk,
        ),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            icon?.let { Text(it, fontSize = 13.sp, maxLines = 1) }
            Text(label, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BoardButton(label: String, enabled: Boolean = true, warning: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = label },
        colors = ButtonDefaults.buttonColors(containerColor = if (warning) Color(0xFF6B2A3B) else Color(0xFF24364A), contentColor = BoardInk),
    ) { Text(label, fontSize = 11.sp) }
}

private fun SmartBoardInputMode.label() = when (this) {
    SmartBoardInputMode.DRAW_WITH_FINGER -> "Draw with finger"
    SmartBoardInputMode.STYLUS_ONLY -> "Stylus-only drawing"
    SmartBoardInputMode.FINGER_PANS -> "Finger pans canvas"
}

private fun com.indianservers.smartboard.smartboard.models.SmartBoardShapeType.shapeLabel() =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private fun SmartBoardElement.accessibleDescription() = when (this) {
    is StrokeElement -> "${tool.name.lowercase()} stroke with ${points.size} vector points${if (hidden) ", hidden" else ""}"
    is ShapeElement -> "Recognized ${shapeType.name.lowercase().replace('_', ' ')} with ${(recognitionConfidence * 100).toInt()} percent confidence${if (hidden) ", hidden" else ""}"
    is MathExpressionElement -> "Mathematical expression ${SafeLatexPreview.accessibleSummary(displayLatex)}${if (hidden) ", hidden" else ""}"
    is TextElement -> "Text: $text${if (hidden) ", hidden" else ""}"
    is TableElement -> "Editable table with ${columnHeaders.size} columns and ${rows.size} data rows${if (hidden) ", hidden" else ""}"
    is ImageElement -> "Imported image, $pixelWidth by $pixelHeight pixels${if (hidden) ", hidden" else ""}"
    is ActionResultElement -> "${if (verified) "Verified" else "Unverified"} ${kind.name.lowercase()} result: $title"
    is GraphConfigurationElement -> "${graphKind.name.lowercase()} graph configuration for ${expressions.joinToString()}"
    is SolutionSequenceElement -> "Solution sequence with ${steps.size} steps${firstInvalidStepIndex?.let { ", first invalid step ${it + 1}" }.orEmpty()}"
    is PhysicsExpressionElement -> "Physics expression $displaySource, ${contentType.name.lowercase().replace('_', ' ')}"
    is PhysicsResultElement -> "${status.name.lowercase().replace('_', ' ')} Physics result: $title"
    is PhysicsDiagramElement -> "${diagramType.name.lowercase().replace('_', ' ')} Physics diagram with ${detectedObjects.size} detected objects"
    is ChemistryExpressionElement -> "Chemistry ${expressionType.name.lowercase().replace('_', ' ')}: ${normalizedChemicalNotation ?: rawText}"
    is EnglishTextElement -> "English ${textType.name.lowercase().replace('_', ' ')}: ${correctedText ?: rawText}"
    is BiologyContentElement -> "Biology ${contentType.name.lowercase().replace('_', ' ')}: ${recognizedText.orEmpty()}"
    is ChemistryResultElement -> "${status.name.lowercase().replace('_', ' ')} Chemistry result: $title"
    is EnglishResultElement -> "${status.name.lowercase().replace('_', ' ')} English result: $title"
    is BiologyResultElement -> "${status.name.lowercase().replace('_', ' ')} Biology result: $title"
}

private fun SmartBoardMathAction.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
private fun com.indianservers.smartboard.smartboard.models.PhysicsActionType.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun SmartBoardSubject.displayName() = if (this == SmartBoardSubject.AUTO) "Auto Detect" else name.lowercase().replaceFirstChar(Char::titlecase)
private fun UnifiedTutorMode.tutorLabel() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun SmartBoardTutorVerificationStatus.spokenStatus() = when (this) {
    SmartBoardTutorVerificationStatus.VERIFIED -> "Verified by deterministic engine"
    SmartBoardTutorVerificationStatus.VERIFIED_WITH_CONDITIONS -> "Verified with conditions"
    SmartBoardTutorVerificationStatus.NUMERICALLY_VERIFIED -> "Numerically verified"
    SmartBoardTutorVerificationStatus.RULE_VERIFIED -> "Verified by local rule"
    SmartBoardTutorVerificationStatus.MODEL_REFERENCE_VERIFIED -> "Matched to reviewed model reference"
    SmartBoardTutorVerificationStatus.PARTIALLY_VERIFIED -> "Partially verified"
    SmartBoardTutorVerificationStatus.AI_ONLY -> "AI explanation, not independently verified"
    SmartBoardTutorVerificationStatus.INCONCLUSIVE -> "Verification inconclusive"
    SmartBoardTutorVerificationStatus.UNSUPPORTED -> "Verification unsupported"
    SmartBoardTutorVerificationStatus.FAILED -> "Verification failed"
}
private fun SmartBoardTutorVerificationStatus.statusColor() = when (this) {
    SmartBoardTutorVerificationStatus.VERIFIED,
    SmartBoardTutorVerificationStatus.NUMERICALLY_VERIFIED,
    SmartBoardTutorVerificationStatus.RULE_VERIFIED,
    SmartBoardTutorVerificationStatus.MODEL_REFERENCE_VERIFIED -> Color(0xFF73E6A5)
    SmartBoardTutorVerificationStatus.FAILED -> Color(0xFFFF718A)
    else -> BoardWarning
}
