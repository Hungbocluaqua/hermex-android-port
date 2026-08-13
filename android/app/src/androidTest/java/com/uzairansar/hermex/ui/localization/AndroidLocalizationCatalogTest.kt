package com.uzairansar.hermex.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidLocalizationCatalogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exposesTheCompleteIosCatalogAndResolvesTranslatedResources() {
        assertTrue(AndroidLocalizationCatalog.sourceEntryCount > 0)
        assertTrue(AndroidLocalizationCatalog.entryCount >= AndroidLocalizationCatalog.sourceEntryCount)
        val settingsId = AndroidLocalizationCatalog.resourceId("Settings")
        val newSessionId = AndroidLocalizationCatalog.resourceId("Start New Session")
        assertNotNull(settingsId)
        assertNotNull(newSessionId)

        val french = context.forLocale(Locale.FRENCH)
        assertEquals("Réglages", french.getString(requireNotNull(settingsId)))
        assertEquals("Nouvelle session", french.getString(requireNotNull(newSessionId)))

        val arabic = context.forLocale(Locale.forLanguageTag("ar"))
        assertEquals("الإعدادات", arabic.getString(requireNotNull(settingsId)))
        assertNotEquals("Start New Session", arabic.getString(requireNotNull(newSessionId)))
    }

    @Test
    fun composeHelperRendersTheConfiguredLocaleInsteadOfEnglish() {
        val french = context.forLocale(Locale.FRENCH)
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides french) {
                Text(localizedString("Settings"))
            }
        }

        composeRule.onNodeWithText("Réglages").assertIsDisplayed()
    }

    @Test
    fun preservesIosPluralVariationsForCardCounts() {
        val cardsId = requireNotNull(AndroidLocalizationCatalog.pluralResourceId("%lld Cards"))
        val english = context.forLocale(Locale.ENGLISH)
        assertEquals("1 Card", english.resources.getQuantityString(cardsId, 1, 1))
        assertEquals("2 Cards", english.resources.getQuantityString(cardsId, 2, 2))

        val french = context.forLocale(Locale.FRENCH)
        assertEquals("1 Carte", french.resources.getQuantityString(cardsId, 1, 1))
        assertEquals("2 Cartes", french.resources.getQuantityString(cardsId, 2, 2))
    }

    private fun Context.forLocale(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration)
    }
}
