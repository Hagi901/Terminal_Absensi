package com.example.terminalabsensi.presentation.karyawan

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import com.example.terminalabsensi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DaftarKaryawanActivity : AppCompatActivity() {

    private val karyawanDao: KaryawanDao by inject()
    private val sampelWajahDao: SampelWajahDao by inject()

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daftar_karyawan)

        container = findViewById(R.id.containerDaftarKaryawan)

        findViewById<Button>(R.id.btnTambahKaryawan).setOnClickListener {
            startActivity(Intent(this, TambahKaryawanActivity::class.java))
        }

        muatDaftarKaryawan()
    }

    private fun muatDaftarKaryawan() {
        CoroutineScope(Dispatchers.IO).launch {
            karyawanDao.getAll().collectLatest { daftarKaryawan ->
                val dataBaris = mutableListOf<Triple<String, String, Int>>()
                for (karyawan in daftarKaryawan) {
                    val jumlahSampel = sampelWajahDao.countByKaryawan(karyawan.idKaryawan)
                    val statusText = if (karyawan.statusAktif) "Aktif" else "Nonaktif"
                    val ringkasan = "${karyawan.nama} (${karyawan.idKaryawan})\n${karyawan.jabatan ?: "-"} — $statusText — Sampel: $jumlahSampel"
                    dataBaris.add(Triple(karyawan.idKaryawan, ringkasan, jumlahSampel))
                }

                runOnUiThread {
                    tampilkanBaris(dataBaris)
                }
            }
        }
    }

    private fun tampilkanBaris(dataBaris: List<Triple<String, String, Int>>) {
        container.removeAllViews()

        if (dataBaris.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Belum ada karyawan terdaftar."
            tv.setTextColor(0xFF000000.toInt())
            container.addView(tv)
            return
        }

        for ((idKaryawan, ringkasan, _) in dataBaris) {
            val tv = TextView(this)
            tv.text = ringkasan
            tv.setTextColor(0xFF000000.toInt())
            tv.setPadding(16, 16, 16, 16)
            tv.gravity = Gravity.START
            tv.setBackgroundColor(0xFFF0F0F0.toInt())
            tv.setOnClickListener {
                val intent = Intent(this, EditKaryawanActivity::class.java)
                intent.putExtra("idKaryawan", idKaryawan)
                startActivity(intent)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12
            tv.layoutParams = params

            container.addView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        muatDaftarKaryawan()
    }
}