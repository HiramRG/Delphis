package io.github.hiramrg.delphis.core

/**
 * What leaves the device when a diagnostics report is copied, shared or sent with Report a problem.
 * Delphis' own Diagnostics screen still shows everything; only the copy that goes out is cleaned:
 * account names (wherever they appear, e.g. "signed in as …"), keys and tokens, folder links and storage paths.
 */
object ReportPrivacy {
    fun sanitize(report: String, names: Collection<String?> = emptyList()): String {
        var out = report
            .replace(Regex("""content://\S+"""), "(folder)")
            // Whole paths, spaces included (folder names can hold names), up to the end of the field.
            .replace(Regex("""(/storage/|/sdcard/|/data/|/mnt/)[^\n,;\])"'`|]*"""), "(path)")
            .replace(Regex("""Username=[^,\s]*"""), "Username=(hidden)")
            .replace(Regex("""account=(?!\(not signed in\))[^,\n]+"""), "account=(hidden)")
            .replace(Regex("""(?i)"(api_?key|api_?token|token|password|key)"\s*:\s*"[^"]*""""), "\"$1\":\"(hidden)\"")
            .replace(Regex("""(?i)(api ?key|token|password)\s*[=:]\s*\S+"""), "$1=(hidden)")
            // Any account named in events, past ones too ("signed in to RetroAchievements as …").
            .replace(Regex("""(signed in to RetroAchievements as )\S+"""), "$1(hidden)")
            // Controller names often carry their owner's name ("Ana's Xbox Controller").
            .replace(Regex("""\b(?!(?:Dolphin|Delphis|Android|Thor|Samsung|Home|GameTDB|There|It|RA|CI|Activity|APK)['’]s\b)\p{Lu}[\p{L}\-]*['’]s\b"""), "(name)'s")
        // The account names themselves, anywhere else in the report (events like "signed in as <name>").
        names.filterNotNull().map { it.trim() }.filter { it.length >= 3 }.distinct().forEach { n ->
            out = out.replace(Regex("""(?i)(?<![\w])${Regex.escape(n)}(?![\w])"""), "(hidden)")
        }
        return out
    }
}
