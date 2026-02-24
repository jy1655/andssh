package com.opencode.sshterminal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_settings.preferences_pb") },
        )

    @Test
    fun `default language tag is empty string`() =
        runTest(testDispatcher) {
            val repo = SettingsRepository(createDataStore())
            assertEquals(SettingsRepository.DEFAULT_LANGUAGE_TAG, repo.languageTag.first())
        }

    @Test
    fun `default theme preset is green`() =
        runTest(testDispatcher) {
            val repo = SettingsRepository(createDataStore())
            assertEquals(SettingsRepository.DEFAULT_THEME_PRESET, repo.themePresetId.first())
        }

    @Test
    fun `setLanguageTag persists value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setLanguageTag("ko")
            assertEquals("ko", repo.languageTag.first())
        }

    @Test
    fun `setThemePreset persists value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setThemePreset("ocean")
            assertEquals("ocean", repo.themePresetId.first())
        }

    @Test
    fun `overwriting language tag replaces previous value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setLanguageTag("ko")
            repo.setLanguageTag("en")
            assertEquals("en", repo.languageTag.first())
        }

    @Test
    fun `overwriting theme preset replaces previous value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setThemePreset("sunset")
            repo.setThemePreset("purple")
            assertEquals("purple", repo.themePresetId.first())
        }

    @Test
    fun `language and theme are independent`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setLanguageTag("ko")
            repo.setThemePreset("ocean")
            assertEquals("ko", repo.languageTag.first())
            assertEquals("ocean", repo.themePresetId.first())
        }

    @Test
    fun `default ssh keepalive interval is configured value`() =
        runTest(testDispatcher) {
            val repo = SettingsRepository(createDataStore())
            assertEquals(
                SettingsRepository.DEFAULT_SSH_KEEPALIVE_INTERVAL,
                repo.sshKeepaliveIntervalSeconds.first(),
            )
        }

    @Test
    fun `setSshKeepaliveInterval persists value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setSshKeepaliveInterval(30)
            assertEquals(30, repo.sshKeepaliveIntervalSeconds.first())
        }

    @Test
    fun `default screenshot protection is disabled`() =
        runTest(testDispatcher) {
            val repo = SettingsRepository(createDataStore())
            assertEquals(
                SettingsRepository.DEFAULT_SCREENSHOT_PROTECTION_ENABLED,
                repo.screenshotProtectionEnabled.first(),
            )
        }

    @Test
    fun `setScreenshotProtectionEnabled persists value`() =
        runTest(testDispatcher) {
            val ds = createDataStore()
            val repo = SettingsRepository(ds)
            repo.setScreenshotProtectionEnabled(true)
            assertEquals(true, repo.screenshotProtectionEnabled.first())
        }
}
