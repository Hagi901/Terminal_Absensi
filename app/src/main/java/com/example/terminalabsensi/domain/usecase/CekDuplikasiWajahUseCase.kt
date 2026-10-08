package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import com.example.terminalabsensi.data.local.entity.Karyawan
import com.example.terminalabsensi.facerecognition.FaceEmbedder

/**
 * Mencegah wajah yang SAMA didaftarkan lagi dengan ID berbeda (titip absen).
 * Karyawan nonaktif ikut dicek.
 */
class CekDuplikasiWajahUseCase(
    private val karyawanDao: KaryawanDao,
    private val sampelWajahDao: SampelWajahDao,
    private val faceEmbedder: FaceEmbedder
) {

    data class Duplikat(
        val karyawan: Karyawan,
        val score: Float
    )

    suspend operator fun invoke(sampelBaru: List<FloatArray>, threshold: Float): Duplikat? {
        val sampelTersimpan = sampelWajahDao.getAll()
        if (sampelTersimpan.isEmpty() || sampelBaru.isEmpty()) return null

        var idTerbaik: String? = null
        var scoreTerbaik = -1f

        for (baru in sampelBaru) {
            for (lama in sampelTersimpan) {
                val score = faceEmbedder.compare(baru, lama.fiturWajah)
                if (score > scoreTerbaik) {
                    scoreTerbaik = score
                    idTerbaik = lama.idKaryawan
                }
            }
        }

        if (idTerbaik == null || scoreTerbaik < threshold) return null
        val karyawan = karyawanDao.getById(idTerbaik) ?: return null
        return Duplikat(karyawan, scoreTerbaik)
    }
}