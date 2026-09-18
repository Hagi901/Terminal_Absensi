package com.example.terminalabsensi.presentation.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout  // ← tambahkan import ini
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.presentation.attendance.AttendanceActivity
import com.example.terminalabsensi.presentation.karyawan.DaftarKaryawanActivity
import com.example.terminalabsensi.presentation.laporan.LaporanActivity

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        findViewById<LinearLayout>(R.id.btnManajemenKaryawan).setOnClickListener {
            startActivity(Intent(this, DaftarKaryawanActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnLaporanAbsensi).setOnClickListener {
            startActivity(Intent(this, LaporanActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnPengaturan).setOnClickListener {
            startActivity(Intent(this, PengaturanActivity::class.java))
        }

        findViewById<Button>(R.id.btnKeluar).setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}