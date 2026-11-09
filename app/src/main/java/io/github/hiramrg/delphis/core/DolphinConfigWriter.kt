package io.github.hiramrg.delphis.core

/**
 * Produces the two files Dolphin needs to auto-apply a preset for one game:
 *
 *  1. `Config/Profiles/<Wiimote|GCPad>/<profileName>.ini`, an input profile with a `[Profile]` section.
 *  2. `GameSettings/<GAMEID>.ini`, the per-game settings file. Its `[Controls]` section gets
 *     `WiimoteProfile1 = <profileName>` (or `PadProfile1`), which Dolphin's InputConfig reads at boot
 *     to load that profile for player 1 *only while this game runs*.
 *
 * Key names and expression syntax match Dolphin's ControllerEmu serialization.
 */
object DolphinConfigWriter {

    fun profilePath(preset: Preset) = listOf("Config", "Profiles", preset.controller.profileFolder, "${preset.profileName}.ini")

    fun gameIniPath(gameId: String) = listOf("GameSettings", "$gameId.ini")

    /** Renders the profile INI text for [preset] on the given controller. */
    fun renderProfile(preset: Preset, controller: ControllerProfile): String {
        val bindings = linkedMapOf<String, MutableList<String>>()
        fun bind(key: String, expr: String) {
            val list = bindings.getOrPut(key) { mutableListOf() }
            if (expr !in list) list += expr
        }

        var gyro: Target.GyroPointer? = null
        for (mapping in preset.mappings) {
            when (val target = mapping.target) {
                is Target.GyroPointer -> gyro = target
                is Target.Buttons -> {
                    val expr = controller.buttonExpr(mapping.control)
                    target.keys.forEach { bind(it, expr) }
                }
                is Target.Directional -> for (dir in Direction.entries) {
                    val key = target.key(dir) ?: continue
                    bind(key, controller.directionExpr(mapping.control, dir))
                }
            }
        }

        val lines = linkedMapOf<String, String>()
        for ((key, exprs) in bindings) lines[key] = exprs.joinToString(" | ")
        // Trackpad presets: the pointer follows the bottom-screen trackpad, which Delphis serves to Dolphin
        // as a DSU pad's touchpad (platform/DsuServer). Absolute: Dolphin already adds up the touch movement.
        if (preset.isTrackpad) {
            lines["IR/Up"] = "`${Dsu.DEVICE}:Touch Y-`"
            lines["IR/Down"] = "`${Dsu.DEVICE}:Touch Y+`"
            lines["IR/Left"] = "`${Dsu.DEVICE}:Touch X-`"
            lines["IR/Right"] = "`${Dsu.DEVICE}:Touch X+`"
        }
        // Not a Dolphin input: the gyro expressions read it to pause aiming while it's held.
        val hold = lines.remove(GYRO_HOLD)
        // "Delphis/…" options are Delphis' own (e.g. the trackpad switch), not Dolphin keys.
        val options = LinkedHashMap(preset.options.filterKeys { !it.startsWith("Delphis/") })
        gyro?.let { g ->
            // The pointer becomes absolute (positioned by our expressions), so drop relative mode.
            options.remove("IR/Relative Input")
            lines.putAll(if (g.precision) precisionPointerBindings(g, lines, controller, hold) else gyroPointerBindings(g, lines, controller))
            options["IR/Relative Input"] = "False"
            // Gyro aiming needs the pointer on screen the whole time.
            options.remove("IR/Auto-Hide")
        }

        return buildString {
            appendLine("[Profile]")
            appendLine("Device = ${controller.dolphinDevice}")
            for ((key, expr) in lines) appendLine("$key = $expr")
            for ((key, value) in options) appendLine("$key = $value")
        }
    }

