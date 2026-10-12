package com.mazzzuta.travellog

import com.mazzzuta.travellog.backup.BackupArchive
import com.mazzzuta.travellog.backup.BackupData
import com.mazzzuta.travellog.database.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    private val diary = BackupData(
        trips = listOf(TripEntity(id = 7, title = "Байкал", startDate = 100, coverPhotoPath = "photos/0.jpg", createdAt = 50)),
        entries = listOf(
            EntryEntity(id = 11, tripId = 7, title = "Ольхон", description = "Фото и теги", date = 200,
                latitude = 0.0, longitude = 0.0, createdAt = 100, updatedAt = 300),
            EntryEntity(id = 12, title = "Отдельная запись", description = "", date = 400, createdAt = 100, updatedAt = 400)),
        photos = listOf(PhotoEntity(id = 21, entryId = 11, filePath = "photos/0.jpg", orderIndex = 2)),
        tags = listOf(TagEntity(id = 31, name = "Природа", colorHex = "#B6FF00")),
        links = listOf(EntryTagCrossRef(entryId = 11, tagId = 31)),
        preferences = UserPrefs(nickname = "Максим", registeredAt = 123, themeMode = ThemeMode.DARK,
            schemeId = "neon", defaultSort = "title", dateFormat = "mdy"),
    )

    private fun rawZip(data: BackupData = diary, photos: Map<String, ByteArray> = mapOf("photos/0.jpg" to byteArrayOf(1, 2, 3))): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (mapOf("data.json" to Json { encodeDefaults = true }.encodeToString(data).toByteArray()) + photos).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun read(bytes: ByteArray): BackupData = BackupArchive.read(ByteArrayInputStream(bytes), temporary.newFolder())

    private fun rejects(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Повреждённый архив должен быть отклонён")
    }

    @Test fun roundTripPreservesRelationshipsSettingsAndPhotoBytes() {
        val source = temporary.newFile().apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, diary, mapOf("photos/0.jpg" to source))
        val destination = temporary.newFolder()
        assertEquals(diary, BackupArchive.read(ByteArrayInputStream(output.toByteArray()), destination))
        assertArrayEquals(source.readBytes(), File(destination, "0.jpg").readBytes())
        assertEquals(1, diary.photoNames().size) // Обложка и фото записи могут ссылаться на один файл.
    }

    @Test fun emptyDiaryRoundTrips() {
        val empty = diary.copy(trips = emptyList(), entries = emptyList(), photos = emptyList(), tags = emptyList(), links = emptyList())
        assertEquals(empty, read(rawZip(empty, emptyMap())))
    }

    @Test fun missingOrExtraPhotoIsRejected() {
        rejects { read(rawZip(photos = emptyMap())) }
        rejects { read(rawZip(photos = mapOf("photos/0.jpg" to byteArrayOf(1), "photos/1.jpg" to byteArrayOf(2)))) }
    }

    @Test fun traversalAndAbsolutePathsAreRejected() {
        listOf("../escape.jpg", "/escape.jpg", "photos/../../escape.jpg", "photos\\0.jpg").forEach { path ->
            rejects { read(rawZip(photos = mapOf(path to byteArrayOf(1)))) }
        }
        assertFalse(File(temporary.root, "escape.jpg").exists())
    }

    @Test fun unsupportedVersionIsRejected() {
        rejects { read(rawZip(diary.copy(version = 2))) }
    }

    @Test fun invalidForeignKeysAndDuplicateIdsAreRejected() {
        rejects { read(rawZip(diary.copy(trips = emptyList()))) }
        rejects { read(rawZip(diary.copy(entries = diary.entries + diary.entries.first()))) }
        rejects { read(rawZip(diary.copy(links = listOf(EntryTagCrossRef(11, 999))))) }
        rejects { read(rawZip(diary.copy(photos = listOf(diary.photos.single().copy(entryId = 999))))) }
    }

    @Test fun invalidCoordinatesAndSettingsAreRejected() {
        rejects { read(rawZip(diary.copy(entries = listOf(diary.entries.first().copy(latitude = 91.0))))) }
        rejects { read(rawZip(diary.copy(entries = listOf(diary.entries.first().copy(longitude = null))))) }
        rejects { read(rawZip(diary.copy(preferences = diary.preferences.copy(defaultSort = "unknown")))) }
    }

    @Test fun damagedPhotoCrcIsRejected() {
        val payload = byteArrayOf(11, 22, 33, 44, 55, 66)
        val bytes = rawZip(photos = mapOf("photos/0.jpg" to payload))
        val offset = (0..bytes.size - payload.size).first { index -> payload.indices.all { bytes[index + it] == payload[it] } }
        bytes[offset] = 99
        rejects { read(bytes) }
    }

    @Test fun missingManifestAndInvalidJsonAreRejected() {
        rejects { read(byteArrayOf()) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write("{invalid json}".toByteArray())
            zip.closeEntry()
        }
        rejects { read(output.toByteArray()) }
    }

    @Test fun oversizedManifestIsRejectedBeforeParsing() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            repeat(16 * 1024 + 1) { zip.write(ByteArray(1024) { 32 }) }
            zip.closeEntry()
        }
        rejects { read(output.toByteArray()) }
    }
}
