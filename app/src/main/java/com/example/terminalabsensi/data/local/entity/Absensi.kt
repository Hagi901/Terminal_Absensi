package com.example.terminalabsensi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "absensi",
    foreignKeys = [
        ForeignKey(
            entity = Karyawan::class,
            parentColumns = ["idKaryawan"],
            childColumns = ["idKaryawan"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idKaryawan"]),
        Index(value = ["tanggal"]),
        Index(value = ["idKaryawan", "tanggal", "jenisAbsen"], unique = true)
    ]
)
data class Absensi(
    @PrimaryKey
    val idAbsensi: String = UUID.randomUUID().toString(),
    val idKaryawan: String,
    val tanggal: String,           // format "yyyy-MM-dd", memudahkan query filter tanggal
    val jenisAbsen: String,        // "masuk" / "pulang"
    val timestamp: Long,           // epoch millis, waktu presisi transaksi
    val status: String,            // "Tepat Waktu" / "Terlambat" / "Pulang Normal" / "Pulang Cepat"
    val selisihMenit: Int? = null, // menit telat/pulang cepat, null jika tidak relevan
    val keterangan: String? = null,       // "Izin" / "Sakit" / "Lainnya" / "Tanpa Keterangan", hanya untuk Pulang Cepat
    val catatanTambahan: String? = null,  // teks bebas untuk opsi "Lainnya"
    val keteranganDiubahOleh: String? = null,  // audit trail: username admin
    val keteranganDiubahPada: Long? = null,    // audit trail: waktu perubahan
    val confidenceScore: Float
)