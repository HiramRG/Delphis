package io.github.hiramrg.delphis.core

import java.util.zip.Deflater
import org.junit.Test

private fun check(c: Boolean, msg: String) { if (!c) throw AssertionError(msg) }

private fun disc(id: String, title: String, wii: Boolean): ByteArray {
    val b = ByteArray(0x80)
    id.toByteArray().copyInto(b, 0)
    title.toByteArray().copyInto(b, 0x20)
    if (wii) byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()).copyInto(b, 0x18)
    else byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D).copyInto(b, 0x1C)
    return b
}

private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
private fun le64(v: Long) = le32(v.toInt()) + le32((v shr 32).toInt())


/** Same checks used during development; runs with `gradle test`. */
class CoreLogicTest {
    @Test
    fun everything() {
        val iso = ByteArray(0x1000); disc("RMGE01", "Super Mario Galaxy", true).copyInto(iso)
        val g = GameIdReader.identify(iso, "x.iso")
        check(g.gameId == "RMGE01" && g.platform == Platform.WII && g.title == "Super Mario Galaxy", "ISO header $g")
        val rvz = ByteArray(0x200); "RVZ\u0001".toByteArray(Charsets.ISO_8859_1).copyInto(rvz, 0); disc("RMCP01", "Mario Kart Wii", true).copyInto(rvz, 0x58)
        check(GameIdReader.identify(rvz, "mk.rvz").gameId == "RMCP01", "RVZ header")
        val wbfs = ByteArray(0x400); "WBFS".toByteArray().copyInto(wbfs, 0); disc("SMNE01", "NSMB", true).copyInto(wbfs, 0x200)
        check(GameIdReader.identify(wbfs, "a.wbfs").gameId == "SMNE01", "WBFS header")
        val gc = ByteArray(0x100); disc("GALE01", "Melee", false).copyInto(gc)
        check(GameIdReader.identify(gc, "melee.iso").platform == Platform.GAMECUBE, "GC magic")
        // GCZ: header + 1 block pointer + hash + zlib(block0)
        val raw = ByteArray(0x8000); disc("SB4E01", "Super Mario Galaxy 2", true).copyInto(raw)
        val d = Deflater(); d.setInput(raw); d.finish(); val buf = ByteArray(0x10000); val n = d.deflate(buf); val comp = buf.copyOf(n)
        val gcz = byteArrayOf(1, 0xC0.toByte(), 0x0B, 0xB1.toByte()) + le32(0) + le64(comp.size.toLong()) + le64(raw.size.toLong()) + le32(0x8000) + le32(1) + le64(0) + le32(0) + comp
        val gi = GameIdReader.identify(gcz, "whatever.gcz")
        check(gi.gameId == "SB4E01" && gi.platform == Platform.WII, "GCZ inflate $gi")
        val ps2 = ByteArray(0x1000) // zeros, like a PS2/PC ISO
        check(GameIdReader.identify(ps2, "God of War [SCUS97399].iso").platform == Platform.UNKNOWN, "non-GC/Wii filtered")
        val wad = le32(0x20).reversedArray() + "Is\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + ByteArray(0x40)
        check(GameIdReader.identify(wad, "Super Mario Bros (USA).wad").platform == Platform.WII, "WAD")
        check(GameIdReader.coverUrls("RMGE01").first() == "https://art.gametdb.com/wii/coverHQ/US/RMGE01.png", "cover url")
        check(GameIdReader.coverUrls("RMGP01").first().contains("/EN/"), "cover url PAL")

        check(GameDatabase.suggest(GameInfo("RMCE01","",Platform.WII)).run { askBeforePlaying && choices.size == 3 }, "MKW asks")
        check(GameDatabase.suggest(GameInfo("SMNP01","",Platform.WII)).choices.first().id == "nsmbw-sideways", "NSMB sideways")
        check(GameDatabase.suggest(GameInfo("GALE01","",Platform.GAMECUBE)).choices.first().id == "gamecube", "GC fallback")
        check(GameDatabase.suggest(GameInfo(null,"Super Mario Galaxy 2",Platform.WII)).matchedTitle == "Super Mario Galaxy 2", "keyword match")

        val cp = ControllerProfile(devices = listOf("Android/0/Thor Controller", "Android/1/Thor Controller"))
        val prof = DolphinConfigWriter.renderProfile(PresetLibrary.GALAXY_RIGHT_STICK, cp)
        check(prof.contains("Device = Android/0/Thor Controller"), "device line")
        check(prof.contains("Buttons/A = `Android/0/Thor Controller:Button B` | `Android/1/Thor Controller:Button B` | `Android/0/Thor Controller:Button A` | `Android/1/Thor Controller:Button A`"), "qualified OR across devices")
        check(prof.contains("IR/Up = `Android/0/Thor Controller:Axis 14-`"), "IR on RZ")
        val cal = ControllerProfile(devices = listOf("Android/0/AYN Gamepad"), calibrated = mapOf(
            "FACE_EAST" to listOf("Button A"), "FACE_SOUTH" to listOf("Button B"),
            "RIGHT_STICK:UP" to listOf("Axis 13-"), "R2" to listOf("Axis 22+")))
        val p2 = DolphinConfigWriter.renderProfile(PresetLibrary.GALAXY_RIGHT_STICK, cal)
        check(p2.contains("Buttons/A = `Android/0/AYN Gamepad:Button A` | `Android/0/AYN Gamepad:Button B`"), "calibrated face")
        check(p2.contains("IR/Up = `Android/0/AYN Gamepad:Axis 13-`") && p2.contains("Buttons/B = `Android/0/AYN Gamepad:Axis 22+`"), "calibrated axes")
        check(ControllerProfile.decodeCalibration(ControllerProfile.encodeCalibration(cal.calibrated)) == cal.calibrated, "calibration roundtrip")
        val wheel = DolphinConfigWriter.renderProfile(PresetLibrary.MKW_WHEEL_GYRO, cp)
        check(wheel.contains("Tilt/Left = `Android/0/Device Sensors:Accel Right` / 5.5"), "gyro tilt")
        check(splitAction("Shoot Star Bit (B)") == ("Shoot Star Bit" to "B") && splitAction("Move") == ("Move" to null), "split action")

        val existing = "[Core]\nCPUThread = True\n\n[Controls]\nPadProfile1 = Old\nFoo = 1\n\n[Video_Settings]\nX = 1\n"
        val merged = DolphinConfigWriter.mergeGameIni(existing, PresetLibrary.MKW_NUNCHUK)
        check(merged.contains("WiimoteProfile1 = Delphis-mkw-nunchuk") && !merged.contains("PadProfile1") && merged.contains("CPUThread = True") && merged.contains("[Video_Settings]"), "merge")
        val again = DolphinConfigWriter.mergeGameIni(merged, PresetLibrary.MKW_WHEEL_STICK)
        check(again.lines().count { it.startsWith("WiimoteProfile1") } == 1 && again.contains("mkw-wheel-stick"), "replace")
        check(DolphinConfigWriter.mergeGameIni("", PresetLibrary.GAMECUBE) == "[Controls]\nPadProfile1 = Delphis-gamecube\n", "fresh")
        check(!DolphinConfigWriter.clearGameIni(again).contains("WiimoteProfile1"), "clear")
        val global = DolphinConfigWriter.mergeGlobalConfig("[Wiimote1]\nSource = 1\nButtons/A = `Old`\n\n[Wiimote2]\nSource = 0\n", PresetLibrary.NSMB_SIDEWAYS, cp)
        check(global.contains("Source = 1") && !global.contains("`Old`") && global.contains("Options/Sideways Wiimote = True") && global.contains("[Wiimote2]\nSource = 0"), "global merge")
        check(DolphinConfigWriter.needsBaseConfig(null) && DolphinConfigWriter.needsBaseConfig("") && !DolphinConfigWriter.needsBaseConfig("[Wiimote1]\nSource = 1\n"), "base config detection")
        val base = DolphinConfigWriter.baseConfig(listOf("Config", "WiimoteNew.ini"), cp)
        check(base.startsWith("[Wiimote1]\nDevice = Android/0/Thor Controller") && base.contains("[Wiimote4]") && base.contains("[BalanceBoard]"), "base wiimote ini")
        check(DolphinConfigWriter.baseConfig(listOf("Config", "GCPadNew.ini"), cp).startsWith("[GCPad1]"), "base gcpad ini")
        PresetLibrary.ALL.forEach { DolphinConfigWriter.renderProfile(it, cp) }
        check(PresetLibrary.ALL.map { it.id }.toSet().size == PresetLibrary.ALL.size, "unique ids")
        }

