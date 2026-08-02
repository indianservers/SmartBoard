package com.indianservers.smartboard.smartboard.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.SmartBoardBackground
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardInputMode
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardPreferences
import com.indianservers.smartboard.smartboard.models.SmartBoardTool
import com.indianservers.smartboard.smartboard.models.SmartBoardViewport
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.StrokePoint
import com.indianservers.smartboard.smartboard.models.StrokeTool
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import java.util.UUID
import kotlin.math.abs

data class SmartBoardStrokeStyle(
    val width: Float = 3.2f,
    val opacity: Float = 1f,
    val argbColor: Long = 0xFFF4F7FF,
)

class SmartBoardCanvasView(context: Context) : View(context) {
    var document: SmartBoardDocument = SmartBoardDocument.new("preview", 0L)
        set(value) {
            field = value
            val liveIds = value.elements.filterIsInstance<StrokeElement>().mapTo(hashSetOf(), StrokeElement::id)
            strokePathCache.keys.removeAll { it !in liveIds }
            invalidate()
        }
    var selectedIds: Set<String> = emptySet()
        set(value) {
            field = value
            invalidate()
        }
    var activeTool: SmartBoardTool = SmartBoardTool.PEN
    var preferences: SmartBoardPreferences = SmartBoardPreferences()
    var strokeStyle: SmartBoardStrokeStyle = SmartBoardStrokeStyle()
    var eraserRadius: Float = 18f
    var onStrokeCommitted: (StrokeElement) -> Unit = {}
    var onSelectionChanged: (Set<String>) -> Unit = {}
    var onErase: (SmartBoardPoint, Float) -> Unit = { _, _ -> }
    var onMoveSelection: (SmartBoardPoint) -> Unit = {}
    var onViewportChanged: (SmartBoardViewport) -> Unit = {}
    var onInteractionAnnouncement: (String) -> Unit = {}

