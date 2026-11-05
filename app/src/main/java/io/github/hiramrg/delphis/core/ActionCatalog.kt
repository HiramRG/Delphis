package io.github.hiramrg.delphis.core

/**
 * Everything a Thor control can be set to in the preset editor, per emulated controller.
 * Labels are plain-language; when a choice matches something the original preset already used,
 * the editor keeps that preset's friendlier, game-specific label (e.g. "Jump (A)").
 */
object ActionCatalog {
    data class Option(val label: String, val target: Target?) // null target = do nothing

    val NOTHING = Option("Nothing", null)

    private fun b(label: String, vararg keys: String) = Option(label, Target.Buttons(keys.toList()))
    private fun d(label: String, group: String, up: String = "Up", down: String = "Down") =
        Option(label, Target.Directional("$group/$up", "$group/$down", "$group/Left", "$group/Right"))

    private val wiiButtons = listOf(
        b("A button", "Buttons/A"), b("B button", "Buttons/B"),
        b("1 button", "Buttons/1"), b("2 button", "Buttons/2"),
        b("+ (Pause)", "Buttons/+"), b("− (Minus)", "Buttons/-"), b("HOME menu", "Buttons/Home"),
        b("Shake Wii Remote", "Shake/X", "Shake/Y", "Shake/Z"),
        b("D-pad Up", "D-Pad/Up"), b("D-pad Down", "D-Pad/Down"), b("D-pad Left", "D-Pad/Left"), b("D-pad Right", "D-Pad/Right"),
        b("Recenter pointer", "IR/Recenter"),
        b("Hold: pause gyro aim", GYRO_HOLD),
    )
    private val nunchukButtons = listOf(
        b("C button (Nunchuk)", "Nunchuk/Buttons/C"), b("Z button (Nunchuk)", "Nunchuk/Buttons/Z"),
        b("Shake Nunchuk", "Nunchuk/Shake/X", "Nunchuk/Shake/Y", "Nunchuk/Shake/Z"),
    )
    private val wiiDirectional = listOf(
        d("Pointer (aim)", "IR"), d("D-pad", "D-Pad"),
        d("Tilt Wii Remote", "Tilt", "Forward", "Backward"), d("Swing Wii Remote", "Swing"),
    )

    fun buttonOptions(c: EmulatedController): List<Option> = listOf(NOTHING) + when (c) {
        EmulatedController.WIIMOTE_NUNCHUK -> wiiButtons + nunchukButtons
        EmulatedController.WIIMOTE_SIDEWAYS, EmulatedController.WIIMOTE_WHEEL -> wiiButtons
        EmulatedController.CLASSIC -> listOf(
            b("a", "Classic/Buttons/A"), b("b", "Classic/Buttons/B"), b("x", "Classic/Buttons/X"), b("y", "Classic/Buttons/Y"),
            b("L", "Classic/Triggers/L"), b("R", "Classic/Triggers/R"), b("ZL", "Classic/Buttons/ZL"), b("ZR", "Classic/Buttons/ZR"),
            b("+ (Pause)", "Classic/Buttons/+"), b("− (Minus)", "Classic/Buttons/-"), b("HOME menu", "Classic/Buttons/Home"),
            b("D-pad Up", "Classic/D-Pad/Up"), b("D-pad Down", "Classic/D-Pad/Down"),
            b("D-pad Left", "Classic/D-Pad/Left"), b("D-pad Right", "Classic/D-Pad/Right"),
        )
        EmulatedController.GAMECUBE -> listOf(
            b("A", "Buttons/A"), b("B", "Buttons/B"), b("X", "Buttons/X"), b("Y", "Buttons/Y"), b("Z", "Buttons/Z"),
            b("Start", "Buttons/Start"), b("L (analog)", "Triggers/L", "Triggers/L-Analog"), b("R (analog)", "Triggers/R", "Triggers/R-Analog"),
            b("D-pad Up", "D-Pad/Up"), b("D-pad Down", "D-Pad/Down"), b("D-pad Left", "D-Pad/Left"), b("D-pad Right", "D-Pad/Right"),
        )
    }

