package io.github.hiramrg.delphis.core

/** Which emulated controller a preset configures in Dolphin. */
enum class EmulatedController(val label: String, val profileFolder: String, val gameIniKey: String) {
    WIIMOTE_NUNCHUK("Wii Remote + Nunchuk", "Wiimote", "WiimoteProfile1"),
    WIIMOTE_SIDEWAYS("Wii Remote (held sideways)", "Wiimote", "WiimoteProfile1"),
    WIIMOTE_WHEEL("Wii Remote in a Wii Wheel", "Wiimote", "WiimoteProfile1"),
    CLASSIC("Classic Controller", "Wiimote", "WiimoteProfile1"),
    GAMECUBE("GameCube Controller", "GCPad", "PadProfile1"),
}

/** What a Thor control drives inside Dolphin. Keys are Dolphin INI keys such as "Buttons/A". */
sealed interface Target {
    /** One physical button drives every listed Dolphin input (e.g. Shake X+Y+Z). */
    data class Buttons(val keys: List<String>) : Target

    /**
     * The Thor's gyroscope moves the Wii pointer ("gyro mouse"): turning the Thor left/right moves it
     * sideways and tilting it up/down moves it vertically, however the Thor is held.
     * See DolphinConfigWriter.gyroPointerBindings for how this is expressed for Dolphin.
     */
    data class GyroPointer(
        val invertX: Boolean = false,
        val invertY: Boolean = false,
        /**
         * "Precision aim": pure gyro on both axes (no gravity pull), slow movements scaled down and
         * fast ones up (acceleration), and a button bound to [GYRO_HOLD] freezes the pointer while held
         * so you can re-position the Thor (like lifting a mouse).
         */
        val precision: Boolean = false,
    ) : Target

    /** A stick / D-pad drives up to four Dolphin inputs. Null directions are left unbound. */
    data class Directional(
        val up: String?,
        val down: String?,
        val left: String?,
        val right: String?,
    ) : Target {
        fun key(dir: Direction) = when (dir) {
            Direction.UP -> up; Direction.DOWN -> down; Direction.LEFT -> left; Direction.RIGHT -> right
        }
    }
}

/** Pseudo input key: a button bound to it pauses gyro aiming while held (not a Dolphin key). */
const val GYRO_HOLD = "Delphis/GyroHold"

/** One row of a preset: "this Thor control does this Wii action". [action] is shown on the diagram. */
data class Mapping(val control: ThorControl, val target: Target, val action: String)

data class Preset(
    val id: String,
    val name: String,
    /** One-line pitch shown on the suggestion card. */
    val summary: String,
    val controller: EmulatedController,
    val mappings: List<Mapping>,
    /** Extra profile keys, e.g. "Options/Sideways Wiimote" = "True". */
    val options: Map<String, String> = emptyMap(),
    val tips: List<String> = emptyList(),
    val experimental: Boolean = false,
) {
    /** File name (without .ini) used under Config/Profiles/<Wiimote|GCPad>/. */
    val profileName: String get() = "Delphis-$id"

    fun actionFor(control: ThorControl): String? = mappings.firstOrNull { it.control == control }?.action
}

// ---------------------------------------------------------------------------------------------
// Small DSL so the preset library below reads like a controls chart.
// ---------------------------------------------------------------------------------------------

class PresetBuilder(private val id: String, private val name: String, private val controller: EmulatedController) {
    var summary = ""
    var experimental = false
    private val mappings = mutableListOf<Mapping>()
    private val options = linkedMapOf<String, String>()
    private val tips = mutableListOf<String>()

    fun button(control: ThorControl, action: String, vararg keys: String) {
        mappings += Mapping(control, Target.Buttons(keys.toList()), action)
    }

    fun directional(control: ThorControl, action: String, group: String, up: String? = "Up", down: String? = "Down", left: String? = "Left", right: String? = "Right") {
        mappings += Mapping(
            control,
            Target.Directional(up?.let { "$group/$it" }, down?.let { "$group/$it" }, left?.let { "$group/$it" }, right?.let { "$group/$it" }),
            action,
        )
    }

