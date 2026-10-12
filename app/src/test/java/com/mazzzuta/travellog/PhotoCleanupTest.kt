package com.mazzzuta.travellog

import com.mazzzuta.travellog.utils.removeUnusedPhotos
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun keepsEntryPhotosAndCoversAndDeletesOnlyUnusedFiles() {
        val root = temporary.newFolder("photos")
        val entry = File(root, "entry.jpg").apply { writeText("entry") }
        val cover = File(root, "import-1/cover.jpg").apply { parentFile!!.mkdirs(); writeText("cover") }
        val unused = File(root, "import-2/unused.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val outside = temporary.newFile("outside.jpg").apply { writeText("outside") }

        val result = removeUnusedPhotos(root, setOf(entry.path, cover.path))

        assertTrue(entry.exists())
        assertTrue(cover.exists())
        assertTrue(outside.exists())
        assertFalse(unused.exists())
        assertFalse(unused.parentFile!!.exists())
        assertEquals(1, result.deleted)
        assertEquals(3L, result.bytes)
        assertEquals(0, result.failed)
        assertEquals(0, removeUnusedPhotos(root, setOf(entry.path, cover.path)).deleted)
    }

    @Test fun normalizesReferencesAndHandlesMissingDirectory() {
        val root = temporary.newFolder("photos")
        val photo = File(root, "photo.jpg").apply { writeText("photo") }
        assertEquals(0, removeUnusedPhotos(root, setOf(File(root, "./photo.jpg").path)).deleted)
        assertTrue(photo.exists())
        assertEquals(0, removeUnusedPhotos(File(root, "missing"), emptySet()).deleted)
    }
}
