package io.github.hiramrg.delphis.core

/** The letters printed on a controller's face buttons. */
enum class ButtonLabels {
    /** Thor, Switch: X top, Y left, A right, B bottom. */
    NINTENDO,
    /** Xbox and most other pads: Y top, X left, B right, A bottom. */
    XBOX,
    /** PlayStation: △ top, □ left, ○ right, ✕ bottom. */
    PLAYSTATION;

    /**
     * What a player on this controller should know, in the Thor's letters (buttons are matched by
     * position, so the Thor's A is this controller's right button), or null when it's the same.
     */
    val note: String?
        get() = when (this) {
            NINTENDO -> null
            XBOX -> "A and B, X and Y are swapped"
            PLAYSTATION -> "A = ○ · B = ✕ · X = △ · Y = □"
        }

    companion object {
        /** From the USB vendor id: Nintendo 0x057e, Sony 0x054c, anything else (Microsoft 0x045e included) Xbox-style. */
        fun forVendor(vendorId: Int): ButtonLabels = when (vendorId) {
            0x057e -> NINTENDO
            0x054c -> PLAYSTATION
            else -> XBOX
        }
    }
}

/** A connected gamepad as Delphis sees it. [key] is Android's stable input-device descriptor. */
data class Pad(
    val key: String, val name: String, val qualifiers: List<String>, val builtIn: Boolean,
    val labels: ButtonLabels = if (builtIn) ButtonLabels.NINTENDO else ButtonLabels.XBOX,
    /**
     * Android reports this controller's face buttons by their printed letter (like the Thor) instead of
     * by position. Learned from the one-time "press the bottom button" check; null = not checked yet.
     */
    val byLabel: Boolean? = null,
) {
    /** Short label for the pills: "Thor", "Xbox Wireless", "8BitDo Pro 2". */
    val shortName: String
        get() = if (builtIn) "Thor" else name.replace(Regex("(?i)\\s*(controller|gamepad)\\s*"), " ").trim().ifEmpty { name }
    val longName: String get() = if (builtIn) "Thor (built-in)" else name
}

/**
 * A game's saved player setup: the order controllers were arranged in (pad keys) and which ones sit
 * out. Pads not in [order] (newly connected) come after the known ones, the Thor first.
 */
data class PlayerSetup(val order: List<String> = emptyList(), val out: Set<String> = emptySet()) {
    /** "order=a,b;out=c" (pad keys are Android's hex descriptors, so ',' and ';' never appear in them). */
    fun encode(): String = "order=" + order.joinToString(",") + ";out=" + out.joinToString(",")

    companion object {
        fun decode(text: String?): PlayerSetup {
            val parts = text.orEmpty().split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            fun list(name: String) = parts[name].orEmpty().split(',').filter { it.isNotBlank() }
            return PlayerSetup(list("order"), list("out").toSet())
        }
    }
}

object Players {
    const val MAX = 4

    /** Every connected pad in the setup's order (playing or not): known order first, then new ones, the Thor first among those. */
    fun arranged(pads: List<Pad>, setup: PlayerSetup): List<Pad> {
        fun rank(p: Pad) = setup.order.indexOf(p.key).let { if (it < 0) Int.MAX_VALUE else it }
        return pads.withIndex().sortedWith(compareBy({ rank(it.value) }, { if (it.value.builtIn) 0 else 1 }, { it.index })).map { it.value }
    }

    /** Who plays, P1 first (at most [MAX]). With nobody left, the Thor plays alone. */
    fun assign(pads: List<Pad>, setup: PlayerSetup): List<Pad> {
        val playing = arranged(pads, setup).filter { it.key !in setup.out }.take(MAX)
        return playing.ifEmpty { pads.filter { it.builtIn }.take(1) }
    }

    /** True when there's anything to set up: at least one controller besides the Thor. */
    fun hasExternal(pads: List<Pad>) = pads.any { !it.builtIn }

    /** Needs the Thor itself (gyro, tilt, gyro-hold button or the bottom-screen trackpad). */
    fun usesThorOnly(p: Preset): Boolean = p.isTrackpad || p.mappings.any { m ->
        m.control == ThorControl.GYRO || m.control == ThorControl.MOTION ||
            (m.target is Target.Buttons && GYRO_HOLD in (m.target as Target.Buttons).keys)
    }

    /** Each gyro / tilt / trackpad preset and the plain preset it's built on. */
    private val plainVersion: Map<String, Preset> by lazy {
        val galaxy = PresetLibrary.GALAXY_GYRO_ALL + PresetLibrary.GALAXY_PRECISION_ALL + PresetLibrary.GALAXY_TRACKPAD
        val pointer = PresetLibrary.POINTER_GYRO_ALL + PresetLibrary.POINTER_PRECISION_ALL + PresetLibrary.POINTER_TRACKPAD +
            PresetLibrary.RED_STEEL_TRACKPAD + PresetLibrary.RED_STEEL_TOUCH
        galaxy.associate { it.id to PresetLibrary.GALAXY_RIGHT_STICK } + pointer.associate { it.id to PresetLibrary.POINTER_ADVENTURE } +
            (PresetLibrary.MKW_WHEEL_GYRO.id to PresetLibrary.MKW_WHEEL_STICK)
    }

    /**
     * The preset everyone uses in multiplayer (and anyone on an external controller): no gyro, tilt or
     * trackpad, so all players have the same controls. Built-in gyro / tilt / trackpad presets become
     * the plain preset they're built on (e.g. Precision Gyro → Nunchuk + Pointer on Right Stick, Wii
     * Wheel (Tilt the Thor) → Wii Wheel (Tilt on Stick)); custom ones lose those mappings.
     */
    fun multiplayerPreset(p: Preset): Preset {
        plainVersion[p.id]?.let { return it }
        if (!usesThorOnly(p)) return p
        val mappings = p.mappings.filter { m ->
            m.control != ThorControl.GYRO && m.control != ThorControl.MOTION &&
                !(m.target is Target.Buttons && GYRO_HOLD in (m.target as Target.Buttons).keys)
        }
        return p.copy(mappings = mappings, options = p.options - TRACKPAD_OPTION - TOUCH_BUTTONS_OPTION)
    }

    fun forExternal(p: Preset): Preset = multiplayerPreset(p)

    /** The profile written for player [n] (2-4): its own file, since it's bound to another controller. */
    fun profileFor(p: Preset, n: Int): Preset = if (n <= 1) p else p.copy(id = "${p.id}-p$n")

    /**
     * Game INI `[Controls]` for players 2-4: each gets its profile (`WiimoteProfileN` / `PadProfileN`)
     * and its Wii Remote / GameCube port is switched on for this game only (`WiimoteSource1-3 = 1`
     * emulated, `PadType1-3 = 6` standard controller; see Dolphin's GameConfigLoader). Slots without
     * a player get those keys removed again, so a single-player game is left as it was.
     */
    fun mergeGameIni(existing: String, others: List<Preset>): String = IniEditor(existing).apply {
        for (n in 2..MAX) {
            val slot = n - 1
            listOf("WiimoteProfile$n", "PadProfile$n", "WiimoteSource$slot", "PadType$slot").forEach { remove("Controls", it) }
            val p = others.getOrNull(n - 2) ?: continue
            val wii = p.controller.gameIniKey.startsWith("Wiimote")
            val profile = profileFor(p, n).profileName
            set("Controls", if (wii) mapOf("WiimoteProfile$n" to profile, "WiimoteSource$slot" to "1") else mapOf("PadProfile$n" to profile, "PadType$slot" to "6"))
        }
    }.toString()
}