    /** Wii Remote tilt group uses Forward/Backward instead of Up/Down. */
    fun tilt(control: ThorControl, action: String, group: String = "Tilt", leftRightOnly: Boolean = false) =
        directional(control, action, group, if (leftRightOnly) null else "Forward", if (leftRightOnly) null else "Backward")

    fun option(key: String, value: String) { options[key] = value }
    fun tip(text: String) { tips += text }

    fun build() = Preset(id, name, summary, controller, mappings.toList(), options.toMap(), tips.toList(), experimental)
}

fun preset(id: String, name: String, controller: EmulatedController, block: PresetBuilder.() -> Unit): Preset =
    PresetBuilder(id, name, controller).apply(block).build()

private val SHAKE = arrayOf("Shake/X", "Shake/Y", "Shake/Z")
private val NUNCHUK_SHAKE = arrayOf("Nunchuk/Shake/X", "Nunchuk/Shake/Y", "Nunchuk/Shake/Z")

/**
 * Some games (New Super Mario Bros. Wii's pause menu, for one) let the pointer override the D-pad.
 * With nothing driving the pointer Dolphin parks it in the middle of the screen, right on top of the
 * first menu item, so the D-pad can never move the selection away. Auto-Hide takes the idle pointer
 * off-screen after a moment without movement (what a real remote held sideways does); moving a
 * stick that drives the pointer brings it straight back.
 */
private fun PresetBuilder.hideIdlePointer() = option("IR/Auto-Hide", "True")

/** Shared bits every Wii Remote preset gets: +, -, Home. */
private fun PresetBuilder.wiiSystemButtons() {
    button(ThorControl.START, "+ (Pause)", "Buttons/+")
    button(ThorControl.SELECT, "− (Minus)", "Buttons/-")
    button(ThorControl.L3, "HOME menu", "Buttons/Home")
}

object PresetLibrary {
    // ---------------------------------------------------------------- Mario Galaxy 1 & 2
    val GALAXY_RIGHT_STICK = preset("galaxy-rstick", "Pointer on right stick", EmulatedController.WIIMOTE_NUNCHUK) {
        summary = "Nunchuk on the left stick, the Wii pointer on the right stick, spin on a button. No waggling."
        directional(ThorControl.LEFT_STICK, "Move Mario", "Nunchuk/Stick")
        directional(ThorControl.RIGHT_STICK, "Aim pointer, grab Star Bits", "IR")
        button(ThorControl.FACE_EAST, "Jump (A)", "Buttons/A")
        button(ThorControl.FACE_SOUTH, "Jump (A)", "Buttons/A")
        button(ThorControl.FACE_WEST, "Spin (shake)", *SHAKE)
        button(ThorControl.FACE_NORTH, "Spin (shake)", *SHAKE)
        button(ThorControl.R2, "Shoot Star Bit (B)", "Buttons/B")
        button(ThorControl.R1, "Spin (shake)", *SHAKE)
        button(ThorControl.L2, "Crouch / Ground pound (Z)", "Nunchuk/Buttons/Z")
        button(ThorControl.L1, "Center camera (C)", "Nunchuk/Buttons/C")
        directional(ThorControl.DPAD, "Camera / first person", "D-Pad")
        button(ThorControl.R3, "Recenter pointer", "IR/Recenter")
        // Galaxy's manual lists both − and + for the pause menu; pressing both from Start makes
        // pausing work no matter which one the current screen listens to.
        button(ThorControl.START, "Pause (+)", "Buttons/+", "Buttons/-")
        button(ThorControl.SELECT, "Pause (−)", "Buttons/-")
        button(ThorControl.L3, "HOME menu", "Buttons/Home")
        option("Extension", "Nunchuk")
        option("IR/Relative Input", "True")
        tip("The pointer stays where you leave it (relative mode). Click R3 to snap it back to the center.")
        tip("Tilt-ball and surfing levels use the Wii Remote tilt; switch to the \"Wii Remote + Nunchuk\" generic preset for those if needed.")
    }

