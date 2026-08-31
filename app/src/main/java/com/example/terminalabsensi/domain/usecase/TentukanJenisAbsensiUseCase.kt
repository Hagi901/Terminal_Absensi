package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.AbsensiDao
import com.example.terminalabsensi.data.local.entity.Absensi

/**
 * Menentukan jenis absensi (Masuk/Pulang) berdasarkan riwayat absensi
 * karyawan pada hari yang sama, sesuai FR-3.2.4 di SRS.
 */
class TentukanJenisAbsensiUseCase(
    private val absensiDao: AbsensiDao
) {

    sealed class Hasil {
        object AbsenMasuk : Hasil()
        object AbsenPulang : Hasil()
        object SudahLengkap : Hasil()
    }

    suspend operator fun invoke(idKaryawan: String, tanggal: String): Hasil {
        val riwayatHariIni = absensiDao.getByKaryawanDanTanggal(idKaryawan, tanggal)

        val sudahMasuk = riwayatHariIni.any { it.jenisAbsen == "masuk" }
        val sudahPulang = riwayatHariIni.any { it.jenisAbsen == "pulang" }

        return when {
            !sudahMasuk -> Hasil.AbsenMasuk
            sudahMasuk && !sudahPulang -> Hasil.AbsenPulang
            else -> Hasil.SudahLengkap
        }
    }
}