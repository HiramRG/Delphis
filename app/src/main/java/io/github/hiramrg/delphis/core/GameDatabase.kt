package io.github.hiramrg.delphis.core

enum class Platform { GAMECUBE, WII, UNKNOWN }

/** A game as identified from its disc header (or, failing that, its file name). */
data class GameInfo(
    /** 6-character Dolphin game ID, e.g. "RMGE01". Null when it couldn't be read. */
    val gameId: String?,
    val title: String,
    val platform: Platform,
)

/** What the companion recommends for a game. */
data class Suggestion(
    /** Friendly name of the matched entry, or null when only generic presets apply. */
    val matchedTitle: String?,
    /** Question shown above the choices, e.g. "How do you want to race?". */
    val question: String,
    /** Presets offered as first-class choices; the first one is the default. */
    val choices: List<Preset>,
    /** Everything else that could work, shown under "Other presets". */
    val alternatives: List<Preset>,
    /** True when the user should actively pick between [choices] before playing. */
    val askBeforePlaying: Boolean,
)

private data class GameRule(
    /** First three characters of the game ID (region-independent). */
    val idPrefixes: Set<String>,
    val title: String,
    /** Lower-case fragments matched against file names when no game ID is available. */
    val keywords: List<String>,
    val question: String,
    val choices: List<Preset>,
    val askBeforePlaying: Boolean = false,
    /** Shown first under "Other layouts". */
    val more: List<Preset> = emptyList(),
)

object GameDatabase {
    private val rules = listOf(
        GameRule(
            setOf("RMG"), "Super Mario Galaxy", listOf("mario galaxy"),
            "Galaxy needs the Wii pointer and a spin. Aim with the right stick, or by turning the Thor (gyro):",
            listOf(PresetLibrary.GALAXY_RIGHT_STICK) + PresetLibrary.GALAXY_PRECISION_ALL,
            more = PresetLibrary.GALAXY_GYRO_ALL,
        ),
        GameRule(
            setOf("SB4"), "Super Mario Galaxy 2", listOf("mario galaxy 2", "galaxy 2"),
            "Galaxy 2 needs the Wii pointer and a spin. Aim with the right stick, or by turning the Thor (gyro):",
            listOf(PresetLibrary.GALAXY_RIGHT_STICK) + PresetLibrary.GALAXY_PRECISION_ALL,
            more = PresetLibrary.GALAXY_GYRO_ALL,
        ),
        GameRule(
            setOf("SMN"), "New Super Mario Bros. Wii", listOf("new super mario", "nsmb"),
            "This game is played with the Wii Remote held sideways. Use this layout:",
            listOf(PresetLibrary.NSMB_SIDEWAYS),
        ),
        GameRule(
            setOf("RMC"), "Mario Kart Wii", listOf("mario kart wii", "mario kart"),
            "How do you want to race?",
            listOf(PresetLibrary.MKW_NUNCHUK, PresetLibrary.MKW_WHEEL_STICK, PresetLibrary.MKW_WHEEL_GYRO),
            askBeforePlaying = true,
        ),
        GameRule(
            setOf("SF8"), "Donkey Kong Country Returns", listOf("donkey kong country returns", "dkcr"),
            "Donkey Kong Country Returns uses a sideways Wii Remote and shaking. Use this layout:",
            listOf(PresetLibrary.DKCR_SIDEWAYS, PresetLibrary.POINTER_ADVENTURE),
        ),
        GameRule(
            setOf("SUK"), "Kirby's Return to Dream Land", listOf("kirby"),
            "Kirby is played with the Wii Remote held sideways. Use this layout:",
            listOf(PresetLibrary.KIRBY_SIDEWAYS),
        ),
        GameRule(
            setOf("R8P"), "Super Paper Mario", listOf("paper mario"),
            "Super Paper Mario is played sideways, but Tippi needs the pointer. Use this layout:",
            listOf(PresetLibrary.PAPER_MARIO_SIDEWAYS),
        ),
        GameRule(
            setOf("RZD"), "Zelda: Twilight Princess (Wii)", listOf("twilight princess"),
            "Twilight Princess uses the Nunchuk, the pointer and shakes. Aim with the right stick or the gyro:",
            listOf(PresetLibrary.POINTER_ADVENTURE) + PresetLibrary.POINTER_PRECISION_ALL,
            more = PresetLibrary.POINTER_GYRO_ALL,
        ),
        GameRule(
            setOf("RM3", "R3M"), "Metroid Prime 3 / Trilogy", listOf("metroid prime"),
            "Metroid Prime aims with the Wii pointer. Aim with the right stick, or by turning the Thor (gyro):",
            listOf(PresetLibrary.POINTER_ADVENTURE) + PresetLibrary.POINTER_PRECISION_ALL,
            more = PresetLibrary.POINTER_GYRO_ALL,
        ),
        GameRule(
            setOf("RSB"), "Super Smash Bros. Brawl", listOf("smash bros", "brawl"),
            "Brawl supports the GameCube controller, which is the best fit for the Thor:",
            listOf(PresetLibrary.SMASH_GC, PresetLibrary.CLASSIC_CONTROLLER),
            askBeforePlaying = false,
        ),
    )

