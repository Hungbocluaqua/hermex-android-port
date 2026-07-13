package com.uzairansar.hermex.ui.settings

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconManagerInstrumentedTest {
    @Test
    fun explicitChoiceChangesTheLauncherActivityAlias() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = AppIconManager(context)

        try {
            assertTrue(manager.setChoice(AppIconChoice.Dark).isSuccess)

            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
            val launcherActivities = context.packageManager.queryIntentActivities(
                launcherIntent,
                0,
            )

            assertEquals(
                listOf(AppIconChoice.Dark.componentName(context.packageName).className),
                launcherActivities.map { it.activityInfo.name },
            )
        } finally {
            assertTrue(manager.setChoice(AppIconChoice.Default).isSuccess)
        }
    }
}
