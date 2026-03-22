package com.handsfree.control.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.handsfree.control.data.model.GestureSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore instance scoped to the application. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gesture_settings"
)

/**
 * Repository that persists user gesture settings using DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val SHOW_OVERLAY = booleanPreferencesKey("show_overlay")
        val SHOW_PREVIEW = booleanPreferencesKey("show_preview")
        val SCROLL_AMOUNT = intPreferencesKey("scroll_amount")
    }

    /** Observe settings changes as a Flow. */
    val settingsFlow: Flow<GestureSettings> = context.dataStore.data.map { prefs ->
        GestureSettings(
            isEnabled = prefs[Keys.IS_ENABLED] ?: true,
            sensitivity = prefs[Keys.SENSITIVITY] ?: 0.7f,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.6f,
            gestureCooldownMs = prefs[Keys.COOLDOWN_MS] ?: 800L,
            showOverlay = prefs[Keys.SHOW_OVERLAY] ?: true,
            showPreview = prefs[Keys.SHOW_PREVIEW] ?: true,
            scrollAmount = prefs[Keys.SCROLL_AMOUNT] ?: 500
        )
    }

    /** Update settings atomically. */
    suspend fun updateSettings(settings: GestureSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = settings.isEnabled
            prefs[Keys.SENSITIVITY] = settings.sensitivity
            prefs[Keys.CONFIDENCE_THRESHOLD] = settings.confidenceThreshold
            prefs[Keys.COOLDOWN_MS] = settings.gestureCooldownMs
            prefs[Keys.SHOW_OVERLAY] = settings.showOverlay
            prefs[Keys.SHOW_PREVIEW] = settings.showPreview
            prefs[Keys.SCROLL_AMOUNT] = settings.scrollAmount
        }
    }

    /** Toggle the master enabled state. */
    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = enabled
        }
    }
}