    /**
     * Gyro aiming ("gyro mouse") as Dolphin input expressions on the Wii pointer (IR group, absolute
     * mode). Mirrors GyroAim.Tracker (used by the in-app test screen) step by step.
     *
     * - Left/right integrates the turn rate (GyroAim.turnLeftExpression for the chosen mode).
     * - Up/down integrates `Gyro Pitch Up` and is also pulled toward the tilt measured from gravity
     *   (`atan2(Accel Forward, Accel Up)` relative to the tilt when the game started / R3 was
     *   pressed), so it can't drift.
     * - A soft dead zone (v·min(|v|/d, 1)) instead of Dolphin's deadzone(), which subtracts from
     *   every movement.
     * - `relative(input, speed, ±1, $var)` integrates over real time (no speed cap). Gyro and right
     *   stick are kept in separate variables ($vx/$vy and $sx/$sy) so the tilt pull doesn't undo
     *   stick movement; the pointer is their sum. Everything is updated in the Right/Up mappings;
     *   Left/Down just read the result.
     * - The recenter button (whatever was bound to IR/Recenter, usually R3) zeroes the pointer and
     *   makes the current tilt the new middle.
     *
     * Dolphin's Android sensor inputs are raw signed values in the device's axes (X/Y rotated by the
     * screen rotation): `Gyro Pitch Up`/`Roll Right`/`Yaw Left` = rotation about X/Y/Z in rad/s,
     * `Accel Right`/`Forward`/`Up` = X/Y/Z in m/s² (gravity reads +9.81 upward).
     */
    fun gyroPointerBindings(g: Target.GyroPointer, existing: Map<String, String>, controller: ControllerProfile): Map<String, String> {
        val d = controller.sensorDevice
        fun s(name: String) = "`$d:$name`"
        val k = "%.2f".format(java.util.Locale.US, controller.gyroSpeed)
        val dz = GyroAim.DEAD_ZONE
        val pull = GyroAim.TILT_PULL
        val turnLeft = GyroAim.turnLeftExpression(controller.gyroHorizontal, d)
        val xRate = if (g.invertX != controller.gyroInvertX) turnLeft else "(0 - $turnLeft)"
        val ySign = if (g.invertY != controller.gyroInvertY) "(0 - 1)" else "1"

        fun stick(key: String) = existing[key]?.let { "($it) * 2" } ?: "0" // 2 = Dolphin's own relative-pointer speed
        val r = existing["IR/Recenter"]?.let { "($it)" }
        fun zeroOnRecenter(v: String) = if (r != null) "($v = if($r, 0, $v)), " else ""
        val newMiddle = if (r != null) "max($r, 1 - \$ti)" else "(1 - \$ti)"

        val right = zeroOnRecenter("\$vx") + zeroOnRecenter("\$sx") +
            "(\$wx = $xRate), (\$wx = \$wx * min(abs(\$wx) / $dz, 1)), " +
            "relative(max(\$wx * $k, 0), 1, 1, \$vx), relative(max(0 - \$wx * $k, 0), 1, -1, \$vx), " +
            "relative(${stick("IR/Right")}, 1, 1, \$sx), relative(${stick("IR/Left")}, 1, -1, \$sx), " +
            "max(\$vx + \$sx, 0)"
        val up = "(\$p = atan2(${s("Accel Forward")}, ${s("Accel Up")})), " +
            "(\$t0 = if($newMiddle, \$p, \$t0)), (\$ti = 1), " +
            zeroOnRecenter("\$vy") + zeroOnRecenter("\$sy") +
            "(\$wy = $ySign * ${s("Gyro Pitch Up")}), (\$wy = \$wy * min(abs(\$wy) / $dz, 1)), " +
            "(\$uy = \$wy * $k + $pull * (clamp($ySign * $k * (\$p - \$t0), -1, 1) - \$vy)), " +
            "relative(max(\$uy, 0), 1, 1, \$vy), relative(max(0 - \$uy, 0), 1, -1, \$vy), " +
            "relative(${stick("IR/Up")}, 1, 1, \$sy), relative(${stick("IR/Down")}, 1, -1, \$sy), " +
            "max(\$vy + \$sy, 0)"
        return linkedMapOf(
            "IR/Right" to right,
            "IR/Left" to "max(0 - (\$vx + \$sx), 0)",
            "IR/Up" to up,
            "IR/Down" to "max(0 - (\$vy + \$sy), 0)",
        )
    }

