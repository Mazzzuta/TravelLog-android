package com.mazzzuta.travellog.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoStorageHelper(private val context: Context) {

    suspend fun saveToInternalStorage(sourceUri: String): String = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    }
}