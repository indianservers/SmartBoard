package com.indianservers.smartboard.smartboard.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.RecognitionRegion
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.StrokeElement
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartBoardImageAssetStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "smartboard-assets").apply { mkdirs() }
    private val resolver = context.applicationContext.contentResolver

    suspend fun import(uri: Uri, now: Long): ImageElement = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMPORT_BYTES) { "Use an image smaller than 20 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("The selected image could not be opened.")
        require(bytes.size <= MAX_IMPORT_BYTES) { "Use an image smaller than 20 MB." }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..MAX_SOURCE_DIMENSION && options.outHeight in 1..MAX_SOURCE_DIMENSION) {
            "Image dimensions are unsupported."
        }
        val sample = sampleSize(options.outWidth, options.outHeight)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("The selected file is not a readable image.")
        try {
            val assetId = UUID.randomUUID().toString()
            val file = File(root, "$assetId.png")
            FileOutputStream(file).use { output ->
                check(decoded.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Image could not be stored." }
            }
            // Re-encoding removes unneeded source metadata and constrains decoded dimensions.
            ImageElement(
                id = "image-$assetId",
                assetId = assetId,
                relativePath = "smartboard-assets/${file.name}",
                mimeType = "image/png",
                pixelWidth = decoded.width,
                pixelHeight = decoded.height,
                bounds = SmartBoardBounds(24f, 24f, 24f + decoded.width.coerceAtMost(900), 24f + decoded.height.coerceAtMost(900)),
                createdAt = now,
            )
        } finally {
            decoded.recycle()
        }
    }

    suspend fun rotate(element: ImageElement, clockwise: Boolean): ImageElement = withContext(Dispatchers.IO) {
        val source = safeFile(element)
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Imported image is unavailable.")
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(if (clockwise) 90f else -90f) }, true)
        try {
            val assetId = UUID.randomUUID().toString()
            val target = File(root, "$assetId.png")
            FileOutputStream(target).use { check(rotated.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            element.copy(
                assetId = assetId,
                relativePath = "smartboard-assets/${target.name}",
                pixelWidth = rotated.width,
                pixelHeight = rotated.height,
                rotationDegrees = (element.rotationDegrees + if (clockwise) 90 else 270) % 360,
                bounds = SmartBoardBounds(element.bounds.left, element.bounds.top, element.bounds.left + element.bounds.height, element.bounds.top + element.bounds.width),
            )
        } finally {
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
        }
    }

    suspend fun crop(element: ImageElement, normalized: SmartBoardBounds): ImageElement = withContext(Dispatchers.IO) {
        require(normalized.left in 0f..1f && normalized.top in 0f..1f && normalized.right in 0f..1f && normalized.bottom in 0f..1f)
        require(normalized.width > .01f && normalized.height > .01f)
        val source = safeFile(element)
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Imported image is unavailable.")
        val x = (normalized.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (normalized.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val width = (normalized.width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val height = (normalized.height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        val cropped = Bitmap.createBitmap(bitmap, x, y, width, height)
        try {
            val assetId = UUID.randomUUID().toString()
            val target = File(root, "$assetId.png")
            FileOutputStream(target).use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            element.copy(assetId = assetId, relativePath = "smartboard-assets/${target.name}", pixelWidth = cropped.width, pixelHeight = cropped.height)
        } finally {
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
        }
    }

    suspend fun delete(element: ImageElement) = withContext(Dispatchers.IO) { safeFile(element).delete(); Unit }

    suspend fun cleanupUnreferenced(referencedAssetIds: Set<String>, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().filter { file ->
            file.isFile && file.extension == "png" && file.nameWithoutExtension !in referencedAssetIds &&
                now - file.lastModified() > ORPHAN_GRACE_MILLIS
        }.forEach(File::delete)
    }

    fun resolve(element: ImageElement): File = safeFile(element)

    private fun safeFile(element: ImageElement): File {
        val file = File(root, "${element.assetId}.png").canonicalFile
        require(file.parentFile == root.canonicalFile) { "Invalid Smart Board asset path." }
        return file
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DECODED_DIMENSION || height / sample > MAX_DECODED_DIMENSION) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 20_000_000
        const val MAX_SOURCE_DIMENSION = 32_768
        const val MAX_DECODED_DIMENSION = 4_096
        const val ORPHAN_GRACE_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

object SmartBoardRegionEngine {
    fun add(regions: List<RecognitionRegion>, bounds: SmartBoardBounds, sources: List<String> = emptyList()): List<RecognitionRegion> =
        (regions + RecognitionRegion("region-${UUID.randomUUID()}", bounds, regions.size, sources)).reindexed()

    fun move(regions: List<RecognitionRegion>, id: String, dx: Float, dy: Float) =
        regions.map { if (it.id == id) it.copy(bounds = it.bounds.translate(com.indianservers.smartboard.smartboard.models.SmartBoardPoint(dx, dy))) else it }.reindexed()

    fun resize(regions: List<RecognitionRegion>, id: String, bounds: SmartBoardBounds) =
        regions.map { if (it.id == id) it.copy(bounds = bounds) else it }.reindexed()

    fun delete(regions: List<RecognitionRegion>, id: String) = regions.filterNot { it.id == id }.reindexed()

    fun reorder(regions: List<RecognitionRegion>, orderedIds: List<String>): List<RecognitionRegion> {
        val byId = regions.associateBy(RecognitionRegion::id)
        return (orderedIds.mapNotNull(byId::get) + regions.filterNot { it.id in orderedIds }).reindexed()
    }

    fun merge(regions: List<RecognitionRegion>, ids: Set<String>): List<RecognitionRegion> {
        val selected = regions.filter { it.id in ids }
        require(selected.size >= 2)
        val mergedBounds = SmartBoardBounds.from(selected.flatMap {
            listOf(com.indianservers.smartboard.smartboard.models.SmartBoardPoint(it.bounds.left, it.bounds.top), com.indianservers.smartboard.smartboard.models.SmartBoardPoint(it.bounds.right, it.bounds.bottom))
        })
        val merged = RecognitionRegion("region-${UUID.randomUUID()}", mergedBounds, selected.minOf(RecognitionRegion::order), selected.flatMap(RecognitionRegion::sourceElementIds).distinct())
        return (regions.filterNot { it.id in ids } + merged).sortedBy(RecognitionRegion::order).reindexed()
    }

    fun split(regions: List<RecognitionRegion>, id: String, horizontal: Boolean, ratio: Float = .5f): List<RecognitionRegion> {
        require(ratio in .1f..0.9f)
        val region = regions.first { it.id == id }
        val first: SmartBoardBounds
        val second: SmartBoardBounds
        if (horizontal) {
            val y = region.bounds.top + region.bounds.height * ratio
            first = SmartBoardBounds(region.bounds.left, region.bounds.top, region.bounds.right, y)
            second = SmartBoardBounds(region.bounds.left, y, region.bounds.right, region.bounds.bottom)
        } else {
            val x = region.bounds.left + region.bounds.width * ratio
            first = SmartBoardBounds(region.bounds.left, region.bounds.top, x, region.bounds.bottom)
            second = SmartBoardBounds(x, region.bounds.top, region.bounds.right, region.bounds.bottom)
        }
        val replacements = listOf(first, second).map { RecognitionRegion("region-${UUID.randomUUID()}", it, region.order, region.sourceElementIds) }
        return (regions.filterNot { it.id == id } + replacements).sortedWith(compareBy<RecognitionRegion> { it.bounds.top }.thenBy { it.bounds.left }).reindexed()
    }

    private fun List<RecognitionRegion>.reindexed() = mapIndexed { index, region -> region.copy(order = index) }
}

object SmartBoardLineDetector {
    /**
     * Deterministic layout grouping based on overlapping vertical bands. It never recognizes or
     * rewrites mathematics; users can reorder, merge and split the resulting regions.
     */
    fun detect(strokes: List<StrokeElement>, verticalTolerance: Float = 12f): List<RecognitionRegion> {
        if (strokes.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<StrokeElement>>()
        strokes.sortedWith(compareBy<StrokeElement> { it.bounds.top }.thenBy { it.bounds.left }).forEach { stroke ->
            val row = rows.firstOrNull { existing ->
                val top = existing.minOf { it.bounds.top } - verticalTolerance
                val bottom = existing.maxOf { it.bounds.bottom } + verticalTolerance
                stroke.bounds.center.y in top..bottom
            }
            if (row == null) rows += mutableListOf(stroke) else row += stroke
        }
        return rows.sortedBy { it.minOf { stroke -> stroke.bounds.top } }.mapIndexed { index, row ->
            val bounds = SmartBoardBounds(
                row.minOf { it.bounds.left },
                row.minOf { it.bounds.top },
                row.maxOf { it.bounds.right },
                row.maxOf { it.bounds.bottom },
            ).expand(8f)
            RecognitionRegion("region-${UUID.randomUUID()}", bounds, index, row.sortedBy { it.bounds.left }.map(StrokeElement::id))
        }
    }
}