    /**
     * "Precision aim": like [gyroPointerBindings] but pure gyro on both axes (no gravity pull, so a
     * re-positioned Thor isn't dragged back), with an acceleration curve (GyroAim.precisionGain) and
     * an optional hold button ([hold], the expression bound to GYRO_HOLD) that freezes the gyro part
     * while pressed. Mirrors GyroAim.Tracker with precision = true.
     */
    fun precisionPointerBindings(g: Target.GyroPointer, existing: Map<String, String>, controller: ControllerProfile, hold: String?): Map<String, String> {
        val d = controller.sensorDevice
        fun s(name: String) = "`$d:$name`"
        fun n(v: Double) = "%.2f".format(java.util.Locale.US, v)
        val k = n(controller.gyroSpeed)
        val dz = GyroAim.DEAD_ZONE
        val turnLeft = GyroAim.turnLeftExpression(controller.gyroHorizontal, d)
        val xRate = if (g.invertX != controller.gyroInvertX) turnLeft else "(0 - $turnLeft)"
        val ySign = if (g.invertY != controller.gyroInvertY) "(0 - 1)" else "1"
        val slow = n(GyroAim.PRECISION_SLOW)
        val span = n(GyroAim.PRECISION_FAST - GyroAim.PRECISION_SLOW)
        val ramp = n(GyroAim.PRECISION_RAMP)

        fun stick(key: String) = existing[key]?.let { "($it) * 2" } ?: "0"
        val r = existing["IR/Recenter"]?.let { "($it)" }
        fun zeroOnRecenter(v: String) = if (r != null) "($v = if($r, 0, $v)), " else ""
        fun shape(w: String) = "($w = $w * min(abs($w) / $dz, 1)), ($w = $w * ($slow + $span * min(abs($w) / $ramp, 1))), " +
            (if (hold != null) "($w = if(($hold), 0, $w)), " else "")
        fun integrate(w: String, v: String) = "relative(max($w * $k, 0), 1, 1, $v), relative(max(0 - $w * $k, 0), 1, -1, $v), "

        val right = zeroOnRecenter("\$vx") + zeroOnRecenter("\$sx") +
            "(\$wx = $xRate), " + shape("\$wx") + integrate("\$wx", "\$vx") +
            "relative(${stick("IR/Right")}, 1, 1, \$sx), relative(${stick("IR/Left")}, 1, -1, \$sx), " +
            "max(\$vx + \$sx, 0)"
        val up = zeroOnRecenter("\$vy") + zeroOnRecenter("\$sy") +
            "(\$wy = $ySign * ${s("Gyro Pitch Up")}), " + shape("\$wy") + integrate("\$wy", "\$vy") +
            "relative(${stick("IR/Up")}, 1, 1, \$sy), relative(${stick("IR/Down")}, 1, -1, \$sy), " +
            "max(\$vy + \$sy, 0)"
        return linkedMapOf(
            "IR/Right" to right,
            "IR/Left" to "max(0 - (\$vx + \$sx), 0)",
            "IR/Up" to up,
            "IR/Down" to "max(0 - (\$vy + \$sy), 0)",
        )
    }

    /**
     * Returns [existing] (the current contents of GameSettings/<ID>.ini, possibly empty) with the
     * `[Controls]` section pointing at [preset]'s profile. Every other line is preserved untouched,
     * so settings the user made in Dolphin's own per-game screen survive.
     */
    fun mergeGameIni(existing: String, preset: Preset): String {
        val key = preset.controller.gameIniKey
        // Only one emulated controller type should be forced for player 1.
        val otherKey = if (key == "PadProfile1") "WiimoteProfile1" else "PadProfile1"
        val updates = mapOf(key to preset.profileName)
        return IniEditor(existing).apply {
            set("Controls", updates)
            remove("Controls", otherKey)
        }.toString()
    }

    /**
     * Fallback when a game's ID is unknown: copy the profile into player 1's slot of Dolphin's global
     * controller file (Config/WiimoteNew.ini [Wiimote1] or Config/GCPadNew.ini [GCPad1]).
     */
    fun globalConfigPath(preset: Preset) =
        listOf("Config", if (preset.controller == EmulatedController.GAMECUBE) "GCPadNew.ini" else "WiimoteNew.ini")

    fun mergeGlobalConfig(existing: String, preset: Preset, controller: ControllerProfile): String {
        val section = if (preset.controller == EmulatedController.GAMECUBE) "GCPad1" else "Wiimote1"
        val body = renderProfile(preset, controller).lines().drop(1).filter { it.isNotBlank() }
        val editor = IniEditor(existing)
        // Keep the slot's Source (emulated vs real Wii Remote); replace every mapping.
        val source = editor.get(section, "Source")
        editor.replaceSection(section, (if (source != null) listOf("Source = $source") else emptyList()) + body)
        return editor.toString()
    }

    /** Dolphin's main settings file; `[Core] EnableSaveStates` shows the Quick Save/Load buttons. */
    val dolphinIniPath = listOf("Config", "Dolphin.ini")

    fun enableSaveStates(existing: String): String =
        IniEditor(existing).apply { set("Core", mapOf("EnableSaveStates" to "True")) }.toString()

    /**
     * Dolphin.ini for trackpad presets: Dolphin's own touch pointer off (`[Android] IRMode = 0`), so a
     * finger on the top screen can't take the pointer over from the trackpad. (Older builds turned it on
     * and hid the overlay buttons; those settings are left as they are.)
     */
    fun trackpadDolphinIni(existing: String): String = IniEditor(existing).apply {
        set("Android", mapOf("IRMode" to "0"))
    }.toString()

    /** Config/DSUClient.ini with Delphis' trackpad server added to Dolphin's DSU servers (keeping any others). */
    fun dsuClientIni(existing: String): String = IniEditor(existing).apply {
        val entry = "${Dsu.SERVER_NAME}:127.0.0.1:${Dsu.PORT};"
        val current = get("Server", "Entries").orEmpty()
        val others = current.split(';').filter { it.isNotBlank() && !it.startsWith("${Dsu.SERVER_NAME}:") }.joinToString("") { "$it;" }
        set("Server", mapOf("Enabled" to "True", "Entries" to others + entry))
    }.toString()

