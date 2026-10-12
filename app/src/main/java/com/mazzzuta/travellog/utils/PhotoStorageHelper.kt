package com.mazzzuta.travellog.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhotoStorageHelper(private val context: Context) {
    private val mutex = Mutex()

    // Очистка, копирование и фиксация ссылок в БД не должны обгонять друг друга.
    suspend fun <T> withPhotoAccess(block: suspend () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) { block() }
    }

    suspend fun saveToInternalStorage(sourceUri: String): String = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                ?: error("Не удалось открыть фотографию")
            input.use {
                destFile.outputStream().use { output -> it.copyTo(output) }
            }
        } catch (e: Exception) {
            destFile.delete()
            throw e
        }
        destFile.absolutePath
    }
}
