package io.github.hiramrg.delphis.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect

/** "Try it" in Settings: makes the chosen motor buzz once through Android, so you can feel whether it works. */
object RumbleTest {
    /** Returns a short line about what happened (shown under the button and logged). */
    fun buzz(context: Context, mode: io.github.hiramrg.delphis.core.Rumble.Mode): String {
        val effect = VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
        // Never let the test take Delphis down (a crash also stops Android's helper until it's turned on again).
        val result = runCatching { when (mode) {
            io.github.hiramrg.delphis.core.Rumble.Mode.OFF -> "Rumble is off."
            io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE -> {
                val v = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                else @Suppress("DEPRECATION") context.getSystemService(android.os.Vibrator::class.java)
                if (v == null || !v.hasVibrator()) "This device has no motor Android can use."
                else { v.vibrate(effect); "It should have buzzed. Felt nothing? This device may not have a motor." }
            }
            io.github.hiramrg.delphis.core.Rumble.Mode.PAD -> {
                val pad = DeviceInfo.gamepads().firstOrNull { it.isExternal } ?: DeviceInfo.gamepads().firstOrNull()
                when {
                    pad == null -> "No controller connected."
                    Build.VERSION.SDK_INT >= 31 && pad.vibratorManager.vibratorIds.isNotEmpty() -> {
                        pad.vibratorManager.vibrate(android.os.CombinedVibration.createParallel(effect))
                        "${pad.name} should have buzzed."
                    }
                    @Suppress("DEPRECATION") pad.vibrator.hasVibrator() -> { @Suppress("DEPRECATION") pad.vibrator.vibrate(effect); "${pad.name} should have buzzed." }
                    else -> "${pad.name} has no motor Android can use, so it can't rumble."
                }
            }
        } }.getOrElse { e ->
            CrashLog.event("rumble test failed: $e")
            "Android didn't let Delphis test the motor."
        }
        CrashLog.event("rumble test ($mode): $result")
        return result
    }
}
