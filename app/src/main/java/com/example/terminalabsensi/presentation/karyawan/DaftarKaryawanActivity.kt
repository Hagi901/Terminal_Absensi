package com.example.terminalabsensi.presentation.karyawan

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DaftarKaryawanActivity : AppCompatActivity() {

    private val karyawanDao: KaryawanDao by inject()
    private val sampelWajahDao: SampelWajahDao by inject()

    private lateinit var tvDaftarKaryawan: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daftar_karyawan)

        tvDaftarKaryawan = findViewById(R.id.tvDaftarKaryawan)

        findViewById<Button>(R.id.btnTambahKaryawan).setOnClickListener {
            startActivity(Intent(this, TambahKaryawanActivity::class.java))
        }

        muatDaftarKaryawan()
    }

    private fun muatDaftarKaryawan() {
        CoroutineScope(Dispatchers.IO).launch {
            karyawanDao.getAll().collectLatest { daftarKaryawan ->
                val teks = if (daftarKaryawan.isEmpty()) {
                    "Belum ada karyawan terdaftar."
                } else {
                    val baris = mutableListOf<String>()
                    for (karyawan in daftarKaryawan) {
                        val jumlahSampel = sampelWajahDao.countByKaryawan(karyawan.idKaryawan)
                        val status = if (karyawan.statusAktif) "Aktif" else "Nonaktif"
                        baris.add("ID: ${karyawan.idKaryawan}\nNama: ${karyawan.nama}\nJabatan: ${karyawan.jabatan ?: "-"}\nStatus: $status\nSampel Wajah: $jumlahSampel")
                    }
                    baris.joinToString("\n\n")
                }
                runOnUiThread {
                    tvDaftarKaryawan.text = teks
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        muatDaftarKaryawan() // refresh setiap kembali dari Tambah Karyawan
    }
}