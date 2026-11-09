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
    var custom by mutableStateOf(act.store.customFor(game.key))
    /**
     * Several players (or the Thor sitting out): gyro, tilt and trackpad presets aren't offered, so
     * everyone has the same controls. Set by the game page from the connected controllers.
     */
    var multiplayer by mutableStateOf(false)
        private set
    private fun offered(list: List<Preset>) = if (multiplayer) list.filter { !Players.usesThorOnly(it) } else list
    val choices: List<Preset> get() = offered(listOfNotNull(custom) + suggestion.choices)
    val alternatives: List<Preset> get() = offered(suggestion.alternatives)

    fun updateMultiplayer(on: Boolean) {
        if (multiplayer == on) return
        multiplayer = on
        if (on) selected?.let { s -> if (Players.usesThorOnly(s)) selected = Players.multiplayerPreset(s).takeIf { m -> everyPreset.any { it.id == m.id } } ?: choices.firstOrNull() }
        else act.prefs.presetFor(game.key)?.let { act.store.byId(it) }?.let { selected = it }
    }
    /** Same order as on screen: main list, gyro folder, other layouts. */
    val everyPreset: List<Preset> get() = PresetGroups(choices, alternatives).ordered
    /** The game's first suggested preset, tagged "(suggested)" in the lists. */
    val suggestedId: String? get() = suggestion.choices.firstOrNull()?.id
    val recommendFirst: Boolean get() = !suggestion.askBeforePlaying && custom == null

    var selected by mutableStateOf(
        act.prefs.presetFor(game.key)?.let { act.store.byId(it) }
            ?: if (suggestion.askBeforePlaying) null else suggestion.choices.firstOrNull(),
    )
    var rememberChoice by mutableStateOf(true)
    var editing by mutableStateOf(false)

    /** Moves the selection up/down the list (D-pad). */
    fun move(delta: Int) {
        val all = everyPreset
        if (all.isEmpty()) return
        val i = all.indexOfFirst { it.id == selected?.id }
        selected = all[if (i < 0) 0 else (i + delta).coerceIn(0, all.lastIndex)]
    }

    fun play() {
        val chosen = selected ?: return
        // In multiplayer, playing the plain version of a remembered gyro preset keeps the gyro one remembered for solo play.
        val remembered = act.prefs.presetFor(game.key)?.let { act.store.byId(it) }
        val keepSolo = multiplayer && remembered != null && Players.multiplayerPreset(remembered).id == chosen.id
        if (!keepSolo) act.prefs.setPresetFor(game.key, if (rememberChoice) chosen.id else null)
        act.play(game, chosen)
    }

    fun saveEdited(edited: Preset) {
        act.store.save(edited)
        custom = edited
        selected = edited
        editing = false
    }
}
