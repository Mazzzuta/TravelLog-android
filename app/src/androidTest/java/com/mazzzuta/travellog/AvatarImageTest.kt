package com.mazzzuta.travellog

import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.mazzzuta.travellog.database.UserPreferencesRepository
import com.mazzzuta.travellog.utils.AvatarImage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AvatarImageTest {
    @Test fun avatarIsResizedPersistedRestoredAndRemovedWithoutChangingNickname() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "avatar-test-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(root, "test.preferences_pb") }
            val repository = UserPreferencesRepository(context, store)
            val photo = File(root, "photo.jpg")
            val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            repository.setNickname("Тест")
            repository.setAvatar(Uri.fromFile(photo))
            val saved = repository.preferencesFlow.first()
            assertNotNull(saved.avatar)
            val decoded = requireNotNull(AvatarImage.decode(saved.avatar))
            assertEquals(256, decoded.width)
            assertEquals(256, decoded.height)
            decoded.recycle()
            photo.delete()
            assertEquals(saved, UserPreferencesRepository(context, store).preferencesFlow.first())
            repository.setAvatar(null)
            assertNull(repository.preferencesFlow.first().avatar)
            repository.restore(saved)
            assertEquals(saved, repository.preferencesFlow.first())
            try {
                repository.setAvatar(Uri.fromFile(File(root, "missing.jpg")))
                fail("Missing image must fail")
            } catch (_: java.io.FileNotFoundException) { }
            assertEquals(saved, repository.preferencesFlow.first())
            assertNull(AvatarImage.decode("invalid"))
            assertNull(AvatarImage.decode("a".repeat(AvatarImage.MAX_LENGTH + 1)))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            root.deleteRecursively()
        }
    }
}