    val DSU_CLIENT_INI = listOf("Config", "DSUClient.ini")

    /** Dolphin's Quick Save/Load slot: NativeLibrary.SaveState(9) → StateSaves/<GAMEID>.s09. */
    fun quickStatePath(gameId: String) = listOf("StateSaves", "$gameId.s09")

    fun baseConfigPaths() = listOf(listOf("Config", "WiimoteNew.ini"), listOf("Config", "GCPadNew.ini"))

    /**
     * Dolphin only honours `WiimoteProfile1`/`PadProfile1` when Config/WiimoteNew.ini (or GCPadNew.ini)
     * already exists with at least one section (see InputConfig::LoadConfig). On a fresh Dolphin
     * install that has never opened controller settings the file is missing, Dolphin falls back to
     * its built-in defaults and silently ignores the per-game profile.
     */
    fun needsBaseConfig(existing: String?): Boolean = existing == null || existing.lines().none { it.trim().startsWith("[") }

    /** A usable WiimoteNew.ini / GCPadNew.ini for player 1 (players 2-4 left unmapped). */
    fun baseConfig(path: List<String>, controller: ControllerProfile): String {
        val gc = path.last() == "GCPadNew.ini"
        val p1 = if (gc) PresetLibrary.GAMECUBE else PresetLibrary.POINTER_ADVENTURE
        val body = renderProfile(p1, controller).lines().drop(1).filter { it.isNotBlank() }
        return buildString {
            appendLine(if (gc) "[GCPad1]" else "[Wiimote1]")
            body.forEach { appendLine(it) }
            for (i in 2..4) { appendLine(); appendLine(if (gc) "[GCPad$i]" else "[Wiimote$i]"); appendLine("Device = ") }
            if (!gc) { appendLine(); appendLine("[BalanceBoard]"); appendLine("Device = ") }
        }
    }

    /** Removes the companion's override from a game INI (used by "Restore Dolphin defaults"). */
    fun clearGameIni(existing: String): String = IniEditor(existing).apply {
        remove("Controls", "WiimoteProfile1")
        remove("Controls", "PadProfile1")
    }.toString()
}

/** Minimal line-preserving INI editor: enough for Dolphin's simple `key = value` files. */
class IniEditor(text: String) {
    private val lines: MutableList<String> = text.replace("\r\n", "\n").split('\n').toMutableList().also {
        if (it.size == 1 && it[0].isEmpty()) it.clear()
        if (it.isNotEmpty() && it.last().isEmpty()) it.removeAt(it.lastIndex)
    }

    private fun sectionRange(section: String): IntRange? {
        val start = lines.indexOfFirst { it.trim() == "[$section]" }
        if (start < 0) return null
        var end = start + 1
        while (end < lines.size && !lines[end].trim().startsWith("[")) end++
        return start until end
    }

    private fun keyOf(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith(";") || t.startsWith("$") || t.startsWith("[")) return null
        val eq = t.indexOf('=')
        return if (eq < 0) null else t.substring(0, eq).trim()
    }

    fun get(section: String, key: String): String? {
        val range = sectionRange(section) ?: return null
        for (i in range) if (keyOf(lines[i]) == key) return lines[i].substringAfter('=').trim()
        return null
    }

    fun set(section: String, values: Map<String, String>) {
        var range = sectionRange(section)
        if (range == null) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "[$section]"
            range = sectionRange(section)!!
        }
        val pending = values.toMutableMap()
        for (i in range) {
            val k = keyOf(lines[i]) ?: continue
            pending.remove(k)?.let { lines[i] = "$k = $it" }
        }
        // Insert remaining keys after the last non-blank line of the section.
        var insertAt = range.last + 1
        while (insertAt - 1 > range.first && lines[insertAt - 1].isBlank()) insertAt--
        for ((k, v) in pending) lines.add(insertAt++, "$k = $v")
    }

    fun replaceSection(section: String, body: List<String>) {
        val range = sectionRange(section)
        if (range == null) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "[$section]"
            lines += body
            return
        }
        val trailingBlank = range.last > range.first && lines[range.last].isBlank()
        for (i in range.last downTo range.first + 1) lines.removeAt(i)
        lines.addAll(range.first + 1, body + if (trailingBlank) listOf("") else emptyList())
    }

    fun remove(section: String, key: String) {
        val range = sectionRange(section) ?: return
        for (i in range.reversed()) if (keyOf(lines[i]) == key) lines.removeAt(i)
    }

    override fun toString() = lines.joinToString("\n", postfix = "\n")
}
