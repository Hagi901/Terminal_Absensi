package com.example.terminalabsensi

import android.app.Application
import android.util.Log
import com.example.terminalabsensi.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.opencv.android.OpenCVLoader

class AbsensiApp : Application() {

    companion object {
        private const val TAG = "AbsensiApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi OpenCV (WAJIB sebelum modul face recognition dipakai)
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV berhasil dimuat")
        } else {
            Log.e(TAG, "Gagal memuat OpenCV!")
        }

        // Inisialisasi Koin Dependency Injection
        startKoin {
            androidContext(this@AbsensiApp)
            modules(appModule)
        }

        Log.i(TAG, "Pondasi Koin DI Berhasil Dimuat!")
    }
}