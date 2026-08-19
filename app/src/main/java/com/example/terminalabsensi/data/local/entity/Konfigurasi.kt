package com.example.terminalabsensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "konfigurasi")
data class Konfigurasi(
    @PrimaryKey
    val id: Int = 1, // selalu bernilai 1, single-row table
    val jamMulaiKerja: String = "08:00",       // format "HH:mm"
    val batasToleransiTelat: String = "08:30", // format "HH:mm"
    val jamPulangKerja: String = "17:00",      // format "HH:mm"
    val thresholdConfidence: Float = 0.6f      // nilai default sementara, akan dikalibrasi nanti di Fase 2
)