package com.example.terminalabsensi.presentation.karyawan

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import androidx.lifecycle.lifecycleScope
import com.example.terminalabsensi.data.local.dao.AbsensiDao
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class EditKaryawanActivity : AppCompatActivity() {

    private val karyawanDao: KaryawanDao by inject()
    private val sampelWajahDao: SampelWajahDao by inject()
    private val absensiDao: AbsensiDao by inject()

    private lateinit var tvIdKaryawan: TextView
    private lateinit var etNama: EditText
    private lateinit var etJabatan: EditText
    private lateinit var switchAktif: Switch
    private lateinit var btnSimpan: Button
    private lateinit var btnHapus: Button

    private lateinit var idKaryawan: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_karyawan)

        idKaryawan = intent.getStringExtra("idKaryawan") ?: run {
            finish()
            return
        }

        tvIdKaryawan = findViewById(R.id.tvIdKaryawan)
        etNama = findViewById(R.id.etNama)
        etJabatan = findViewById(R.id.etJabatan)
        switchAktif = findViewById(R.id.switchAktif)
        btnSimpan = findViewById(R.id.btnSimpan)
        btnHapus = findViewById(R.id.btnHapus)

        tvIdKaryawan.text = "ID Karyawan: $idKaryawan (tidak dapat diubah)"

        muatDataKaryawan()

        btnSimpan.setOnClickListener { onKlikSimpan() }
        btnHapus.setOnClickListener { onKlikHapus() }
    }

    private fun muatDataKaryawan() {
        lifecycleScope.launch {
            val karyawan = withContext(Dispatchers.IO) { karyawanDao.getById(idKaryawan) }
            if (karyawan == null) {
                Toast.makeText(this@EditKaryawanActivity, "Data karyawan tidak ditemukan", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            etNama.setText(karyawan.nama)
            etJabatan.setText(karyawan.jabatan ?: "")
            switchAktif.isChecked = karyawan.statusAktif
        }
    }

    private fun onKlikSimpan() {
        val namaBaru = etNama.text.toString().trim()
        val jabatanBaru = etJabatan.text.toString().trim().ifBlank { null }
        val statusBaru = switchAktif.isChecked

        if (namaBaru.isBlank()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val karyawanLama = karyawanDao.getById(idKaryawan) ?: return@withContext
                karyawanDao.update(
                    karyawanLama.copy(
                        nama = namaBaru,
                        jabatan = jabatanBaru,
                        statusAktif = statusBaru,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            Toast.makeText(this@EditKaryawanActivity, "Perubahan disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun onKlikHapus() {
        lifecycleScope.launch {
            val jumlahRiwayat = withContext(Dispatchers.IO) { absensiDao.countByKaryawan(idKaryawan) }

            if (jumlahRiwayat == 0) {
                AlertDialog.Builder(this@EditKaryawanActivity)
                    .setTitle("Hapus Karyawan")
                    .setMessage("Yakin ingin menghapus karyawan ini beserta data wajahnya? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ -> hapusKaryawan() }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                AlertDialog.Builder(this@EditKaryawanActivity)
                    .setTitle("Karyawan Memiliki Riwayat Presensi")
                    .setMessage(
                        "Karyawan ini memiliki $jumlahRiwayat catatan presensi.\n\n" +
                                "Disarankan NONAKTIFKAN agar riwayat tetap tersimpan di laporan dan " +
                                "karyawan tidak bisa absen lagi.\n\n" +
                                "Hapus permanen akan menghapus data wajah DAN seluruh riwayat presensinya."
                    )
                    .setPositiveButton("Nonaktifkan") { _, _ -> nonaktifkanKaryawan() }
                    .setNeutralButton("Hapus Permanen") { _, _ -> konfirmasiHapusPermanen(jumlahRiwayat) }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun konfirmasiHapusPermanen(jumlahRiwayat: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Permanen?")
            .setMessage("$jumlahRiwayat catatan presensi akan hilang selamanya dan tidak dapat dipulihkan.")
            .setPositiveButton("Ya, Hapus") { _, _ -> hapusKaryawan() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun nonaktifkanKaryawan() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val karyawan = karyawanDao.getById(idKaryawan) ?: return@withContext
                karyawanDao.update(karyawan.copy(statusAktif = false, updatedAt = System.currentTimeMillis()))
            }
            Toast.makeText(this@EditKaryawanActivity, "Karyawan dinonaktifkan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun hapusKaryawan() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val karyawan = karyawanDao.getById(idKaryawan) ?: return@withContext
                karyawanDao.delete(karyawan)
            }
            Toast.makeText(this@EditKaryawanActivity, "Karyawan berhasil dihapus", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}