    @Test
    fun presetEditor() {
        val base = PresetLibrary.GALAXY_RIGHT_STICK
        val opts = ActionCatalog.buttonOptions(base.controller)
        val two = opts.first { it.label == "2 button" }
        val edited = ActionCatalog.applyEdits(base, mapOf(ThorControl.FACE_SOUTH to two, ThorControl.R3 to ActionCatalog.NOTHING), "custom-RMGE01")
        check(edited.id == "custom-RMGE01" && edited.name == "Pointer on Right Stick (Custom)", "custom id/name")
        check(edited.mappings.first { it.control == ThorControl.FACE_SOUTH }.target == io.github.hiramrg.delphis.core.Target.Buttons(listOf("Buttons/2")), "edited target")
        check(edited.mappings.none { it.control == ThorControl.R3 }, "nothing removes")
        check(edited.mappings.first { it.control == ThorControl.FACE_EAST }.action == "Jump (A)", "unchanged kept")
        val jump = opts.first { it.label == "A button" }
        check(ActionCatalog.applyEdits(base, mapOf(ThorControl.R1 to jump), "c").mappings.first { it.control == ThorControl.R1 }.action == "Jump (A)", "friendly label reused")
        val p = DolphinConfigWriter.renderProfile(PresetLibrary.GALAXY_RIGHT_STICK, ControllerProfile())
        check(p.contains("Buttons/+ = `Android/0/Thor Controller:Start`") && p.contains("Buttons/- = `Android/0/Thor Controller:Start` | `Android/0/Thor Controller:Select`"), "galaxy start pauses with + and -")
        PresetLibrary.ALL.forEach { pr -> ActionCatalog.EDITABLE.forEach { c -> ActionCatalog.optionsFor(c, pr.controller) } }
        }
}