    fun directionalOptions(c: EmulatedController): List<Option> = listOf(NOTHING) + when (c) {
        EmulatedController.WIIMOTE_NUNCHUK -> listOf(d("Nunchuk stick", "Nunchuk/Stick")) + wiiDirectional
        EmulatedController.WIIMOTE_SIDEWAYS, EmulatedController.WIIMOTE_WHEEL -> wiiDirectional
        EmulatedController.CLASSIC -> listOf(d("Left stick", "Classic/Left Stick"), d("Right stick", "Classic/Right Stick"), d("D-pad", "Classic/D-Pad"))
        EmulatedController.GAMECUBE -> listOf(d("Control stick", "Main Stick"), d("C-Stick", "C-Stick"), d("D-pad", "D-Pad"))
    }

    /** Gyro aiming needs a Wii Remote pointer, so only Wii Remote styles (not the wheel) offer it. */
    val GYRO_AIM = listOf(
        Option("Aim pointer (turn the Thor) (experimental)", Target.GyroPointer()),
        Option("Aim pointer · inverted up/down (experimental)", Target.GyroPointer(invertY = true)),
        Option("Aim pointer · inverted left/right (experimental)", Target.GyroPointer(invertX = true)),
        Option("Aim pointer · both inverted (experimental)", Target.GyroPointer(invertX = true, invertY = true)),
        Option("Precision aim (experimental)", Target.GyroPointer(precision = true)),
        Option("Precision aim · inverted up/down (experimental)", Target.GyroPointer(invertY = true, precision = true)),
        Option("Precision aim · inverted left/right (experimental)", Target.GyroPointer(invertX = true, precision = true)),
        Option("Precision aim · both inverted (experimental)", Target.GyroPointer(invertX = true, invertY = true, precision = true)),
    )

    fun gyroOptions(c: EmulatedController): List<Option> = listOf(NOTHING) + when (c) {
        EmulatedController.WIIMOTE_NUNCHUK, EmulatedController.WIIMOTE_SIDEWAYS -> GYRO_AIM
        else -> emptyList()
    }

    fun optionsFor(control: ThorControl, c: EmulatedController) = when {
        control == ThorControl.GYRO -> gyroOptions(c)
        control.kind == ThorControl.Kind.DIRECTIONAL -> directionalOptions(c)
        else -> buttonOptions(c)
    }

    /** Controls the editor lets you change, in the order they sit on the Thor. */
    val EDITABLE = listOf(
        ThorControl.L2, ThorControl.L1, ThorControl.R2, ThorControl.R1,
        ThorControl.LEFT_STICK, ThorControl.L3, ThorControl.DPAD,
        ThorControl.FACE_NORTH, ThorControl.FACE_WEST, ThorControl.FACE_EAST, ThorControl.FACE_SOUTH,
        ThorControl.RIGHT_STICK, ThorControl.R3, ThorControl.SELECT, ThorControl.START,
        ThorControl.GYRO,
    )

    /** Which catalog option a mapping currently corresponds to (by target keys), if any. */
    fun matching(control: ThorControl, c: EmulatedController, target: Target?): Option? =
        optionsFor(control, c).firstOrNull { sameTarget(it.target, target) }

    fun sameTarget(a: Target?, b: Target?): Boolean = when {
        a == null || b == null -> a == b
        a is Target.GyroPointer || b is Target.GyroPointer -> a == b
        a is Target.Buttons && b is Target.Buttons -> a.keys.toSet() == b.keys.toSet()
        a is Target.Directional && b is Target.Directional ->
            a.up?.substringBeforeLast('/') == b.up?.substringBeforeLast('/') ||
                a.left?.substringBeforeLast('/') == b.left?.substringBeforeLast('/')
        else -> false
    }

    /**
     * Builds the edited preset. [choices] maps each control to the picked option; controls absent
     * from the map keep their original mapping. The result is a separate "custom" preset.
     */
    fun applyEdits(base: Preset, choices: Map<ThorControl, Option>, customId: String): Preset {
        val friendly = base.mappings.associate { m -> m.target to m.action }
        val edited = mutableListOf<Mapping>()
        for (control in ThorControl.entries) {
            val pick = choices[control]
            if (pick == null) {
                edited += base.mappings.filter { it.control == control }
                continue
            }
            val target = pick.target ?: continue
            val label = friendly.entries.firstOrNull { sameTarget(it.key, target) }?.value ?: pick.label
            edited += Mapping(control, target, label)
        }
        val baseName = base.name.removeSuffix(" (custom)")
        return base.copy(
            id = customId,
            name = "$baseName (custom)",
            summary = "Your own version of \"$baseName\".",
            mappings = edited,
        )
    }
}
