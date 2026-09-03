package com.example.terminalabsensi.presentation.admin

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.data.local.dao.KonfigurasiDao
import com.example.terminalabsensi.data.local.entity.Konfigurasi
import com.example.terminalabsensi.domain.usecase.AutentikasiAdminUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PengaturanActivity : AppCompatActivity() {

    private val konfigurasiDao: KonfigurasiDao by inject()
    private val autentikasiAdminUseCase: AutentikasiAdminUseCase by inject()

    private lateinit var etJamMulai: EditText
    private lateinit var etBatasTelat: EditText
    private lateinit var etJamPulang: EditText
    private lateinit var etPinLama: EditText
    private lateinit var etPinBaru: EditText
    private lateinit var etKonfirmasiPinBaru: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pengaturan)

        etJamMulai = findViewById(R.id.etJamMulai)
        etBatasTelat = findViewById(R.id.etBatasTelat)
        etJamPulang = findViewById(R.id.etJamPulang)
        etPinLama = findViewById(R.id.etPinLama)
        etPinBaru = findViewById(R.id.etPinBaru)
        etKonfirmasiPinBaru = findViewById(R.id.etKonfirmasiPinBaru)

        muatKonfigurasi()

        findViewById<Button>(R.id.btnSimpanJamKerja).setOnClickListener { onKlikSimpanJamKerja() }
        findViewById<Button>(R.id.btnUbahPin).setOnClickListener { onKlikUbahPin() }
    }

    private fun muatKonfigurasi() {
        CoroutineScope(Dispatchers.IO).launch {
            val konfigurasi = konfigurasiDao.get() ?: Konfigurasi()
            runOnUiThread {
                etJamMulai.setText(konfigurasi.jamMulaiKerja)
                etBatasTelat.setText(konfigurasi.batasToleransiTelat)
                etJamPulang.setText(konfigurasi.jamPulangKerja)
            }
        }
    }

    private fun formatJamValid(jam: String): Boolean {
        return Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(jam)
    }

    private fun onKlikSimpanJamKerja() {
        val jamMulai = etJamMulai.text.toString().trim()
        val batasTelat = etBatasTelat.text.toString().trim()
        val jamPulang = etJamPulang.text.toString().trim()

        if (!formatJamValid(jamMulai) || !formatJamValid(batasTelat) || !formatJamValid(jamPulang)) {
            Toast.makeText(this, "Format jam harus HH:mm, contoh 08:00", Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            konfigurasiDao.insertOrUpdate(
                Konfigurasi(
                    jamMulaiKerja = jamMulai,
                    batasToleransiTelat = batasTelat,
                    jamPulangKerja = jamPulang
                )
            )
            runOnUiThread {
                Toast.makeText(this@PengaturanActivity, "Jam kerja berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onKlikUbahPin() {
        val pinLama = etPinLama.text.toString()
        val pinBaru = etPinBaru.text.toString()
        val konfirmasi = etKonfirmasiPinBaru.text.toString()

        if (pinBaru.length < 6) {
            Toast.makeText(this, "PIN baru minimal 6 digit", Toast.LENGTH_SHORT).show()
            return
        }
        if (pinBaru != konfirmasi) {
            Toast.makeText(this, "Konfirmasi PIN baru tidak sama", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val berhasil = autentikasiAdminUseCase.ubahPin(pinLama, pinBaru)
            runOnUiThread {
                if (berhasil) {
                    Toast.makeText(this@PengaturanActivity, "PIN berhasil diubah", Toast.LENGTH_SHORT).show()
                    etPinLama.text.clear()
                    etPinBaru.text.clear()
                    etKonfirmasiPinBaru.text.clear()
                } else {
                    Toast.makeText(this@PengaturanActivity, "PIN lama salah", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}