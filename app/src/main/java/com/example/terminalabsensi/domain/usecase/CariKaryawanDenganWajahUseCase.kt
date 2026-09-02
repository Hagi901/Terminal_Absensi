package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import com.example.terminalabsensi.data.local.entity.Karyawan
import com.example.terminalabsensi.facerecognition.FaceEmbedder

/**
 * Mencari karyawan aktif yang embedding-nya paling cocok dengan wajah
 * yang terdeteksi di kamera, dari SELURUH data sampel wajah tersimpan.
 * Sesuai FR-3.2.3 di SRS.
 */
class CariKaryawanDenganWajahUseCase(
    private val karyawanDao: KaryawanDao,
    private val sampelWajahDao: SampelWajahDao,
    private val faceEmbedder: FaceEmbedder
) {

    data class Hasil(
        val karyawan: Karyawan,
        val confidenceScore: Float
    )

    /**
     * @param embeddingWajah embedding hasil ekstraksi wajah yang terdeteksi kamera
     * @param threshold nilai ambang batas cocok/tidak
     * @return karyawan dengan score tertinggi jika >= threshold, atau null jika
     *         tidak ada yang cocok / tidak ada karyawan aktif sama sekali
     */
    suspend operator fun invoke(embeddingWajah: FloatArray, threshold: Float): Hasil? {
        val semuaSampel = sampelWajahDao.getAll()
        if (semuaSampel.isEmpty()) return null

        var karyawanTerbaik: String? = null
        var scoreTerbaik = -1f

        for (sampel in semuaSampel) {
            val score = faceEmbedder.compare(embeddingWajah, sampel.fiturWajah)
            if (score > scoreTerbaik) {
                scoreTerbaik = score
                karyawanTerbaik = sampel.idKaryawan
            }
        }

        if (karyawanTerbaik == null || scoreTerbaik < threshold) {
            return null
        }

        val karyawan = karyawanDao.getById(karyawanTerbaik) ?: return null
        if (!karyawan.statusAktif) {
            return null // BR-02: karyawan nonaktif tidak dikenali sistem
        }

        return Hasil(karyawan, scoreTerbaik)
    }
}