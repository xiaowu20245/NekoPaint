package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.ui.geometry.Offset

data class MaskHistory(
    val strokes: List<MaskStroke>,
    val redo: List<MaskStroke>,
)

/**
 * 蒙版编辑的纯状态策略：撤销/重做栈、缩放边界、平移边界、反选和退出确认。
 */
object MaskEditorPolicy {

    const val MAX_REDO = 20
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    fun pushStroke(strokes: List<MaskStroke>, stroke: MaskStroke): MaskHistory =
        MaskHistory(strokes = strokes + stroke, redo = emptyList())

    fun undo(strokes: List<MaskStroke>, redo: List<MaskStroke>): MaskHistory {
        if (strokes.isEmpty()) return MaskHistory(strokes, redo)
        val removed = strokes.last()
        val nextRedo = (redo + removed).takeLast(MAX_REDO)
        return MaskHistory(strokes = strokes.dropLast(1), redo = nextRedo)
    }

    fun redo(strokes: List<MaskStroke>, redo: List<MaskStroke>): MaskHistory {
        if (redo.isEmpty()) return MaskHistory(strokes, redo)
        return MaskHistory(
            strokes = strokes + redo.last(),
            redo = redo.dropLast(1),
        )
    }

    fun clear(): MaskHistory = MaskHistory(emptyList(), emptyList())

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * 以视口中心为基准限制平移；缩放到 1x 时强制回到中心。
     */
    fun clampPan(
        pan: Offset,
        viewportWidth: Float,
        viewportHeight: Float,
        baseWidth: Float,
        baseHeight: Float,
        zoom: Float,
    ): Offset {
        val safeZoom = clampZoom(zoom)
        val maxX = ((baseWidth * safeZoom - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((baseHeight * safeZoom - viewportHeight) / 2f).coerceAtLeast(0f)
        return Offset(
            x = pan.x.coerceIn(-maxX, maxX),
            y = pan.y.coerceIn(-maxY, maxY),
        )
    }

    fun toggleInverted(inverted: Boolean): Boolean = !inverted

    fun requiresExitConfirmation(strokes: List<MaskStroke>, inverted: Boolean): Boolean =
        strokes.isNotEmpty() || inverted
}
