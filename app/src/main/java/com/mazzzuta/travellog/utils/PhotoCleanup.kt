package com.mazzzuta.travellog.utils

import android.content.Context
import androidx.room.withTransaction
import com.mazzzuta.travellog.database.AppDatabase
import java.io.File
import java.nio.file.Files

data class PhotoCleanupResult(val deleted: Int = 0, val bytes: Long = 0, val failed: Int = 0)

class PhotoCleanup(context: Context, private val database: AppDatabase, private val storage: PhotoStorageHelper) {
    private val root = File(context.filesDir, "photos")

    suspend fun clean(): PhotoCleanupResult = storage.withPhotoAccess {
        database.withTransaction {
            val dao = database.backupDao()
            val referenced = dao.photos().map { it.filePath } + dao.trips().mapNotNull { it.coverPhotoPath }
            removeUnusedPhotos(root, referenced.toSet())
        }
    }
}

internal fun removeUnusedPhotos(root: File, referenced: Set<String>): PhotoCleanupResult {
    if (!root.exists()) return PhotoCleanupResult()
    check(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
    val directory = root.canonicalFile.toPath()
    val keep = referenced.map { File(it).canonicalPath }.toSet()
    var deleted = 0
    var bytes = 0L
    var failed = 0
    root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }
        .onFail { _, error -> throw error }.forEach { file ->
            if (file.isFile && !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.toPath().startsWith(directory) &&
                file.canonicalPath !in keep) {
                val size = file.length()
                if (file.delete()) { deleted++; bytes += size } else failed++
            }
        }
    root.walkBottomUp().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
        if (file != root && file.isDirectory && !Files.isSymbolicLink(file.toPath()) && file.list()?.isEmpty() == true) file.delete()
    }
    return PhotoCleanupResult(deleted, bytes, failed)
}
