package io.github.hiramrg.delphis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hiramrg.delphis.core.GameDatabase
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.Players
import io.github.hiramrg.delphis.platform.GameEntry

/**
 * The "how do you want to play?" choice for one game. It's shared state, so the top screen
 * (game details + preview) and the bottom-screen picker show and change the same selection.
 */
class GamePicker(private val act: MainActivity, val game: GameEntry) {
    val suggestion = GameDatabase.suggest(game.info)
    /** Bumped when presets are saved, reset or deleted, so the lists re-read the store. */
    private var version by mutableStateOf(0)
    /**
     * Several players (or the Thor sitting out): gyro, tilt and trackpad presets aren't offered, so
     * everyone has the same controls. Set by the game page from the connected controllers.
     */
    var multiplayer by mutableStateOf(false)
        private set
    private fun offered(list: List<Preset>) = if (multiplayer) list.filter { !Players.usesThorOnly(it) } else list
    /** The game's suggestions (edited versions in place of their originals). */
    val choices: List<Preset> get() { version; return offered(act.store.withEdits(game.key, suggestion.choices)) }
    val alternatives: List<Preset> get() { version; return offered(act.store.withEdits(game.key, suggestion.alternatives)) }
    /** Presets the user saved as new for this game. */
    val mine: List<Preset> get() { version; return offered(act.store.mine(game.key)) }

    fun updateMultiplayer(on: Boolean) {
        if (multiplayer == on) return
        multiplayer = on
        if (on) selected?.let { s -> if (Players.usesThorOnly(s)) choose(Players.multiplayerPreset(act.store.root(s)).let { m -> everyPreset.firstOrNull { act.store.root(it).id == m.id } } ?: choices.firstOrNull()) }
        else remembered()?.let { choose(it) }
    }
    val groups: PresetGroups get() = PresetGroups(choices, alternatives, mine)
    /** Same order as on screen. */
    val everyPreset: List<Preset> get() = groups.ordered
    /** The game's first suggested preset, tagged "(suggested)" in the lists. */
    val suggestedId: String? get() = suggestion.choices.firstOrNull()?.id
    fun isSuggested(p: Preset) = act.store.root(p).id == suggestedId
    fun isEdited(p: Preset) = act.store.isEdited(p.id)

    private fun remembered() = act.prefs.presetFor(game.key)?.let { act.store.forGame(game.key, it) }

    /** The preset in use: what Play starts and what's marked "✓ Selected". */
    var selected by mutableStateOf(remembered() ?: if (suggestion.askBeforePlaying) null else act.store.withEdits(game.key, suggestion.choices).firstOrNull())
        private set
    /** Where the ring is in the list (D-pad): the map previews it. Ⓐ makes it the preset in use. */
    var focused by mutableStateOf(selected)
        private set
    /** True while the ring is on another preset than the one in use. */
    val browsing: Boolean get() = focused != null && focused?.id != selected?.id

    var rememberChoice by mutableStateOf(true)
    var editing by mutableStateOf(false)

    /** Makes [p] the preset in use (and moves the ring to it). */
    fun choose(p: Preset?) { selected = p; focused = p }

    /** Moves the ring up/down the list (D-pad). */
    fun move(delta: Int) {
        val all = everyPreset
        if (all.isEmpty()) return
        val i = all.indexOfFirst { it.id == focused?.id }
        focused = all[if (i < 0) 0 else (i + delta).coerceIn(0, all.lastIndex)]
    }

    /** Ⓐ: picks the preset under the ring; if it's already the one in use, plays. */
    fun confirm() { if (browsing) choose(focused) else play() }

    fun play() {
        if (browsing) choose(focused)
        val chosen = selected ?: return
        // In multiplayer, playing the plain version of a remembered gyro preset keeps the gyro one remembered for solo play.
        val rememberedNow = remembered()
        val keepSolo = multiplayer && rememberedNow != null && Players.multiplayerPreset(act.store.root(rememberedNow)).id == act.store.root(chosen).id
        if (!keepSolo) act.prefs.setPresetFor(game.key, if (rememberChoice) chosen.id else null)
        act.play(game, chosen)
    }

    /** The preset Edit opens: the one on the map. */
    val editTarget: Preset? get() = focused ?: selected

    /** Saves an edit: as this game's version of the preset ([newName] null) or as a new preset. */
    fun saveEdited(edited: Preset, base: Preset, newName: String?, keepId: Boolean = false) {
        val saved = when {
            keepId -> edited.also { act.store.save(it) } // one of My presets, kept under its own id
            newName == null -> act.store.saveEdited(game.key, base, edited)
            else -> act.store.saveNew(game.key, edited, newName)
        }
        // A remembered original now means its edited version.
        if (!keepId && newName == null && act.prefs.presetFor(game.key) in setOf(base.id, act.store.root(base).id)) act.prefs.setPresetFor(game.key, saved.id)
        version++
        choose(saved)
        editing = false
    }

    /** Throws away this game's edited version of [p] and goes back to the original. */
    fun resetToDefault(p: Preset) {
        val original = act.store.root(p)
        if (original.id == p.id) return
        act.store.delete(p.id)
        if (act.prefs.presetFor(game.key) == p.id) act.prefs.setPresetFor(game.key, original.id)
        version++
        if (selected?.id == p.id) selected = original
        if (focused?.id == p.id) focused = original
    }

    /** Deletes one of "My presets". */
    fun deleteMine(p: Preset) {
        if (!act.store.isMine(p.id)) return
        act.store.delete(p.id)
        if (act.prefs.presetFor(game.key) == p.id) act.prefs.setPresetFor(game.key, null)
        version++
        val fallback = choices.firstOrNull()
        if (selected?.id == p.id) selected = fallback
        if (focused?.id == p.id) focused = selected
    }
}
