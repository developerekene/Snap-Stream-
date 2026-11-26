package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppPreferences
import com.example.model.SortOrder
import com.example.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.snapStreamDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "snapstream_offline_prefs"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DEVICE_LABEL = stringPreferencesKey("device_label")
        val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SAVE_EDITS_AS_COPY = booleanPreferencesKey("save_edits_as_copy")
        val HIGH_QUALITY_EXPORT = booleanPreferencesKey("high_quality_export")
    }

    val preferencesFlow: Flow<AppPreferences> = context.snapStreamDataStore.data.map { prefs ->
        AppPreferences(
            deviceLabel = prefs[Keys.DEVICE_LABEL] ?: "Local Storage",
            darkModeEnabled = prefs[Keys.DARK_MODE] ?: false,
            viewMode = prefs[Keys.VIEW_MODE]?.let { name ->
                ViewMode.entries.firstOrNull { it.name == name }
            } ?: ViewMode.GRID,
            sortOrder = prefs[Keys.SORT_ORDER]?.let { name ->
                SortOrder.entries.firstOrNull { it.name == name }
            } ?: SortOrder.NEWEST,
            saveEditsAsCopy = prefs[Keys.SAVE_EDITS_AS_COPY] ?: true,
            highQualityExport = prefs[Keys.HIGH_QUALITY_EXPORT] ?: true
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.snapStreamDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.snapStreamDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.snapStreamDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun setDeviceLabel(label: String) {
        context.snapStreamDataStore.edit {
            it[Keys.DEVICE_LABEL] = label.trim().ifEmpty { "Local Storage" }
        }
    }

    suspend fun setSaveEditsAsCopy(enabled: Boolean) {
        context.snapStreamDataStore.edit { it[Keys.SAVE_EDITS_AS_COPY] = enabled }
    }

    suspend fun setHighQualityExport(enabled: Boolean) {
        context.snapStreamDataStore.edit { it[Keys.HIGH_QUALITY_EXPORT] = enabled }
    }
}
