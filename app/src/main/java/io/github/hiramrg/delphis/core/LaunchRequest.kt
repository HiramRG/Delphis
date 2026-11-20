package io.github.hiramrg.delphis.core

/**
 * Other apps (frontends) can ask Delphis to start a game. Delphis only accepts what a frontend really sends:
 * a game file (by its name) given as a plain path, a file:// link or another app's content:// link. It never
 * passes on a link to Delphis' own files, since Delphis hands the game's link to Dolphin with read access.
 */
object LaunchRequest {
    fun accept(scheme: String?, authority: String?, displayName: String, ownPackage: String): Boolean {
        val okScheme = when (scheme?.lowercase()) {
            null, "", "file" -> true
            "content" -> authority != null && authority != ownPackage && !authority.startsWith("$ownPackage.")
            else -> false
        }
        val lower = displayName.lowercase()
        return okScheme && (GameIdReader.isCandidateFile(displayName) || EXTRA.any { lower.endsWith(".$it") })
    }

    /** Also started by Dolphin, though not in Delphis' library (homebrew). */
    private val EXTRA = setOf("dol", "elf")
}
