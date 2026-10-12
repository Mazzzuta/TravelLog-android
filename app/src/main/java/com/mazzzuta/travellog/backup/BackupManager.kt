package com.mazzzuta.travellog.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.mazzzuta.travellog.database.AppDatabase
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.utils.PhotoStorageHelper
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PreparedImport(val data: BackupData, val directory: File) {
    fun discard() { directory.deleteRecursively() }
}

class BackupManager(private val context: Context, private val database: AppDatabase,
    private val preferences: UserPreferencesRepository, private val storage: PhotoStorageHelper) {

    suspend fun export(uri: Uri) = storage.withPhotoAccess {
        val archive = File.createTempFile("travellog-export-", ".zip", context.cacheDir)
        try {
            val prefs = preferences.preferencesFlow.first()
            val snapshot = database.withTransaction {
                val dao = database.backupDao()
                BackupData(trips = dao.trips(), entries = dao.entries(), photos = dao.photos(),
                    tags = dao.tags(), links = dao.links(), preferences = prefs)
            }
            val paths = (snapshot.photos.map { it.filePath } + snapshot.trips.mapNotNull { it.coverPhotoPath }).distinct()
            val photoRoot = File(context.filesDir, "photos").canonicalFile
            val mapping = paths.mapIndexed { index, path -> path to "photos/$index.jpg" }.toMap()
            val files = mapping.map { (path, name) ->
                val file = File(path).canonicalFile
                require(file.toPath().startsWith(photoRoot.toPath()) && file.isFile) { "Одна из фотографий недоступна" }
                name to file
            }.toMap()
            val data = snapshot.copy(
                photos = snapshot.photos.map { it.copy(filePath = mapping.getValue(it.filePath)) },
                trips = snapshot.trips.map { it.copy(coverPhotoPath = it.coverPhotoPath?.let(mapping::getValue)) })
            archive.outputStream().use { BackupArchive.write(it, data, files) }
            currentCoroutineContext().ensureActive()
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Не удалось открыть файл для записи")
            output.use { archive.inputStream().use { input -> input.copyTo(it) } }
        } finally {
            archive.delete()
        }
    }

    suspend fun prepareImport(uri: Uri): PreparedImport = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "travellog-import-${UUID.randomUUID()}")
        check(directory.mkdir()) { "Не удалось подготовить хранилище" }
        val archive = File.createTempFile("travellog-read-", ".zip", context.cacheDir)
        var prepared: PreparedImport? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть архив")
            input.use { source -> archive.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 1100L * 1024 * 1024) { "Архив слишком большой" }
                    output.write(buffer, 0, count)
                }
            } }
            // ZipFile проверяет наличие центрального каталога, ZipInputStream ниже — CRC каждого файла.
            val names = ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
            val data = archive.inputStream().use { BackupArchive.read(it, directory) }
            require(names.size == names.toSet().size && names.toSet() == data.photoNames() + "data.json") { "Повреждённый каталог ZIP" }
            data.photoNames().forEach { name ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(File(directory, name.substringAfter('/')).path, options)
                require(options.outWidth > 0 && options.outHeight > 0) { "Повреждена фотография в архиве" }
            }
            prepared = PreparedImport(data, directory)
            prepared!!
        } finally {
            archive.delete()
            if (prepared == null) directory.deleteRecursively()
        }
    }

    /** false означает: дневник восстановлен, настройки не удалось записать. */
    suspend fun restore(prepared: PreparedImport): Boolean = storage.withPhotoAccess {
        val destination = File(context.filesDir, "photos/import-${UUID.randomUUID()}")
        check(destination.mkdirs()) { "Не удалось подготовить фотографии" }
        var committed = false
        try {
            prepared.data.photoNames().forEach { name ->
                currentCoroutineContext().ensureActive()
                File(prepared.directory, name.substringAfter('/')).copyTo(File(destination, name.substringAfter('/')))
            }
            fun localPath(name: String) = File(destination, name.substringAfter('/')).absolutePath
            val data = prepared.data
            withContext(NonCancellable) {
                database.withTransaction {
                    val dao = database.backupDao()
                    dao.clearEntries()
                    dao.clearTrips()
                    dao.clearTags()
                    dao.insertTrips(data.trips.map { it.copy(coverPhotoPath = it.coverPhotoPath?.let(::localPath)) })
                    dao.insertEntries(data.entries)
                    dao.insertTags(data.tags)
                    dao.insertPhotos(data.photos.map { it.copy(filePath = localPath(it.filePath)) })
                    dao.insertLinks(data.links)
                }
                committed = true
                // shortcut: Room и DataStore фиксируются отдельно; журнал восстановления нужен для атомарности при завершении процесса.
                try {
                    preferences.restore(data.preferences)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } finally {
            if (!committed) destination.deleteRecursively()
        }
    }
}
