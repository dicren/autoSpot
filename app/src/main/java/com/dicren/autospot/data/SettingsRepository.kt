package com.dicren.autospot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

interface SettingsRepository {
    val isOnboardingCompleted: Flow<Boolean>

    suspend fun setOnboardingCompleted()
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val isOnboardingCompleted: Flow<Boolean> =
        dataStore.data
            // A corrupt preferences file must not crash the app on every launch.
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map { preferences -> preferences[ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[ONBOARDING_COMPLETED] = true }
    }
}
