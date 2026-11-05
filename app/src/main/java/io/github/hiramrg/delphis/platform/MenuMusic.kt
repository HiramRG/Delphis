package io.github.hiramrg.delphis.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.hiramrg.delphis.R

/**
 * Looping background music for Delphis' menus. Plays while the menus are on screen, fades out
 * when a game starts or the app goes to the background, and never plays during games.
 *
 * The built-in tune (res/raw/menu_music.ogg) is an original loop made for Delphis
 * (tools/compose_menu_music.py). The user can pick their own audio file instead.
 * Volume is relative to the system media volume (default 70 %, a bit under it).
 */
object MenuMusic {
    private var player: MediaPlayer? = null
    private var playerSource: Uri? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var level = 0f      // current fade level 0..1
    private var target = 0f     // where the fade is heading
    private var volume = 0.7f   // user volume 0..1
    private var wanted = false  // menus are on screen

    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            level = if (target > level) minOf(target, level + 0.08f) else maxOf(target, level - 0.08f)
            runCatching { p.setVolume(level * volume, level * volume) }
            if (level != target) handler.postDelayed(this, 40)
            else if (target == 0f) runCatching { if (p.isPlaying) p.pause() }
        }
    }

    /** The menus are showing: start (or resume) the music if it's turned on. */
    fun start(context: Context, prefs: Prefs) {
        wanted = true
        volume = prefs.menuMusicVolume / 100f
        if (!prefs.menuMusicOn || volume <= 0f) { stop(); return }
        val p = ensurePlayer(context.applicationContext, prefs) ?: return
        if (!requestFocus(context)) return
        runCatching { if (!p.isPlaying) p.start() }
        fadeTo(1f)
    }

    /** Leaving the menus (game starting, app in background): fade out and pause. */
    fun pause() {
        wanted = false
        fadeTo(0f)
        abandonFocus()
    }

    /** Settings changed (on/off, volume, file): apply right away. */
    fun refresh(context: Context, prefs: Prefs) {
        volume = prefs.menuMusicVolume / 100f
        val source = prefs.menuMusicUri
        if (player != null && source != playerSource) release()
        if (!prefs.menuMusicOn || volume <= 0f) { stop(); return }
        if (wanted) start(context, prefs) else runCatching { player?.setVolume(level * volume, level * volume) }
    }

    fun stop() {
        handler.removeCallbacks(fadeStep)
        level = 0f; target = 0f
        runCatching { player?.pause() }
        abandonFocus()
    }

    fun release() {
        handler.removeCallbacks(fadeStep)
        runCatching { player?.release() }
        player = null; playerSource = null; level = 0f; target = 0f
    }

    private fun fadeTo(t: Float) {
        target = t
        handler.removeCallbacks(fadeStep)
        handler.post(fadeStep)
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun ensurePlayer(context: Context, prefs: Prefs): MediaPlayer? {
        player?.let { return it }
        val custom = prefs.menuMusicUri
        val p = custom?.let { uri ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    prepare()
                }
            }.onFailure { CrashLog.event("menu music: couldn't open the chosen file (${it.message}); using the built-in tune") }.getOrNull()
        } ?: runCatching {
            MediaPlayer.create(context, R.raw.menu_music, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
        }.getOrNull() ?: return null
        p.isLooping = true
        p.setVolume(0f, 0f)
        level = 0f
        player = p
        playerSource = custom
        return p
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                handler.removeCallbacks(fadeStep); level = 0f; target = 0f
                runCatching { player?.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { player?.setVolume(level * volume * 0.3f, level * volume * 0.3f) }
            AudioManager.AUDIOFOCUS_GAIN -> if (wanted) { runCatching { player?.start() }; fadeTo(1f) }
        }
    }

    private var audioManager: AudioManager? = null

    private fun requestFocus(context: Context): Boolean {
        val am = audioManager ?: context.getSystemService(AudioManager::class.java).also { audioManager = it }
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build().also { focus = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val req = focus ?: return
        audioManager?.abandonAudioFocusRequest(req)
    }
}
