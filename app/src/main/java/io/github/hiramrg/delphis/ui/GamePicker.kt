package io.github.hiramrg.delphis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hiramrg.delphis.core.GameDatabase
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.platform.GameEntry

/**
 * The "how do you want to play?" choice for one game. It's shared state, so the top screen
 * (game details + preview) and the bottom-screen picker show and change the same selection.
 */
class GamePicker(private val act: MainActivity, val game: GameEntry) {
    val suggestion = GameDatabase.suggest(game.info)
    var custom by mutableStateOf(act.store.customFor(game.key))
    val choices: List<Preset> get() = listOfNotNull(custom) + suggestion.choices
    val alternatives: List<Preset> get() = suggestion.alternatives
    val everyPreset: List<Preset> get() = choices + alternatives
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
        act.prefs.setPresetFor(game.key, if (rememberChoice) chosen.id else null)
        act.play(game, chosen)
    }

    fun saveEdited(edited: Preset) {
        act.store.save(edited)
        custom = edited
        selected = edited
        editing = false
    }
}
