package io.github.hiramrg.delphis.core

/**
 * How a GameCube game's 4:3 picture fills the Thor's 16:9 top screen.
 *  - 16:9 "More of the world": Dolphin's own widescreen code for the game when it ships one (Gecko /
 *    Action Replay, turned on in the game's settings file with cheats on for that game only), otherwise
 *    Dolphin's widescreen hack (or stretch, if the player turned the hack off).
 *  - 16:9 "Stretch": Dolphin stretches the picture.
 *  - 4:3: Dolphin's 4:3 picture; Delphis draws the sides (black, pattern or a colour from the cover).
 * Dolphin reads these settings when a game starts.
 */
object GameScreen {
    enum class Shape { STANDARD, WIDE }
    enum class Fill { WORLD, STRETCH }
    enum class Sides(val label: String) { BLACK("Black"), PATTERN("Pattern"), COLOUR("Colour") }

    data class Choice(val shape: Shape, val fill: Fill, val sides: Sides)

    enum class Method { PATCH, HACK, STRETCH, STANDARD }

    fun method(c: Choice, gameId: String?, useHack: Boolean): Method = when {
        c.shape == Shape.STANDARD -> Method.STANDARD
        c.fill == Fill.STRETCH -> Method.STRETCH
        WidescreenPatches.forGame(gameId) != null -> Method.PATCH
        useHack -> Method.HACK
        else -> Method.STRETCH
    }

    /** The look line under the Screen switch. */
    fun lookLabel(c: Choice, gameId: String?, useHack: Boolean): String = when (method(c, gameId, useHack)) {
        Method.PATCH, Method.HACK -> "More of the world"
        Method.STRETCH -> "Stretch"
        Method.STANDARD -> "Sides: ${c.sides.label.lowercase()}"
    }

    // Dolphin's AspectMode: 1 = Force 16:9, 2 = Force 4:3, 3 = Stretch to window.
    private const val FORCE_WIDE = "1"
    private const val FORCE_STANDARD = "2"
    private const val STRETCH = "3"
    /** Marks the cheats setting Delphis turned on (so it only ever turns off its own). */
    private const val MARK = "# Delphis: cheats on for the widescreen code"

    /** Writes [c] into a GameCube game's Dolphin settings file (GameSettings/<id>.ini). */
    fun applyToGameIni(existing: String, c: Choice, gameId: String?, useHack: Boolean): String = IniEditor(existing).apply {
        val m = method(c, gameId, useHack)
        val code = WidescreenPatches.forGame(gameId)
        set("Video_Settings", mapOf(
            "AspectRatio" to when (m) { Method.PATCH, Method.HACK -> FORCE_WIDE; Method.STRETCH -> STRETCH; Method.STANDARD -> FORCE_STANDARD },
            "wideScreenHack" to if (m == Method.HACK) "True" else "False",
        ))
        if (code != null) {
            val line = "$" + code.name
            if (m == Method.PATCH) addLine(code.kind.enabledSection, line) else removeLine(code.kind.enabledSection, line)
        }
        if (m == Method.PATCH) {
            if (get("Core", "EnableCheats") != "True") { set("Core", mapOf("EnableCheats" to "True")); addLine("Core", MARK) }
        } else if (hasLine("Core", MARK)) {
            remove("Core", "EnableCheats"); removeLine("Core", MARK)
        }
    }.toString()
}
