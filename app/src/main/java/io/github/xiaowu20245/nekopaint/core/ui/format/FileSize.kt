package io.github.xiaowu20245.nekopaint.core.ui.format

import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kib = bytes / 1024.0
    if (kib < 1.0) return "$bytes B"
    val mib = kib / 1024.0
    if (mib < 1.0) return String.format(Locale.getDefault(), "%.1f KiB", kib)
    val gib = mib / 1024.0
    return if (gib < 1.0) {
        String.format(Locale.getDefault(), "%.1f MiB", mib)
    } else {
        String.format(Locale.getDefault(), "%.2f GiB", gib)
    }
}
