package io.github.xiaowu20245.nekopaint.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenMappingTest {

    @Test
    fun brandLightPrimaryAndPageTitleMatchDesignTokens() {
        assertEquals(Color(0xFF4A63E7), NekoPrimaryLight)
        assertEquals(20.sp, NekoTypography.titleLarge.fontSize)
        assertEquals(28.sp, NekoTypography.titleLarge.lineHeight)
    }

    @Test
    fun shapeTokensExposeDesignRadii() {
        assertEquals(9.dp, NekoShape.smallRadius)
        assertEquals(18.dp, NekoShape.mediumRadius)
        assertEquals(20.dp, NekoShape.largeRadius)
    }
}
