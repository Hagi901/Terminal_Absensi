package com.example.terminalabsensi.presentation.admin

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.terminalabsensi.R
import com.example.terminalabsensi.domain.usecase.AutentikasiAdminUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class AdminLoginActivity : AppCompatActivity() {

    companion object {
        private const val MAKS_PERCOBAAN_GAGAL = 5
        private const val DURASI_LOCKOUT_MS = 5 * 60 * 1000L // 5 menit

        // Disimpan permanen supaya tidak bisa direset dengan menutup layar/aplikasi
        private const val PREFS_NAME = "admin_login_guard"
        private const val KEY_JUMLAH_GAGAL = "jumlah_gagal"
        private const val KEY_LOCKOUT_SAMPAI = "lockout_sampai"
    }

    private val autentikasiAdminUseCase: AutentikasiAdminUseCase by inject()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var tvJudul: TextView
    private lateinit var tvSubjudul: TextView
    private lateinit var etPin: EditText
    private lateinit var etKonfirmasiPin: EditText
    private lateinit var tvError: TextView
    private lateinit var btnMasuk: Button
    private lateinit var btnBatal: Button

    private var modeSetupPertamaKali = false
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
        lanjutkanLockoutJikaMasihBerlaku()
    }

    private fun cekModeAwal() {
        lifecycleScope.launch {
            val sudahAdaAdmin = withContext(Dispatchers.IO) {
                autentikasiAdminUseCase.sudahAdaAdmin()
            }
            modeSetupPertamaKali = !sudahAdaAdmin

            if (modeSetupPertamaKali) {
                tvJudul.text = "Setup PIN Admin"
                tvSubjudul.text = "Buat PIN admin (minimal 6 digit)"
                etKonfirmasiPin.visibility = View.VISIBLE
                btnMasuk.text = "Simpan PIN"
            } else {
                tvJudul.text = "Masuk Admin"
                tvSubjudul.text = "Masukkan PIN Admin"
                etKonfirmasiPin.visibility = View.GONE
                btnMasuk.text = "Masuk"
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

            btnMasuk.isEnabled = false
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    autentikasiAdminUseCase.setupPinPertamaKali(pin)
                }
                bukaDashboard()
            }
        } else {
            if (sisaLockoutMs() > 0L) return // sedang lockout

            btnMasuk.isEnabled = false
            lifecycleScope.launch {
                val berhasil = withContext(Dispatchers.IO) {
                    autentikasiAdminUseCase.login(pin)
                }
                if (berhasil) {
                    resetPercobaanGagal()
                    bukaDashboard()
                } else {
                    val jumlahGagal = prefs.getInt(KEY_JUMLAH_GAGAL, 0) + 1
                    if (jumlahGagal >= MAKS_PERCOBAAN_GAGAL) {
                        prefs.edit()
                            .putInt(KEY_JUMLAH_GAGAL, 0)
                            .putLong(KEY_LOCKOUT_SAMPAI, System.currentTimeMillis() + DURASI_LOCKOUT_MS)
                            .apply()
                        mulaiLockout(DURASI_LOCKOUT_MS)
                    } else {
                        prefs.edit().putInt(KEY_JUMLAH_GAGAL, jumlahGagal).apply()
                        btnMasuk.isEnabled = true
                        tvError.text = "PIN salah (percobaan $jumlahGagal/$MAKS_PERCOBAAN_GAGAL)"
                    }
                }
            }
        }
    }

    /** Sisa lockout (ms). Dibatasi ke DURASI_LOCKOUT_MS bila jam perangkat dimundurkan. */
    private fun sisaLockoutMs(): Long {
        val sampai = prefs.getLong(KEY_LOCKOUT_SAMPAI, 0L)
        val sisa = sampai - System.currentTimeMillis()
        return sisa.coerceIn(0L, DURASI_LOCKOUT_MS)
    }

    private fun lanjutkanLockoutJikaMasihBerlaku() {
        val sisa = sisaLockoutMs()
        if (sisa > 0L) mulaiLockout(sisa)
    }

    private fun resetPercobaanGagal() {
        prefs.edit()
            .putInt(KEY_JUMLAH_GAGAL, 0)
            .putLong(KEY_LOCKOUT_SAMPAI, 0L)
            .apply()
    }

    private fun mulaiLockout(durasiMs: Long) {
        lockoutTimer?.cancel()
        btnMasuk.isEnabled = false
        lockoutTimer = object : CountDownTimer(durasiMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val detik = millisUntilFinished / 1000
                tvError.text = "Terlalu banyak percobaan, coba lagi dalam ${detik}s"
            }

            override fun onFinish() {
                resetPercobaanGagal()
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