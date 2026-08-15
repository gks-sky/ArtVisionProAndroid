package com.gkssky.artvisionpro.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReferenceExportManager(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun export(
        sourceUri: Uri,
        configuration: ValueExportConfiguration,
        option: ReferenceExportOption,
    ): List<Uri> = withContext(Dispatchers.Default) {
        val source = decodeReference(sourceUri)
        var valueMap: Bitmap? = null
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            when (option) {
                ReferenceExportOption.CURRENT_VIEW,
                ReferenceExportOption.VALUE_STUDY -> {
                    val output = ValueShapeExporter.currentView(source, configuration)
                    try {
                        listOf(savePng(output, fileName(stamp, studySuffix(configuration.mode))))
                    } finally {
                        if (output !== source) output.recycle()
                    }
                }

                ReferenceExportOption.ALL_VALUE_SHAPES -> {
                    val levels = requireShapeLevels(configuration.mode)
                    val processed = ValueShapeExporter.currentView(source, configuration)
                    valueMap = processed
                    val saved = ArrayList<Uri>(levels.size + 1)
                    levels.forEachIndexed { index, level ->
                        val shape = ValueShapeExporter.shape(processed, level)
                        try {
                            saved += savePng(shape, fileName(stamp, shapeSuffix(configuration.mode, index)))
                        } finally {
                            shape.recycle()
                        }
                    }
                    saved += savePng(processed, fileName(stamp, studySuffix(configuration.mode)))
                    saved
                }

                else -> {
                    val levels = requireShapeLevels(configuration.mode)
                    val index = option.shapeIndex()
                    require(index in levels.indices) { "That value shape is not available in the current mode." }
                    val processed = ValueShapeExporter.currentView(source, configuration)
                    valueMap = processed
                    val shape = ValueShapeExporter.shape(processed, levels[index])
                    try {
                        listOf(savePng(shape, fileName(stamp, shapeSuffix(configuration.mode, index))))
                    } finally {
                        shape.recycle()
                    }
                }
            }
        } catch (error: OutOfMemoryError) {
            throw IllegalStateException("The reference image is too large to export safely.", error)
        } finally {
            valueMap?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun decodeReference(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val largestDimension = maxOf(info.size.width, info.size.height)
            if (largestDimension > MAX_EXPORT_DIMENSION) {
                val scale = MAX_EXPORT_DIMENSION.toFloat() / largestDimension
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }

    private suspend fun savePng(bitmap: Bitmap, displayName: String): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, PNG_MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, EXPORT_RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    EXPORT_DIRECTORY,
                ).apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val outputUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Unable to create the export file.")
        try {
            resolver.openOutputStream(outputUri, "w")?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to encode the export as PNG."
                }
            } ?: throw IllegalStateException("Unable to open the export file.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(outputUri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            outputUri
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun requireShapeLevels(mode: com.gkssky.artvisionpro.processing.ValueMode): IntArray =
        ValueShapeExporter.levelsFor(mode).also {
            require(it.isNotEmpty()) { "Value-shape export requires 3 Values or 5 Values." }
        }

    private fun fileName(stamp: String, suffix: String) = "ArtVision_${stamp}_$suffix.png"

    private fun studySuffix(mode: com.gkssky.artvisionpro.processing.ValueMode) = when (mode) {
        com.gkssky.artvisionpro.processing.ValueMode.ORIGINAL -> "Original"
        com.gkssky.artvisionpro.processing.ValueMode.GRAYSCALE -> "Grayscale"
        com.gkssky.artvisionpro.processing.ValueMode.TWO_VALUES -> "2Value"
        com.gkssky.artvisionpro.processing.ValueMode.THREE_VALUES -> "3Value"
        com.gkssky.artvisionpro.processing.ValueMode.FIVE_VALUES -> "5Value"
    }

    private fun shapeSuffix(mode: com.gkssky.artvisionpro.processing.ValueMode, index: Int): String = when (mode) {
        com.gkssky.artvisionpro.processing.ValueMode.THREE_VALUES ->
            listOf("DarkShape", "MidShape", "LightShape")[index]
        com.gkssky.artvisionpro.processing.ValueMode.FIVE_VALUES ->
            listOf("DarkestShape", "DarkMidShape", "MiddleShape", "LightMidShape", "LightestShape")[index]
        else -> error("Value shapes are unavailable for $mode")
    }

    private fun ReferenceExportOption.shapeIndex(): Int = when (this) {
        ReferenceExportOption.SHAPE_0 -> 0
        ReferenceExportOption.SHAPE_1 -> 1
        ReferenceExportOption.SHAPE_2 -> 2
        ReferenceExportOption.SHAPE_3 -> 3
        ReferenceExportOption.SHAPE_4 -> 4
        else -> -1
    }

    private companion object {
        const val MAX_EXPORT_DIMENSION = 3000
        const val PNG_MIME_TYPE = "image/png"
        const val EXPORT_DIRECTORY = "ArtVision Pro"
        const val EXPORT_RELATIVE_PATH = "Pictures/$EXPORT_DIRECTORY"
    }
}
