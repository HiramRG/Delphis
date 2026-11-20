package io.github.hiramrg.delphis.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps secrets (the RetroAchievements web API key) encrypted in Delphis' settings, with a key that lives in
 * Android's keystore and never leaves the device. A copy restored from a backup on another device can't be read,
 * so Delphis just asks you to sign in again there.
 */
object SecretBox {
    private const val ALIAS = "delphis_secrets"
    private const val PREFIX = "enc1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun isSealed(stored: String?) = stored?.startsWith(PREFIX) == true

    /** Encrypted text to store. Null if the keystore can't be used (then the caller keeps it as before). */
    fun seal(plain: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    /** The secret, or null if it can't be read (restored on another device, keystore reset). */
    fun open(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()
}
