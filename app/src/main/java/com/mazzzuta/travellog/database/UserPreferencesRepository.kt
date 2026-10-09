package com.mazzzuta.travellog.database

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Все пользовательские настройки приложения. */
data class UserPrefs(
    val nickname: String = "Путешественник",
    val registeredAt: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val schemeId: String? = null,
    // Старые ключи читаются для сохранения ранее выбранного оформления.
    val lightSchemeId: String = "light_base",
    val darkSchemeId: String = "dark_base",
    val defaultSort: String = "date_desc",
    val dateFormat: String = "dmy",
)

/** Хранит настройки пользователя в DataStore (не в Room: это не записи дневника). */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val REGISTERED_AT = longPreferencesKey("registered_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SCHEME = stringPreferencesKey("scheme")
        val LIGHT_SCHEME = stringPreferencesKey("light_scheme")
        val DARK_SCHEME = stringPreferencesKey("dark_scheme")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val DATE_FORMAT = stringPreferencesKey("date_format")
    }

    val preferencesFlow: Flow<UserPrefs> = context.dataStore.data.map { it.toUserPrefs() }

    private fun Preferences.toUserPrefs(): UserPrefs {
        val defaults = UserPrefs()
        return UserPrefs(
            nickname = this[Keys.NICKNAME] ?: defaults.nickname,
            registeredAt = this[Keys.REGISTERED_AT] ?: 0L,
            themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME_MODE] ?: "") }.getOrDefault(defaults.themeMode),
            schemeId = this[Keys.SCHEME],
            lightSchemeId = this[Keys.LIGHT_SCHEME] ?: defaults.lightSchemeId,
            darkSchemeId = this[Keys.DARK_SCHEME] ?: defaults.darkSchemeId,
            defaultSort = this[Keys.DEFAULT_SORT] ?: defaults.defaultSort,
            dateFormat = this[Keys.DATE_FORMAT] ?: defaults.dateFormat,
        )
    }

    /** Фиксирует дату первого запуска («регистрации»), только если её ещё нет. */
    suspend fun ensureRegisteredAt() {
        context.dataStore.edit { if (it[Keys.REGISTERED_AT] == null) it[Keys.REGISTERED_AT] = System.currentTimeMillis() }
    }

    suspend fun setNickname(name: String) = context.dataStore.edit { it[Keys.NICKNAME] = name }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit {
        it[Keys.THEME_MODE] = mode.name
        it[Keys.SCHEME] = "default"
    }
    suspend fun setScheme(id: String) = context.dataStore.edit { it[Keys.SCHEME] = id }
    suspend fun setDefaultSort(sort: String) = context.dataStore.edit { it[Keys.DEFAULT_SORT] = sort }
    suspend fun setDateFormat(format: String) = context.dataStore.edit { it[Keys.DATE_FORMAT] = format }
}
