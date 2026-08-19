package com.example.terminalabsensi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sampel_wajah",
    foreignKeys = [
        ForeignKey(
            entity = Karyawan::class,
            parentColumns = ["idKaryawan"],
            childColumns = ["idKaryawan"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SampelWajah(
    @PrimaryKey
    val idSampel: String = UUID.randomUUID().toString(),
    val idKaryawan: String,
    val fiturWajah: FloatArray,
    val sudutCapture: String, // "depan" / "kiri" / "kanan"
    val createdAt: Long = System.currentTimeMillis()
)