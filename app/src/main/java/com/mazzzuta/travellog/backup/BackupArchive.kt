package com.mazzzuta.travellog.backup

import com.mazzzuta.travellog.database.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val version: Int = 1,
    val trips: List<TripEntity>,
    val entries: List<EntryEntity>,
    val photos: List<PhotoEntity>,
    val tags: List<TagEntity>,
    val links: List<EntryTagCrossRef>,
    val preferences: UserPrefs,
) {
    fun photoNames(): Set<String> = (photos.map { it.filePath } + trips.mapNotNull { it.coverPhotoPath }).toSet()

    fun validate(availablePhotos: Set<String>) {
        require(version == 1) { "Эта версия архива не поддерживается" }
        fun unique(ids: List<Long>) = ids.all { it > 0 } && ids.toSet().size == ids.size
        require(unique(trips.map { it.id }) && unique(entries.map { it.id }) &&
            unique(photos.map { it.id }) && unique(tags.map { it.id })) { "Некорректные идентификаторы" }
        val tripIds = trips.map { it.id }.toSet()
        val entryIds = entries.map { it.id }.toSet()
        val tagIds = tags.map { it.id }.toSet()
        require(trips.all { it.title.isNotBlank() && (it.endDate == null || it.endDate >= it.startDate) }) { "Некорректная поездка" }
        require(entries.all { it.title.isNotBlank() && (it.tripId == null || it.tripId in tripIds) &&
            ((it.latitude == null && it.longitude == null) || it.hasCoordinates()) }) { "Некорректная запись" }
        require(photos.all { it.entryId in entryIds && it.orderIndex >= 0 }) { "Некорректная фотография" }
        require(tags.all { it.name.isNotBlank() }) { "Некорректный тег" }
        require(links.distinct().size == links.size && links.all { it.entryId in entryIds && it.tagId in tagIds }) { "Некорректные связи тегов" }
        require(photoNames().all { PHOTO_NAME.matches(it) } && photoNames() == availablePhotos) { "В архиве отсутствуют фотографии или есть лишние файлы" }
        require(preferences.nickname.isNotBlank() && preferences.nickname.length <= 30 &&
            preferences.registeredAt >= 0 && preferences.defaultSort in setOf("date_desc", "date_asc", "title") &&
            preferences.dateFormat in setOf("dmy", "mdy")) { "Некорректные настройки" }
    }
}

private val PHOTO_NAME = Regex("photos/[0-9]+\\.jpg")
private const val MAX_JSON = 16L * 1024 * 1024
private const val MAX_PHOTO = 64L * 1024 * 1024
private const val MAX_TOTAL = 1024L * 1024 * 1024
private const val MAX_FILES = 10000
private val json = Json { encodeDefaults = true }

object BackupArchive {
    fun write(output: OutputStream, data: BackupData, files: Map<String, File>) {
        data.validate(files.keys)
        val bytes = json.encodeToString(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_JSON && files.size < MAX_FILES && files.values.all { it.isFile && it.length() <= MAX_PHOTO } &&
            files.values.sumOf { it.length() } + bytes.size <= MAX_TOTAL) { "Дневник слишком большой для одного архива (лимит 1 ГБ)" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(bytes)
            zip.closeEntry()
            files.forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // Принимаем только имена нашего формата; пути из ZIP никогда не используются для извлечения напрямую.
    fun read(input: InputStream, directory: File): BackupData {
        check(directory.isDirectory && directory.listFiles()?.isEmpty() == true)
        val seen = mutableSetOf<String>()
        var data: BackupData? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && seen.add(entry.name) && seen.size <= MAX_FILES &&
                    (entry.name == "data.json" || PHOTO_NAME.matches(entry.name))) { "Неподдерживаемое содержимое ZIP" }
                val limit = if (entry.name == "data.json") MAX_JSON else MAX_PHOTO
                val target = File(directory, if (entry.name == "data.json") "data.json" else entry.name.substringAfter('/'))
                var size = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count
                        total += count
                        require(size <= limit && total <= MAX_TOTAL) { "Архив превышает допустимый размер" }
                        output.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
                if (entry.name == "data.json") data = json.decodeFromString<BackupData>(target.readText(Charsets.UTF_8))
            }
        }
        val result = requireNotNull(data) { "В архиве нет data.json" }
        result.validate(seen - "data.json")
        return result
    }
}
