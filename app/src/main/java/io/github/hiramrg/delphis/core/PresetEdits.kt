package io.github.hiramrg.delphis.core

/**
 * The preset editor's model ("pick an action, press its Thor button"). Pure: the screens in
 * ui/PresetEditor.kt only show this and call [move], [setTarget] and [clear].
 *
 * An *action* is a [Mapping]: which Thor control, what the Wii gets ([Target]), and its name.
 * Moving an action to a control that already has one swaps the two.
 */
object PresetEdits {
    /** One piece of "what the Wii gets": a button ("A") or a move ("⤒ Nunchuk thrust", [motion] = true). */
    data class Part(val text: String, val motion: Boolean)

    /** A tile in the editor's picker. [label] is the full name used for new actions ("Nunchuk thrust"). */
    data class Tile(val glyph: String, val small: String, val label: String, val target: Target, val motion: Boolean = false)

    data class Section(val title: String, val tiles: List<Tile>)

    // ------------------------------------------------------------------ what the Wii gets

    private val keyNames = mapOf(
        "Buttons/-" to "−", "Buttons/Home" to "HOME", "Classic/Buttons/-" to "−", "Classic/Buttons/Home" to "HOME",
        "D-Pad/Up" to "D-pad ↑", "D-Pad/Down" to "D-pad ↓", "D-Pad/Left" to "D-pad ←", "D-Pad/Right" to "D-pad →",
        "Classic/D-Pad/Up" to "D-pad ↑", "Classic/D-Pad/Down" to "D-pad ↓", "Classic/D-Pad/Left" to "D-pad ←", "Classic/D-Pad/Right" to "D-pad →",
        "IR/Recenter" to "Recenter pointer", GYRO_HOLD to "Hold: pause gyro",
        "Triggers/L" to "L", "Triggers/R" to "R", "Classic/Triggers/L" to "L", "Classic/Triggers/R" to "R",
    )
    private val moveNames = mapOf(
        "Forward" to "⤒" to "push", "Backward" to "⤓" to "pull",
        "Up" to "↑" to "swing up", "Down" to "↓" to "swing down", "Left" to "←" to "swing left", "Right" to "→" to "swing right",
    ).entries.associate { (k, v) -> k.first to Triple(k.second, v, k.first) }

    private fun device(nunchuk: Boolean) = if (nunchuk) "Nunchuk" else "Wii Remote"

    fun describe(target: Target?): List<Part> = when (target) {
        null -> emptyList()
        is Target.GyroPointer -> listOf(Part("Gyro aiming", true))
        is Target.Directional -> {
            val group = (target.up ?: target.left ?: "").substringBeforeLast('/')
            listOf(when (group) {
                "Nunchuk/Stick" -> Part("Nunchuk stick", false)
                "IR" -> Part("Pointer", false)
                "D-Pad", "Classic/D-Pad" -> Part("D-pad", false)
                "Swing" -> Part("✥ Wii Remote swing", true)
                "Nunchuk/Swing" -> Part("✥ Nunchuk swing", true)
                "Tilt" -> Part("⟲ Wii Remote tilt", true)
                "Nunchuk/Tilt" -> Part("⟲ Nunchuk tilt", true)
                "Classic/Left Stick" -> Part("Left stick", false)
                "Classic/Right Stick" -> Part("Right stick", false)
                "Main Stick" -> Part("Control stick", false)
                "C-Stick" -> Part("C-stick", false)
                else -> Part(group.substringAfterLast('/'), false)
            })
        }
        is Target.Buttons -> {
            val parts = mutableListOf<Part>()
            val keys = target.keys.filterNot { it.endsWith("-Analog") }
            if (keys.any { it.startsWith("Shake/") }) parts += Part("≈ Wii Remote shake", true)
            if (keys.any { it.startsWith("Nunchuk/Shake/") }) parts += Part("≈ Nunchuk shake", true)
            for (k in keys) {
                if (k.startsWith("Shake/") || k.startsWith("Nunchuk/Shake/")) continue
                val nunchuk = k.startsWith("Nunchuk/")
                val rest = k.removePrefix("Nunchuk/")
                when {
                    rest.startsWith("Swing/") -> {
                        val dir = rest.removePrefix("Swing/")
                        val m = moveNames[dir]
                        val word = if (nunchuk && dir == "Forward") "thrust" else m?.second ?: dir.lowercase()
                        parts += Part("${m?.first ?: ""} ${device(nunchuk)} $word".trim(), true)
                    }
                    rest.startsWith("Tilt/") -> parts += Part("⟲ ${device(nunchuk)} tilt ${rest.removePrefix("Tilt/").lowercase()}", true)
                    else -> parts += Part(keyNames[k] ?: k.substringAfterLast('/').let { if (k.startsWith("Classic/Buttons/") && it.length == 1) it.lowercase() else it }, false)
                }
            }
            parts
        }
    }

