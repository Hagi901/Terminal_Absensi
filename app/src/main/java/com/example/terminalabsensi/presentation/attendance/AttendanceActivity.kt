package com.example.terminalabsensi.presentation.attendance

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.terminalabsensi.R
import com.example.terminalabsensi.data.local.dao.AbsensiDao
import com.example.terminalabsensi.data.local.entity.Absensi
import com.example.terminalabsensi.domain.usecase.CariKaryawanDenganWajahUseCase
import com.example.terminalabsensi.domain.usecase.TentukanJenisAbsensiUseCase
import com.example.terminalabsensi.domain.usecase.TentukanStatusUseCase
import com.example.terminalabsensi.domain.usecase.ValidasiAntiDuplikasiUseCase
import com.example.terminalabsensi.facerecognition.FaceDetectorYNWrapper
import com.example.terminalabsensi.facerecognition.FaceEmbedder
import com.example.terminalabsensi.facerecognition.FaceUtils
import com.example.terminalabsensi.presentation.admin.AdminLoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class AttendanceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AttendanceActivity"
        private const val THRESHOLD_SEMENTARA = 0.5f
        private const val COOLDOWN_MS = 5000L
        private const val DURASI_TAMPIL_HASIL_MS = 3000L
        private const val DURASI_TIMEOUT_KETERANGAN_DETIK = 15
    }

    private val faceDetectorYN: FaceDetectorYNWrapper by inject()
    private val faceEmbedder: FaceEmbedder by inject()
    private val absensiDao: AbsensiDao by inject()
    private val cariKaryawanDenganWajahUseCase: CariKaryawanDenganWajahUseCase by inject()
    private val tentukanJenisAbsensiUseCase: TentukanJenisAbsensiUseCase by inject()
    private val tentukanStatusUseCase: TentukanStatusUseCase by inject()
    private val validasiAntiDuplikasiUseCase: ValidasiAntiDuplikasiUseCase by inject()

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private lateinit var tvStatus: TextView
    private lateinit var tvJam: TextView

    private lateinit var overlayHasil: LinearLayout
    private lateinit var tvIkonHasil: TextView
    private lateinit var tvNamaHasil: TextView
    private lateinit var tvPesanHasil: TextView

    private lateinit var overlayKeterangan: LinearLayout
    private lateinit var btnIzin: Button
    private lateinit var btnSakit: Button
    private lateinit var btnLainnya: Button
    private lateinit var btnLewati: Button
    private lateinit var progressTimeout: ProgressBar

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }

    @Volatile private var sedangMemprosesAbsensi = false
    @Volatile private var waktuPercobaanTerakhir = 0L
    @Volatile private var overlayHasilTampil = false

    private val jamHandler = Handler(Looper.getMainLooper())
    private val jamRunnable = object : Runnable {
        override fun run() {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvJam.text = format.format(Date())
            jamHandler.postDelayed(this, 15_000L)
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera dibutuhkan untuk fitur presensi wajah",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        // ===== KIOSK MODE: Full screen & layar tetap menyala =====
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(
                        android.view.WindowInsets.Type.statusBars() or
                                android.view.WindowInsets.Type.navigationBars()
                    )
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
            }
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.w(TAG, "Kiosk mode gagal diaktifkan: ${e.message}")
        }
        // ===== AKHIR KIOSK MODE =====

        // Blokir tombol Back pada mode kiosk
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Sengaja dikosongkan — kiosk tidak boleh keluar sembarangan
                }
            }
        )

        previewView = findViewById(R.id.previewView)
        faceOverlayView = findViewById(R.id.faceOverlayView)
        tvStatus = findViewById(R.id.tvStatus)
        tvJam = findViewById(R.id.tvJam)

        overlayHasil = findViewById(R.id.overlayHasil)
        tvIkonHasil = findViewById(R.id.tvIkonHasil)
        tvNamaHasil = findViewById(R.id.tvNamaHasil)
        tvPesanHasil = findViewById(R.id.tvPesanHasil)

        overlayKeterangan = findViewById(R.id.overlayKeterangan)
        btnIzin = findViewById(R.id.btnIzin)
        btnSakit = findViewById(R.id.btnSakit)
        btnLainnya = findViewById(R.id.btnLainnya)
        btnLewati = findViewById(R.id.btnLewati)
        progressTimeout = findViewById(R.id.progressTimeout)

        findViewById<View>(R.id.rootLayout).setOnLongClickListener {
            startActivity(android.content.Intent(this, AdminLoginActivity::class.java))
            true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            val successYN = faceDetectorYN.setup(R.raw.face_detection_yunet)
            val successEmbedder = faceEmbedder.setup(R.raw.face_recognition_sface)

            if (!successYN || !successEmbedder) {
                val pesanGagal = buildString {
                    if (!successYN) appendLine("• Model deteksi wajah (YuNet) gagal dimuat.")
                    if (!successEmbedder) appendLine("• Model pengenalan wajah (SFace) gagal dimuat.")
                    appendLine("\nCoba restart aplikasi. Jika masalah berlanjut, hubungi administrator.")
                }
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Sistem Tidak Siap")
                        .setMessage(pesanGagal.trim())
                        .setCancelable(false)
                        .setPositiveButton("Tutup Aplikasi") { _, _ -> finish() }
                        .show()
                }
            } else {
                Log.i(TAG, "Semua model AI berhasil dimuat.")
            }
        }

        // Jika belum ada izin, minta izin kamera. Jika sudah ada, kamera akan distart otomatis oleh onResume()
        if (!hasCameraPermission()) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            startCamera()
        }
        jamHandler.post(jamRunnable)
    }

    override fun onPause() {
        super.onPause()
        jamHandler.removeCallbacks(jamRunnable)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, ::processFrame) }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal bind kamera", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!faceDetectorYN.isReady() || !faceEmbedder.isReady()) return

            var colorMat = FaceUtils.imageProxyToColorMat(imageProxy)
            colorMat = FaceUtils.rotateMat(colorMat, imageProxy.imageInfo.rotationDegrees)

            faceDetectorYN.setInputSize(colorMat.width(), colorMat.height())
            val detections = faceDetectorYN.detect(colorMat)

            val rectsUntukOverlay = if (detections != null) {
                faceDetectorYN.detectionsToRects(detections)
            } else {
                emptyList()
            }

            val jumlahWajah = detections?.rows() ?: 0

            var debugText = when {
                jumlahWajah == 0 -> "Arahkan wajah ke kamera"
                jumlahWajah > 1  -> "Terdeteksi $jumlahWajah wajah, pastikan hanya 1 orang"
                else             -> "Wajah terdeteksi"
            }

            val bolehProses = jumlahWajah == 1 &&
                    !sedangMemprosesAbsensi &&
                    !overlayHasilTampil

            if (bolehProses) {
                val faceRow = detections!!.row(0)
                val embedding = faceEmbedder.extractEmbeddingAligned(colorMat, faceRow)

                if (embedding != null) {
                    debugText = "Mencocokkan wajah..."
                    val sekarang = System.currentTimeMillis()
                    if (sekarang - waktuPercobaanTerakhir > COOLDOWN_MS) {
                        waktuPercobaanTerakhir = sekarang
                        cariDanProsesAbsensi(embedding)
                    }
                }
            }

            val matWidth = colorMat.width()
            val matHeight = colorMat.height()

            detections?.release()
            colorMat.release()

            val finalText = debugText
            runOnUiThread {
                faceOverlayView.setSourceSize(matWidth, matHeight)
                faceOverlayView.updateFaces(rectsUntukOverlay)
                if (!sedangMemprosesAbsensi && !overlayHasilTampil) {
                    tvStatus.text = finalText
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memproses frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun cariDanProsesAbsensi(embeddingWajah: FloatArray) {
        sedangMemprosesAbsensi = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasilPencarian = cariKaryawanDenganWajahUseCase(embeddingWajah, THRESHOLD_SEMENTARA)

                if (hasilPencarian == null) {
                    tampilkanOverlayHasil(
                        tipe = TipeOverlay.GAGAL,
                        nama = "",
                        pesan = "Wajah tidak dikenali, silakan coba lagi"
                    )
                    return@launch
                }

                val idKaryawan = hasilPencarian.karyawan.idKaryawan
                val namaKaryawan = hasilPencarian.karyawan.nama
                val confidenceScore = hasilPencarian.confidenceScore
                val sekarang = System.currentTimeMillis()

                val bolehLanjut = validasiAntiDuplikasiUseCase(idKaryawan, sekarang)
                if (!bolehLanjut) {
                    tampilkanOverlayHasil(tipe = TipeOverlay.NETRAL, nama = namaKaryawan, pesan = "Presensi terlalu cepat, coba lagi sebentar")
                    return@launch
                }

                val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val tanggalHariIni = formatTanggal.format(Date(sekarang))
                val jenisHasil = tentukanJenisAbsensiUseCase(idKaryawan, tanggalHariIni)

                when (jenisHasil) {
                    is TentukanJenisAbsensiUseCase.Hasil.SudahLengkap -> {
                        tampilkanOverlayHasil(
                            tipe = TipeOverlay.NETRAL,
                            nama = namaKaryawan,
                            pesan = "Anda sudah menyelesaikan presensi hari ini"
                        )
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenMasuk -> {
                        val statusHasil = tentukanStatusUseCase("masuk", sekarang)
                        simpanAbsensi(idKaryawan, "masuk", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang, null, null)
                        tampilkanOverlayHasil(tipe = TipeOverlay.SUKSES, nama = namaKaryawan, pesan = "Presensi Masuk — ${statusHasil.status}")
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenPulang -> {
                        val statusHasil = tentukanStatusUseCase("pulang", sekarang)
                        if (statusHasil.status == "Pulang Cepat") {
                            val (keterangan, catatan) = tampilkanPilihanKeteranganDanTunggu()
                            simpanAbsensi(idKaryawan, "pulang", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang, keterangan, catatan)
                            tampilkanOverlayHasil(tipe = TipeOverlay.SUKSES, nama = namaKaryawan, pesan = "Presensi Pulang — Pulang Cepat ($keterangan)")
                        } else {
                            simpanAbsensi(idKaryawan, "pulang", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang, null, null)
                            tampilkanOverlayHasil(tipe = TipeOverlay.SUKSES, nama = namaKaryawan, pesan = "Presensi Pulang — ${statusHasil.status}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saat memproses absensi", e)
                tampilkanOverlayHasil(tipe = TipeOverlay.GAGAL, nama = "", pesan = "Terjadi kesalahan, coba lagi")
            } finally {
                sedangMemprosesAbsensi = false
            }
        }
    }

    private suspend fun tampilkanPilihanKeteranganDanTunggu(): Pair<String, String?> {
        return suspendCancellableCoroutine { cont ->
            var sudahDijawab = false

            fun jawab(keterangan: String, catatan: String?) {
                if (sudahDijawab) return
                sudahDijawab = true
                runOnUiThread { overlayKeterangan.visibility = View.GONE }
                if (cont.isActive) cont.resume(keterangan to catatan)
            }

            runOnUiThread {
                try {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
                } catch (e: Exception) { /* abaikan */ }

                overlayKeterangan.visibility = View.VISIBLE
                progressTimeout.max = DURASI_TIMEOUT_KETERANGAN_DETIK
                progressTimeout.progress = DURASI_TIMEOUT_KETERANGAN_DETIK

                val timer = object : CountDownTimer(DURASI_TIMEOUT_KETERANGAN_DETIK * 1000L, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        progressTimeout.progress = (millisUntilFinished / 1000L).toInt()
                    }
                    override fun onFinish() { jawab("Tanpa Keterangan", null) }
                }
                timer.start()

                btnIzin.setOnClickListener { timer.cancel(); jawab("Izin", null) }
                btnSakit.setOnClickListener { timer.cancel(); jawab("Sakit", null) }
                btnLewati.setOnClickListener { timer.cancel(); jawab("Tanpa Keterangan", null) }
                btnLainnya.setOnClickListener {
                    timer.cancel()
                    val editText = EditText(this)
                    editText.hint = "Catatan singkat"
                    AlertDialog.Builder(this)
                        .setTitle("Keterangan Lainnya")
                        .setView(editText)
                        .setPositiveButton("Simpan") { _, _ ->
                            jawab("Lainnya", editText.text.toString().trim().ifBlank { null })
                        }
                        .setNegativeButton("Batal") { _, _ -> timer.start() }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private suspend fun simpanAbsensi(
        idKaryawan: String,
        jenisAbsen: String,
        status: String,
        selisihMenit: Int?,
        confidenceScore: Float,
        waktuTransaksi: Long,
        keterangan: String?,
        catatanTambahan: String?
    ) {
        val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        absensiDao.insert(
            Absensi(
                idAbsensi = UUID.randomUUID().toString(),
                idKaryawan = idKaryawan,
                tanggal = formatTanggal.format(Date(waktuTransaksi)),
                jenisAbsen = jenisAbsen,
                timestamp = waktuTransaksi,
                status = status,
                selisihMenit = selisihMenit,
                keterangan = keterangan,
                catatanTambahan = catatanTambahan,
                confidenceScore = confidenceScore
            )
        )
    }

    private enum class TipeOverlay { SUKSES, GAGAL, NETRAL }

    private fun tampilkanOverlayHasil(tipe: TipeOverlay, nama: String, pesan: String) {
        overlayHasilTampil = true

        try {
            when (tipe) {
                TipeOverlay.SUKSES  -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                TipeOverlay.GAGAL   -> toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 400)
                TipeOverlay.NETRAL  -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memainkan nada notifikasi", e)
        }

        runOnUiThread {
            overlayHasil.visibility = View.VISIBLE
            overlayHasil.setBackgroundColor(
                when (tipe) {
                    TipeOverlay.SUKSES  -> 0xE61B5E20.toInt()
                    TipeOverlay.GAGAL   -> 0xE6B71C1C.toInt()
                    TipeOverlay.NETRAL  -> 0xE637474F.toInt()
                }
            )
            tvIkonHasil.text = when (tipe) {
                TipeOverlay.SUKSES  -> "✓"
                TipeOverlay.GAGAL   -> "✕"
                TipeOverlay.NETRAL  -> "ℹ"
            }
            tvNamaHasil.visibility = if (nama.isBlank()) View.GONE else View.VISIBLE
            tvNamaHasil.text = nama
            tvPesanHasil.text = pesan
        }

        Handler(Looper.getMainLooper()).postDelayed({
            overlayHasilTampil = false
            overlayHasil.visibility = View.GONE
        }, DURASI_TAMPIL_HASIL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator.release()
    }
}