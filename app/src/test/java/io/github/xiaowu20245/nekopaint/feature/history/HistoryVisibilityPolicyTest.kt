package io.github.xiaowu20245.nekopaint.feature.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryVisibilityPolicyTest {

    @Test
    fun allFilterUsesGlobalSwitchOff() {
        assertFalse(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.ALL, false))
    }

    @Test
    fun allFilterUsesGlobalSwitchOn() {
        assertTrue(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.ALL, true))
    }

    @Test
    fun outputsFilterUsesGlobalSwitchOff() {
        assertFalse(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.OUTPUTS, false))
    }

    @Test
    fun outputsFilterUsesGlobalSwitchOn() {
        assertTrue(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.OUTPUTS, true))
    }

    @Test
    fun tempFilterForcesTempImagesEvenWhenGlobalOff() {
        assertTrue(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.TEMP, false))
    }

    @Test
    fun tempFilterDisablesSwitchAndKeepsForceOn() {
        assertFalse(HistoryVisibilityPolicy.isTempSwitchEnabled(HistoryFilter.TEMP))
        assertTrue(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.TEMP, true))
        assertTrue(HistoryVisibilityPolicy.isTempSwitchEnabled(HistoryFilter.ALL))
        assertTrue(HistoryVisibilityPolicy.isTempSwitchEnabled(HistoryFilter.OUTPUTS))
    }
}
