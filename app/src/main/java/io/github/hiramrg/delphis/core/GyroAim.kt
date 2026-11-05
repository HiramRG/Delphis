package io.github.hiramrg.delphis.core

/**
 * Which rotation moves the pointer left/right. Up/down is always tilting the Thor toward/away
 * from you (rotation around its left-right axis).
 */
enum class GyroHorizontal(val label: String, val description: String) {
    /** Like Star Fox 64 3D on the 3DS: turn the console around its own up axis. */
    UPRIGHT("Upright (like the 3DS)", "Hold the Thor up like a 3DS and turn it left/right."),
    /** Rotation around the real-world vertical, worked out from gravity. */
    AUTO("Automatic", "Follows gravity, so it adapts to however you hold the Thor."),
    /** The Thor lying flat (turning it on a table). */
    FLAT("Flat", "For playing with the Thor lying flat."),
}

/**
 * The gyro-aiming math, shared by the Dolphin profile writer (as Dolphin expressions) and the
 * "Test gyro aiming" screen (as plain numbers), so the test feels exactly like the game.
 *
 * Inputs use Dolphin's Android sensor convention, in the device's own axes:
 * gyro x/y/z in rad/s (Dolphin: `Gyro Pitch Up`, `Gyro Roll Right`, `Gyro Yaw Left`) and
 * accel x/y/z in m/s² (`Accel Right`, `Accel Forward`, `Accel Up`; gravity reads +9.81 upward).
 */
object GyroAim {
    /** Below this rate (rad/s) movement is scaled down smoothly, so a still hand doesn't creep. */
    const val DEAD_ZONE = 0.03

    /**
     * How strongly (per second) the vertical position is pulled toward where the Thor's tilt says
     * it should be. Gravity gives an absolute tilt, so up/down can't drift: tilting away and back
     * always returns the pointer to the same height.
     */
    const val TILT_PULL = 1.5

    /**
     * Precision aim acceleration: at slow rates the pointer moves at PRECISION_SLOW x speed (fine
     * aiming, steadier against hand tremor), rising linearly to PRECISION_FAST x speed at
     * PRECISION_RAMP rad/s (about 57°/s) and above (quick turns still reach the edges).
     */
    const val PRECISION_SLOW = 0.4
    const val PRECISION_FAST = 1.3
    const val PRECISION_RAMP = 1.0

    fun precisionGain(rate: Double): Double =
        PRECISION_SLOW + (PRECISION_FAST - PRECISION_SLOW) * minOf(Math.abs(rate) / PRECISION_RAMP, 1.0)

    /** Rotation rate that should move the pointer left (positive) or right (negative). */
    fun turnLeftRate(mode: GyroHorizontal, gx: Double, gy: Double, gz: Double, ax: Double, ay: Double, az: Double): Double =
        when (mode) {
            GyroHorizontal.UPRIGHT -> gy
            GyroHorizontal.FLAT -> gz
            GyroHorizontal.AUTO -> (gx * ax + gy * ay + gz * az) / 9.81
        }

    /**
     * Soft dead zone: tiny rates are scaled down (v·|v|/d), normal movement passes unchanged. Unlike
     * a classic dead zone it doesn't subtract a bit from every movement, which made the pointer end
     * up somewhere else after moving away and back.
     */
    fun soft(v: Double): Double = v * minOf(Math.abs(v) / DEAD_ZONE, 1.0)

    /** Tilt toward/away from you, from gravity (radians); grows with positive `Gyro Pitch Up`. */
    fun tilt(ay: Double, az: Double): Double = Math.atan2(ay, az)

    /** The pointer the test screen shows; mirrors the Dolphin expressions step by step. */
    class Tracker {
        var x = 0.0; var y = 0.0
        private var tilt0 = 0.0
        private var started = false

        fun center(ay: Double, az: Double) { x = 0.0; y = 0.0; tilt0 = tilt(ay, az); started = true }

        fun step(
            dt: Double, mode: GyroHorizontal, invertX: Boolean, invertY: Boolean, speed: Double,
            gx: Double, gy: Double, gz: Double, ax: Double, ay: Double, az: Double,
            precision: Boolean = false, hold: Boolean = false,
        ) {
            if (!started) center(ay, az)
            val turnLeft = turnLeftRate(mode, gx, gy, gz, ax, ay, az)
            if (precision) {
                if (hold) return
                val wx = soft(if (invertX) turnLeft else -turnLeft)
                val wy = soft((if (invertY) -1.0 else 1.0) * gx)
                x = (x + wx * precisionGain(wx) * speed * dt).coerceIn(-1.0, 1.0)
                y = (y + wy * precisionGain(wy) * speed * dt).coerceIn(-1.0, 1.0)
                return
            }
            val wx = soft(if (invertX) turnLeft else -turnLeft)
            x = (x + wx * speed * dt).coerceIn(-1.0, 1.0)
            val sy = if (invertY) -1.0 else 1.0
            val wy = soft(sy * gx)
            val target = (sy * speed * (tilt(ay, az) - tilt0)).coerceIn(-1.0, 1.0)
            val uy = wy * speed + TILT_PULL * (target - y)
            y = (y + uy * dt).coerceIn(-1.0, 1.0)
        }
    }

    /** The same left/right rate as a Dolphin input expression on the given sensor device. */
    fun turnLeftExpression(mode: GyroHorizontal, sensorDevice: String): String {
        fun s(name: String) = "`$sensorDevice:$name`"
        return when (mode) {
            GyroHorizontal.UPRIGHT -> s("Gyro Roll Right")
            GyroHorizontal.FLAT -> s("Gyro Yaw Left")
            GyroHorizontal.AUTO -> "((${s("Gyro Pitch Up")} * ${s("Accel Right")} + ${s("Gyro Roll Right")} * ${s("Accel Forward")} + " +
                "${s("Gyro Yaw Left")} * ${s("Accel Up")}) / 9.81)"
        }
    }
}
