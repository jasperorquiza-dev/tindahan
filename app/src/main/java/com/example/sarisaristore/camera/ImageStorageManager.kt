package com.example.sarisaristore.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class ImageStorageManager(
    private val context: Context,
) {
    private val imagesRootDirectory: File
        get() = File(context.filesDir, "images")

    fun createImageFile(imageType: ImageType): File {
        val directory = File(imagesRootDirectory, imageType.directoryName).apply {
            mkdirs()
        }
        return File(directory, "${imageType.filePrefix}_${System.currentTimeMillis()}.jpg")
    }

    suspend fun importImage(uri: Uri, imageType: ImageType): String = withContext(Dispatchers.IO) {
        val destination = createImageFile(imageType)
        val optimizedBitmap = decodeImportedBitmap(uri = uri, imageType = imageType)

        if (optimizedBitmap == null) {
            copySourceToDestination(uri = uri, destination = destination)
            return@withContext destination.absolutePath
        }

        try {
            destination.outputStream().use { output ->
                check(
                    optimizedBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        imageType.compressionQuality(),
                        output,
                    ),
                ) {
                    "Could not save selected image."
                }
            }
            destination.absolutePath
        } finally {
            optimizedBitmap.recycle()
        }
    }

    suspend fun saveBitmap(
        bitmap: Bitmap,
        imageType: ImageType,
        quality: Int = imageType.compressionQuality(),
    ): String = withContext(Dispatchers.IO) {
        val destination = createImageFile(imageType)
        destination.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                "Could not save image."
            }
        }
        destination.absolutePath
    }

    suspend fun deleteImage(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) {
            return@withContext
        }
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun clearAllImages() = withContext(Dispatchers.IO) {
        runCatching {
            if (imagesRootDirectory.exists()) {
                imagesRootDirectory.deleteRecursively()
            }
            imagesRootDirectory.mkdirs()
        }
    }

    fun resolveRestoredImageFile(relativePath: String): File {
        val safeRelativePath = relativePath
            .replace('\\', '/')
            .removePrefix("/")
            .removePrefix("images/")
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString(separator = File.separator)

        val destination = File(imagesRootDirectory, safeRelativePath)
        destination.parentFile?.mkdirs()
        return destination
    }

    private fun decodeImportedBitmap(
        uri: Uri,
        imageType: ImageType,
    ): Bitmap? {
        val maxDimension = imageType.maxImportedDimension()
        val bounds = decodeBounds(uri) ?: return null
        val sampledBitmap = decodeBitmap(
            uri = uri,
            sampleSize = calculateInSampleSize(
                width = bounds.first,
                height = bounds.second,
                maxDimension = maxDimension,
            ),
        ) ?: return null

        val resizedBitmap = resizeBitmapIfNeeded(sampledBitmap, maxDimension)
        return applyOrientationIfNeeded(uri = uri, bitmap = resizedBitmap)
    }

    private fun decodeBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }
        return options.outWidth to options.outHeight
    }

    private fun decodeBitmap(uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension / largestSide.toFloat()
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (resizedBitmap != bitmap) {
            bitmap.recycle()
        }
        return resizedBitmap
    }

    private fun applyOrientationIfNeeded(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }

        val transformedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
        if (transformedBitmap != bitmap) {
            bitmap.recycle()
        }
        return transformedBitmap
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height

        while (max(sampledWidth, sampledHeight) > maxDimension * 2) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize
    }

    private fun copySourceToDestination(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Could not open selected image.")
    }

    private fun ImageType.maxImportedDimension(): Int = when (this) {
        ImageType.PRODUCT -> 1440
        ImageType.RECEIPT -> 1920
        ImageType.SIGNATURE -> 1024
    }

    private fun ImageType.compressionQuality(): Int = when (this) {
        ImageType.PRODUCT -> 84
        ImageType.RECEIPT -> 82
        ImageType.SIGNATURE -> 80
    }
}
