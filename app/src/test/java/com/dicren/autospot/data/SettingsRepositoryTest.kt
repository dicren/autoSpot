package com.dicren.autospot.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
    private lateinit var directory: File
    private lateinit var file: File
    private var dataStore: DataStore<Preferences>? = null

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("settings-test").toFile()
        file = File(directory, "settings.preferences_pb")
    }

    @After
    fun tearDown() {
        dataStore?.let { scope.cancel() }
        directory.deleteRecursively()
    }

    private fun repository(): DataStoreSettingsRepository {
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        dataStore = store
        return DataStoreSettingsRepository(store)
    }

    @Test
    fun `onboarding starts as not completed`() =
        runTest {
            assertFalse(repository().isOnboardingCompleted.first())
        }

    @Test
    fun `onboarding completion is persisted`() =
        runTest {
            val repository = repository()

            repository.setOnboardingCompleted()

            assertTrue(repository.isOnboardingCompleted.first())
        }

    @Test
    fun `the background location rationale flag is persisted`() =
        runTest {
            val repository = repository()

            assertFalse(repository.isBackgroundLocationRationaleShown.first())

            repository.setBackgroundLocationRationaleShown()

            assertTrue(repository.isBackgroundLocationRationaleShown.first())
        }

    @Test
    fun `a corrupt preferences file does not crash the app`() =
        runTest {
            file.parentFile?.mkdirs()
            file.writeText("this is not a preferences file")

            assertFalse(repository().isOnboardingCompleted.first())
        }
}
