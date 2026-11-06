package io.github.hiramrg.delphis.core

/** Picks the temperature shown in the in-game banner from Linux thermal zones (type, raw value). */
object Thermal {
    /** Raw sysfs value → °C. Most zones report millidegrees; a few report whole degrees. */
    fun celsius(raw: String): Float? {
        val v = raw.trim().toFloatOrNull() ?: return null
        val c = if (kotlin.math.abs(v) >= 1000f) v / 1000f else v
        return c.takeIf { it in 1f..150f }
    }

    /** Hottest CPU zone ("cpu-1-0-usr", "cpuss-0", ...), or null when none is readable. */
    fun cpuTemp(zones: List<Pair<String, String>>): Float? =
        zones.filter { (type, _) -> type.trim().lowercase().let { it.startsWith("cpu") || it.contains("cpuss") } }
            .mapNotNull { (_, raw) -> celsius(raw) }
            .maxOrNull()
}
