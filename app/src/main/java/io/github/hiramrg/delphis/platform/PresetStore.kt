package io.github.hiramrg.delphis.platform

import android.content.Context
import io.github.hiramrg.delphis.core.EmulatedController
import io.github.hiramrg.delphis.core.Mapping
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.PresetLibrary
import io.github.hiramrg.delphis.core.Target
import io.github.hiramrg.delphis.core.ThorControl
import org.json.JSONArray
import org.json.JSONObject

/** Built-in presets plus the user's edited ("custom") presets, one per game. */
class PresetStore(context: Context) {
    private val sp = context.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)

    fun customIdFor(gameKey: String) = "custom-" + gameKey.replace(Regex("[^A-Za-z0-9]"), "").take(40)

    fun byId(id: String): Preset? = PresetLibrary.byId(id) ?: sp.getString(id, null)?.let(::decode)

    fun customFor(gameKey: String): Preset? = byId(customIdFor(gameKey))

    fun save(preset: Preset) = sp.edit().putString(preset.id, encode(preset).toString()).apply()

    fun delete(id: String) = sp.edit().remove(id).apply()

    private fun encode(p: Preset) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("summary", p.summary); put("controller", p.controller.name)
        put("experimental", p.experimental)
        put("tips", JSONArray(p.tips))
        put("options", JSONObject(p.options))
        put("mappings", JSONArray().apply {
            p.mappings.forEach { m ->
                put(JSONObject().apply {
                    put("control", m.control.name); put("action", m.action)
                    when (val t = m.target) {
                        is Target.Buttons -> { put("type", "buttons"); put("keys", JSONArray(t.keys)) }
                        is Target.GyroPointer -> { put("type", "gyro"); put("invertX", t.invertX); put("invertY", t.invertY); put("precision", t.precision) }
                        is Target.Directional -> {
                            put("type", "dir")
                            put("up", t.up ?: JSONObject.NULL); put("down", t.down ?: JSONObject.NULL)
                            put("left", t.left ?: JSONObject.NULL); put("right", t.right ?: JSONObject.NULL)
                        }
                    }
                })
            }
        })
    }

    private fun decode(json: String): Preset? = try {
        val o = JSONObject(json)
        val maps = o.getJSONArray("mappings")
        val opts = o.getJSONObject("options")
        val tips = o.getJSONArray("tips")
        Preset(
            id = o.getString("id"),
            name = o.getString("name"),
            summary = o.getString("summary"),
            controller = EmulatedController.valueOf(o.getString("controller")),
            mappings = (0 until maps.length()).map { i ->
                val m = maps.getJSONObject(i)
                val target = if (m.getString("type") == "gyro") {
                    Target.GyroPointer(m.optBoolean("invertX", false), m.optBoolean("invertY", false), m.optBoolean("precision", false))
                } else if (m.getString("type") == "buttons") {
                    val keys = m.getJSONArray("keys")
                    Target.Buttons((0 until keys.length()).map { keys.getString(it) })
                } else {
                    fun s(k: String) = if (m.isNull(k)) null else m.getString(k)
                    Target.Directional(s("up"), s("down"), s("left"), s("right"))
                }
                Mapping(ThorControl.valueOf(m.getString("control")), target, m.getString("action"))
            },
            options = opts.keys().asSequence().associateWith { opts.getString(it) },
            tips = (0 until tips.length()).map { tips.getString(it) },
            experimental = o.optBoolean("experimental", false),
        )
    } catch (_: Exception) { null }
}
