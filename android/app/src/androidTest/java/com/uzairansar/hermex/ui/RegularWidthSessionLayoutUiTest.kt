package com.uzairansar.hermex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.uzairansar.hermex.ui.theme.HermexTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegularWidthSessionLayoutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun regularWidthContainerKeepsSidebarAndDetailSideBySide() {
        composeRule.setContent {
            HermexTheme {
                RegularWidthSessionContainer(
                    sidebar = {
                        Box(Modifier.fillMaxSize().testTag("regular_width_sidebar"))
                    },
                    detail = {
                        Box(Modifier.fillMaxSize().testTag("regular_width_detail"))
                    },
                )
            }
        }

        val sidebar = composeRule.onNodeWithTag("regular_width_sidebar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val detail = composeRule.onNodeWithTag("regular_width_detail").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Sidebar should have a stable positive width", sidebar.width > 0f)
        assertTrue("Detail should begin after the sidebar", detail.left >= sidebar.right)
        assertTrue("Detail should remain visible", detail.width > 0f)
    }
}
