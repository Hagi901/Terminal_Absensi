package com.example.terminalabsensi.presentation.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.example.terminalabsensi.facerecognition.FaceDetector
import com.example.terminalabsensi.facerecognition.FaceDetectorYNWrapper
import com.example.terminalabsensi.facerecognition.FaceEmbedder
import com.example.terminalabsensi.facerecognition.FaceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AttendanceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AttendanceActivity"
        private const val THRESHOLD_SEMENTARA = 0.5f
        private const val COOLDOWN_MS = 5000L
        private const val DURASI_TAMPIL_HASIL_MS = 3000L
    }

    private val faceDetector: FaceDetector by inject()
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

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile private var sedangMemprosesAbsensi = false
    @Volatile private var waktuPercobaanTerakhir = 0L
    @Volatile private var pesanHasilAbsensi: String? = null
    @Volatile private var waktuPesanDitampilkan = 0L

    private val requestCameraPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera dibutuhkan untuk fitur absensi wajah",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        previewView = findViewById(R.id.previewView)
        faceOverlayView = findViewById(R.id.faceOverlayView)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<View>(R.id.rootLayout).setOnLongClickListener {
            startActivity(
                android.content.Intent(this, com.example.terminalabsensi.presentation.admin.AdminLoginActivity::class.java)
            )
            true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            val successDetector = faceDetector.setup(R.raw.haarcascade_frontalface_alt2)
            val successYN = faceDetectorYN.setup(R.raw.face_detection_yunet)
            val successEmbedder = faceEmbedder.setup(R.raw.face_recognition_sface)
            if (!successDetector) Log.e(TAG, "Setup FaceDetector gagal")
            if (!successYN) Log.e(TAG, "Setup FaceDetectorYN gagal")
            if (!successEmbedder) Log.e(TAG, "Setup FaceEmbedder gagal")
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
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
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, ::processFrame)
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal bind kamera", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!faceDetector.isReady()) {
                return
            }

            var grayMat = FaceUtils.imageProxyToGrayMat(imageProxy)
            grayMat = FaceUtils.rotateMat(grayMat, imageProxy.imageInfo.rotationDegrees)
            val facesForOverlay = faceDetector.detectFaces(grayMat)

            var debugText = if (facesForOverlay.isEmpty()) "Arahkan wajah ke kamera" else "Wajah terdeteksi: ${facesForOverlay.size}"

            if (facesForOverlay.isNotEmpty() && faceDetectorYN.isReady() && faceEmbedder.isReady() && !sedangMemprosesAbsensi) {
                var colorMat = FaceUtils.imageProxyToColorMat(imageProxy)
                colorMat = FaceUtils.rotateMat(colorMat, imageProxy.imageInfo.rotationDegrees)

                faceDetectorYN.setInputSize(colorMat.width(), colorMat.height())
                val detections = faceDetectorYN.detect(colorMat)

                if (detections != null && detections.rows() > 0) {
                    val faceRow = detections.row(0)
                    val embedding = faceEmbedder.extractEmbeddingAligned(colorMat, faceRow)

                    if (embedding != null) {
                        debugText = "Mencocokkan wajah..."
                        val sekarang = System.currentTimeMillis()
                        if (sekarang - waktuPercobaanTerakhir > COOLDOWN_MS) {
                            waktuPercobaanTerakhir = sekarang
                            cariDanProsesAbsensi(embedding)
                        }
                    } else {
                        debugText = "GAGAL align/embed: ${faceEmbedder.lastError}"
                    }
                    detections.release()
                } else {
                    debugText = "Wajah tidak terdeteksi dengan jelas"
                }

                colorMat.release()
            }

            val sedangTampilkanHasil = pesanHasilAbsensi != null &&
                    (System.currentTimeMillis() - waktuPesanDitampilkan) < DURASI_TAMPIL_HASIL_MS

            if (!sedangTampilkanHasil) {
                pesanHasilAbsensi = null
            }

            val finalText = debugText
            runOnUiThread {
                faceOverlayView.setSourceSize(grayMat.width(), grayMat.height())
                faceOverlayView.updateFaces(facesForOverlay)
                if (!sedangMemprosesAbsensi && !sedangTampilkanHasil) {
                    tvStatus.text = finalText
                }
            }

            grayMat.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memproses frame", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Mencari karyawan yang cocok dengan wajah, lalu jalankan alur bisnis
     * lengkap: cek anti-duplikasi, tentukan jenis (masuk/pulang), tentukan
     * status, simpan ke database. Sesuai FR-3.2.3, FR-3.2.4, BR-07 di SRS.
     */
    private fun cariDanProsesAbsensi(embeddingWajah: FloatArray) {
        sedangMemprosesAbsensi = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasilPencarian = cariKaryawanDenganWajahUseCase(embeddingWajah, THRESHOLD_SEMENTARA)

                if (hasilPencarian == null) {
                    tampilkanHasil("Wajah tidak dikenali, silakan coba lagi")
                    return@launch
                }

                val idKaryawan = hasilPencarian.karyawan.idKaryawan
                val namaKaryawan = hasilPencarian.karyawan.nama
                val confidenceScore = hasilPencarian.confidenceScore
                val sekarang = System.currentTimeMillis()

                val bolehLanjut = validasiAntiDuplikasiUseCase(idKaryawan, sekarang)
                if (!bolehLanjut) {
                    tampilkanHasil("$namaKaryawan, absen terlalu cepat")
                    return@launch
                }

                val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val tanggalHariIni = formatTanggal.format(Date(sekarang))

                val jenisHasil = tentukanJenisAbsensiUseCase(idKaryawan, tanggalHariIni)

                when (jenisHasil) {
                    is TentukanJenisAbsensiUseCase.Hasil.SudahLengkap -> {
                        tampilkanHasil("$namaKaryawan sudah menyelesaikan absensi hari ini")
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenMasuk -> {
                        val statusHasil = tentukanStatusUseCase("masuk", sekarang)
                        simpanAbsensi(idKaryawan, "masuk", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang)
                        tampilkanHasil("$namaKaryawan — Absen Masuk: ${statusHasil.status}")
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenPulang -> {
                        val statusHasil = tentukanStatusUseCase("pulang", sekarang)
                        simpanAbsensi(idKaryawan, "pulang", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang)
                        tampilkanHasil("$namaKaryawan — Absen Pulang: ${statusHasil.status}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saat memproses absensi", e)
                tampilkanHasil("Error: ${e.message}")
            } finally {
                sedangMemprosesAbsensi = false
            }
        }
    }

    private suspend fun simpanAbsensi(
        idKaryawan: String,
        jenisAbsen: String,
        status: String,
        selisihMenit: Int?,
        confidenceScore: Float,
        waktuTransaksi: Long
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
                confidenceScore = confidenceScore
            )
        )
    }

    private fun tampilkanHasil(pesan: String) {
        pesanHasilAbsensi = pesan
        waktuPesanDitampilkan = System.currentTimeMillis()
        runOnUiThread {
            tvStatus.text = pesan
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}