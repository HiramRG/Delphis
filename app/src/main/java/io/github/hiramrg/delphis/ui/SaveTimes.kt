package io.github.hiramrg.delphis.ui

/** "Today 14:32", "Yesterday 09:05" or "3 Nov 2026 21:40". */
fun formatSaveTime(t: Long): String {
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(t))
    val day = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val today = java.util.Calendar.getInstance()
    fun same(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today $time"
    if (same(day, (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) })) return "Yesterday $time"
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(t)) + " " + time
}

fun formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024) "%.0f MB".format(bytes / (1024.0 * 1024)) else "${(bytes / 1024).coerceAtLeast(1)} KB"
