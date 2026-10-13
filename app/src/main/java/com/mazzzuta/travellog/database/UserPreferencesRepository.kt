package com.mazzzuta.travellog.database

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import com.mazzzuta.travellog.utils.AvatarImage

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Serializable
data class UserPrefs(
    val nickname: String = "Путешественник",
    val registeredAt: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val schemeId: String? = null,
    val lightSchemeId: String = "light_base",
    val darkSchemeId: String = "dark_base",
    val defaultSort: String = "date_desc",
    val dateFormat: String = "dmy",
    val avatar: String? = null,
)

class UserPreferencesRepository(private val context: Context, private val store: DataStore<Preferences> = context.dataStore) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR = stringPreferencesKey("avatar")
        val REGISTERED_AT = longPreferencesKey("registered_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SCHEME = stringPreferencesKey("scheme")
        val LIGHT_SCHEME = stringPreferencesKey("light_scheme")
        val DARK_SCHEME = stringPreferencesKey("dark_scheme")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val DATE_FORMAT = stringPreferencesKey("date_format")
    }

    val preferencesFlow: Flow<UserPrefs> = store.data.map { it.toUserPrefs() }

    private fun Preferences.toUserPrefs(): UserPrefs {
        val defaults = UserPrefs()
        return UserPrefs(
            nickname = this[Keys.NICKNAME] ?: defaults.nickname,
            avatar = this[Keys.AVATAR],
            registeredAt = this[Keys.REGISTERED_AT] ?: 0L,
            themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME_MODE] ?: "") }.getOrDefault(defaults.themeMode),
            schemeId = this[Keys.SCHEME],
            lightSchemeId = this[Keys.LIGHT_SCHEME] ?: defaults.lightSchemeId,
            darkSchemeId = this[Keys.DARK_SCHEME] ?: defaults.darkSchemeId,
            defaultSort = this[Keys.DEFAULT_SORT] ?: defaults.defaultSort,
            dateFormat = this[Keys.DATE_FORMAT] ?: defaults.dateFormat,
        )
    }

    // Фиксирует дату регистрации, только если её ещё нет.
    suspend fun ensureRegisteredAt() {
        store.edit { if (it[Keys.REGISTERED_AT] == null) it[Keys.REGISTERED_AT] = System.currentTimeMillis() }
    }

    suspend fun setNickname(name: String) = store.edit { it[Keys.NICKNAME] = name }
    suspend fun setAvatar(uri: Uri?) = withContext(Dispatchers.IO) {
        // Храним уменьшенное фото, а не URI галереи.
        val image = uri?.let { AvatarImage.read(context.contentResolver, it) }
        store.edit { if (image == null) it.remove(Keys.AVATAR) else it[Keys.AVATAR] = image }
    }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit {
        it[Keys.THEME_MODE] = mode.name
        it[Keys.SCHEME] = "default"
    }
    suspend fun setScheme(id: String) = store.edit { it[Keys.SCHEME] = id }
    suspend fun setDefaultSort(sort: String) = store.edit { it[Keys.DEFAULT_SORT] = sort }
    suspend fun setDateFormat(format: String) = store.edit { it[Keys.DATE_FORMAT] = format }

    suspend fun restore(prefs: UserPrefs) = store.edit {
        it[Keys.NICKNAME] = prefs.nickname
        if (prefs.avatar == null) it.remove(Keys.AVATAR) else it[Keys.AVATAR] = prefs.avatar
        it[Keys.REGISTERED_AT] = prefs.registeredAt
        it[Keys.THEME_MODE] = prefs.themeMode.name
        if (prefs.schemeId == null) it.remove(Keys.SCHEME) else it[Keys.SCHEME] = prefs.schemeId
        it[Keys.LIGHT_SCHEME] = prefs.lightSchemeId
        it[Keys.DARK_SCHEME] = prefs.darkSchemeId
        it[Keys.DEFAULT_SORT] = prefs.defaultSort
        it[Keys.DATE_FORMAT] = prefs.dateFormat
    }
}
