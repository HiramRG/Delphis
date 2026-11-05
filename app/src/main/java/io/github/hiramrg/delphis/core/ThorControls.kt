package io.github.hiramrg.delphis.core

/**
 * Physical controls on the AYN Thor's bottom half.
 *
 * Face buttons are identified by *position* (north/south/east/west) because the label printed on
 * the Thor (Nintendo layout: A east, B south, X north, Y west) and the Android keycode it reports
 * can differ depending on the Thor's controller-mode setting.
 */
enum class ThorControl(val shortLabel: String, val kind: Kind) {
    FACE_EAST("A", Kind.BUTTON),
    FACE_SOUTH("B", Kind.BUTTON),
    FACE_NORTH("X", Kind.BUTTON),
    FACE_WEST("Y", Kind.BUTTON),
    DPAD("D-Pad", Kind.DIRECTIONAL),
    LEFT_STICK("L-Stick", Kind.DIRECTIONAL),
    RIGHT_STICK("R-Stick", Kind.DIRECTIONAL),
    L1("L1", Kind.BUTTON),
    R1("R1", Kind.BUTTON),
    L2("L2", Kind.BUTTON),
    R2("R2", Kind.BUTTON),
    L3("L3", Kind.BUTTON),
    R3("R3", Kind.BUTTON),
    START("Start", Kind.BUTTON),
    SELECT("Select", Kind.BUTTON),
    /** The Thor's built-in accelerometer/gyro ("Device Sensors" in Dolphin). */
    MOTION("Tilt Thor", Kind.DIRECTIONAL),
    /** The Thor's gyroscope, for aiming the Wii pointer by turning the device (Dolphin's IMU pointing). */
    GYRO("Gyro", Kind.DIRECTIONAL);

    enum class Kind { BUTTON, DIRECTIONAL }
}

enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    fun opposite() = when (this) { UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT }
}

/**
 * Everything needed to turn a [ThorControl] into the text Dolphin expects in a profile INI.
 *
 * Input names come from Dolphin's Android input backend
 * (Source/Core/InputCommon/ControllerInterface/Android/Android.cpp):
 *  - key events are named from Android keycodes: "Button A", "Button L1", "Start", "Up", ...
 *  - joystick axes are named "Axis <MotionEvent axis id><+|->", e.g. "Axis 0-" for left stick left.
 *  - sensors live on a separate device called "Device Sensors" ("Accel Left", "Gyro Roll Left", ...).
 *
 * Every input is written **fully qualified** (`Android/0/<device>:Button A`) and OR-ed across every
 * candidate device, so a binding still works when the Thor exposes its pad as several Android input
 * devices, or when Dolphin numbers it differently than expected.
 */
