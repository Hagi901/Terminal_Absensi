package com.example.terminalabsensi.util

import java.security.MessageDigest

/**
 * Utility untuk hash PIN admin sebelum disimpan ke database, sesuai
 * kebutuhan keamanan di SRS (kredensial tidak boleh disimpan plain text).
 *
 * Catatan: versi ini pakai SHA-256 dengan salt tetap tingkat-aplikasi.
 * Untuk peningkatan keamanan lebih lanjut nanti, bisa di-upgrade ke
 * salt unik per-admin (butuh migrasi skema database).
 */
object PasswordHasher {

    private const val SALT = "TerminalAbsensi_Salt_2026"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((pin + SALT).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, hash: String): Boolean {
        return hash(pin) == hash
    }
}