package com.indianservers.smartboard.smartboard.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
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
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SolutionSequenceElement
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SmartBoardExportFormat(val extension: String) { PNG("png"), PDF("pdf"), LATEX("tex"), STRUCTURED("smartboard") }

class SmartBoardExporter(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "shared-maths").apply { mkdirs() }

    suspend fun export(document: SmartBoardDocument, format: SmartBoardExportFormat): File = withContext(Dispatchers.IO) {
        val safeName = document.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "smart-board" }
        val file = File(directory, "$safeName-${document.updatedAt}.${format.extension}")
        when (format) {
            SmartBoardExportFormat.STRUCTURED -> file.writeText(SmartBoardDocumentCodec.encode(document))
            SmartBoardExportFormat.LATEX -> file.writeText(latex(document))
            SmartBoardExportFormat.PNG -> renderPng(document, file)
            SmartBoardExportFormat.PDF -> renderPdf(document, file)
        }
        file
    }

    private fun renderPng(document: SmartBoardDocument, target: File) {
        val bounds = documentBounds(document)
        val scale = minOf(3f, 4096f / maxOf(bounds.width, bounds.height).coerceAtLeast(1f))
        val bitmap = Bitmap.createBitmap((bounds.width * scale).toInt().coerceIn(320, 4096), (bounds.height * scale).toInt().coerceIn(240, 4096), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-bounds.left, -bounds.top)
            render(document, canvas)
            FileOutputStream(target).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderPdf(document: SmartBoardDocument, target: File) {
        val bounds = documentBounds(document)
        val width = bounds.width.toInt().coerceIn(320, 2400)
        val height = bounds.height.toInt().coerceIn(240, 3200)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            page.canvas.translate(-bounds.left, -bounds.top)
            render(document, page.canvas)
            pdf.finishPage(page)
            FileOutputStream(target).use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }

    private fun render(document: SmartBoardDocument, canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        }
        document.elements.filterNot { it.hidden }.forEach { element ->
            when (element) {
                is StrokeElement -> {
                    paint.style = Paint.Style.STROKE
                    paint.color = element.argbColor.toInt()
                    paint.alpha = (element.opacity * 255).toInt()
                    paint.strokeWidth = element.width
                    element.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
                }
                is ShapeElement -> {
                    paint.style = Paint.Style.STROKE
                    paint.color = element.argbColor.toInt()
                    paint.alpha = (element.opacity * 255).toInt()
                    paint.strokeWidth = element.strokeWidth
                    element.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
                }
                else -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(245, 248, 252)
                    canvas.drawRoundRect(element.bounds.left, element.bounds.top, element.bounds.right, element.bounds.bottom, 8f, 8f, paint)
                    paint.color = Color.BLACK
                    paint.textSize = 16f
                    canvas.drawText(element.exportSummary().take(140), element.bounds.left + 8f, minOf(element.bounds.bottom - 8f, element.bounds.top + 24f), paint)
                }
            }
        }
    }

    private fun documentBounds(document: SmartBoardDocument): com.indianservers.smartboard.smartboard.models.SmartBoardBounds {
        if (document.elements.isEmpty()) return com.indianservers.smartboard.smartboard.models.SmartBoardBounds(0f, 0f, 1280f, 720f)
        val left = document.elements.minOf { it.bounds.left } - 32f
        val top = document.elements.minOf { it.bounds.top } - 32f
        val right = document.elements.maxOf { it.bounds.right } + 32f
        val bottom = document.elements.maxOf { it.bounds.bottom } + 32f
        return com.indianservers.smartboard.smartboard.models.SmartBoardBounds(left, top, maxOf(right, left + 320f), maxOf(bottom, top + 240f))
    }

    private fun latex(document: SmartBoardDocument) = buildString {
        appendLine("\\documentclass{article}")
        appendLine("\\usepackage{amsmath}")
        appendLine("\\begin{document}")
        appendLine("\\section*{${escape(document.title)}}")
        document.elements.filterNot { it.hidden }.forEach { element ->
            when (element) {
                is MathExpressionElement -> appendLine("\\[${element.displayLatex}\\]")
                is ActionResultElement -> appendLine("\\textbf{${escape(element.title)}}: \\(${escape(element.exact ?: element.approximate.orEmpty())}\\)\\\\")
                is TextElement -> appendLine(escape(element.text) + "\\\\")
                is TableElement -> {
                    appendLine("\\begin{tabular}{${"l".repeat(element.columnHeaders.size)}}")
                    appendLine(element.columnHeaders.joinToString(" & ", transform = ::escape) + "\\\\")
                    element.rows.take(10_000).forEach { row -> appendLine(row.joinToString(" & ", transform = ::escape) + "\\\\") }
                    appendLine("\\end{tabular}")
                }
                is SolutionSequenceElement -> element.steps.forEach { appendLine("\\[${it.expression}\\]") }
                is PhysicsExpressionElement -> appendLine("\\[${element.displaySource}\\]")
                is PhysicsResultElement -> {
                    appendLine("\\textbf{${escape(element.title)}}\\\\")
                    element.steps.forEach { appendLine("\\[${it.expression}\\]") }
                }
                is ChemistryExpressionElement -> appendLine(escape(element.normalizedChemicalNotation ?: element.rawText) + "\\\\")
                is EnglishTextElement -> appendLine(escape(element.correctedText ?: element.rawText) + "\\\\")
                is BiologyContentElement -> appendLine(escape(element.recognizedText.orEmpty()) + "\\\\")
                is ChemistryResultElement -> appendLine(escape("${element.title}: ${element.balancedEquation ?: element.numericalResult?.toString().orEmpty()}") + "\\\\")
                is EnglishResultElement -> appendLine(escape("${element.title}: ${element.suggestedText ?: element.originalText}") + "\\\\")
                is BiologyResultElement -> appendLine(escape("${element.title}: ${element.explanation.orEmpty()}") + "\\\\")
                is ShapeElement -> appendLine(escape("Recognized ${element.shapeType.name.lowercase().replace('_', ' ')}") + "\\\\")
                else -> Unit
            }
        }
        appendLine("\\end{document}")
    }

    private fun escape(value: String) = value.replace("\\", "\\textbackslash{}").replace("_", "\\_").replace("%", "\\%")

    private fun com.indianservers.smartboard.smartboard.models.SmartBoardElement.exportSummary() = when (this) {
        is MathExpressionElement -> displayLatex
        is ActionResultElement -> "$title: ${exact ?: approximate.orEmpty()}"
        is GraphConfigurationElement -> "Graph: ${expressions.joinToString()}"
        is ImageElement -> "Imported image ($pixelWidth × $pixelHeight)"
        is SolutionSequenceElement -> "Solution sequence: ${steps.size} steps"
        is TextElement -> text
        is TableElement -> "Table: ${columnHeaders.size} columns × ${rows.size} rows"
        is StrokeElement -> "Handwriting stroke"
        is ShapeElement -> "Recognized ${shapeType.name.lowercase().replace('_', ' ')}"
        is PhysicsExpressionElement -> "Physics: $displaySource"
        is PhysicsResultElement -> "$title: ${numericalResult?.toString().orEmpty()} ${resultUnitSymbol.orEmpty()}"
        is PhysicsDiagramElement -> "${diagramType.name.lowercase().replace('_', ' ')} diagram"
        is ChemistryExpressionElement -> "Chemistry: ${normalizedChemicalNotation ?: rawText}"
        is EnglishTextElement -> "English: ${correctedText ?: rawText}"
        is BiologyContentElement -> "Biology: ${recognizedText.orEmpty()}"
        is ChemistryResultElement -> "Chemistry result: $title"
        is EnglishResultElement -> "English result: $title"
        is BiologyResultElement -> "Biology result: $title"
    }
}
