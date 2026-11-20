package io.github.xiaowu20245.nekopaint.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NekoIconButtonTouchTargetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultIconButtonMeets48DpTouchTarget() {
        compose.setContent {
            NekoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "删除",
                onClick = {},
            )
        }

        val bounds = compose.onNodeWithContentDescription("删除").getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left) >= 48.dp)
        assertTrue((bounds.bottom - bounds.top) >= 48.dp)
    }

    @Test
    fun auditedSmallIconButtonIsClickableAndKeeps48DpTarget() {
        var clicked = false
        compose.setContent {
            NekoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "审计删除",
                onClick = { clicked = true },
            )
        }

        compose.onNodeWithContentDescription("审计删除")
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(clicked) }
        val bounds = compose.onNodeWithContentDescription("审计删除")
            .getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left) >= 48.dp)
        assertTrue((bounds.bottom - bounds.top) >= 48.dp)
    }
}
