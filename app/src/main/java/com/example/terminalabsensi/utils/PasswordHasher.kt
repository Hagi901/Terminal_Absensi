package com.example.terminalabsensi.util

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Format hash baru (disimpan di kolom pinPasswordHash, tanpa perubahan skema):
 *   pbkdf2$<ALGORITMA>$<iterasi>$<salt hex>$<hash hex>
 *
 * Hash LAMA (SHA-256 + salt tetap) masih bisa diverifikasi supaya admin lama tetap
 * bisa login. Gunakan needsUpgrade() lalu hash() ulang setelah login berhasil.
 */
object PasswordHasher {

    private const val LEGACY_SALT = "TerminalAbsensi_Salt_2026"

    private const val PREFIX = "pbkdf2"
    private const val SEPARATOR = "$"
    private const val ITERASI = 100_000
    private const val PANJANG_KUNCI_BIT = 256
    private const val PANJANG_SALT_BYTE = 16

    private val random = SecureRandom()

    fun hash(pin: String): String {
        val salt = ByteArray(PANJANG_SALT_BYTE).also { random.nextBytes(it) }
        val (namaAlgoritma, jcaName) = pilihAlgoritma()
        val hasil = pbkdf2(pin, salt, ITERASI, jcaName)
        return listOf(PREFIX, namaAlgoritma, ITERASI.toString(), salt.toHex(), hasil.toHex())
            .joinToString(SEPARATOR)
    }

    fun verify(pin: String, tersimpan: String): Boolean {
        return if (tersimpan.startsWith(PREFIX + SEPARATOR)) {
            verifyPbkdf2(pin, tersimpan)
        } else {
            sama(legacyHash(pin), tersimpan) // hash lama
        }
    }

    fun needsUpgrade(tersimpan: String): Boolean {
        return !tersimpan.startsWith(PREFIX + SEPARATOR)
    }

    private fun verifyPbkdf2(pin: String, tersimpan: String): Boolean {
        val bagian = tersimpan.split(SEPARATOR)
        if (bagian.size != 5) return false

        val jcaName = jcaNameDariTag(bagian[1]) ?: return false
        val iterasi = bagian[2].toIntOrNull() ?: return false
        if (iterasi <= 0) return false
        val salt = bagian[3].fromHex() ?: return false
        val hashTersimpan = bagian[4].fromHex() ?: return false

        return try {
            val dihitung = pbkdf2(pin, salt, iterasi, jcaName)
            MessageDigest.isEqual(dihitung, hashTersimpan)
        } catch (e: Exception) {
            false
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterasi: Int, jcaName: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterasi, PANJANG_KUNCI_BIT)
        return try {
            SecretKeyFactory.getInstance(jcaName).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** SHA256 jika tersedia (API 26+), kalau tidak SHA1 (API 24-25). */
    private fun pilihAlgoritma(): Pair<String, String> {
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "SHA256" to "PBKDF2WithHmacSHA256"
        } catch (e: NoSuchAlgorithmException) {
            "SHA1" to "PBKDF2WithHmacSHA1"
        }
    }

    private fun jcaNameDariTag(tag: String): String? = when (tag) {
        "SHA256" -> "PBKDF2WithHmacSHA256"
        "SHA1" -> "PBKDF2WithHmacSHA1"
        else -> null
    }

    private fun legacyHash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((pin + LEGACY_SALT).toByteArray())
        return bytes.toHex()
    }

    private fun sama(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}