    private val wiiGenerics = listOf(
        PresetLibrary.POINTER_ADVENTURE,
        *PresetLibrary.POINTER_PRECISION_ALL.toTypedArray(),
        *PresetLibrary.POINTER_GYRO_ALL.toTypedArray(),
        PresetLibrary.GENERIC_SIDEWAYS,
        PresetLibrary.CLASSIC_CONTROLLER,
    )

    fun platformOf(gameId: String?): Platform = when (gameId?.firstOrNull()?.uppercaseChar()) {
        'G', 'D', 'P' -> Platform.GAMECUBE
        'R', 'S', 'W', 'H', 'F', 'J', 'N', 'L', 'M', 'E', 'C', 'X' -> Platform.WII
        else -> Platform.UNKNOWN
    }

    fun suggest(game: GameInfo): Suggestion {
        val rule = findRule(game)
        if (rule != null) {
            val alternatives = (rule.more + wiiGenerics).distinctBy { it.id }.filter { candidate -> rule.choices.none { it.id == candidate.id } }
            return Suggestion(rule.title, rule.question, rule.choices, alternatives, rule.askBeforePlaying)
        }

        return when (game.platform) {
            Platform.GAMECUBE -> Suggestion(
                null, "GameCube game: the standard GameCube layout fits the Thor perfectly.",
                listOf(PresetLibrary.GAMECUBE), emptyList(), askBeforePlaying = false,
            )
            Platform.WII -> {
                // Virtual Console (NES/SNES/N64/...) titles are best on the Classic Controller.
                val vc = game.gameId?.first()?.uppercaseChar() in setOf('F', 'J', 'N', 'L', 'M', 'E', 'C', 'X')
                val ordered = if (vc) listOf(PresetLibrary.CLASSIC_CONTROLLER) + wiiGenerics else wiiGenerics
                Suggestion(
                    null,
                    if (vc) "Virtual Console title: the Classic Controller is the natural fit."
                    else "No game-specific preset yet. Pick the style this game uses:",
                    ordered.distinctBy { it.id }, emptyList(), askBeforePlaying = !vc,
                )
            }
            Platform.UNKNOWN -> Suggestion(
                null, "Couldn't identify this game. Pick a controller style:",
                (wiiGenerics + PresetLibrary.GAMECUBE).distinctBy { it.id }, emptyList(), askBeforePlaying = true,
            )
        }
    }

    /** The proper name of a game Delphis has presets for (matched by game ID), or null. */
    fun titleFor(game: GameInfo): String? {
        val prefix = game.gameId?.take(3)?.uppercase() ?: return null
        return rules.firstOrNull { prefix in it.idPrefixes }?.title
    }

    private fun findRule(game: GameInfo): GameRule? {
        val prefix = game.gameId?.take(3)?.uppercase()
        if (prefix != null) rules.firstOrNull { prefix in it.idPrefixes }?.let { return it }
        if (game.gameId == null) {
            val name = game.title.lowercase()
            // Longest keyword wins so "mario galaxy 2" beats "mario galaxy".
            return rules
                .flatMap { rule -> rule.keywords.map { it to rule } }
                .filter { (kw, _) -> name.contains(kw) }
                .maxByOrNull { (kw, _) -> kw.length }
                ?.second
        }
        return null
    }
}
