package io.github.hiramrg.delphis.core

/** Pure helpers for the status icons in Home's bottom bar (Wi-Fi, Do Not Disturb, battery). */
object StatusLevels {
    /** Wi-Fi RSSI (dBm) → 0..4 bars, like Android's own status bar. Unknown → 4 (connected, no reading). */
    fun wifiBars(rssi: Int?): Int = when {
        rssi == null || rssi == Int.MIN_VALUE || rssi >= 0 -> 4
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    /** NotificationManager interruption filter: 1 = all (DND off), 0 = unknown; anything else means DND is on. */
    fun dndOn(filter: Int): Boolean = filter > 1

    /** Battery is shown orange at 15 % or less, unless it's charging. */
    fun batteryLow(level: Int, charging: Boolean): Boolean = !charging && level in 0..15
}
