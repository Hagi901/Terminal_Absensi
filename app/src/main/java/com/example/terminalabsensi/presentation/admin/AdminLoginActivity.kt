package com.example.terminalabsensi.presentation.admin

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.domain.usecase.AutentikasiAdminUseCase
import com.example.terminalabsensi.presentation.karyawan.TambahKaryawanActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.example.terminalabsensi.presentation.admin.DashboardActivity

class AdminLoginActivity : AppCompatActivity() {

    companion object {
        private const val MAKS_PERCOBAAN_GAGAL = 5
        private const val DURASI_LOCKOUT_MS = 5 * 60 * 1000L // 5 menit
    }

    private val autentikasiAdminUseCase: AutentikasiAdminUseCase by inject()

    private lateinit var tvJudul: TextView
    private lateinit var tvSubjudul: TextView
    private lateinit var etPin: EditText
    private lateinit var etKonfirmasiPin: EditText
    private lateinit var tvError: TextView
    private lateinit var btnMasuk: Button
    private lateinit var btnBatal: Button

    private var modeSetupPertamaKali = false
    private var jumlahPercobaanGagal = 0
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        tvJudul = findViewById(R.id.tvJudul)
        tvSubjudul = findViewById(R.id.tvSubjudul)
        etPin = findViewById(R.id.etPin)
        etKonfirmasiPin = findViewById(R.id.etKonfirmasiPin)
        tvError = findViewById(R.id.tvError)
        btnMasuk = findViewById(R.id.btnMasuk)
        btnBatal = findViewById(R.id.btnBatal)

        btnBatal.setOnClickListener { finish() }
        btnMasuk.setOnClickListener { onKlikMasuk() }

        cekModeAwal()
    }

    private fun cekModeAwal() {
        CoroutineScope(Dispatchers.IO).launch {
            val sudahAdaAdmin = autentikasiAdminUseCase.sudahAdaAdmin()
            modeSetupPertamaKali = !sudahAdaAdmin

            runOnUiThread {
                if (modeSetupPertamaKali) {
                    tvJudul.text = "Setup PIN Admin"
                    tvSubjudul.text = "Buat PIN admin (minimal 6 digit)"
                    etKonfirmasiPin.visibility = android.view.View.VISIBLE
                    btnMasuk.text = "Simpan PIN"
                } else {
                    tvJudul.text = "Masuk Admin"
                    tvSubjudul.text = "Masukkan PIN Admin"
                    etKonfirmasiPin.visibility = android.view.View.GONE
                    btnMasuk.text = "Masuk"
                }
            }
        }
    }

    private fun onKlikMasuk() {
        tvError.text = ""
        val pin = etPin.text.toString()

        if (modeSetupPertamaKali) {
            val konfirmasi = etKonfirmasiPin.text.toString()
            if (pin.length < 6) {
                tvError.text = "PIN minimal 6 digit"
                return
            }
            if (pin != konfirmasi) {
                tvError.text = "Konfirmasi PIN tidak sama"
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                autentikasiAdminUseCase.setupPinPertamaKali(pin)
                runOnUiThread {
                    bukaDashboard()
                }
            }
        } else {
            if (lockoutTimer != null) return // sedang lockout

            CoroutineScope(Dispatchers.IO).launch {
                val berhasil = autentikasiAdminUseCase.login(pin)
                runOnUiThread {
                    if (berhasil) {
                        jumlahPercobaanGagal = 0
                        bukaDashboard()
                    } else {
                        jumlahPercobaanGagal++
                        if (jumlahPercobaanGagal >= MAKS_PERCOBAAN_GAGAL) {
                            mulaiLockout()
                        } else {
                            tvError.text = "PIN salah (percobaan $jumlahPercobaanGagal/$MAKS_PERCOBAAN_GAGAL)"
                        }
                    }
                }
            }
        }
    }

    private fun mulaiLockout() {
        btnMasuk.isEnabled = false
        lockoutTimer = object : CountDownTimer(DURASI_LOCKOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val detik = millisUntilFinished / 1000
                tvError.text = "Terlalu banyak percobaan, coba lagi dalam ${detik}s"
            }

            override fun onFinish() {
                jumlahPercobaanGagal = 0
                tvError.text = ""
                btnMasuk.isEnabled = true
                lockoutTimer = null
            }
        }.start()
    }

    private fun bukaDashboard() {
        startActivity(android.content.Intent(this, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
    }
}