    private val densityValue get() = resources.displayMetrics.density.coerceAtLeast(.5f)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(64, 220, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val mathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
    }
    private val presentationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
    }
    private val activePoints = ArrayList<StrokePoint>(256)
    private data class CachedStrokePath(val pointCount: Int, val lastTimestamp: Long, val path: Path, val averagePressure: Float)
    private val strokePathCache = object : LinkedHashMap<String, CachedStrokePath>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStrokePath>?) = size > 512
    }
    private val selectionPath = ArrayList<SmartBoardPoint>(128)
    private var selectionStart: SmartBoardPoint? = null
    private var lastScreen = SmartBoardPoint(0f, 0f)
    private var gestureStartDocument = SmartBoardPoint(0f, 0f)
    private var movingSelection = false
    private var panning = false
    private var drawing = false
    private var activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
    private var hoverPoint: SmartBoardPoint? = null
    private var selectionMoveDelta = SmartBoardPoint(0f, 0f)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val viewport = document.viewport
            val focusCanvas = SmartBoardCoordinates.screenToCanvas(SmartBoardPoint(detector.focusX, detector.focusY), densityValue)
            val focusDocument = SmartBoardCoordinates.canvasToDocument(focusCanvas, viewport)
            val nextZoom = (viewport.zoom * detector.scaleFactor).coerceIn(.2f, 8f)
            val next = viewport.copy(
                zoom = nextZoom,
                panX = focusCanvas.x - focusDocument.x * nextZoom,
                panY = focusCanvas.y - focusDocument.y * nextZoom,
            )
            onViewportChanged(next)
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Unified multi-subject Smart Board canvas. Use the accessible element list for structured navigation."
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        canvas.save()
        canvas.scale(densityValue, densityValue)
        canvas.translate(document.viewport.panX, document.viewport.panY)
        canvas.scale(document.viewport.zoom, document.viewport.zoom)
        document.elements.forEach { element ->
            if (!element.hidden) {
                val translatedX = if (element.id in selectedIds) selectionMoveDelta.x else 0f
                val translatedY = if (element.id in selectedIds) selectionMoveDelta.y else 0f
                canvas.save()
                canvas.translate(translatedX, translatedY)
                when (element) {
                    is StrokeElement -> drawStroke(canvas, element)
                    is ShapeElement -> drawShape(canvas, element)
                    is MathExpressionElement -> drawMath(canvas, element)
                    is TextElement -> drawCard(canvas, element.bounds, element.text)
                    is TableElement -> drawTable(canvas, element)
                    is ImageElement -> drawCard(canvas, element.bounds, "Imported image")
                    is ActionResultElement -> drawCard(canvas, element.bounds, "${element.title}: ${element.exact ?: element.approximate.orEmpty()}")
                    is GraphConfigurationElement -> drawCard(canvas, element.bounds, "Graph: ${element.expressions.joinToString()}")
                    is SolutionSequenceElement -> drawCard(canvas, element.bounds, "Solution: ${element.steps.size} steps")
                    is PhysicsExpressionElement -> drawCard(canvas, element.bounds, "Physics: ${element.displaySource}")
                    is PhysicsResultElement -> drawCard(
                        canvas,
                        element.bounds,
                        "${element.title}: ${element.numericalResult?.toString().orEmpty()} ${element.resultUnitSymbol.orEmpty()}",
                    )
                    is PhysicsDiagramElement -> drawCard(canvas, element.bounds, "${element.diagramType.name.lowercase().replace('_', ' ')} diagram")
                    is ChemistryExpressionElement -> drawCard(canvas, element.bounds, "CHEMISTRY · ${element.normalizedChemicalNotation ?: element.rawText}")
                    is EnglishTextElement -> drawCard(canvas, element.bounds, "ENGLISH · ${element.correctedText ?: element.rawText}")
                    is BiologyContentElement -> drawCard(canvas, element.bounds, "BIOLOGY · ${element.recognizedText.orEmpty()}")
                    is ChemistryResultElement -> drawCard(canvas, element.bounds, "CHEMISTRY · ${element.title}")
                    is EnglishResultElement -> drawCard(canvas, element.bounds, "ENGLISH · ${element.title}")
                    is BiologyResultElement -> drawCard(canvas, element.bounds, "BIOLOGY · ${element.title}")
                }
                val classification = document.elementSubjectClassifications[element.id] ?: element.subjectClassification
                classification?.primarySubject?.let { subject ->
                    mathPaint.textSize = 8f
                    mathPaint.color = if (preferences.highContrast) Color.YELLOW else Color.rgb(67, 217, 245)
                    canvas.drawText(
                        subject.name.take(7),
                        element.bounds.left + 4f,
                        (element.bounds.top - 3f).coerceAtLeast(9f),
                        mathPaint,
                    )
                }
                canvas.restore()
            }
        }
        drawActiveStroke(canvas)
        drawSelection(canvas)
        canvas.restore()
        drawHover(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            drawing = false
            activePoints.clear()
            return true
        }
        val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val toolType = event.getToolType(index)
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val stylusEraser = toolType == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 && activeTool == SmartBoardTool.ERASER
        val screen = SmartBoardPoint(event.getX(index), event.getY(index))
        val documentPoint = SmartBoardCoordinates.screenToDocument(screen, densityValue, document.viewport)

        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE && stylus) {
            hoverPoint = screen
            invalidate()
            return true
        }
        if (likelyPalm(event, index, stylus)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                lastScreen = screen
                gestureStartDocument = documentPoint
                selectionMoveDelta = SmartBoardPoint(0f, 0f)
                if (stylus) activeStylusPointerId = event.getPointerId(index)
                val effective = effectiveTool(stylus, stylusEraser)
                when (effective) {
                    SmartBoardTool.PAN -> panning = true
                    SmartBoardTool.ERASER -> {
                        hoverPoint = screen
                        erase(documentPoint)
                        invalidate()
                    }
                    SmartBoardTool.LASSO, SmartBoardTool.RECTANGLE_SELECT -> beginSelection(documentPoint)
                    SmartBoardTool.LASER_POINTER, SmartBoardTool.SPOTLIGHT -> {
                        hoverPoint = screen
                        invalidate()
                    }
                    else -> beginStroke(event, index, documentPoint, effective)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    activeTool in setOf(SmartBoardTool.LASER_POINTER, SmartBoardTool.SPOTLIGHT) -> {
                        hoverPoint = screen
                        invalidate()
                    }
                    panning -> {
                        val dx = (screen.x - lastScreen.x) / densityValue
                        val dy = (screen.y - lastScreen.y) / densityValue
                        onViewportChanged(document.viewport.copy(panX = document.viewport.panX + dx, panY = document.viewport.panY + dy))
                        lastScreen = screen
                    }
                    activeTool == SmartBoardTool.ERASER || stylusEraser -> {
                        hoverPoint = screen
                        event.historyIndices(index).forEach { erase(it) }
                        erase(documentPoint)
                        invalidate()
                    }
                    movingSelection -> {
                        selectionMoveDelta = documentPoint - gestureStartDocument
                        invalidate()
                    }
                    selectionStart != null -> {
                        if (activeTool == SmartBoardTool.LASSO) {
                            event.historyIndices(index).forEach(selectionPath::add)
                            selectionPath += documentPoint
                        } else {
                            if (selectionPath.size == 1) selectionPath += documentPoint else selectionPath[1] = documentPoint
                        }
                        invalidate()
                    }
                    drawing -> {
                        appendHistorical(event, index)
                        appendPoint(event, index, documentPoint)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                when {
                    activeTool in setOf(SmartBoardTool.LASER_POINTER, SmartBoardTool.SPOTLIGHT) -> {
                        hoverPoint = null
                        invalidate()
                    }
                    panning -> panning = false
                    movingSelection -> {
                        if (abs(selectionMoveDelta.x) + abs(selectionMoveDelta.y) > .5f) onMoveSelection(selectionMoveDelta)
                        selectionMoveDelta = SmartBoardPoint(0f, 0f)
                        movingSelection = false
                    }
                    selectionStart != null -> finishSelection(documentPoint)
                    drawing -> finishStroke(event.eventTime)
                }
                if (activeTool == SmartBoardTool.ERASER || stylusEraser) {
                    hoverPoint = null
                    invalidate()
                }
                if (stylus) activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun effectiveTool(stylus: Boolean, stylusEraser: Boolean): SmartBoardTool {
        if (stylusEraser) return SmartBoardTool.ERASER
        if (activeTool in setOf(SmartBoardTool.LASER_POINTER, SmartBoardTool.SPOTLIGHT)) return activeTool
        if (!stylus && preferences.inputMode == SmartBoardInputMode.FINGER_PANS) return SmartBoardTool.PAN
        return activeTool
    }

    private fun likelyPalm(event: MotionEvent, index: Int, stylus: Boolean): Boolean {
        if (stylus) return false
        if (activeStylusPointerId != MotionEvent.INVALID_POINTER_ID) return true
        if (preferences.inputMode == SmartBoardInputMode.STYLUS_ONLY) return true
        return event.getToolMajor(index) > 72f * densityValue && event.pointerCount > 1
    }

    private fun beginStroke(event: MotionEvent, index: Int, point: SmartBoardPoint, tool: SmartBoardTool) {
        if (tool !in setOf(SmartBoardTool.PEN, SmartBoardTool.PENCIL, SmartBoardTool.HIGHLIGHTER)) return
        activePoints.clear()
        drawing = true
        appendPoint(event, index, point)
    }

    private fun appendHistorical(event: MotionEvent, pointerIndex: Int) {
        for (history in 0 until event.historySize) {
            val screen = SmartBoardPoint(event.getHistoricalX(pointerIndex, history), event.getHistoricalY(pointerIndex, history))
            val point = SmartBoardCoordinates.screenToDocument(screen, densityValue, document.viewport)
            activePoints += StrokePoint(
                point.x,
                point.y,
                pressure(event.getHistoricalPressure(pointerIndex, history)),
                event.getHistoricalEventTime(history).coerceAtLeast(0L),
            )
        }
    }

    private fun appendPoint(event: MotionEvent, index: Int, point: SmartBoardPoint) {
        activePoints += StrokePoint(point.x, point.y, pressure(event.getPressure(index)), event.eventTime.coerceAtLeast(0L))
    }

    private fun pressure(raw: Float) = if (preferences.pressureSensitivity) raw.coerceIn(.08f, 1.5f) else 1f

    private fun finishStroke(now: Long) {
        drawing = false
        if (activePoints.size >= 2) {
            val tolerance = listOf(0f, .12f, .28f, .5f, .85f)[preferences.smoothingLevel]
            val points = SmartBoardStrokeGeometry.simplify(activePoints.toList(), tolerance / document.viewport.zoom)
            val tool = when (activeTool) {
                SmartBoardTool.PENCIL -> StrokeTool.PENCIL
                SmartBoardTool.HIGHLIGHTER -> StrokeTool.HIGHLIGHTER
                else -> StrokeTool.PEN
            }
            val style = when (tool) {
                StrokeTool.HIGHLIGHTER -> strokeStyle.copy(width = maxOf(strokeStyle.width, 12f), opacity = minOf(strokeStyle.opacity, .34f))
                StrokeTool.PENCIL -> strokeStyle.copy(width = minOf(strokeStyle.width, 2.2f), opacity = minOf(strokeStyle.opacity, .78f))
                else -> strokeStyle
            }
            onStrokeCommitted(
                StrokeElement(
                    id = "stroke-${UUID.randomUUID()}",
                    points = points,
                    tool = tool,
                    width = style.width,
                    opacity = style.opacity,
                    argbColor = style.argbColor,
                    bounds = SmartBoardStrokeGeometry.bounds(points, style.width),
                    createdAt = now,
                ),
            )
        }
        activePoints.clear()
        invalidate()
    }

    private fun beginSelection(point: SmartBoardPoint) {
        val selectedHit = SmartBoardSelection.tap(document.elements.filter { it.id in selectedIds }, point)
        if (selectedHit != null && selectedIds.isNotEmpty()) {
            movingSelection = true
            return
        }
        selectionStart = point
        selectionPath.clear()
        selectionPath += point
    }

    private fun finishSelection(point: SmartBoardPoint) {
        val start = selectionStart ?: return
        val movement = start.distanceTo(point)
        val selected = if (movement < 6f / document.viewport.zoom) {
            SmartBoardSelection.tap(document.elements, point)?.let(::setOf).orEmpty()
        } else if (activeTool == SmartBoardTool.RECTANGLE_SELECT) {
            SmartBoardSelection.rectangle(document.elements, normalizedBounds(start, point))
        } else {
            SmartBoardSelection.lasso(document.elements, selectionPath + point)
        }
        onSelectionChanged(SmartBoardSelection.groupedSelection(selected, document.relationships))
        selectionStart = null
        selectionPath.clear()
        invalidate()
    }

    private fun erase(point: SmartBoardPoint) {
        onErase(point, eraserRadius.coerceIn(6f, 48f) / document.viewport.zoom)
    }

    private fun cancelGesture() {
        drawing = false
        panning = false
        movingSelection = false
        selectionStart = null
        activePoints.clear()
        selectionPath.clear()
        selectionMoveDelta = SmartBoardPoint(0f, 0f)
        activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        val highContrast = preferences.highContrast
        canvas.drawColor(if (highContrast) Color.BLACK else Color.rgb(10, 16, 24))
        if (document.background == SmartBoardBackground.PLAIN) return
        val spacingDp = 28f * document.viewport.zoom
        val spacingPx = spacingDp * densityValue
        if (spacingPx < 8f) return
        backgroundPaint.color = if (highContrast) Color.argb(120, 255, 255, 255) else Color.argb(45, 100, 205, 230)
        backgroundPaint.strokeWidth = densityValue
        val startX = ((document.viewport.panX * densityValue) % spacingPx + spacingPx) % spacingPx
        val startY = ((document.viewport.panY * densityValue) % spacingPx + spacingPx) % spacingPx
        when (document.background) {
            SmartBoardBackground.GRID -> {
                var x = startX
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), backgroundPaint)
                    x += spacingPx
                }
                var y = startY
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, backgroundPaint)
                    y += spacingPx
                }
            }
            SmartBoardBackground.DOTS -> {
                var x = startX
                while (x < width) {
                    var y = startY
                    while (y < height) {
                        canvas.drawCircle(x, y, 1.2f * densityValue, backgroundPaint)
                        y += spacingPx
                    }
                    x += spacingPx
                }
            }
            SmartBoardBackground.RULED -> {
                var y = startY
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, backgroundPaint)
                    y += spacingPx
                }
            }
            SmartBoardBackground.PLAIN -> Unit
        }
    }

    private fun drawShape(canvas: Canvas, element: ShapeElement) {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = element.argbColor.toInt()
        strokePaint.alpha = (element.opacity * 255).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = element.strokeWidth
        val pairwise = element.shapeType in setOf(
            SmartBoardShapeType.COORDINATE_AXES,
            SmartBoardShapeType.ANGLE,
            SmartBoardShapeType.RIGHT_ANGLE_MARKER,
            SmartBoardShapeType.PARALLEL_LINES,
            SmartBoardShapeType.PERPENDICULAR_LINES,
            SmartBoardShapeType.NUMBER_LINE,
            SmartBoardShapeType.GRAPH_GRID,
        )
        val path = Path()
        if (pairwise) {
            element.points.chunked(2).filter { it.size == 2 }.forEach { pair ->
                path.moveTo(pair[0].x, pair[0].y)
                path.lineTo(pair[1].x, pair[1].y)
            }
        } else {
            path.moveTo(element.points.first().x, element.points.first().y)
            element.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        }
        element.fillArgbColor?.let { fill ->
            val fillPaint = Paint(strokePaint).apply {
                style = Paint.Style.FILL
                color = fill.toInt()
                alpha = (element.opacity * 100).toInt().coerceIn(0, 100)
            }
            canvas.drawPath(path, fillPaint)
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawStroke(canvas: Canvas, stroke: StrokeElement) {
        strokePaint.color = stroke.argbColor.toInt()
        strokePaint.alpha = (stroke.opacity * 255).toInt().coerceIn(0, 255)
        val cached = strokePathCache[stroke.id]?.takeIf {
            it.pointCount == stroke.points.size && it.lastTimestamp == stroke.points.last().timestampMillis
        } ?: CachedStrokePath(
            pointCount = stroke.points.size,
            lastTimestamp = stroke.points.last().timestampMillis,
            path = Path().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
            },
            averagePressure = stroke.points.map(StrokePoint::pressure).average().toFloat(),
        ).also { strokePathCache[stroke.id] = it }
        val pressure = if (preferences.pressureSensitivity) cached.averagePressure else 1f
        strokePaint.strokeWidth = stroke.width * pressure.coerceIn(.35f, 1.5f)
        canvas.drawPath(cached.path, strokePaint)
    }

    private fun drawMath(canvas: Canvas, element: MathExpressionElement) {
        val preview = SafeLatexPreview.validate(element.displayLatex).getOrElse { "Invalid notation" }
        mathPaint.textSize = (element.bounds.height.coerceIn(20f, 52f))
        mathPaint.color = if (preferences.highContrast) Color.WHITE else Color.rgb(215, 245, 255)
        canvas.drawText(preview, element.bounds.left, element.bounds.bottom, mathPaint)
    }

    private fun drawCard(canvas: Canvas, bounds: SmartBoardBounds, text: String) {
        backgroundPaint.color = if (preferences.highContrast) Color.BLACK else Color.rgb(22, 38, 52)
        backgroundPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, 8f, 8f, backgroundPaint)
        mathPaint.textSize = 16f
        mathPaint.color = Color.WHITE
        canvas.drawText(text.take(100), bounds.left + 10f, minOf(bounds.bottom - 8f, bounds.top + 25f), mathPaint)
    }

    private fun drawActiveStroke(canvas: Canvas) {
        if (activePoints.size < 2) return
        strokePaint.color = strokeStyle.argbColor.toInt()
        strokePaint.alpha = (strokeStyle.opacity * 255).toInt()
        activePoints.zipWithNext().forEach { (first, second) ->
            strokePaint.strokeWidth = strokeStyle.width * ((first.pressure + second.pressure) / 2f).coerceIn(.35f, 1.5f)
            canvas.drawLine(first.x, first.y, second.x, second.y, strokePaint)
        }
    }

    private fun drawSelection(canvas: Canvas) {
        val selected = document.elements.filter { it.id in selectedIds }
        if (selected.isNotEmpty()) {
            val bounds = SmartBoardBounds.from(
                selected.flatMap { listOf(SmartBoardPoint(it.bounds.left, it.bounds.top), SmartBoardPoint(it.bounds.right, it.bounds.bottom)) },
                6f / document.viewport.zoom,
            ).translate(selectionMoveDelta)
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, selectionPaint)
            listOf(
                SmartBoardPoint(bounds.left, bounds.top),
                SmartBoardPoint(bounds.right, bounds.top),
                SmartBoardPoint(bounds.right, bounds.bottom),
                SmartBoardPoint(bounds.left, bounds.bottom),
            ).forEach { canvas.drawCircle(it.x, it.y, 4f / document.viewport.zoom, selectionPaint) }
        }
        val start = selectionStart ?: return
        if (activeTool == SmartBoardTool.RECTANGLE_SELECT && selectionPath.size >= 2) {
            val bounds = normalizedBounds(start, selectionPath.last())
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, selectionPaint)
        } else if (selectionPath.size >= 2) {
            val path = Path()
            selectionPath.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            canvas.drawPath(path, selectionPaint)
        }
    }

    private fun drawHover(canvas: Canvas) {
        val point = hoverPoint ?: return
        when (activeTool) {
            SmartBoardTool.LASER_POINTER -> {
                presentationPaint.style = Paint.Style.FILL
                presentationPaint.color = Color.argb(45, 255, 40, 55)
                canvas.drawCircle(point.x, point.y, 22f * densityValue, presentationPaint)
                presentationPaint.color = Color.rgb(255, 42, 58)
                canvas.drawCircle(point.x, point.y, 6f * densityValue, presentationPaint)
            }
            SmartBoardTool.SPOTLIGHT -> {
                presentationPaint.style = Paint.Style.FILL
                presentationPaint.color = Color.argb(38, 255, 218, 80)
                canvas.drawCircle(point.x, point.y, 78f * densityValue, presentationPaint)
                presentationPaint.style = Paint.Style.STROKE
                presentationPaint.strokeWidth = 3f * densityValue
                presentationPaint.color = Color.argb(210, 255, 224, 112)
                canvas.drawCircle(point.x, point.y, 78f * densityValue, presentationPaint)
            }
            SmartBoardTool.ERASER -> {
                presentationPaint.style = Paint.Style.FILL
                presentationPaint.color = Color.argb(32, 255, 102, 136)
                canvas.drawCircle(point.x, point.y, eraserRadius * densityValue, presentationPaint)
                presentationPaint.style = Paint.Style.STROKE
                presentationPaint.strokeWidth = 2f * densityValue
                presentationPaint.color = Color.argb(230, 255, 102, 136)
                canvas.drawCircle(point.x, point.y, eraserRadius * densityValue, presentationPaint)
            }
            else -> {
                hoverPaint.strokeWidth = 2f * densityValue
                canvas.drawCircle(point.x, point.y, 8f * densityValue, hoverPaint)
            }
        }
    }

    private fun drawTable(canvas: Canvas, element: TableElement) {
        val bounds = element.bounds
        val columns = element.columnHeaders.size
        val visibleRows = minOf(element.rows.size + 1, 8)
        val rowHeight = bounds.height / visibleRows.coerceAtLeast(1)
        val columnWidth = bounds.width / columns
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.2f
        strokePaint.color = if (preferences.highContrast) Color.WHITE else Color.rgb(96, 130, 154)
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, strokePaint)
        for (column in 1 until columns) {
            val x = bounds.left + column * columnWidth
            canvas.drawLine(x, bounds.top, x, bounds.bottom, strokePaint)
        }
        for (row in 1 until visibleRows) {
            val y = bounds.top + row * rowHeight
            canvas.drawLine(bounds.left, y, bounds.right, y, strokePaint)
        }
        mathPaint.textSize = minOf(13f, rowHeight * .44f)
        mathPaint.color = if (preferences.highContrast) Color.WHITE else Color.rgb(232, 242, 250)
        val displayedRows = listOf(element.columnHeaders) + element.rows.take(visibleRows - 1)
        displayedRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                canvas.drawText(
                    cell.take(24),
                    bounds.left + columnIndex * columnWidth + 5f,
                    bounds.top + rowIndex * rowHeight + rowHeight * .65f,
                    mathPaint,
                )
            }
        }
        if (element.rows.size + 1 > visibleRows) {
            mathPaint.textSize = 9f
            canvas.drawText("+${element.rows.size + 1 - visibleRows} rows", bounds.left + 5f, bounds.bottom - 4f, mathPaint)
        }
    }

    private fun MotionEvent.historyIndices(pointerIndex: Int): List<SmartBoardPoint> =
        (0 until historySize).map { history ->
            SmartBoardCoordinates.screenToDocument(
                SmartBoardPoint(getHistoricalX(pointerIndex, history), getHistoricalY(pointerIndex, history)),
                densityValue,
                document.viewport,
            )
        }

    private fun normalizedBounds(first: SmartBoardPoint, second: SmartBoardPoint) = SmartBoardBounds(
        minOf(first.x, second.x),
        minOf(first.y, second.y),
        maxOf(first.x, second.x),
        maxOf(first.y, second.y),
    )
}
