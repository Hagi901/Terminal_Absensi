package com.example.terminalabsensi.di

import androidx.room.Room
import com.example.terminalabsensi.data.local.AppDatabase
import com.example.terminalabsensi.facerecognition.FaceDetector
import com.example.terminalabsensi.facerecognition.FaceEmbedder
import org.koin.dsl.module
import com.example.terminalabsensi.facerecognition.FaceDetectorYNWrapper
import com.example.terminalabsensi.domain.usecase.TentukanJenisAbsensiUseCase
import com.example.terminalabsensi.domain.usecase.TentukanStatusUseCase
import com.example.terminalabsensi.domain.usecase.ValidasiAntiDuplikasiUseCase
import com.example.terminalabsensi.domain.usecase.ValidasiEnrollmentUseCase


val appModule = module {
    // Menyediakan instance database Room secara tunggal (Singleton)
    single {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            "absensi_db"
        ).build()
    }

    // DAO — masing-masing diambil dari instance AppDatabase di atas
    single { get<AppDatabase>().karyawanDao() }
    single { get<AppDatabase>().sampelWajahDao() }
    single { get<AppDatabase>().absensiDao() }
    single { get<AppDatabase>().adminDao() }
    single { get<AppDatabase>().konfigurasiDao() }

    // Menyediakan instance FaceDetector secara tunggal (Singleton),
    // supaya cascade classifier cukup di-load sekali selama app hidup
    single { FaceDetector(get()) }
    single { FaceEmbedder(get()) }
    single { FaceDetectorYNWrapper(get()) }

    single { TentukanJenisAbsensiUseCase(get()) }
    single { TentukanStatusUseCase(get()) }
    single { ValidasiAntiDuplikasiUseCase(get()) }
    single { ValidasiEnrollmentUseCase(get()) }
}