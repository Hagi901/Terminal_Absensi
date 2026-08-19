package com.example.terminalabsensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "karyawan")
data class Karyawan(
    @PrimaryKey
    val idKaryawan: String,
    val nama: String,
    val jabatan: String?,
    val statusAktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)