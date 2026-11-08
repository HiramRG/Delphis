package io.github.hiramrg.delphis.core

/**
 * How the in-game bottom screen behaves while nobody touches it.
 *  - [Mode.ON]: never dims.
 *  - [Mode.DIM]: after the delay it fades to the chosen brightness.
 *  - [Mode.OFF]: after the delay it fades to black (minimum brightness), so it's effectively off.
 * A touch on a dimmed or black screen only wakes it, and every touch restarts the full delay.
 */
object BottomScreen {
    enum class Mode(val id: String) { ON("on"), DIM("dim"), OFF("off") }

    enum class Look { BRIGHT, DIM, BLACK }

    /** The "Dim after" choices in Settings, in seconds. */
    val DELAYS = listOf(5, 10, 30, 60)
    const val DEFAULT_DELAY = 10

    /** How long the change between bright, dim and black takes. */
    const val FADE_MS = 450

    fun modeOf(id: String?): Mode = Mode.entries.firstOrNull { it.id == id } ?: Mode.DIM

    /** Settings stores one of [DELAYS]; anything else (e.g. the old 0-15 s slider) falls back to the default. */
    fun delayOf(seconds: Int): Int = if (seconds in DELAYS) seconds else DEFAULT_DELAY

    fun delayLabel(seconds: Int) = if (seconds >= 60) "${seconds / 60} min" else "$seconds s"

    /**
     * What the screen shows. [idle] = the delay ran out since the last touch, [autoDim] = this
     * session's Auto-dim switch, [screenOff] = "Turn screen off" was pressed, [keepBright] = a menu,
     * dialog or the editor is open.
     */
    fun look(mode: Mode, autoDim: Boolean, idle: Boolean, screenOff: Boolean, keepBright: Boolean): Look = when {
        keepBright -> Look.BRIGHT
        screenOff -> Look.BLACK
        !autoDim || !idle -> Look.BRIGHT
        mode == Mode.OFF -> Look.BLACK
        else -> Look.DIM
    }
}
