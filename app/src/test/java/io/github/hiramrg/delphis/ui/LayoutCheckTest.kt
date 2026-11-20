package io.github.hiramrg.delphis.ui

import io.github.hiramrg.delphis.platform.DolphinBridge.SaveEntry
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveKind
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.isSpecified
import androidx.test.core.app.ApplicationProvider
import io.github.hiramrg.delphis.core.ButtonLabels
import io.github.hiramrg.delphis.core.GameInfo
import io.github.hiramrg.delphis.core.Platform
import io.github.hiramrg.delphis.platform.GameEntry
import io.github.hiramrg.delphis.platform.GameSession
import io.github.hiramrg.delphis.platform.GameWatch
import io.github.hiramrg.delphis.platform.Prefs
import io.github.hiramrg.delphis.platform.TestHooks
import io.github.hiramrg.delphis.platform.Updater
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import kotlin.math.max
import kotlin.math.min

/**
 * Layout check (runs before every build is published): draws every screen at the Thor's two sizes, a phone
 * and a tablet, and fails if any words are cut off, run off the screen, overlap other words, or a label wraps
 * to more than two lines. Screenshots and a summary go to app/build/layout-check (CI attaches them).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LayoutCheckTest {

    private data class Size(val id: String, val label: String, val qualifiers: String, val oneScreen: Boolean, val labels: ButtonLabels?)

    private data class Problem(val size: String, val screen: String, val what: String, val shot: String)

    private val thor = Size("thor", "Thor top 700×393", "w700dp-h393dp-land-hdpi", false, ButtonLabels.NINTENDO)
    private val bottom = Size("bottom", "Thor bottom 451×393", "w451dp-h393dp-land-hdpi", false, ButtonLabels.NINTENDO)
    private val phone = Size("phone", "Phone 820×370", "w820dp-h370dp-land-hdpi", true, ButtonLabels.XBOX)
    private val tablet = Size("tablet", "Tablet 1480×924", "w1480dp-h924dp-land-mdpi", true, null)
    private val sizes = listOf(thor, bottom, phone, tablet)

    private val out = File("build/layout-check").apply { deleteRecursively(); mkdirs() }
    private val problems = mutableListOf<Problem>()
    /** screen → size id → ok. */
    private val table = linkedMapOf<String, MutableMap<String, Boolean>>()
    private val notes = mutableListOf<String>()

    private val games = listOf(
        game("SMNE01", "New Super Mario Bros. Wii", Platform.WII),
        game("GZ2E01", "The Legend of Zelda: Twilight Princess", Platform.GAMECUBE),
        game("RMGE01", "Super Mario Galaxy", Platform.WII),
        game("GALE01", "Super Smash Bros. Melee", Platform.GAMECUBE),
        game("SOUE01", "The Legend of Zelda: Skyward Sword", Platform.WII),
        game("RMCE01", "Mario Kart Wii", Platform.WII),
        game("GM4E01", "Mario Kart: Double Dash!!", Platform.GAMECUBE),
        game("R8PE01", "Super Paper Mario", Platform.WII),
    )

    private fun game(id: String, title: String, platform: Platform) =
        GameEntry(Uri.parse("content://delphis.test/games/$id"), "$title.rvz", 1_000_000L, 0L, GameInfo(id, title, platform))

    @Volatile private var doing = "starting"
    @Volatile private var doingSince = System.currentTimeMillis()

    private fun step(what: String) {
        val now = System.currentTimeMillis()
        println("[layout] ${(now - doingSince)} ms · $doing")
        doing = what; doingSince = now
    }

    /** Stops a stuck run (instead of CI waiting for hours) with what it was doing and where. */
    private fun startWatchdog(testThread: Thread) {
        val started = System.currentTimeMillis()
        Thread {
            while (true) {
                Thread.sleep(5_000)
                val now = System.currentTimeMillis()
                if (now - doingSince > 150_000 || now - started > 16 * 60_000) {
                    val dump = buildString {
                        appendLine("Layout check stopped: \"$doing\" ran ${(now - doingSince) / 1000} s (whole check ${(now - started) / 1000} s).")
                        testThread.stackTrace.take(70).forEach { appendLine("  at $it") }
                    }
                    println(dump)
                    runCatching { File(out, "hang.txt").writeText(dump); writeSummary(dump) }
                    Runtime.getRuntime().halt(3)
                }
            }
        }.apply { isDaemon = true; name = "layout-watchdog" }.start()
    }

    @Test
    fun layoutCheck() {
        startWatchdog(Thread.currentThread())
        // Fewer frames per second of (virtual) time: animations still finish, with far less drawing.
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(64))
        TestHooks.offline = true
        TestHooks.still = true
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Prefs(app).apply {
            warnNoController = false
            menuMusicOn = false
            menuSounds = false
            markPlayed(games[2].key); markPlayed(games[1].key); markPlayed(games[0].key)
            addPlayTime(games[0].key, 3 * 3_600_000L)
        }

        for (size in listOf(thor, phone, tablet)) withMain(size) { act ->
            show(act, size, "Setup") { act.screen = Screen.Setup }
            show(act, size, "Home") { act.screen = Screen.Home }
            show(act, size, "Game page") { act.openGame(games[0]) }
            show(act, size, "Game page (long title)") { act.openGame(games[1]) }
            show(act, size, "Game page · Picture → Sharpness", after = { TestHooks.pictureTab = null; act.screen = Screen.Home }) {
                TestHooks.pictureTab = 2; act.screen = Screen.Home; settle(100); act.openGame(games[0])
            }
            // Saves: the game page with its Saves button, then the list; Home with "helper has stopped".
            run {
                val now = System.currentTimeMillis()
                TestHooks.saves = listOf(
                    SaveEntry(SaveKind.LATEST, now - 60_000, 40_000_000, 9),
                    SaveEntry(SaveKind.EARLIER, now - 1_200_000, 40_000_000),
                    SaveEntry(SaveKind.EARLIER, now - 8_000_000, 40_000_000),
                    SaveEntry(SaveKind.SLOT, now - 90_000_000, 40_000_000, 1),
                )
                show(act, size, "Game page with saves", after = { act.screen = Screen.Home }) { act.screen = Screen.Home; settle(100); act.openGame(games[0]) }
                show(act, size, "Game page · Saves", after = { TestHooks.savesDialog = false; act.screen = Screen.Home }) {
                    TestHooks.savesDialog = true; act.screen = Screen.Home; settle(100); act.openGame(games[0])
                }
                TestHooks.saves = emptyList()
                show(act, size, "Home · helper stopped", after = { TestHooks.helperStopped = false; act.setupVersion++ }) { TestHooks.helperStopped = true; act.setupVersion++; act.screen = Screen.Home }
            }
            for (s in SettingsSection.entries) {
                if (size.oneScreen && s == SettingsSection.BOTTOM) continue
                show(act, size, "Settings → ${s.label}") { act.screen = Screen.Settings; act.settingsSection = s }
            }
            // The bottom bar with an Xbox controller's hints, in dark mode (where they wrapped on the Galaxy Tab).
            if (size.oneScreen) for (s in listOf(SettingsSection.DISPLAY, SettingsSection.CONTROLS)) {
                show(act, size, "Settings → ${s.label} · Xbox hints", after = { PadGlyphs.labels = size.labels; act.themeMode = "light" }) {
                    PadGlyphs.labels = io.github.hiramrg.delphis.core.ButtonLabels.XBOX; act.themeMode = "dark"
                    act.screen = Screen.Settings; act.settingsSection = s
                }
            }
            show(act, size, "Diagnostics") { act.screen = Screen.Diagnostics }
            // Pop-ups over Home (only the pop-up's own words are checked).
            show(act, size, "Pop-up: update found", popupOnly = true, after = { act.foundPrompt = false; act.updateState = MainActivity.UpdateState.Idle }) {
                act.screen = Screen.Home
                act.updateState = MainActivity.UpdateState.Available(Updater.Release(999, "", 0L, ""))
                act.foundPrompt = true
            }
            show(act, size, "Pop-up: report a problem", popupOnly = true, after = { act.reportOpen = false }) { act.screen = Screen.Home; act.openReport(1) }
            show(act, size, "Notice", popupOnly = true, after = { Notices.dismiss() }) {
                act.screen = Screen.Home
                Notices.problem(act, DelphisCheck.summary(2), "\"Close games\" is off · updates not allowed", "Delphis check") {}
            }
            if (size.oneScreen) {
                show(act, size, "Pop-up: no controller", popupOnly = true, after = { act.noPadPrompt = false }) {
                    act.screen = Screen.Home; act.padConnected = null; act.padNeedsCheck = false; act.noPadPrompt = true
                }
                show(act, size, "Pop-up: check the controller", popupOnly = true, after = { act.noPadPrompt = false; act.padConnected = null; act.padNeedsCheck = false }) {
                    act.screen = Screen.Home; act.padConnected = "Xbox Wireless Controller"; act.padVendor = 0x045e; act.padNeedsCheck = true; act.noPadPrompt = true
                }
                show(act, size, "Pop-up: press A on a new controller", popupOnly = true, after = { act.labelCheck = null }) {
                    act.screen = Screen.Home; act.labelCheck = "test" to "8BitDo Ultimate 2C Wireless Controller"
                }
                show(act, size, "Pop-up: close games", popupOnly = true, after = { act.exitHelperPrompt = null }) {
                    act.screen = Screen.Home; act.exitHelperForPlay = true; act.exitHelperPrompt = {}
                }
                for (step in 1..3) show(act, size, "Pop-up: add a controller, step $step", popupOnly = true, after = { act.addPadClose() }) {
                    act.screen = Screen.Home; act.addPadBrand = 0; act.addPadFound = "Xbox Wireless Controller"; act.addPadPlayer = 1; act.addPadStep = step
                }
                show(act, size, "Settings → Controllers (testing the button)", after = { act.shortcutTest = null }) {
                    act.screen = Screen.Settings; act.settingsSection = SettingsSection.CONTROLS
                    act.shortcutTest = "Nothing arrived. If you pressed the Xbox button, this device keeps it, so use View + Menu (now both work)."
                }
                show(act, size, "Pop-up: game still open", popupOnly = true, after = { act.runningPrompt = false; GameWatch.session = null }) {
                    act.screen = Screen.Home; GameWatch.session = session(games[1], "gamecube"); act.runningPrompt = true
                }
            } else {
                // The Thor's bottom screen beside Home (needs Delphis' Home running on the top screen).
                withBottomCompanion(act) { companion ->
                    act.screen = Screen.Home; act.focusedGame = games[0]
                    check(companion, bottom, "Bottom screen · Home")
                    act.focusedGame = games[1]
                    check(companion, bottom, "Bottom screen · Home (long title)")
                }
            }
        }

        // In game: the Thor's bottom screen, and the panel over the game on one screen.
        val inGame = listOf(
            Triple(games[0], "nsmbw-sideways", "In-game menu"),
            Triple(games[3], "gamecube", "In-game menu (GameCube)"),
            Triple(games[2], "galaxy-rstick", "In-game menu (many controls)"),
        )
        for ((g, preset, name) in inGame) {
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), name)
            inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), name)
            inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), name)
        }
        inGameMenu(bottom, NowPlayingActivity::class.java, session(game("REDE41", "Red Steel", Platform.WII), "redsteel-trackpad"), "In-game menu (trackpad)")
        // One screen with a controller: the map draws that controller (and the panel widens).
        for ((vendor, brand) in listOf(0x045e to "Xbox", 0x054c to "PlayStation", 0x057e to "Switch Pro", 0x2dc8 to "8BitDo")) {
            TestHooks.padVendor = vendor
            for ((g, preset, name) in inGame.take(if (vendor == 0x045e) 3 else 1)) {
                inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), "$name · $brand")
                inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), "$name · $brand")
            }
        }
        // The Picture panel in the in-game menu, with a controller (button icons on tabs and buttons).
        TestHooks.padVendor = 0x045e
        for (tab in listOf(1, 2)) {
            TestHooks.pictureTab = tab
            val (g, preset, _) = inGame.first()
            inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), "In-game Picture · ${if (tab == 1) "Look" else "Sharpness"} · Xbox")
            inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), "In-game Picture · ${if (tab == 1) "Look" else "Sharpness"} · Xbox")
        }
        // The Thor: the same Picture panel on its bottom screen (touch).
        TestHooks.padVendor = null
        for (tab in listOf(1, 2)) {
            TestHooks.pictureTab = tab
            val (g, preset, _) = inGame.first()
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game Picture · ${if (tab == 1) "Look" else "Sharpness"} (Thor)")
        }
        TestHooks.pictureTab = null
        // Change controls, in the same box (controller on one screen, touch on the Thor).
        TestHooks.inGameChange = true
        run {
            val (g, preset, _) = inGame.first()
            TestHooks.padVendor = 0x045e
            inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), "In-game Change controls · Xbox")
            inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), "In-game Change controls · Xbox")
            TestHooks.padVendor = null
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game Change controls (Thor)")
        }
        TestHooks.inGameChange = false
        // The autosave couldn't be loaded when the game started.
        run {
            val (g, preset, _) = inGame.first()
            io.github.hiramrg.delphis.platform.ResumeNotice.failed.value = io.github.hiramrg.delphis.platform.ResumeNotice.Failed(g.info.gameId!!, System.currentTimeMillis())
            TestHooks.padVendor = 0x045e
            inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), "In-game autosave not loaded · Xbox")
            TestHooks.padVendor = null
            io.github.hiramrg.delphis.platform.ResumeNotice.failed.value = io.github.hiramrg.delphis.platform.ResumeNotice.Failed(g.info.gameId!!, System.currentTimeMillis())
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game autosave not loaded (Thor)")
            // Back from Accessibility: the helper runs again, so it offers "Load it".
            TestHooks.helperUp = true
            io.github.hiramrg.delphis.platform.ResumeNotice.failed.value = io.github.hiramrg.delphis.platform.ResumeNotice.Failed(g.info.gameId!!, System.currentTimeMillis(), helperOff = true)
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game autosave not loaded, helper back (Thor)")
            TestHooks.helperUp = false
            io.github.hiramrg.delphis.platform.ResumeNotice.failed.value = null
        }
        TestHooks.padVendor = null
        // The Saves tab: autosaves and Dolphin's slots; then the same tab with the helper stopped.
        run {
            val (g, preset, _) = inGame.first()
            val now = System.currentTimeMillis()
            TestHooks.saves = listOf(
                SaveEntry(SaveKind.LATEST, now - 60_000, 40_000_000, 9),
                SaveEntry(SaveKind.EARLIER, now - 1_200_000, 40_000_000),
                SaveEntry(SaveKind.EARLIER, now - 8_000_000, 40_000_000),
                SaveEntry(SaveKind.EARLIER, now - 30_000_000, 40_000_000),
                SaveEntry(SaveKind.SLOT, now - 90_000_000, 40_000_000, 1),
                SaveEntry(SaveKind.SLOT, now - 300_000_000, 40_000_000, 3),
            )
            TestHooks.savesTab = true
            TestHooks.helperUp = true
            TestHooks.padVendor = 0x045e
            inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), "In-game Saves · Xbox")
            inGameMenu(tablet, InGameMenuActivity::class.java, session(g, preset), "In-game Saves · Xbox")
            TestHooks.padVendor = null
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game Saves (Thor)")
            TestHooks.helperUp = false
            inGameMenu(bottom, NowPlayingActivity::class.java, session(g, preset), "In-game Saves, helper stopped (Thor)")
            TestHooks.padVendor = 0x045e
            inGameMenu(phone, InGameMenuActivity::class.java, session(g, preset), "In-game Saves, helper stopped · Xbox")
            TestHooks.padVendor = null
            TestHooks.savesTab = false
            TestHooks.saves = emptyList()
        }

        step("done")
        writeSummary()
        if (problems.isNotEmpty()) fail("${problems.size} layout problem(s):\n" + problems.joinToString("\n") { "${it.size} · ${it.screen}: ${it.what}" })
    }

    // ------------------------------------------------------------------ activities

    private fun withMain(size: Size, block: (MainActivity) -> Unit) {
        step("${size.id}: starting Delphis")
        RuntimeEnvironment.setQualifiers(size.qualifiers)
        val controller = try { Robolectric.buildActivity(MainActivity::class.java).setup() } catch (e: Throwable) {
            harness(size, "Delphis (main screen)", e); return
        }
        val act = controller.get()
        try {
            act.oneScreen = size.oneScreen
            PadGlyphs.labels = size.labels
            settle(640)
            // Games come from a scan normally; these stand in (set after the start-up refresh of the list).
            act.games = games
            settle(200)
            block(act)
        } catch (e: Throwable) {
            harness(size, "Delphis (main screen)", e)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            settle(50)
        }
    }

    private fun withBottomCompanion(main: MainActivity, block: (Activity) -> Unit) {
        MenuCompanion.show(main) // remembers Delphis' Home (no second display here, so nothing starts)
        RuntimeEnvironment.setQualifiers(bottom.qualifiers)
        val controller = try { Robolectric.buildActivity(MenuCompanionActivity::class.java).setup() } catch (e: Throwable) {
            harness(bottom, "Bottom screen · Home", e); return
        }
        try { block(controller.get()) } catch (e: Throwable) { harness(bottom, "Bottom screen · Home", e) }
        finally { runCatching { controller.pause().stop().destroy() }; settle(50) }
    }

    private fun session(g: GameEntry, preset: String) = GameSession(g.uri, g.fileName, g.info, preset, applied = true)

    private fun inGameMenu(size: Size, cls: Class<out Activity>, s: GameSession, name: String) {
        step("${size.id}: starting $name")
        RuntimeEnvironment.setQualifiers(size.qualifiers)
        PadGlyphs.labels = size.labels
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = s.writeTo(Intent(app, cls)).putExtra(InGameMenuActivity.EXTRA_OPEN, true)
        val controller = try { Robolectric.buildActivity(cls, intent).setup() } catch (e: Throwable) {
            harness(size, name, e); GameWatch.session = null; return
        }
        try { check(controller.get(), size, name, wait = 900) } catch (e: Throwable) { harness(size, name, e) }
        finally {
            runCatching { controller.pause().stop().destroy() }
            GameWatch.session = null
            settle(50)
        }
    }

    // ------------------------------------------------------------------ one screen, checked

    private fun show(act: MainActivity, size: Size, screen: String, popupOnly: Boolean = false, after: () -> Unit = {}, setUp: () -> Unit) {
        try {
            setUp()
            check(act, size, screen, popupOnly)
        } catch (e: Throwable) {
            harness(size, screen, e)
        } finally {
            runCatching { after() }
            settle(150)
        }
    }

    private fun check(act: Activity, size: Size, screen: String, popupOnly: Boolean = false, wait: Long = 700) {
        step("${size.id}: $screen")
        settle(wait)
        val decor = act.window.decorView
        // A panel window (the one-screen in-game menu) keeps its own width; Robolectric makes every window full screen.
        val lp = act.window.attributes
        if (lp.width > 0 && decor.width != lp.width) {
            decor.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(decor.height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, lp.width, decor.height)
        }
        // Robolectric doesn't draw frames, and Compose lays out a changed screen when it draws: lay it out now
        // (twice, for anything placed from another part's size), so the check reads the screen as it is.
        repeat(2) { layOut(decor); settle(128) }
        layOut(decor)
        val shot = "${size.id}/${slug(screen)}.png"
        val found = mutableListOf<String>()
        val roots = composeRoots(decor)
        if (roots.isEmpty()) found += "nothing was drawn"
        val density = act.resources.displayMetrics.density
        for (root in roots) found += analyse(root, density, popupOnly)
        screenshot(decor, File(out, shot))
        found.distinct().forEach { problems += Problem(size.label, screen, it, shot) }
        table.getOrPut(screen) { linkedMapOf() }[size.id] = found.isEmpty()
    }

    private fun harness(size: Size, screen: String, e: Throwable) {
        val where = e.stackTrace.firstOrNull { it.className.startsWith("io.github.hiramrg") }?.let { " at ${it.className.substringAfterLast('.')}:${it.lineNumber}" } ?: ""
        problems += Problem(size.label, screen, "couldn't be drawn: ${e.javaClass.simpleName}: ${e.message?.take(160)}$where", "")
        table.getOrPut(screen) { linkedMapOf() }[size.id] = false
        e.printStackTrace()
    }

    // ------------------------------------------------------------------ the checks

    private class Words(
        val text: String, val node: SemanticsNode, val ink: Rect, val visibleInk: Rect?,
        val layer: Int?, val scroller: Int?, val ancestors: Set<Int>,
    )

    private fun analyse(root: ViewRootForTest, density: Float, popupOnly: Boolean): List<String> {
        val found = mutableListOf<String>()
        val rootNode = root.semanticsOwner.unmergedRootSemanticsNode
        val screen = Rect(0f, 0f, rootNode.size.width.toFloat(), rootNode.size.height.toFloat())
        val tol = 1.5f * density
        val words = mutableListOf<Words>()

        fun walk(node: SemanticsNode, ancestors: List<SemanticsNode>) {
            val chain = ancestors + node
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.trim()
            val editable = node.config.contains(SemanticsProperties.EditableText)
            val cover = chain.any { it.config.getOrNull(CoverText) == true }
            if (!text.isNullOrEmpty() && !editable && !cover) {
                val results = mutableListOf<TextLayoutResult>()
                runCatching { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) }
                val layout = results.firstOrNull()
                val layer = chain.lastOrNull { it.config.getOrNull(PopupLayer) == true }?.id
                if (layout != null && (!popupOnly || layer != null)) {
                    val marquee = chain.any { it.config.getOrNull(MarqueeText) == true }
                    val scroller = ancestors.lastOrNull {
                        it.config.contains(SemanticsProperties.VerticalScrollAxisRange) || it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
                    }?.id
                    // Where the words are drawn: the text's own layout box (inside any padding of the node).
                    val coords = runCatching {
                        node.layoutInfo.getModifierInfo().lastOrNull { it.modifier.javaClass.simpleName.contains("Text") }?.coordinates?.takeIf { it.isAttached }
                    }.getOrNull()
                    val origin = coords?.positionInRoot() ?: node.positionInRoot
                    val box = coords?.boundsInRoot() ?: node.boundsInRoot
                    // (The layout read through semantics may be laid out at the full available width rather than the
                    // text's own width: place each line in the text's box by its alignment.)
                    val boxW = layout.size.width.toFloat()
                    val boxH = layout.size.height.toFloat()
                    val paraW = layout.multiParagraph.width
                    val align = layout.layoutInput.style.textAlign
                    val fs = layout.layoutInput.style.fontSize
                    val fontPx = with(layout.layoutInput.density) { if (fs.isSpecified) fs.toPx() else 14f * density }
                    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
                    var widest = 0f
                    for (i in 0 until layout.lineCount) {
                        var ll = layout.getLineLeft(i); var lr = layout.getLineRight(i)
                        if (paraW > boxW + 1f) {
                            val w = lr - ll
                            when (align) {
                                TextAlign.Center -> { ll = (boxW - w) / 2f; lr = (boxW + w) / 2f }
                                TextAlign.End, TextAlign.Right -> { ll = boxW - w; lr = boxW }
                                else -> { ll = 0f; lr = w }
                            }
                        }
                        widest = max(widest, lr - ll)
                        l = min(l, ll); r = max(r, lr)
                        // The letters, not the line box: Material's 24 sp line height is taller than small text.
                        val base = layout.getLineBaseline(i)
                        t = min(t, base - 0.76f * fontPx); b = max(b, base + 0.22f * fontPx)
                    }
                    if (layout.lineCount > 0 && r > l && b > t) {
                        val ink = Rect(l, t, r, b).translate(origin)
                        val visible = if (box.isEmpty) null else ink.intersect(box).takeIf { !it.isEmpty && it.width > 0f && it.height > 0f }
                        val quote = "\"" + text.take(70) + (if (text.length > 70) "…" else "") + "\""
                        val shown = visible != null || scroller != null
                        // 1. Cut off: more words than fit (ellipsis, max lines, a box too small).
                        if (!marquee && shown) {
                            fun dp(px: Float) = Math.round(px / density)
                            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
                            val why = when {
                                layout.multiParagraph.didExceedMaxLines || ellipsized -> "only ${layout.lineCount} line(s) fit"
                                b > boxH + tol || t < -tol -> "the letters need ${dp(b - min(t, 0f))} dp of height, the box has ${dp(boxH)}"
                                widest > boxW + tol -> "needs ${dp(widest)} dp of width, has ${dp(boxW)}"
                                else -> null
                            }
                            if (why != null) found += "$quote is cut off ($why)"
                        }
                        // 2. Labels wrap to two lines at most (your rule); paragraphs may wrap more.
                        val bold = (layout.layoutInput.style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold
                        if (bold && layout.lineCount > 2 && shown) found += "$quote wraps to ${layout.lineCount} lines (labels can use 2)"
                        if (scroller == null && !marquee && visible != null) {
                            // 3. Cut by the box around it (a clip, a card edge).
                            if (ink.left < box.left - tol || ink.right > box.right + tol || ink.top < box.top - tol || ink.bottom > box.bottom + tol)
                                found += "$quote is cut by the box around it"
                            // 4. Off the screen.
                            if (ink.left < screen.left - tol || ink.right > screen.right + tol || ink.top < screen.top - tol || ink.bottom > screen.bottom + tol)
                                found += "$quote runs off the screen"
                        }
                        if (shown) words += Words(text, node, ink, visible, layer, scroller, ancestors.map { it.id }.toSet())
                    }
                }
            }
            node.children.forEach { walk(it, chain) }
        }
        walk(rootNode, emptyList())
        if (words.isEmpty() && !popupOnly) found += "no words on the screen (it didn't draw)"

        // 5. Words on top of other words (in the same pop-up, or both on the screen itself).
        for (i in words.indices) for (j in i + 1 until words.size) {
            val a = words[i]; val b = words[j]
            if (a.layer != b.layer) continue
            // A button icon inside a hint line is part of that line.
            if (a.node.id in b.ancestors || b.node.id in a.ancestors) continue
            val (ra, rb) = if (a.scroller != null && a.scroller == b.scroller) a.ink to b.ink else (a.visibleInk ?: continue) to (b.visibleInk ?: continue)
            val x = ra.intersect(rb)
            if (x.isEmpty || x.width < 2f * density || x.height < 2f * density) continue
            val smaller = min(ra.width * ra.height, rb.width * rb.height)
            if (smaller <= 0f || x.width * x.height < 0.1f * smaller) continue
            found += "\"${a.text.take(50)}\" overlaps \"${b.text.take(50)}\""
        }
        return found
    }

    /** Runs Compose's pending measure and layout (what drawing a frame would do). */
    private fun layOut(decor: View) {
        for (r in composeRoots(decor)) {
            val done = runCatching { r.javaClass.getMethod("measureAndLayout", Boolean::class.javaPrimitiveType).invoke(r, true) }.isSuccess
            if (!done) runCatching { r.view.draw(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))) }
        }
    }

    private fun Rect.translate(o: Offset) = Rect(left + o.x, top + o.y, right + o.x, bottom + o.y)

    private fun composeRoots(v: View): List<ViewRootForTest> = buildList {
        if (v is ViewRootForTest) add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(composeRoots(v.getChildAt(i)))
    }

    // ------------------------------------------------------------------ helpers

    /** Lets Delphis run for [ms] (virtual time: animations, delays), and background work finish. */
    private fun settle(ms: Long) {
        val looper = shadowOf(Looper.getMainLooper())
        var t = 0L
        while (t < ms) {
            looper.idleFor(Duration.ofMillis(64)); t += 64
            Thread.sleep(2)
        }
        looper.idle()
    }

    private fun screenshot(view: View, file: File) {
        runCatching {
            if (view.width <= 0 || view.height <= 0) return
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            file.parentFile?.mkdirs()
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }.onFailure { if (notes.size < 3) notes += "Screenshot failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}" }
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun writeSummary(stopped: String? = null) {
        val md = StringBuilder()
        md.appendLine("### Layout check: every screen at every size")
        md.appendLine()
        md.appendLine("| Screen | " + sizes.joinToString(" | ") { it.label } + " |")
        md.appendLine("|---|" + sizes.joinToString("") { ":-:|" })
        for ((screen, bySize) in table) md.appendLine("| $screen | " + sizes.joinToString(" | ") { s -> when (bySize[s.id]) { true -> "✓"; false -> "✗"; null -> "—" } } + " |")
        md.appendLine()
        if (problems.isEmpty()) md.appendLine("**No problems:** nothing cut, off the screen or overlapping, and no label over two lines.")
        else {
            md.appendLine("**${problems.size} problem(s), build not published:**")
            md.appendLine()
            problems.forEach { md.appendLine("- ${it.size} · ${it.screen}: ${it.what}" + if (it.shot.isNotEmpty()) " (`${it.shot}`)" else "") }
        }
        notes.forEach { md.appendLine(); md.appendLine("_${it}_") }
        if (stopped != null) { md.appendLine(); md.appendLine("```"); md.appendLine(stopped.take(6000)); md.appendLine("```") }
        File(out, "summary.md").writeText(md.toString())
        File(out, "problems.txt").writeText(problems.joinToString("\n") { "${it.size} · ${it.screen}: ${it.what}" })
    }
}
