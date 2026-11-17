package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.ui.geometry.Offset

/** 归一化坐标的蒙版笔迹；UI 与后台导出共用，不持有 Bitmap。 */
data class MaskStroke(
    val points: List<Offset>,
    val widthFraction: Float,
    val eraser: Boolean,
)
