package de.rosberg.onepluscamerabridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

data class CapturedImageInfo(
    val width: Int,
    val height: Int,
    val bytes: Long,
    val mime: String,
    val model: String?,
    val dateTime: String?,
    val orientation: Int
) {
    fun russianSummary(): String = buildString {
        appendLine("Разрешение: $width × $height")
        appendLine("Размер JPEG: ${"%.2f".format(bytes / 1024.0 / 1024.0)} МБ ($bytes байт)")
        appendLine("Формат: $mime")
        appendLine("Модель камеры EXIF: ${model ?: "не указана"}")
        appendLine("Время съёмки EXIF: ${dateTime ?: "не указано"}")
        append("Ориентация EXIF: ${orientationName(orientation)} ($orientation)")
    }

    private fun orientationName(value: Int): String = when (value) {
        ExifInterface.ORIENTATION_NORMAL -> "обычная"
        ExifInterface.ORIENTATION_ROTATE_90 -> "поворот 90°"
        ExifInterface.ORIENTATION_ROTATE_180 -> "поворот 180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "поворот 270°"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "зеркально по горизонтали"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "зеркально по вертикали"
        ExifInterface.ORIENTATION_TRANSPOSE -> "транспонирована"
        ExifInterface.ORIENTATION_TRANSVERSE -> "поперечно отражена"
        else -> "не определена"
    }
}

object ImageUtils {
    fun inspect(file: File): CapturedImageInfo {
        require(file.exists() && file.length() > 0) { "Камера не записала JPEG" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не распознан как изображение" }
        require(bounds.outWidth >= 320 && bounds.outHeight >= 240) { "Неразумно малое разрешение ${bounds.outWidth}×${bounds.outHeight}" }
        val mime = bounds.outMimeType ?: "неизвестно"
        require(mime == "image/jpeg" || mime == "image/jpg") { "Ожидался JPEG, получен $mime" }
        val exif = ExifInterface(file)
        return CapturedImageInfo(
            bounds.outWidth,
            bounds.outHeight,
            file.length(),
            mime,
            exif.getAttribute(ExifInterface.TAG_MODEL),
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        )
    }

    fun thumbnail(file: File, maxSide: Int = 384): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Не удалось создать миниатюру")
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
            }
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