    // ---------------------------------------------------------------- New Super Mario Bros. Wii
    val NSMB_SIDEWAYS = preset("nsmbw-sideways", "Sideways Wii Remote", EmulatedController.WIIMOTE_SIDEWAYS) {
        summary = "The Wii Remote is held horizontally like an NES pad: D-pad/stick to move, 2 jumps, 1 runs, shake spins."
        directional(ThorControl.LEFT_STICK, "Move", "D-Pad")
        directional(ThorControl.DPAD, "Move", "D-Pad")
        button(ThorControl.FACE_EAST, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Run / Fireball (1)", "Buttons/1")
        button(ThorControl.FACE_NORTH, "Run / Fireball (1)", "Buttons/1")
        button(ThorControl.R1, "Spin / Pick up (shake)", *SHAKE)
        button(ThorControl.L1, "Spin / Pick up (shake)", *SHAKE)
        button(ThorControl.R2, "Bubble / Pick up (A)", "Buttons/A")
        tilt(ThorControl.RIGHT_STICK, "Tilt platforms & lifts", leftRightOnly = true)
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
        tip("Dolphin rotates the D-pad for you in sideways mode, so up is up on screen.")
        tip("Hold Y/X (1) while moving to run; press it alone to throw fireballs.")
    }

    // ---------------------------------------------------------------- Mario Kart Wii
    val MKW_NUNCHUK = preset("mkw-nunchuk", "Wii Remote + Nunchuk", EmulatedController.WIIMOTE_NUNCHUK) {
        summary = "Steer with the stick, accelerate with A, drift on the shoulder. The most precise way to race."
        directional(ThorControl.LEFT_STICK, "Steer / aim items", "Nunchuk/Stick")
        button(ThorControl.FACE_EAST, "Accelerate (A)", "Buttons/A")
        button(ThorControl.FACE_SOUTH, "Accelerate (A)", "Buttons/A")
        button(ThorControl.R1, "Drift / Brake (B)", "Buttons/B")
        button(ThorControl.R2, "Drift / Brake (B)", "Buttons/B")
        button(ThorControl.L1, "Use item (Z)", "Nunchuk/Buttons/Z")
        button(ThorControl.L2, "Use item (Z)", "Nunchuk/Buttons/Z")
        button(ThorControl.FACE_NORTH, "Look behind (C)", "Nunchuk/Buttons/C")
        button(ThorControl.FACE_WEST, "Trick / Wheelie (shake)", *SHAKE)
        directional(ThorControl.RIGHT_STICK, "Menu pointer", "IR")
        wiiSystemButtons()
        option("Extension", "Nunchuk")
        hideIdlePointer()
        tip("Select \"Wii Remote + Nunchuk\" on the controller screen before a race.")
        tip("Shake for a trick right as you leave a ramp for a speed boost.")
    }

    val MKW_WHEEL_STICK = preset("mkw-wheel-stick", "Wii Wheel (tilt on stick)", EmulatedController.WIIMOTE_WHEEL) {
        summary = "Classic Wii Wheel controls. The left stick tilts the emulated Wii Remote, so steering feels analog without moving the Thor."
        tilt(ThorControl.LEFT_STICK, "Steer (tilt)", leftRightOnly = true)
        button(ThorControl.FACE_EAST, "Accelerate (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Accelerate (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Brake / Reverse (1)", "Buttons/1")
        button(ThorControl.R1, "Drift (B)", "Buttons/B")
        button(ThorControl.R2, "Drift (B)", "Buttons/B")
        button(ThorControl.L1, "Use item (D-pad)", "D-Pad/Up")
        button(ThorControl.L2, "Use item (D-pad)", "D-Pad/Up")
        directional(ThorControl.DPAD, "Throw item fwd / back", "D-Pad")
        button(ThorControl.FACE_NORTH, "Look behind (A)", "Buttons/A")
        directional(ThorControl.RIGHT_STICK, "Trick / Wheelie (flick)", "Swing")
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
        tip("Flick the right stick up as you leave a ramp to do a trick.")
    }

    val MKW_WHEEL_GYRO = preset("mkw-wheel-gyro", "Wii Wheel (tilt the Thor)", EmulatedController.WIIMOTE_WHEEL) {
        summary = "Real motion control: tilt the whole Thor left and right like a wheel. Uses the Thor's built-in accelerometer."
        experimental = true
        tilt(ThorControl.MOTION, "Steer (tilt device)", leftRightOnly = true)
        button(ThorControl.FACE_EAST, "Accelerate (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Accelerate (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Brake / Reverse (1)", "Buttons/1")
        button(ThorControl.R1, "Drift (B)", "Buttons/B")
        button(ThorControl.R2, "Drift (B)", "Buttons/B")
        button(ThorControl.L1, "Use item (D-pad)", "D-Pad/Up")
        button(ThorControl.L2, "Use item (D-pad)", "D-Pad/Up")
        directional(ThorControl.DPAD, "Throw item fwd / back", "D-Pad")
        button(ThorControl.FACE_NORTH, "Look behind (A)", "Buttons/A")
        directional(ThorControl.RIGHT_STICK, "Trick / Wheelie (flick)", "Swing")
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
        tip("Hold the Thor level when the race starts. If steering is reversed, turn on \"Invert motion steering\" in Settings and apply again.")
    }

    // ---------------------------------------------------------------- Other sideways platformers
    val DKCR_SIDEWAYS = preset("dkcr-sideways", "Sideways Wii Remote", EmulatedController.WIIMOTE_SIDEWAYS) {
        summary = "Sideways remote with the hand-slap/roll shake on shoulder buttons."
        directional(ThorControl.LEFT_STICK, "Move", "D-Pad")
        directional(ThorControl.DPAD, "Move", "D-Pad")
        button(ThorControl.FACE_EAST, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Grab (1)", "Buttons/1")
        button(ThorControl.FACE_NORTH, "Grab (1)", "Buttons/1")
        button(ThorControl.R1, "Roll / Slap / Blow (shake)", *SHAKE)
        button(ThorControl.L1, "Roll / Slap / Blow (shake)", *SHAKE)
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
        tip("Hold Down + R1 to blow, run + R1 to roll.")
    }

    val KIRBY_SIDEWAYS = preset("kirby-sideways", "Sideways Wii Remote", EmulatedController.WIIMOTE_SIDEWAYS) {
        summary = "Sideways remote: 1 inhales/attacks, 2 jumps/floats, shake triggers Super Abilities."
        directional(ThorControl.LEFT_STICK, "Move", "D-Pad")
        directional(ThorControl.DPAD, "Move", "D-Pad")
        button(ThorControl.FACE_EAST, "Jump / Float (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Jump / Float (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Inhale / Attack (1)", "Buttons/1")
        button(ThorControl.FACE_NORTH, "Inhale / Attack (1)", "Buttons/1")
        button(ThorControl.R1, "Super Ability (shake)", *SHAKE)
        button(ThorControl.L1, "Drop ability (A)", "Buttons/A")
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
    }

    val PAPER_MARIO_SIDEWAYS = preset("spm-sideways", "Sideways + pointer", EmulatedController.WIIMOTE_SIDEWAYS) {
        summary = "Sideways remote for platforming, with Tippi's pointer on the right stick."
        directional(ThorControl.LEFT_STICK, "Move", "D-Pad")
        directional(ThorControl.DPAD, "Move", "D-Pad")
        directional(ThorControl.RIGHT_STICK, "Tippi pointer", "IR")
        button(ThorControl.FACE_EAST, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "Jump (2)", "Buttons/2")
        button(ThorControl.FACE_WEST, "Pixl / Talk (1)", "Buttons/1")
        button(ThorControl.FACE_NORTH, "Flip dimension (A)", "Buttons/A")
        button(ThorControl.R2, "Point & reveal (B)", "Buttons/B")
        button(ThorControl.R1, "Shake (stylish)", *SHAKE)
        button(ThorControl.R3, "Recenter pointer", "IR/Recenter")
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        option("IR/Relative Input", "True")
    }

    // ---------------------------------------------------------------- Pointer-heavy adventures
    val POINTER_ADVENTURE = preset("nunchuk-pointer", "Nunchuk + pointer on right stick", EmulatedController.WIIMOTE_NUNCHUK) {
        summary = "Left stick moves, right stick aims the Wii pointer, R1 shakes the remote, L1 shakes the Nunchuk."
        directional(ThorControl.LEFT_STICK, "Move", "Nunchuk/Stick")
        directional(ThorControl.RIGHT_STICK, "Aim pointer", "IR")
        button(ThorControl.FACE_EAST, "Action (A)", "Buttons/A")
        button(ThorControl.FACE_SOUTH, "Action (A)", "Buttons/A")
        button(ThorControl.R2, "Fire / Item (B)", "Buttons/B")
        button(ThorControl.L2, "Lock-on (Z)", "Nunchuk/Buttons/Z")
        button(ThorControl.L1, "C button", "Nunchuk/Buttons/C")
        button(ThorControl.R1, "Shake Wii Remote", *SHAKE)
        button(ThorControl.FACE_WEST, "Shake Nunchuk", *NUNCHUK_SHAKE)
        button(ThorControl.FACE_NORTH, "1", "Buttons/1")
        directional(ThorControl.DPAD, "D-Pad", "D-Pad")
        button(ThorControl.R3, "Recenter pointer", "IR/Recenter")
        button(ThorControl.SELECT, "− (Minus)", "Buttons/-")
        button(ThorControl.START, "+ (Pause)", "Buttons/+")
        button(ThorControl.L3, "2", "Buttons/2")
        option("Extension", "Nunchuk")
        option("IR/Relative Input", "True")
        tip("Click R3 to recenter the pointer if it drifts off-screen.")
    }

    // ---------------------------------------------------------------- Generic
    val GENERIC_SIDEWAYS = preset("generic-sideways", "Sideways Wii Remote", EmulatedController.WIIMOTE_SIDEWAYS) {
        summary = "For 2D games played with the remote held horizontally."
        directional(ThorControl.LEFT_STICK, "D-Pad", "D-Pad")
        directional(ThorControl.DPAD, "D-Pad", "D-Pad")
        button(ThorControl.FACE_EAST, "2", "Buttons/2")
        button(ThorControl.FACE_SOUTH, "2", "Buttons/2")
        button(ThorControl.FACE_WEST, "1", "Buttons/1")
        button(ThorControl.FACE_NORTH, "1", "Buttons/1")
        button(ThorControl.R1, "Shake", *SHAKE)
        button(ThorControl.L1, "A", "Buttons/A")
        button(ThorControl.L2, "B", "Buttons/B")
        tilt(ThorControl.RIGHT_STICK, "Tilt")
        wiiSystemButtons()
        option("Extension", "None")
        option("Options/Sideways Wiimote", "True")
        hideIdlePointer()
    }

    val CLASSIC_CONTROLLER = preset("classic", "Classic Controller", EmulatedController.CLASSIC) {
        summary = "A normal gamepad layout, for Wii games and Virtual Console titles that support the Classic Controller."
        directional(ThorControl.LEFT_STICK, "Left stick", "Classic/Left Stick")
        directional(ThorControl.RIGHT_STICK, "Right stick", "Classic/Right Stick")
        directional(ThorControl.DPAD, "D-Pad", "Classic/D-Pad")
        button(ThorControl.FACE_EAST, "a", "Classic/Buttons/A")
        button(ThorControl.FACE_SOUTH, "b", "Classic/Buttons/B")
        button(ThorControl.FACE_NORTH, "x", "Classic/Buttons/X")
        button(ThorControl.FACE_WEST, "y", "Classic/Buttons/Y")
        button(ThorControl.L1, "L", "Classic/Triggers/L")
        button(ThorControl.R1, "R", "Classic/Triggers/R")
        button(ThorControl.L2, "ZL", "Classic/Buttons/ZL")
        button(ThorControl.R2, "ZR", "Classic/Buttons/ZR")
        button(ThorControl.START, "+", "Classic/Buttons/+")
        button(ThorControl.SELECT, "−", "Classic/Buttons/-")
        button(ThorControl.L3, "HOME", "Classic/Buttons/Home")
        option("Extension", "Classic")
    }

    val GAMECUBE = preset("gamecube", "GameCube Controller", EmulatedController.GAMECUBE) {
        summary = "Face buttons match their printed letters; analog L2/R2 act as the GameCube's analog triggers."
        directional(ThorControl.LEFT_STICK, "Control stick", "Main Stick")
        directional(ThorControl.RIGHT_STICK, "C-Stick", "C-Stick")
        directional(ThorControl.DPAD, "D-Pad", "D-Pad")
        button(ThorControl.FACE_EAST, "A", "Buttons/A")
        button(ThorControl.FACE_SOUTH, "B", "Buttons/B")
        button(ThorControl.FACE_NORTH, "X", "Buttons/X")
        button(ThorControl.FACE_WEST, "Y", "Buttons/Y")
        button(ThorControl.R1, "Z", "Buttons/Z")
        button(ThorControl.L2, "L (analog)", "Triggers/L", "Triggers/L-Analog")
        button(ThorControl.R2, "R (analog)", "Triggers/R", "Triggers/R-Analog")
        button(ThorControl.START, "Start", "Buttons/Start")
    }

    val SMASH_GC = GAMECUBE.copy(
        id = "brawl-gc",
        name = "GameCube Controller",
        summary = "Brawl plays best on a GameCube pad: standard layout, shoulders shield, R1 grabs.",
    )

    // ---------------------------------------------------------------- Gyro aiming variants
    /**
     * [base] with the Thor's gyroscope also aiming the pointer. The right stick keeps working for big
     * moves, and the recenter button (R3) recenters both.
     */
    private fun withGyroAiming(base: Preset, id: String, name: String, summary: String, invertX: Boolean, invertY: Boolean): Preset {
        val aim = base.mappings.firstOrNull { it.control == ThorControl.RIGHT_STICK }?.action ?: "Aim pointer"
        return base.copy(
            id = id, name = name, summary = summary,
            mappings = base.mappings + Mapping(ThorControl.GYRO, Target.GyroPointer(invertX, invertY), aim),
            tips = listOf(
                "Hold the Thor the way you normally play; turn it left/right and tilt it up/down to aim.",
                "Click R3 to put the pointer back in the middle; the right stick also still aims.",
                "Too fast or too slow? Change it in Delphis Settings → Gyro aiming.",
            ),
        )
    }

    /** A gyro preset (inverting up/down or left/right is a setting: Settings → Gyro aiming). */
    private fun gyroVariants(base: Preset, idBase: String, name: String, summary: String) = listOf(
        withGyroAiming(base, idBase, "$name (Experimental)", summary, invertX = false, invertY = false),
    )

    val GALAXY_GYRO_ALL = gyroVariants(
        GALAXY_RIGHT_STICK, "galaxy-gyro", "Gyro aiming",
        "Aim the Star Pointer by turning the Thor (the right stick still works too).",
    )
    val GALAXY_GYRO = GALAXY_GYRO_ALL.first()

    val POINTER_GYRO_ALL = gyroVariants(
        POINTER_ADVENTURE, "nunchuk-gyro", "Nunchuk + gyro aiming",
        "Left stick moves; aim the Wii pointer by turning the Thor (the right stick still works too).",
    )
    val POINTER_GYRO = POINTER_GYRO_ALL.first()

    // ---------------------------------------------------------------- Precision gyro aiming
    private const val HOLD_LABEL = "Hold: pause gyro aim"

    private fun withPrecisionAim(
        base: Preset, id: String, name: String, summary: String, invertX: Boolean, invertY: Boolean,
        relabel: Map<ThorControl, Mapping>, extraTips: List<String>,
    ): Preset {
        val aim = base.mappings.firstOrNull { it.control == ThorControl.RIGHT_STICK }?.action ?: "Aim pointer"
        val mappings = base.mappings.filter { it.control !in relabel.keys } + relabel.values +
            Mapping(ThorControl.GYRO, Target.GyroPointer(invertX, invertY, precision = true), aim)
        return base.copy(
            id = id, name = name, summary = summary, mappings = mappings,
            tips = listOf(
                "Small, slow movements aim finely; quick turns move the pointer far.",
                "Hold R1 to freeze the pointer, move the Thor back to a comfy position, then let go (like lifting a mouse).",
                "Click R3 to put the pointer back in the middle; the right stick still works for big moves.",
                "Too fast or too slow? Change the speed in Delphis Settings → Gyro aiming (try 4-6).",
            ) + extraTips,
        )
    }

    private fun precisionVariants(
        base: Preset, idBase: String, summary: String, relabel: Map<ThorControl, Mapping>, extraTips: List<String> = emptyList(),
    ): List<Preset> {
        return listOf(withPrecisionAim(base, idBase, "Precision gyro (Experimental)", summary, false, false, relabel, extraTips))
    }

    val GALAXY_PRECISION_ALL = precisionVariants(
        GALAXY_RIGHT_STICK, "galaxy-precise",
        "Gyro made for aiming: fine control when you move slowly, hold R1 to re-position (spin is on X/Y).",
        mapOf(ThorControl.R1 to Mapping(ThorControl.R1, Target.Buttons(listOf(GYRO_HOLD)), HOLD_LABEL)),
    )
    val GALAXY_PRECISION = GALAXY_PRECISION_ALL.first()

    val POINTER_PRECISION_ALL = precisionVariants(
        POINTER_ADVENTURE, "nunchuk-precise",
        "Gyro made for aiming (Metroid, Zelda): fine control when you move slowly, hold R1 to re-position. X shakes the remote.",
        mapOf(
            ThorControl.R1 to Mapping(ThorControl.R1, Target.Buttons(listOf(GYRO_HOLD)), HOLD_LABEL),
            ThorControl.FACE_NORTH to Mapping(ThorControl.FACE_NORTH, Target.Buttons(listOf("Shake/X", "Shake/Y", "Shake/Z")), "Shake Wii Remote"),
            ThorControl.L3 to Mapping(ThorControl.L3, Target.Buttons(listOf("Buttons/1")), "1"),
        ),
        listOf(
            "The 2 button isn't mapped in this layout; use \"Edit preset\" if a game needs it.",
            "Metroid Prime Trilogy: in the game's Options, a higher pointer sensitivity (Advanced) makes turning feel snappier.",
        ),
    )
    val POINTER_PRECISION = POINTER_PRECISION_ALL.first()

    val ALL: List<Preset> = listOf(
        GALAXY_RIGHT_STICK, NSMB_SIDEWAYS, MKW_NUNCHUK, MKW_WHEEL_STICK, MKW_WHEEL_GYRO,
        DKCR_SIDEWAYS, KIRBY_SIDEWAYS, PAPER_MARIO_SIDEWAYS, POINTER_ADVENTURE,
        GENERIC_SIDEWAYS, CLASSIC_CONTROLLER, GAMECUBE, SMASH_GC,
    ) + GALAXY_GYRO_ALL + POINTER_GYRO_ALL + GALAXY_PRECISION_ALL + POINTER_PRECISION_ALL

    fun byId(id: String): Preset? = ALL.firstOrNull { it.id == id }
}

/**
 * Splits an action label like "Shoot Star Bit (B)" into the friendly part ("Shoot Star Bit") and
 * the original Wii input in brackets ("B"), for the simplified on-screen legend.
 */
fun splitAction(action: String): Pair<String, String?> {
    val open = action.lastIndexOf(" (")
    if (open <= 0 || !action.endsWith(")")) return action to null
    val inner = action.substring(open + 2, action.length - 1)
    if (inner == "analog") return action to null // "L (analog)" is a description, not a Wii input
    return action.substring(0, open) to inner
}
