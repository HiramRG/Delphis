package io.github.hiramrg.delphis.platform

import androidx.compose.runtime.mutableStateOf

/**
 * The game started but its autosave couldn't be loaded (for example right after Delphis was updated, while
 * Android was still restarting the close-games helper). The in-game menu then offers to load it.
 */
object ResumeNotice {
    class Failed(val gameId: String, val savedAt: Long)

    val failed = mutableStateOf<Failed?>(null)

    /** "today, 15:49" / "Tue, 15:49". */
    fun whenText(ctx: android.content.Context, t: Long): String {
        val time = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(t))
        return if (android.text.format.DateUtils.isToday(t)) "today, $time"
        else java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(t)) + ", $time"
    }
}
