package br.com.voleipro.data

import br.com.voleipro.domain.Appearance
import br.com.voleipro.domain.ThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ThemePreferencesTest {
    @Test fun paletteDefaultsAndPersistsIndependentlyOfOtherPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = DataStorePreferences(context)
        assertEquals(ThemePalette.SHIELD, settings.preferences.first().palette)

        settings.selectPelada(3L)
        settings.update { it.copy(palette = ThemePalette.IMPROVISADAS) }
        settings.update { it.copy(appearance = Appearance.DARK, sound = true) }

        val saved = DataStorePreferences(context).preferences.first()
        assertEquals(ThemePalette.IMPROVISADAS, saved.palette)
        assertEquals(Appearance.DARK, saved.appearance)
        assertEquals(3L, saved.activeLeagueId)
        assertTrue(saved.sound)

        for (palette in listOf(ThemePalette.SHIELD, ThemePalette.BENACON)) {
            settings.update { it.copy(palette = palette) }
            assertEquals(palette, settings.preferences.first().palette)
        }
    }
}
