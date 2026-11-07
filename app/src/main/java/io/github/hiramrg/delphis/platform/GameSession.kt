package io.github.hiramrg.delphis.platform

import android.content.Intent
import android.net.Uri
import io.github.hiramrg.delphis.core.GameInfo
import io.github.hiramrg.delphis.core.Platform

/** What the bottom-screen view needs to know about the running game (passed as Intent extras). */
data class GameSession(
    val uri: Uri,
    val fileName: String,
    val info: GameInfo,
    val presetId: String,
    val applied: Boolean,
    /** Package of the frontend (RetroHrai, Cocoon…) that started this game; Home goes back there. */
    val returnTo: String? = null,
) {
    val key: String get() = info.gameId ?: fileName

    fun writeTo(i: Intent): Intent = i.apply {
        putExtra("uri", uri.toString())
        putExtra("file", fileName)
        putExtra("game_id", info.gameId)
        putExtra("title", info.title)
        putExtra("platform", info.platform.name)
        putExtra("preset", presetId)
        putExtra("applied", applied)
        putExtra("return_to", returnTo)
    }

    companion object {
        fun from(i: Intent): GameSession? {
            val uri = i.getStringExtra("uri") ?: return null
            return GameSession(
                Uri.parse(uri),
                i.getStringExtra("file") ?: "",
                GameInfo(
                    i.getStringExtra("game_id"),
                    i.getStringExtra("title") ?: "Game",
                    runCatching { Platform.valueOf(i.getStringExtra("platform")!!) }.getOrDefault(Platform.WII),
                ),
                i.getStringExtra("preset") ?: "",
                i.getBooleanExtra("applied", false),
                i.getStringExtra("return_to"),
            )
        }
    }
}
