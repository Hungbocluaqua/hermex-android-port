package com.uzairansar.hermex.visual

import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class VisualFixtureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<VisualFixtureActivity>()

    @Test
    fun onboardingWelcomeCapturesWithStableSemanticBounds() {
        composeRule.onNodeWithTag(VisualFixtureContract.OnboardingCatalogTag)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        val rootNode = composeRule.onNodeWithTag(VisualFixtureContract.OnboardingRootTag)
        val rootBounds = rootNode.fetchSemanticsNode().boundsInRoot
        val heroBounds = composeRule.onNodeWithTag(VisualFixtureContract.OnboardingHeroTag)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertValidBounds(rootBounds, "onboarding root")
        assertContained(heroBounds, rootBounds, "onboarding hero")
        val image = rootNode.captureToImage()
        assertNearBlack(pixelAtFraction(image, 0.04f, 0.22f), "onboarding backdrop")
        saveScreenshot("onboarding-welcome.png", image)
    }

    @Test
    fun frostedSurfaceCapturesWithStableSemanticBounds() {
        composeRule.onNodeWithTag(VisualFixtureContract.FrostedCatalogTag)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        val rootNode = composeRule.onNodeWithTag(VisualFixtureContract.FrostedRootTag)
        val rootBounds = rootNode.fetchSemanticsNode().boundsInRoot
        val headerBounds = composeRule.onNodeWithTag(VisualFixtureContract.FrostedHeaderTag)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val composerBounds = composeRule.onNodeWithTag(VisualFixtureContract.FrostedComposerTag)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertValidBounds(rootBounds, "frosted root")
        assertContained(headerBounds, rootBounds, "frosted header")
        assertContained(composerBounds, rootBounds, "frosted composer")
        assertTrue("Composer should be below header", composerBounds.top > headerBounds.bottom)
        val image = rootNode.captureToImage()
        assertNearBlack(pixelAtFraction(image, 0.95f, 0.72f), "frosted backdrop")
        assertNeutral(
            pixelAtCoordinate(image, headerBounds.center.x - rootBounds.left, headerBounds.center.y - rootBounds.top),
            "frosted header",
        )
        saveScreenshot("frosted-surface.png", image)
    }

    private fun assertValidBounds(bounds: Rect, label: String) {
        assertTrue("$label width should be positive", bounds.width > 0f)
        assertTrue("$label height should be positive", bounds.height > 0f)
    }

    private fun assertContained(child: Rect, parent: Rect, label: String) {
        assertValidBounds(child, label)
        assertTrue("$label should start inside its root", child.left >= parent.left && child.top >= parent.top)
        assertTrue("$label should end inside its root", child.right <= parent.right && child.bottom <= parent.bottom)
    }

    private fun pixelAtFraction(image: ImageBitmap, xFraction: Float, yFraction: Float): Int = pixelAtCoordinate(
        image = image,
        x = image.width * xFraction,
        y = image.height * yFraction,
    )

    private fun pixelAtCoordinate(image: ImageBitmap, x: Float, y: Float): Int {
        val bitmap = image.asAndroidBitmap()
        return bitmap.getPixel(
            x.roundToInt().coerceIn(0, bitmap.width - 1),
            y.roundToInt().coerceIn(0, bitmap.height - 1),
        )
    }

    private fun assertNearBlack(pixel: Int, label: String) {
        val maxChannel = maxOf(AndroidColor.red(pixel), AndroidColor.green(pixel), AndroidColor.blue(pixel))
        assertTrue("$label should match the iOS black background", maxChannel <= 6)
    }

    private fun assertNeutral(pixel: Int, label: String) {
        val channels = listOf(AndroidColor.red(pixel), AndroidColor.green(pixel), AndroidColor.blue(pixel))
        assertTrue("$label should not carry a blue color cast", channels.max() - channels.min() <= 12)
    }

    private fun saveScreenshot(fileName: String, image: ImageBitmap) {
        assertTrue("Captured image width should be positive", image.width > 0)
        assertTrue("Captured image height should be positive", image.height > 0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("visual-fixtures"))
        val output = File(directory, fileName)
        output.outputStream().use { stream ->
            check(image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Expected screenshot at ${output.absolutePath}", output.isFile && output.length() > 0L)
    }
}
