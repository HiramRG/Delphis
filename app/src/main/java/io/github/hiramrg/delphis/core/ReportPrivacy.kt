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
            .replace(Regex("""/storage/\S+"""), "(path)")
            .replace(Regex("""/sdcard/\S+"""), "(path)")
            .replace(Regex("""Username=[^,\s]*"""), "Username=(hidden)")
            .replace(Regex("""account=(?!\(not signed in\))[^,\n]+"""), "account=(hidden)")
            .replace(Regex("""(?i)(api ?key|token|password)\s*[=:]\s*\S+"""), "$1=(hidden)")
        // The account names themselves, anywhere else in the report (events like "signed in as <name>").
        names.filterNotNull().map { it.trim() }.filter { it.length >= 3 }.distinct().forEach { n ->
            out = out.replace(Regex("""(?i)(?<![\w])${Regex.escape(n)}(?![\w])"""), "(hidden)")
        }
        return out
    }
}
