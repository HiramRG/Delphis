package io.github.hiramrg.delphis.core

/**
 * Rumble: Dolphin sends a game's rumble (Wii Remote / GameCube controller motor) to whatever the profile's
 * `Rumble/Motor` output names. Dolphin's Android backend exposes a controller's vibrator as "Motor" (or
 * "Motor 0", "Motor 1" when it has several) and the device's own vibrator on "Device Sensors". The exact
 * name isn't certain on every Dolphin version, so the expression lists each candidate: names that don't
 * exist are simply ignored by Dolphin, and an output expression drives every motor it names.
 * (Dolphin can't scale an output, so there's no strength setting.)
 */
object Rumble {
    enum class Mode { PAD, DEVICE, OFF }

    fun byName(name: String?): Mode? = Mode.entries.firstOrNull { it.name == name }

    private const val SENSORS = "Android/0/Device Sensors"
    private val MOTORS = listOf("Motor", "Motor 0", "Motor 1")

    /**
     * The `Rumble/Motor` expression, or null for none. PAD: the controller's own motors ([devices]);
     * DEVICE: the phone / tablet / Thor itself, plus the built-in controls' motors when [builtIn] (the Thor).
     */
    fun expression(mode: Mode, devices: List<String>, builtIn: Boolean = false): String? {
        fun motorsOf(d: String) = MOTORS.map { "`$d:$it`" }
        val refs = when (mode) {
            Mode.OFF -> return null
            Mode.PAD -> devices.flatMap(::motorsOf)
            Mode.DEVICE -> listOf("`$SENSORS:Motor`") + (if (builtIn) devices.flatMap(::motorsOf) else emptyList())
        }
        return refs.distinct().joinToString(" | ").ifEmpty { null }
    }
}
