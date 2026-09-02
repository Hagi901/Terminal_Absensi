package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.AdminDao
import com.example.terminalabsensi.data.local.entity.Admin
import com.example.terminalabsensi.util.PasswordHasher

/**
 * Menangani setup PIN pertama kali dan verifikasi login admin,
 * sesuai FR-3.4.1 di SRS.
 */
class AutentikasiAdminUseCase(
    private val adminDao: AdminDao
) {

    companion object {
        private const val USERNAME_DEFAULT = "admin"
    }

    suspend fun sudahAdaAdmin(): Boolean {
        return adminDao.hasAdmin()
    }

    suspend fun setupPinPertamaKali(pin: String): Boolean {
        if (pin.length < 6) return false
        adminDao.insert(
            Admin(
                username = USERNAME_DEFAULT,
                pinPasswordHash = PasswordHasher.hash(pin)
            )
        )
        return true
    }

    suspend fun login(pin: String): Boolean {
        val admin = adminDao.getByUsername(USERNAME_DEFAULT) ?: return false
        return PasswordHasher.verify(pin, admin.pinPasswordHash)
    }
}