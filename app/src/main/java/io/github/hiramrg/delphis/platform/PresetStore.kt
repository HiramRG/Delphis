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

/**
 * Built-in presets plus the user's own:
 *  - edited versions ("edit-<game>-<preset id>"): a built-in preset changed for one game. The game uses
 *    it in place of the original, under the same name; "Reset to default" deletes it.
 *  - new presets ("user-<game>-<time>"): saved under a name of the user's, listed in "My presets".
 *  - the old per-game "(Custom)" preset ("custom-<game>") from before, also listed in "My presets".
 */
class PresetStore(context: Context) {
    private val sp = context.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)

    private fun key(gameKey: String) = gameKey.replace(Regex("[^A-Za-z0-9]"), "").take(40)
    fun customIdFor(gameKey: String) = "custom-" + key(gameKey)
    fun editIdFor(gameKey: String, baseId: String) = "edit-" + key(gameKey) + "-" + baseId

    fun byId(id: String): Preset? = PresetLibrary.byId(id) ?: sp.getString(id, null)?.let(::decode)
        // Remembered choices of the removed inverted gyro presets fall back to the plain one.
        ?: PresetLibrary.byId(id.replace(Regex("-i(x|y|xy)$"), ""))

    fun customFor(gameKey: String): Preset? = sp.getString(customIdFor(gameKey), null)?.let(::decode)

    /** The built-in preset an edited version was made from (null if [id] isn't an edited version). */
    fun baseOf(id: String): String? =
        if (!id.startsWith("edit-")) null
        else sp.getString(id, null)?.let { runCatching { JSONObject(it).optString("base") }.getOrNull() }?.takeIf { it.isNotEmpty() }

    fun isEdited(id: String) = baseOf(id) != null
    fun isMine(id: String) = id.startsWith("user-") || id.startsWith("custom-")

    /** The original a preset comes from: itself, or the built-in an edited version was made from. */
    fun root(p: Preset): Preset = baseOf(p.id)?.let { PresetLibrary.byId(it) } ?: p

    fun editedFor(gameKey: String, baseId: String): Preset? = sp.getString(editIdFor(gameKey, baseId), null)?.let(::decode)

    /** [list] with each preset swapped for this game's edited version of it, if there is one. */
    fun withEdits(gameKey: String, list: List<Preset>): List<Preset> = list.map { editedFor(gameKey, it.id) ?: it }

    /** A remembered preset id for this game, as the game uses it (an edited version replaces its original). */
    fun forGame(gameKey: String, id: String): Preset? = byId(id)?.let { editedFor(gameKey, it.id) ?: it }

    /** "My presets" for a game: saved as new (oldest first), then the old "(Custom)" one. */
    fun mine(gameKey: String): List<Preset> {
        val prefix = "user-" + key(gameKey) + "-"
        val saved = sp.all.keys.filter { it.startsWith(prefix) }.sortedBy { it.removePrefix(prefix).toLongOrNull() ?: 0L }
            .mapNotNull { k -> sp.getString(k, null)?.let(::decode) }
        return saved + listOfNotNull(customFor(gameKey))
    }

    /** Saves [edited] as this game's version of [base]'s original, keeping the original's name. */
    fun saveEdited(gameKey: String, base: Preset, edited: Preset): Preset {
        val original = root(base)
        val p = edited.copy(id = editIdFor(gameKey, original.id), name = original.name, summary = original.summary)
        // Back to exactly the original: nothing to keep.
        if (p.mappings == original.mappings) { delete(p.id); return original }
        sp.edit().putString(p.id, encode(p).put("base", original.id).put("game", gameKey).toString()).apply()
        return p
    }

    /** Saves [edited] as a new preset called [name] in this game's "My presets". */
    fun saveNew(gameKey: String, edited: Preset, name: String): Preset {
        val p = edited.copy(id = "user-" + key(gameKey) + "-" + System.currentTimeMillis(), name = io.github.hiramrg.delphis.core.presetTitleCase(name.trim()))
        sp.edit().putString(p.id, encode(p).put("game", gameKey).toString()).apply()
        return p
    }

    /** A free name for a new preset made from [base]: "<name> (My Version)", then "(My Version 2)"… */
    fun newName(gameKey: String, base: Preset): String {
        val stem = root(base).name.removeSuffix(" (Custom)") + " (My Version"
        val taken = mine(gameKey).map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) "$stem)" else "$stem $it)" }.first { it !in taken }
    }

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
            // Trackpad presets lost their "(Experimental)" tag; older saved copies drop it too.
            name = io.github.hiramrg.delphis.core.presetTitleCase(o.getString("name"))
                .replace("Trackpad Aiming (Experimental)", "Trackpad Aiming", ignoreCase = true),
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
