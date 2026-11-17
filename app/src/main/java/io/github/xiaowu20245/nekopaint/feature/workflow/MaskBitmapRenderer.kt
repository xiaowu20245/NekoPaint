package io.github.xiaowu20245.nekopaint.feature.workflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get

/**
 * 把归一化笔迹渲染为黑白 RGB_565 蒙版。
 *
 * 调用方（ViewModel）负责在 finally 中 recycle 返回的 Bitmap。
 */
internal object MaskBitmapRenderer {

    fun render(
        target: MaskTargetSize,
        strokes: List<MaskStroke>,
    ): Bitmap {
        require(target.width > 0 && target.height > 0) { "蒙版尺寸无效" }
        val bitmap = createBitmap(target.width, target.height, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            // 蒙版必须是严格黑/白；关闭抗锯齿避免 RGB_565 中间灰量化误差。
            val strokePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val dotPaint = Paint().apply {
                style = Paint.Style.FILL
            }

            strokes.forEach { stroke ->
                val color = if (stroke.eraser) Color.BLACK else Color.WHITE
                val strokeWidth = (stroke.widthFraction * target.width).coerceAtLeast(1f)
                strokePaint.color = color
                strokePaint.strokeWidth = strokeWidth
                dotPaint.color = color
                if (stroke.points.size == 1) {
                    val point = stroke.points.first()
                    canvas.drawCircle(
                        point.x * target.width,
                        point.y * target.height,
                        strokeWidth / 2f,
                        dotPaint,
                    )
                } else if (stroke.points.size > 1) {
                    val path = Path()
                    val first = stroke.points.first()
                    path.moveTo(first.x * target.width, first.y * target.height)
                    stroke.points.drop(1).forEach { point ->
                        path.lineTo(point.x * target.width, point.y * target.height)
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }
            check(bitmap.config == Bitmap.Config.RGB_565) { "蒙版必须使用 RGB_565" }
            check(!bitmap.hasAlpha()) { "蒙版必须是黑白 RGB 语义" }
            assertGrayscale(bitmap)
            return bitmap
        } catch (throwable: Throwable) {
            bitmap.recycle()
            throw throwable
        }
    }

    /** 抽样确认像素为黑/白/灰阶，防止误用彩色或透明配置。 */
    private fun assertGrayscale(bitmap: Bitmap) {
        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 64)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap[x, y]
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                check(red == green && green == blue) { "蒙版必须是黑白 RGB 语义" }
                x += step
            }
            y += step
        }
    }
}
