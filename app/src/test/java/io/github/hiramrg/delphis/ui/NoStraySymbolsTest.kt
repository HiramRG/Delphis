package io.github.hiramrg.delphis.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build check: no emoji or symbol character may reach the screen as text, because phones draw those from
 * their own fonts (different on every brand, crossed boxes on some). Every symbol in Delphis' strings must be
 * one Delphis draws itself (SYMBOL_ICONS, the button glyphs, PlayStation's shapes) or plain punctuation.
 */
class NoStraySymbolsTest {
    private val typography = "·→←↑↓…×–—“”‘’°²≈•›−"
    private val drawnByDelphis = SYMBOL_ICONS.keys.joinToString("") + "ⒶⒷⓍⓎ✚⊖⊕○□△"

    private fun allowed(cp: Int): Boolean {
        if (cp < 0x80) return true
        if (Character.isLetterOrDigit(cp)) return true // accented letters (Spanish menu names Delphis looks for)
        if (cp == 0xFE0F || cp == 0x200B) return true
        val s = String(Character.toChars(cp))
        return s in typography || drawnByDelphis.contains(s)
    }

    @Test fun everySymbolIsDrawnByDelphis() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val stray = mutableListOf<String>()
        val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                literal.findAll(line).forEach { m ->
                    m.groupValues[1].codePoints().forEach { cp ->
                        if (!allowed(cp)) stray += "${f.name}:${i + 1}: '${String(Character.toChars(cp))}' (U+%04X)".format(cp)
                    }
                }
            }
        }
        assertTrue("Symbols Delphis doesn't draw (add them to SYMBOL_ICONS or remove them):\n" + stray.joinToString("\n"), stray.isEmpty())
    }
}
