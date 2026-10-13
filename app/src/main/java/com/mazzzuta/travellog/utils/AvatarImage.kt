package com.mazzzuta.travellog.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64

object AvatarImage {
    const val MAX_LENGTH = 128 * 1024

    fun read(resolver: ContentResolver, uri: Uri): String {
        fun open() = requireNotNull(resolver.openInputStream(uri))
        // Сначала читаем только размеры, чтобы не загружать многомегапиксельный оригинал целиком в память.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        val source = requireNotNull(open().use { BitmapFactory.decodeStream(it, null, options) })
        try {
            val orientation = open().use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val side = minOf(source.width, source.height)
            val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side, matrix, true)
            val avatar = Bitmap.createScaledBitmap(cropped, 256, 256, true)
            return try {
                val output = ByteArrayOutputStream()
                check(avatar.compress(Bitmap.CompressFormat.JPEG, 85, output))
                Base64.getEncoder().encodeToString(output.toByteArray()).also { require(it.length <= MAX_LENGTH) }
            } finally {
                if (avatar !== cropped) avatar.recycle()
                if (cropped !== source) cropped.recycle()
            }
        } finally { source.recycle() }
    }

    fun decode(value: String?): Bitmap? = runCatching {
        if (value == null || value.length > MAX_LENGTH) return null
        val bytes = Base64.getDecoder().decode(value)
        // Проверяем размеры до декодирования
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..256 && bounds.outHeight in 1..256)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
