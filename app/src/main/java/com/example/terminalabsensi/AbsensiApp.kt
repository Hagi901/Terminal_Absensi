package com.example.terminalabsensi

import android.app.Application
import android.util.Log
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.entity.Karyawan
import com.example.terminalabsensi.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.opencv.android.OpenCVLoader

class AbsensiApp : Application() {

    companion object {
        private const val TAG = "AbsensiApp"
    }

    private val karyawanDao: KaryawanDao by inject(KaryawanDao::class.java)

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

        // Seed data dummy untuk keperluan testing (Fase 1/3), sesuai Task Breakdown
        seedDummyData()
    }

    private fun seedDummyData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existing = karyawanDao.getById("DUMMY001")
                if (existing == null) {
                    karyawanDao.insert(
                        Karyawan(
                            idKaryawan = "DUMMY001",
                            nama = "Karyawan Uji Coba",
                            jabatan = "Tester",
                            statusAktif = true
                        )
                    )
                    Log.i(TAG, "Seed data karyawan dummy berhasil dibuat")
                } else {
                    Log.i(TAG, "Seed data karyawan dummy sudah ada, tidak dibuat ulang")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membuat seed data", e)
            }
        }
    }
}