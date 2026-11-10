package io.github.hiramrg.delphis.core

/** Delphis-only preset option: which set of touch buttons sits beside the bottom-screen trackpad. */
const val TOUCH_BUTTONS_OPTION = "Delphis/TouchButtons"

/** A touch button beside the trackpad. While it's held, every Dolphin input in [keys] is pressed. */
data class TouchButton(val icon: String, val label: String, val sub: String, val keys: List<String>)

/**
 * Touch buttons reach Dolphin through Delphis' DSU pad (platform/DsuServer): button i presses the
 * pad's face button [INPUTS][i], and trackpad profiles bind that input next to the Thor's own.
 * Kept as a named set in the preset options (not in the preset itself) so edited copies keep them.
 */
object TouchButtons {
    /** Dolphin's names for the DSU pad's face buttons, in the order the buttons are listed. */
    val INPUTS = listOf("Square", "Cross", "Circle", "Triangle")
    const val MAX = 4

    const val RED_STEEL_SET = "red-steel"

    private val sets = mapOf(
        RED_STEEL_SET to listOf(
            TouchButton("🔍", "Zoom", "hold", listOf("Buttons/A", "Swing/Forward")),
            TouchButton("↻", "Reload", "Nunchuk flick", listOf("Nunchuk/Shake/X", "Nunchuk/Shake/Y", "Nunchuk/Shake/Z")),
            TouchButton("💣", "Grenade", "hold · R2 throws", listOf("D-Pad/Down")),
            TouchButton("⤴", "Swing up", "Nunchuk", listOf("Nunchuk/Swing/Up")),
        ),
    )

    fun of(preset: Preset): List<TouchButton> = sets[preset.options[TOUCH_BUTTONS_OPTION]].orEmpty().take(MAX)
}
