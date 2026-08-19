package com.example.terminalabsensi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.terminalabsensi.data.local.converter.EmbeddingConverter
import com.example.terminalabsensi.data.local.entity.Karyawan
import com.example.terminalabsensi.data.local.entity.SampelWajah
import com.example.terminalabsensi.data.local.entity.Absensi
import com.example.terminalabsensi.data.local.entity.Admin
import com.example.terminalabsensi.data.local.entity.Konfigurasi
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import com.example.terminalabsensi.data.local.dao.AbsensiDao
import com.example.terminalabsensi.data.local.dao.AdminDao
import com.example.terminalabsensi.data.local.dao.KonfigurasiDao

@Database(
    entities = [
        Karyawan::class,
        SampelWajah::class,
        Absensi::class,
        Admin::class,
        Konfigurasi::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(EmbeddingConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun karyawanDao(): KaryawanDao
    abstract fun sampelWajahDao(): SampelWajahDao
    abstract fun absensiDao(): AbsensiDao
    abstract fun adminDao(): AdminDao
    abstract fun konfigurasiDao(): KonfigurasiDao
}