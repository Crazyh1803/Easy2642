package com.easy2642.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The database passphrase is 32 random bytes that only ever exist on disk
 * wrapped by an AES-256-GCM key held in the hardware-backed Android Keystore.
 */
object KeystorePassphrase {
    private const val KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "easy2642_db_key"
    private const val FILE = "db_passphrase.bin"
    private const val IV_LEN = 12

    private fun wrappedFile(context: Context) = File(context.noBackupFilesDir, FILE)

    fun exists(context: Context): Boolean = wrappedFile(context).exists()

    /** @throws java.security.GeneralSecurityException if the stored passphrase can no longer be unwrapped. */
    fun getOrCreate(context: Context): ByteArray {
        val file = wrappedFile(context)
        if (file.exists()) {
            val blob = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN))
            return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        file.writeBytes(cipher.iv + cipher.doFinal(passphrase))
        return passphrase
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    fun destroy(context: Context) {
        wrappedFile(context).delete()
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        }
    }
}

object SecureDatabase {
    /** True when a previous database had to be discarded because its key was lost. */
    @Volatile
    var wasReset: Boolean = false
        private set

    fun open(context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = try {
            KeystorePassphrase.getOrCreate(context)
        } catch (e: Exception) {
            // The Keystore key is gone (e.g. restored onto another device): the old
            // database can never be decrypted, so start clean rather than crash forever.
            wasReset = true
            deleteFiles(context)
            KeystorePassphrase.getOrCreate(context)
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    fun deleteFiles(context: Context) {
        context.deleteDatabase(AppDatabase.NAME)
        KeystorePassphrase.destroy(context)
    }
}