    fun wiiGets(target: Target?): String = describe(target).joinToString(" + ") { it.text }.ifEmpty { "Nothing" }

    // ------------------------------------------------------------------ the picker

    private fun b(glyph: String, small: String, label: String, vararg keys: String, motion: Boolean = false) =
        Tile(glyph, small, label, Target.Buttons(keys.toList()), motion)
    private fun d(glyph: String, small: String, label: String, group: String, up: String = "Up", down: String = "Down", motion: Boolean = false) =
        Tile(glyph, small, label, Target.Directional("$group/$up", "$group/$down", "$group/Left", "$group/Right"), motion)

    private fun moves(nunchuk: Boolean): List<Tile> {
        val p = if (nunchuk) "Nunchuk/" else ""
        val dev = device(nunchuk)
        return listOf(
            b("↑", "up", "$dev swing up", "${p}Swing/Up", motion = true),
            b("↓", "down", "$dev swing down", "${p}Swing/Down", motion = true),
            b("←", "left", "$dev swing left", "${p}Swing/Left", motion = true),
            b("→", "right", "$dev swing right", "${p}Swing/Right", motion = true),
            b("⤒", if (nunchuk) "thrust" else "push", if (nunchuk) "Nunchuk thrust" else "Wii Remote push", "${p}Swing/Forward", motion = true),
            b("⤓", "pull", "$dev pull back", "${p}Swing/Backward", motion = true),
            b("≈", "shake", "$dev shake", "${p}Shake/X", "${p}Shake/Y", "${p}Shake/Z", motion = true),
            b("⟲", "tilt", "$dev tilt forward", "${p}Tilt/Forward", motion = true),
        )
    }
    private val dpad = listOf(
        b("↑", "D-pad", "D-pad up", "D-Pad/Up"), b("↓", "D-pad", "D-pad down", "D-Pad/Down"),
        b("←", "D-pad", "D-pad left", "D-Pad/Left"), b("→", "D-pad", "D-pad right", "D-Pad/Right"),
    )
    private val wiiButtons = listOf(
        b("A", "", "A", "Buttons/A"), b("B", "", "B", "Buttons/B"), b("1", "", "1", "Buttons/1"), b("2", "", "2", "Buttons/2"),
        b("+", "", "+ (Pause)", "Buttons/+"), b("−", "", "− (Minus)", "Buttons/-"), b("⌂", "HOME", "HOME menu", "Buttons/Home"),
    )

