package io.github.xiaowu20245.nekopaint.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 轻量自绘图标，避免引入完整图标扩展库。
 *
 * 全部使用 24x24 viewport，可由 Icon 的 tint 统一染色。
 */
object NekoIcons {

    val ServerRack: ImageVector = ImageVector.Builder(
        name = "ServerRack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 10f)
            lineTo(4f, 10f)
            close()
            moveTo(4f, 14f)
            lineTo(20f, 14f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(8f, 7f)
            lineTo(8.01f, 7f)
            moveTo(8f, 17f)
            lineTo(8.01f, 17f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 6f)
            lineTo(17f, 6f)
            lineTo(17f, 8f)
            lineTo(14f, 8f)
            close()
            moveTo(14f, 16f)
            lineTo(17f, 16f)
            lineTo(17f, 18f)
            lineTo(14f, 18f)
            close()
        }
    }.build()

    val Chip: ImageVector = strokeIcon("Chip") {
        moveTo(6f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(6f, 18f)
        close()
        moveTo(9f, 9f)
        lineTo(15f, 9f)
        lineTo(15f, 15f)
        lineTo(9f, 15f)
        close()
        moveTo(3f, 9f)
        lineTo(6f, 9f)
        moveTo(3f, 12f)
        lineTo(6f, 12f)
        moveTo(3f, 15f)
        lineTo(6f, 15f)
        moveTo(18f, 9f)
        lineTo(21f, 9f)
        moveTo(18f, 12f)
        lineTo(21f, 12f)
        moveTo(18f, 15f)
        lineTo(21f, 15f)
        moveTo(9f, 3f)
        lineTo(9f, 6f)
        moveTo(12f, 3f)
        lineTo(12f, 6f)
        moveTo(15f, 3f)
        lineTo(15f, 6f)
        moveTo(9f, 18f)
        lineTo(9f, 21f)
        moveTo(12f, 18f)
        lineTo(12f, 21f)
        moveTo(15f, 18f)
        lineTo(15f, 21f)
    }

    val MemoryStick: ImageVector = strokeIcon("MemoryStick") {
        moveTo(3f, 7f)
        lineTo(21f, 7f)
        lineTo(21f, 17f)
        lineTo(3f, 17f)
        close()
        moveTo(10f, 17f)
        lineTo(10f, 19f)
        lineTo(14f, 19f)
        lineTo(14f, 17f)
        moveTo(6f, 4f)
        lineTo(6f, 7f)
        moveTo(9f, 4f)
        lineTo(9f, 7f)
        moveTo(12f, 4f)
        lineTo(12f, 7f)
        moveTo(15f, 4f)
        lineTo(15f, 7f)
        moveTo(18f, 4f)
        lineTo(18f, 7f)
    }

    val Stopwatch: ImageVector = strokeIcon("Stopwatch") {
        moveTo(9f, 3f)
        lineTo(15f, 3f)
        moveTo(12f, 3f)
        lineTo(12f, 6f)
        moveTo(12f, 6f)
        curveTo(15.87f, 6f, 19f, 9.13f, 19f, 13f)
        curveTo(19f, 16.87f, 15.87f, 20f, 12f, 20f)
        curveTo(8.13f, 20f, 5f, 16.87f, 5f, 13f)
        curveTo(5f, 9.13f, 8.13f, 6f, 12f, 6f)
        close()
        moveTo(12f, 13f)
        lineTo(15f, 10f)
    }

    val Terminal: ImageVector = strokeIcon("Terminal") {
        moveTo(3f, 4f)
        lineTo(21f, 4f)
        lineTo(21f, 20f)
        lineTo(3f, 20f)
        close()
        moveTo(7f, 9f)
        lineTo(10f, 12f)
        lineTo(7f, 15f)
        moveTo(13f, 15f)
        lineTo(17f, 15f)
    }

    val Display: ImageVector = strokeIcon("Display") {
        moveTo(3f, 4f)
        lineTo(21f, 4f)
        lineTo(21f, 17f)
        lineTo(3f, 17f)
        close()
        moveTo(12f, 17f)
        lineTo(12f, 21f)
        moveTo(8f, 21f)
        lineTo(16f, 21f)
    }

    val Code: ImageVector = strokeIcon("Code") {
        moveTo(8f, 7f)
        lineTo(4f, 12f)
        lineTo(8f, 17f)
        moveTo(16f, 7f)
        lineTo(20f, 12f)
        lineTo(16f, 17f)
        moveTo(14f, 5f)
        lineTo(10f, 19f)
    }
}

private fun strokeIcon(
    name: String,
    block: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        block()
    }
}.build()
