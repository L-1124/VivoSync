package com.app.vivosync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class ThemePreferences(
    val followSystemDynamicColor: Boolean,
    val followSystemDarkTheme: Boolean
)

interface ThemePreferencesRepository {
    val themePreferencesFlow: Flow<ThemePreferences>
    suspend fun setFollowSystemDynamicColor(enabled: Boolean)
    suspend fun setFollowSystemDarkTheme(enabled: Boolean)
}

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vivosync_theme_preferences",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "vivosync_theme_settings")
        )
    }
)

class ThemePreferencesRepositoryImpl(
    private val context: Context
) : ThemePreferencesRepository {

    private object Keys {
        val FOLLOW_SYSTEM_DYNAMIC_COLOR = booleanPreferencesKey("key_follow_system_dynamic_color")
        val FOLLOW_SYSTEM_DARK_THEME = booleanPreferencesKey("key_follow_system_dark_theme")
    }

    override val themePreferencesFlow: Flow<ThemePreferences> = context.themeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val dynamicColor = preferences[Keys.FOLLOW_SYSTEM_DYNAMIC_COLOR] ?: true
            val darkTheme = preferences[Keys.FOLLOW_SYSTEM_DARK_THEME] ?: true
            ThemePreferences(
                followSystemDynamicColor = dynamicColor,
                followSystemDarkTheme = darkTheme
            )
        }

    override suspend fun setFollowSystemDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.FOLLOW_SYSTEM_DYNAMIC_COLOR] = enabled
        }
    }

    override suspend fun setFollowSystemDarkTheme(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.FOLLOW_SYSTEM_DARK_THEME] = enabled
        }
    }
}