data class ControllerProfile(
    /** Dolphin device qualifiers to bind to, e.g. "Android/0/Thor Controller". First one is the profile's Device. */
    val devices: List<String> = listOf("Android/0/$DEFAULT_DEVICE_NAME"),
    /**
     * Inputs recorded by the Controller Check, keyed by [slotKey]. A recorded slot replaces the
     * built-in guess for that control.
     */
    val calibrated: Map<String, List<String>> = emptyMap(),
    /** Flip motion steering if the Thor's sensor axes turn out to be mirrored. */
    val invertMotion: Boolean = false,
    /** Gyro aiming speed: pointer travel (half-screens) per radian the Thor is turned. ~4.5 ≈ a real Wii Remote. */
    val gyroSpeed: Double = 4.5,
    /** Which rotation moves the gyro pointer left/right. */
    val gyroHorizontal: GyroHorizontal = GyroHorizontal.UPRIGHT,
) {
    val dolphinDevice: String get() = devices.first()
    val sensorDevice: String get() = "Android/0/Device Sensors"

    /** Dolphin input names (unqualified) used for a control. */
    fun inputsFor(control: ThorControl, dir: Direction? = null): List<String> =
        calibrated[slotKey(control, dir)]?.takeIf { it.isNotEmpty() } ?: defaultInputs(control, dir)

    fun buttonExpr(control: ThorControl): String {
        require(control.kind == ThorControl.Kind.BUTTON) { "$control is not a button" }
        return expr(inputsFor(control))
    }

    fun directionExpr(control: ThorControl, dir: Direction): String {
        if (control == ThorControl.MOTION) {
            // Roll the device left/right, pitch it toward/away. An accelerometer reads the reaction to
            // gravity, so rolling the Thor to the left shows up as positive X ("Accel Right").
            // Values are in m/s^2; dividing by 5.5 reaches full deflection at roughly 35 degrees.
            val physical = if (invertMotion) dir.opposite() else dir
            val name = when (physical) {
                Direction.LEFT -> "Accel Right"; Direction.RIGHT -> "Accel Left"
                Direction.UP -> "Accel Backward"; Direction.DOWN -> "Accel Forward"
            }
            return "`$sensorDevice:$name` / 5.5"
        }
        require(control.kind == ThorControl.Kind.DIRECTIONAL) { "$control is not directional" }
        return expr(inputsFor(control, dir))
    }

    private fun expr(names: List<String>): String =
        names.flatMap { n -> devices.map { d -> "`$d:$n`" } }.distinct().joinToString(" | ")

    companion object {
        const val DEFAULT_DEVICE_NAME = "Thor Controller"

        // android.view.MotionEvent axis constants (duplicated so this file stays platform-free).
        const val AXIS_X = 0
        const val AXIS_Y = 1
        const val AXIS_Z = 11
        const val AXIS_RZ = 14
        const val AXIS_HAT_X = 15
        const val AXIS_HAT_Y = 16
        const val AXIS_LTRIGGER = 17
        const val AXIS_RTRIGGER = 18
        const val AXIS_GAS = 22
        const val AXIS_BRAKE = 23

        /** Android's convention: KEYCODE_BUTTON_A is the bottom (south) face button. */
        val DEFAULT_FACE_BUTTONS = mapOf(
            ThorControl.FACE_SOUTH to "Button A",
            ThorControl.FACE_EAST to "Button B",
            ThorControl.FACE_WEST to "Button X",
            ThorControl.FACE_NORTH to "Button Y",
        )

        /** Same as above but for firmware that reports keycodes matching the printed Nintendo labels. */
        val NINTENDO_FACE_BUTTONS = mapOf(
            ThorControl.FACE_EAST to "Button A",
            ThorControl.FACE_SOUTH to "Button B",
            ThorControl.FACE_NORTH to "Button X",
            ThorControl.FACE_WEST to "Button Y",
        )

        fun slotKey(control: ThorControl, dir: Direction? = null) =
            if (dir == null) control.name else "${control.name}:${dir.name}"

        fun axis(id: Int, positive: Boolean) = "Axis $id${if (positive) "+" else "-"}"

        private fun stick(xAxis: Int, yAxis: Int, dir: Direction) = when (dir) {
            Direction.UP -> axis(yAxis, false)
            Direction.DOWN -> axis(yAxis, true)
            Direction.LEFT -> axis(xAxis, false)
            Direction.RIGHT -> axis(xAxis, true)
        }

        /** Best guesses for a standard Android gamepad, used for anything not calibrated. */
        fun defaultInputs(control: ThorControl, dir: Direction?): List<String> = when (control) {
            ThorControl.FACE_EAST, ThorControl.FACE_SOUTH, ThorControl.FACE_NORTH, ThorControl.FACE_WEST ->
                listOf(DEFAULT_FACE_BUTTONS.getValue(control))
            ThorControl.L1 -> listOf("Button L1")
            ThorControl.R1 -> listOf("Button R1")
            // Analog triggers: accept the digital key and the analog axis.
            ThorControl.L2 -> listOf("Button L2", axis(AXIS_LTRIGGER, true), axis(AXIS_BRAKE, true))
            ThorControl.R2 -> listOf("Button R2", axis(AXIS_RTRIGGER, true), axis(AXIS_GAS, true))
            ThorControl.L3 -> listOf("Button L3")
            ThorControl.R3 -> listOf("Button R3")
            ThorControl.START -> listOf("Start")
            ThorControl.SELECT -> listOf("Select")
            ThorControl.LEFT_STICK -> listOf(stick(AXIS_X, AXIS_Y, dir!!))
            ThorControl.RIGHT_STICK -> listOf(stick(AXIS_Z, AXIS_RZ, dir!!))
            ThorControl.DPAD -> {
                val key = when (dir!!) { Direction.UP -> "Up"; Direction.DOWN -> "Down"; Direction.LEFT -> "Left"; Direction.RIGHT -> "Right" }
                listOf(key, stick(AXIS_HAT_X, AXIS_HAT_Y, dir))
            }
            ThorControl.MOTION, ThorControl.GYRO -> emptyList()
        }

        /** Serialises calibration as `SLOT=input|input` lines (stored in preferences / diagnostics). */
        fun encodeCalibration(map: Map<String, List<String>>) =
            map.entries.joinToString("\n") { (k, v) -> "$k=${v.joinToString("|")}" }

        fun decodeCalibration(text: String?): Map<String, List<String>> =
            text.orEmpty().lines().mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) null else line.substring(0, eq) to line.substring(eq + 1).split('|').filter { it.isNotBlank() }
            }.toMap()
    }
}
