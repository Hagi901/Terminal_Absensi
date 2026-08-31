package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.KaryawanDao

/**
 * Memvalidasi data karyawan baru sebelum disimpan, sesuai FR-3.1.1
 * (ID unik, field wajib) dan BR-05 (minimal 3 sampel wajah) di SRS.
 */
class ValidasiEnrollmentUseCase(
    private val karyawanDao: KaryawanDao
) {

    companion object {
        const val MINIMAL_SAMPEL_WAJAH = 3
    }

    sealed class HasilValidasiData {
        object Valid : HasilValidasiData()
        object IdKosong : HasilValidasiData()
        object NamaKosong : HasilValidasiData()
        object IdSudahDipakai : HasilValidasiData()
    }

    /**
     * Validasi data teks (ID & Nama) sebelum proses capture wajah dimulai.
     */
    suspend fun validasiDataKaryawan(idKaryawan: String, nama: String): HasilValidasiData {
        if (idKaryawan.isBlank()) {
            return HasilValidasiData.IdKosong
        }
        if (nama.isBlank()) {
            return HasilValidasiData.NamaKosong
        }
        if (karyawanDao.isIdExists(idKaryawan)) {
            return HasilValidasiData.IdSudahDipakai
        }
        return HasilValidasiData.Valid
    }

    /**
     * Validasi jumlah sampel wajah yang sudah diambil, dipanggil setelah
     * proses capture (3 sudut: depan, kiri, kanan) selesai.
     */
    fun validasiJumlahSampel(jumlahSampel: Int): Boolean {
        return jumlahSampel >= MINIMAL_SAMPEL_WAJAH
    }
}