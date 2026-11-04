package io.github.xiaowu20245.nekopaint.feature.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.getDefault(), "%.1f GiB", gib)
    } else {
        val mib = bytes / (1024.0 * 1024.0)
        String.format(Locale.getDefault(), "%.0f MiB", mib)
    }
}

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
