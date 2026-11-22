package argus.ui.components

import java.text.SimpleDateFormat
import java.util.*

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hrs = s / 3600L
    val mins = (s % 3600L) / 60L
    val secs = s % 60L
    return when {
        hrs > 0 -> "%dh %02dm".format(Locale.US, hrs, mins)
        mins > 0 -> "%02d:%02d".format(Locale.US, mins, secs)
        else -> "${secs}s"
    }
}

fun formatDateTime(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatTimeOnly(millis: Long): String {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatFileTimestamp(millis: Long = System.currentTimeMillis()): String {
    val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    return fmt.format(Date(millis))
}
