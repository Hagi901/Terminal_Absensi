package com.example.terminalabsensi.domain.usecase

import com.example.terminalabsensi.data.local.dao.KonfigurasiDao
import com.example.terminalabsensi.data.local.entity.Konfigurasi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Menentukan status absensi (Tepat Waktu/Terlambat untuk absen masuk,
 * Pulang Normal/Pulang Cepat untuk absen pulang) berdasarkan konfigurasi
 * jam kerja, sesuai FR-3.2.4 di SRS.
 */
class TentukanStatusUseCase(
    private val konfigurasiDao: KonfigurasiDao
) {

    data class Hasil(
        val status: String,       // "Tepat Waktu" / "Terlambat" / "Pulang Normal" / "Pulang Cepat"
        val selisihMenit: Int?    // null jika Tepat Waktu / Pulang Normal
    )

    private val formatJam = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * @param jenisAbsen "masuk" atau "pulang"
     * @param waktuTransaksi epoch millis waktu transaksi terjadi
     */
    suspend operator fun invoke(jenisAbsen: String, waktuTransaksi: Long): Hasil {
        // Ambil konfigurasi, atau pakai default jika belum ada di database
        val konfigurasi = konfigurasiDao.get() ?: Konfigurasi()

        val kalender = Calendar.getInstance().apply { timeInMillis = waktuTransaksi }
        val menitSaatIni = kalender.get(Calendar.HOUR_OF_DAY) * 60 + kalender.get(Calendar.MINUTE)

        return if (jenisAbsen == "masuk") {
            val menitBatasTelat = menitDariString(konfigurasi.batasToleransiTelat)
            val menitJamMulai = menitDariString(konfigurasi.jamMulaiKerja)

            if (menitSaatIni <= menitBatasTelat) {
                Hasil("Tepat Waktu", null)
            } else {
                Hasil("Terlambat", menitSaatIni - menitJamMulai)
            }
        } else {
            val menitJamPulang = menitDariString(konfigurasi.jamPulangKerja)

            if (menitSaatIni >= menitJamPulang) {
                Hasil("Pulang Normal", null)
            } else {
                Hasil("Pulang Cepat", menitJamPulang - menitSaatIni)
            }
        }
    }

    /** Mengubah string "HH:mm" jadi total menit sejak tengah malam */
    private fun menitDariString(jam: String): Int {
        val bagian = jam.split(":")
        val jamInt = bagian[0].toIntOrNull() ?: 0
        val menitInt = bagian.getOrNull(1)?.toIntOrNull() ?: 0
        return jamInt * 60 + menitInt
    }
}