package com.example.terminalabsensi.di

import androidx.room.Room
import com.example.terminalabsensi.data.local.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "terminal_absensi.db"
        ).build()
    }

    single { get<AppDatabase>().karyawanDao() }
    single { get<AppDatabase>().sampelWajahDao() }
    single { get<AppDatabase>().absensiDao() }
    single { get<AppDatabase>().adminDao() }
    single { get<AppDatabase>().konfigurasiDao() }
}