package com.dicren.autospot.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "SettingsRepository"

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
private val BACKGROUND_LOCATION_RATIONALE_SHOWN = booleanPreferencesKey("background_location_rationale_shown")

interface SettingsRepository {
    val isOnboardingCompleted: Flow<Boolean>

    /** True once the "allow location all the time" explanation has been shown. */
    val isBackgroundLocationRationaleShown: Flow<Boolean>

    suspend fun setOnboardingCompleted()

    suspend fun setBackgroundLocationRationaleShown()
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private val preferences: Flow<Preferences> =
        dataStore.data
            // A corrupt preferences file must not crash the app on every launch.
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }

    override val isOnboardingCompleted: Flow<Boolean> =
        preferences.map { it[ONBOARDING_COMPLETED] ?: false }

    override val isBackgroundLocationRationaleShown: Flow<Boolean> =
        preferences.map { it[BACKGROUND_LOCATION_RATIONALE_SHOWN] ?: false }

    override suspend fun setOnboardingCompleted() {
        // A failed write must not crash the app; the caller simply sees the old value.
        write { it[ONBOARDING_COMPLETED] = true }
    }

    override suspend fun setBackgroundLocationRationaleShown() {
        write { it[BACKGROUND_LOCATION_RATIONALE_SHOWN] = true }
    }

    private suspend fun write(change: suspend (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (e: IOException) {
            // Best effort: storage failures are rare and retrying on the next launch is enough.
            Log.w(TAG, "Could not write settings", e)
        }
    }
}
