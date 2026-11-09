package io.github.hiramrg.delphis.core

/** A connected gamepad as Delphis sees it. [key] is Android's stable input-device descriptor. */
data class Pad(val key: String, val name: String, val qualifiers: List<String>, val builtIn: Boolean) {
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

    /**
     * The preset for a player on an external controller: the Thor's motion sensors, gyro, gyro-hold
     * button and trackpad belong to the Thor, so they're left out (the right stick still aims), and
     * the Thor-tilt wheel becomes the stick-tilt wheel.
     */
    fun forExternal(p: Preset): Preset {
        if (p.id == PresetLibrary.MKW_WHEEL_GYRO.id) return PresetLibrary.MKW_WHEEL_STICK
        val mappings = p.mappings.filter { m ->
            m.control != ThorControl.GYRO && m.control != ThorControl.MOTION &&
                !(m.target is Target.Buttons && GYRO_HOLD in (m.target as Target.Buttons).keys)
        }
        return p.copy(mappings = mappings, options = p.options - TRACKPAD_OPTION)
    }

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
