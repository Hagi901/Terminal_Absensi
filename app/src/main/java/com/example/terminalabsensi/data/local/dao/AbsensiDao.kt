package com.example.terminalabsensi.data.local.dao

import androidx.room.*
import com.example.terminalabsensi.data.local.entity.Absensi
import kotlinx.coroutines.flow.Flow

@Dao
interface AbsensiDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(absensi: Absensi): Long

    @Update
    suspend fun update(absensi: Absensi)

    // Dipakai saat penentuan jenis absensi (FR-3.2.4):
    // cek apakah karyawan sudah absen masuk/pulang pada tanggal tertentu
    @Query("SELECT * FROM absensi WHERE idKaryawan = :idKaryawan AND tanggal = :tanggal")
    suspend fun getByKaryawanDanTanggal(idKaryawan: String, tanggal: String): List<Absensi>

    // Dipakai untuk cek anti-duplikasi (<1 menit dari transaksi sukses terakhir)
    @Query("SELECT * FROM absensi WHERE idKaryawan = :idKaryawan ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTransaksiTerakhir(idKaryawan: String): Absensi?

    // Dipakai untuk modul laporan (FR-3.3.1 - FR-3.3.2): filter rentang tanggal
    @Query("SELECT * FROM absensi WHERE tanggal BETWEEN :tanggalMulai AND :tanggalAkhir ORDER BY tanggal ASC, timestamp ASC")
    fun getByRentangTanggal(tanggalMulai: String, tanggalAkhir: String): Flow<List<Absensi>>

    // Filter laporan dengan karyawan tertentu
    @Query("SELECT * FROM absensi WHERE tanggal BETWEEN :tanggalMulai AND :tanggalAkhir AND idKaryawan = :idKaryawan ORDER BY tanggal ASC, timestamp ASC")
    fun getByRentangTanggalDanKaryawan(tanggalMulai: String, tanggalAkhir: String, idKaryawan: String): Flow<List<Absensi>>

    // Dipakai admin untuk update keterangan manual (BR-01e)
    @Query("""
        UPDATE absensi 
        SET keterangan = :keterangan, catatanTambahan = :catatan, 
            keteranganDiubahOleh = :diubahOleh, keteranganDiubahPada = :diubahPada
        WHERE idAbsensi = :idAbsensi
    """)
    suspend fun updateKeterangan(
        idAbsensi: String,
        keterangan: String,
        catatan: String?,
        diubahOleh: String,
        diubahPada: Long
    )
}