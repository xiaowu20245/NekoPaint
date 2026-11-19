package io.github.xiaowu20245.nekopaint.feature.history

/**
 * 历史 temp 预览可见性。
 *
 * TEMP 筛选必须强制显示 temp 图，否则会出现“有任务卡片但没有图片”的空卡；
 * 强制值只影响当前视图，不写回全局设置。
 */
object HistoryVisibilityPolicy {

    fun effectiveShowTemp(filter: HistoryFilter, globalShowTemp: Boolean): Boolean =
        filter == HistoryFilter.TEMP || globalShowTemp

    fun isTempSwitchEnabled(filter: HistoryFilter): Boolean =
        filter != HistoryFilter.TEMP
}
