package io.github.xiaowu20245.nekopaint.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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

    val Sparkles: ImageVector = strokeIcon("Sparkles") {
        moveTo(12f, 3f)
        lineTo(14.1f, 8.9f)
        lineTo(20f, 11f)
        lineTo(14.1f, 13.1f)
        lineTo(12f, 19f)
        lineTo(9.9f, 13.1f)
        lineTo(4f, 11f)
        lineTo(9.9f, 8.9f)
        close()
        moveTo(19f, 17f)
        lineTo(19f, 21f)
        moveTo(17f, 19f)
        lineTo(21f, 19f)
    }

    val SparklesFilled: ImageVector = ImageVector.Builder(
        name = "SparklesFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 2.5f)
            lineTo(13.2f, 8.8f)
            lineTo(19.5f, 11f)
            lineTo(13.2f, 13.2f)
            lineTo(11f, 19.5f)
            lineTo(8.8f, 13.2f)
            lineTo(2.5f, 11f)
            lineTo(8.8f, 8.8f)
            close()
            moveTo(18.5f, 14.5f)
            lineTo(19.3f, 16.7f)
            lineTo(21.5f, 17.5f)
            lineTo(19.3f, 18.3f)
            lineTo(18.5f, 20.5f)
            lineTo(17.7f, 18.3f)
            lineTo(15.5f, 17.5f)
            lineTo(17.7f, 16.7f)
            close()
        }
    }.build()

    val ImageFilled: ImageVector = ImageVector.Builder(
        name = "ImageFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(6f, 6f)
            lineTo(18f, 6f)
            lineTo(18f, 18f)
            lineTo(6f, 18f)
            close()
            moveTo(8f, 16f)
            lineTo(11f, 12f)
            lineTo(13f, 14.5f)
            lineTo(15f, 11.5f)
            lineTo(17f, 16f)
            close()
            moveTo(9f, 8.5f)
            lineTo(10.5f, 10f)
            lineTo(9f, 11.5f)
            lineTo(7.5f, 10f)
            close()
        }
    }.build()

    val SendFilled: ImageVector = ImageVector.Builder(
        name = "SendFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2.5f, 4f)
            lineTo(21.5f, 12f)
            lineTo(2.5f, 20f)
            lineTo(2.5f, 13.5f)
            lineTo(14f, 12f)
            lineTo(2.5f, 10.5f)
            close()
        }
    }.build()

    val ImageFrame: ImageVector = strokeIcon("ImageFrame") {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(8f, 9f)
        lineTo(9.5f, 9f)
        lineTo(9.5f, 10.5f)
        lineTo(8f, 10.5f)
        close()
        moveTo(20f, 15f)
        lineTo(15f, 11f)
        lineTo(6f, 20f)
    }

    val BrushMask: ImageVector = strokeIcon("BrushMask") {
        moveTo(14f, 4f)
        lineTo(20f, 10f)
        lineTo(10f, 20f)
        lineTo(4f, 20f)
        lineTo(4f, 14f)
        close()
        moveTo(11f, 7f)
        lineTo(17f, 13f)
    }

    val TargetCrosshair: ImageVector = strokeIcon("TargetCrosshair") {
        moveTo(12f, 5f)
        curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
        curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
        curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
        curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
        close()
        moveTo(12f, 9.5f)
        curveTo(13.38f, 9.5f, 14.5f, 10.62f, 14.5f, 12f)
        curveTo(14.5f, 13.38f, 13.38f, 14.5f, 12f, 14.5f)
        curveTo(10.62f, 14.5f, 9.5f, 13.38f, 9.5f, 12f)
        curveTo(9.5f, 10.62f, 10.62f, 9.5f, 12f, 9.5f)
        close()
        moveTo(12f, 2f)
        lineTo(12f, 5f)
        moveTo(12f, 19f)
        lineTo(12f, 22f)
        moveTo(2f, 12f)
        lineTo(5f, 12f)
        moveTo(19f, 12f)
        lineTo(22f, 12f)
    }

    val WorkflowNodes: ImageVector = strokeIcon("WorkflowNodes") {
        moveTo(4f, 4f)
        lineTo(9f, 4f)
        lineTo(9f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(15f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 9f)
        lineTo(15f, 9f)
        close()
        moveTo(9.5f, 15f)
        lineTo(14.5f, 15f)
        lineTo(14.5f, 20f)
        lineTo(9.5f, 20f)
        close()
        moveTo(7.5f, 9f)
        lineTo(11f, 15f)
        moveTo(16.5f, 9f)
        lineTo(13f, 15f)
    }

    val Sliders: ImageVector = strokeIcon("Sliders") {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 18f)
        lineTo(20f, 18f)
        moveTo(9f, 4f)
        lineTo(9f, 8f)
        moveTo(15f, 10f)
        lineTo(15f, 14f)
        moveTo(11f, 16f)
        lineTo(11f, 20f)
    }

    val PlayFilled: ImageVector = ImageVector.Builder(
        name = "PlayFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.5f, 5f)
            lineTo(18.5f, 12f)
            lineTo(7.5f, 19f)
            close()
        }
    }.build()

    val Dice: ImageVector = strokeIcon("Dice") {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(8.5f, 8.5f)
        lineTo(8.55f, 8.5f)
        moveTo(15.5f, 8.5f)
        lineTo(15.55f, 8.5f)
        moveTo(12f, 12f)
        lineTo(12.05f, 12f)
        moveTo(8.5f, 15.5f)
        lineTo(8.55f, 15.5f)
        moveTo(15.5f, 15.5f)
        lineTo(15.55f, 15.5f)
    }

    val Bookmark: ImageVector = strokeIcon("Bookmark") {
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 20f)
        lineTo(12f, 16f)
        lineTo(6f, 20f)
        close()
    }

    val ChevronRight: ImageVector = strokeIcon("ChevronRight") {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }

    val StopSquare: ImageVector = ImageVector.Builder(
        name = "StopSquare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 17f)
            lineTo(7f, 17f)
            close()
        }
    }.build()

    val BrokenImage: ImageVector = strokeIcon("BrokenImage") {
        moveTo(4f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(9f, 4f)
        lineTo(13f, 9f)
        lineTo(10f, 13f)
        lineTo(14f, 17f)
        lineTo(11f, 20f)
    }

    val Timeline: ImageVector = strokeIcon("Timeline") {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        moveTo(4f, 12f)
        lineTo(16f, 12f)
        moveTo(4f, 18f)
        lineTo(12f, 18f)
        moveTo(19f, 12f)
        lineTo(19.01f, 12f)
        moveTo(15f, 18f)
        lineTo(15.01f, 18f)
    }

    val GridView: ImageVector = strokeIcon("GridView") {
        moveTo(4f, 4f)
        lineTo(10.5f, 4f)
        lineTo(10.5f, 10.5f)
        lineTo(4f, 10.5f)
        close()
        moveTo(13.5f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 10.5f)
        lineTo(13.5f, 10.5f)
        close()
        moveTo(4f, 13.5f)
        lineTo(10.5f, 13.5f)
        lineTo(10.5f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(13.5f, 13.5f)
        lineTo(20f, 13.5f)
        lineTo(20f, 20f)
        lineTo(13.5f, 20f)
        close()
    }

    val ViewCompact: ImageVector = strokeIcon("ViewCompact") {
        moveTo(4f, 4f)
        lineTo(8.5f, 4f)
        lineTo(8.5f, 8.5f)
        lineTo(4f, 8.5f)
        close()
        moveTo(9.75f, 4f)
        lineTo(14.25f, 4f)
        lineTo(14.25f, 8.5f)
        lineTo(9.75f, 8.5f)
        close()
        moveTo(15.5f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 8.5f)
        lineTo(15.5f, 8.5f)
        close()
        moveTo(4f, 9.75f)
        lineTo(8.5f, 9.75f)
        lineTo(8.5f, 14.25f)
        lineTo(4f, 14.25f)
        close()
        moveTo(9.75f, 9.75f)
        lineTo(14.25f, 9.75f)
        lineTo(14.25f, 14.25f)
        lineTo(9.75f, 14.25f)
        close()
        moveTo(15.5f, 9.75f)
        lineTo(20f, 9.75f)
        lineTo(20f, 14.25f)
        lineTo(15.5f, 14.25f)
        close()
        moveTo(4f, 15.5f)
        lineTo(8.5f, 15.5f)
        lineTo(8.5f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(9.75f, 15.5f)
        lineTo(14.25f, 15.5f)
        lineTo(14.25f, 20f)
        lineTo(9.75f, 20f)
        close()
        moveTo(15.5f, 15.5f)
        lineTo(20f, 15.5f)
        lineTo(20f, 20f)
        lineTo(15.5f, 20f)
        close()
    }

    val ContentCopy: ImageVector = strokeIcon("ContentCopy") {
        moveTo(9f, 9f)
        lineTo(19f, 9f)
        lineTo(19f, 20f)
        lineTo(9f, 20f)
        close()
        moveTo(5f, 15f)
        lineTo(5f, 5f)
        lineTo(15f, 5f)
    }

    val Download: ImageVector = strokeIcon("Download") {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(8f, 11f)
        lineTo(12f, 15f)
        lineTo(16f, 11f)
        moveTo(5f, 19f)
        lineTo(19f, 19f)
    }

    val Tune: ImageVector = strokeIcon("Tune") {
        moveTo(3f, 6f)
        lineTo(9f, 6f)
        moveTo(13f, 6f)
        lineTo(21f, 6f)
        moveTo(9f, 3f)
        lineTo(9f, 9f)
        moveTo(3f, 12f)
        lineTo(15f, 12f)
        moveTo(19f, 12f)
        lineTo(21f, 12f)
        moveTo(15f, 9f)
        lineTo(15f, 15f)
        moveTo(3f, 18f)
        lineTo(7f, 18f)
        moveTo(11f, 18f)
        lineTo(21f, 18f)
        moveTo(7f, 15f)
        lineTo(7f, 21f)
    }

    val Send: ImageVector = strokeIcon("Send") {
        moveTo(3f, 4f)
        lineTo(21f, 12f)
        lineTo(3f, 20f)
        lineTo(3f, 14f)
        lineTo(15f, 12f)
        lineTo(3f, 10f)
        close()
    }

    val SelectAll: ImageVector = strokeIcon("SelectAll") {
        moveTo(3f, 4.5f)
        lineTo(4.6f, 6.1f)
        lineTo(7.2f, 3.5f)
        moveTo(9.5f, 5f)
        lineTo(21f, 5f)
        moveTo(3f, 11.5f)
        lineTo(4.6f, 13.1f)
        lineTo(7.2f, 10.5f)
        moveTo(9.5f, 12f)
        lineTo(21f, 12f)
        moveTo(3f, 18.5f)
        lineTo(4.6f, 20.1f)
        lineTo(7.2f, 17.5f)
        moveTo(9.5f, 19f)
        lineTo(21f, 19f)
    }

    val Deselect: ImageVector = strokeIcon("Deselect") {
        moveTo(3.2f, 4.2f)
        lineTo(7.2f, 8.2f)
        moveTo(7.2f, 4.2f)
        lineTo(3.2f, 8.2f)
        moveTo(9.5f, 6f)
        lineTo(21f, 6f)
        moveTo(3.2f, 11.2f)
        lineTo(7.2f, 15.2f)
        moveTo(7.2f, 11.2f)
        lineTo(3.2f, 15.2f)
        moveTo(9.5f, 13f)
        lineTo(21f, 13f)
        moveTo(3.2f, 18.2f)
        lineTo(7.2f, 22.2f)
        moveTo(7.2f, 18.2f)
        lineTo(3.2f, 22.2f)
        moveTo(9.5f, 20f)
        lineTo(21f, 20f)
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
