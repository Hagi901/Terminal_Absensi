package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.AbsensiDao

/**
 * Mencegah pencatatan absensi ganda untuk karyawan yang sama dalam
 * rentang waktu singkat (<1 menit dari transaksi sukses terakhir),
 * sesuai BR-07 di SRS.
 */
class ValidasiAntiDuplikasiUseCase(
    private val absensiDao: AbsensiDao
) {

    companion object {
        private const val BATAS_DUPLIKASI_MS = 60_000L // 1 menit
    }

    /**
     * @return true jika transaksi ini BOLEH dilanjutkan (bukan duplikasi),
     *         false jika harus ditolak karena terlalu cepat dari transaksi sebelumnya.
     */
    suspend operator fun invoke(idKaryawan: String, waktuTransaksi: Long): Boolean {
        val transaksiTerakhir = absensiDao.getTransaksiTerakhir(idKaryawan) ?: return true

        val selisihWaktu = waktuTransaksi - transaksiTerakhir.timestamp
        return selisihWaktu >= BATAS_DUPLIKASI_MS
    }
}