    /** The picker's sections for a control of this kind on this emulated controller. */
    fun sections(c: EmulatedController, control: ThorControl): List<Section> {
        if (control == ThorControl.GYRO) {
            return if (c == EmulatedController.WIIMOTE_NUNCHUK || c == EmulatedController.WIIMOTE_SIDEWAYS)
                listOf(Section("AIM", listOf(Tile("◎", "gyro", "Gyro aiming", Target.GyroPointer(precision = true), true)))) else emptyList()
        }
        if (control.kind == ThorControl.Kind.DIRECTIONAL) return when (c) {
            EmulatedController.WIIMOTE_NUNCHUK -> listOf(
                Section("STICKS & POINTER", listOf(d("◉", "stick", "Nunchuk stick", "Nunchuk/Stick"), d("◎", "aim", "Pointer (aim)", "IR"), d("✚", "D-pad", "D-pad", "D-Pad"))),
                Section("WII REMOTE MOVES", listOf(d("✥", "swing", "Wii Remote swing", "Swing", motion = true), d("⟲", "tilt", "Wii Remote tilt", "Tilt", "Forward", "Backward", motion = true))),
                Section("NUNCHUK MOVES", listOf(d("✥", "swing", "Nunchuk swing", "Nunchuk/Swing", motion = true), d("⟲", "tilt", "Nunchuk tilt", "Nunchuk/Tilt", "Forward", "Backward", motion = true))),
            )
            EmulatedController.WIIMOTE_SIDEWAYS, EmulatedController.WIIMOTE_WHEEL -> listOf(
                Section("POINTER & D-PAD", listOf(d("◎", "aim", "Pointer (aim)", "IR"), d("✚", "D-pad", "D-pad", "D-Pad"))),
                Section("WII REMOTE MOVES", listOf(d("✥", "swing", "Wii Remote swing", "Swing", motion = true), d("⟲", "tilt", "Wii Remote tilt", "Tilt", "Forward", "Backward", motion = true))),
            )
            EmulatedController.CLASSIC -> listOf(Section("STICKS", listOf(d("◉", "left", "Left stick", "Classic/Left Stick"), d("◉", "right", "Right stick", "Classic/Right Stick"), d("✚", "D-pad", "D-pad", "Classic/D-Pad"))))
            EmulatedController.GAMECUBE -> listOf(Section("STICKS", listOf(d("◉", "stick", "Control stick", "Main Stick"), d("◉", "C", "C-stick", "C-Stick"), d("✚", "D-pad", "D-pad", "D-Pad"))))
        }
        return when (c) {
            EmulatedController.WIIMOTE_NUNCHUK -> listOf(
                Section("BUTTONS", wiiButtons + listOf(b("C", "Nunchuk", "C (Nunchuk)", "Nunchuk/Buttons/C"), b("Z", "Nunchuk", "Z (Nunchuk)", "Nunchuk/Buttons/Z")) + dpad),
                Section("WII REMOTE MOVES", moves(false)),
                Section("NUNCHUK MOVES", moves(true).filterNot { it.small == "tilt" } + b("⟲", "tilt", "Nunchuk tilt forward", "Nunchuk/Tilt/Forward", motion = true)),
            )
            EmulatedController.WIIMOTE_SIDEWAYS, EmulatedController.WIIMOTE_WHEEL -> listOf(
                Section("BUTTONS", wiiButtons + dpad),
                Section("WII REMOTE MOVES", moves(false)),
            )
            EmulatedController.CLASSIC -> listOf(Section("BUTTONS", listOf(
                b("a", "", "a", "Classic/Buttons/A"), b("b", "", "b", "Classic/Buttons/B"), b("x", "", "x", "Classic/Buttons/X"), b("y", "", "y", "Classic/Buttons/Y"),
                b("L", "", "L", "Classic/Triggers/L"), b("R", "", "R", "Classic/Triggers/R"), b("ZL", "", "ZL", "Classic/Buttons/ZL"), b("ZR", "", "ZR", "Classic/Buttons/ZR"),
                b("+", "", "+ (Pause)", "Classic/Buttons/+"), b("−", "", "− (Minus)", "Classic/Buttons/-"), b("⌂", "HOME", "HOME menu", "Classic/Buttons/Home"),
                b("↑", "D-pad", "D-pad up", "Classic/D-Pad/Up"), b("↓", "D-pad", "D-pad down", "Classic/D-Pad/Down"),
                b("←", "D-pad", "D-pad left", "Classic/D-Pad/Left"), b("→", "D-pad", "D-pad right", "Classic/D-Pad/Right"),
            )))
            EmulatedController.GAMECUBE -> listOf(Section("BUTTONS", listOf(
                b("A", "", "A", "Buttons/A"), b("B", "", "B", "Buttons/B"), b("X", "", "X", "Buttons/X"), b("Y", "", "Y", "Buttons/Y"), b("Z", "", "Z", "Buttons/Z"),
                b("▶", "Start", "Start", "Buttons/Start"), b("L", "", "L", "Triggers/L", "Triggers/L-Analog"), b("R", "", "R", "Triggers/R", "Triggers/R-Analog"),
            ) + dpad))
        }
    }

    // ------------------------------------------------------------------ editing

    /** Controls an action of this kind can live on. */
    fun compatible(from: ThorControl, to: ThorControl): Boolean = when {
        from == ThorControl.GYRO || to == ThorControl.GYRO || from == ThorControl.MOTION || to == ThorControl.MOTION -> from == to
        else -> from.kind == to.kind
    }

    /** Moves the action on [from] to [to]; whatever was on [to] goes to [from] (a swap). */
    fun move(maps: List<Mapping>, from: ThorControl, to: ThorControl): List<Mapping> {
        if (from == to || !compatible(from, to)) return maps
        return maps.map { m ->
            when (m.control) {
                from -> m.copy(control = to)
                to -> m.copy(control = from)
                else -> m
            }
        }
    }

    /** The action's name without its Wii part: "Block (Nunchuk thrust)" → "Block". */
    fun actionName(m: Mapping): String = splitAction(m.action).first.trim()

    /** Makes [control] do [tile]. An existing action keeps its name ("Block (Nunchuk shake)"); a new one is named after the tile. */
    fun setTarget(maps: List<Mapping>, control: ThorControl, tile: Tile): List<Mapping> {
        val old = maps.firstOrNull { it.control == control }
        val action = if (old == null) tile.label else "${actionName(old)} (${tile.label})"
        return maps.filterNot { it.control == control } + Mapping(control, tile.target, action)
    }

    fun clear(maps: List<Mapping>, control: ThorControl): List<Mapping> = maps.filterNot { it.control == control }

    /** The edited preset, saved as the game's own "(Custom)" version. */
    fun build(base: Preset, maps: List<Mapping>, customId: String): Preset {
        val baseName = base.name.removeSuffix(" (Custom)")
        return base.copy(id = customId, name = "$baseName (Custom)", summary = "Your own version of \"$baseName\".", mappings = maps)
    }
}
