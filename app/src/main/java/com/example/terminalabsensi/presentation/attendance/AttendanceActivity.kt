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

        // Untuk uji coba Fase 3, sebelum fitur enrollment (Fase 5) dibangun.
        // Nanti diganti dengan hasil pencarian dari seluruh data SampelWajah tersimpan.
        private const val DUMMY_ID_KARYAWAN = "DUMMY001"
        private const val THRESHOLD_SEMENTARA = 0.5f
        private const val COOLDOWN_MS = 5000L // jeda antar percobaan absen, agar tidak spam
    }

    private val faceDetector: FaceDetector by inject()
    private val faceDetectorYN: FaceDetectorYNWrapper by inject()
    private val faceEmbedder: FaceEmbedder by inject()
    private val absensiDao: AbsensiDao by inject()
    private val tentukanJenisAbsensiUseCase: TentukanJenisAbsensiUseCase by inject()
    private val tentukanStatusUseCase: TentukanStatusUseCase by inject()
    private val validasiAntiDuplikasiUseCase: ValidasiAntiDuplikasiUseCase by inject()

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private lateinit var tvStatus: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile private var lastEmbedding: FloatArray? = null
    @Volatile private var referenceEmbedding: FloatArray? = null
    @Volatile private var sedangMemprosesAbsensi = false
    @Volatile private var waktuPercobaanTerakhir = 0L

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

        // Tap layar untuk simpan wajah saat ini sebagai "wajah DUMMY001" (uji coba)
        findViewById<View>(R.id.rootLayout).setOnClickListener {
            val current = lastEmbedding
            if (current != null) {
                referenceEmbedding = current.copyOf()
                Toast.makeText(this, "Wajah disimpan sebagai DUMMY001", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Belum ada wajah terdeteksi untuk disimpan", Toast.LENGTH_SHORT).show()
            }
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
                        lastEmbedding = embedding
                        val ref = referenceEmbedding

                        if (ref != null) {
                            val score = faceEmbedder.compare(embedding, ref)
                            debugText = "Score: %.4f".format(score)

                            if (score >= THRESHOLD_SEMENTARA) {
                                val sekarang = System.currentTimeMillis()
                                if (sekarang - waktuPercobaanTerakhir > COOLDOWN_MS) {
                                    waktuPercobaanTerakhir = sekarang
                                    prosesAbsensi(score)
                                }
                            }
                        } else {
                            debugText = "Wajah OK. Tap layar untuk simpan sebagai DUMMY001"
                        }
                    } else {
                        debugText = "GAGAL align/embed: ${faceEmbedder.lastError}"
                    }
                    detections.release()
                } else {
                    debugText = "YuNet: wajah tidak terdeteksi"
                }

                colorMat.release()
            }

            val finalText = debugText
            runOnUiThread {
                faceOverlayView.setSourceSize(grayMat.width(), grayMat.height())
                faceOverlayView.updateFaces(facesForOverlay)
                if (!sedangMemprosesAbsensi) {
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
     * Menjalankan alur bisnis lengkap: cek anti-duplikasi, tentukan jenis
     * (masuk/pulang), tentukan status, simpan ke database. Sesuai FR-3.2.4
     * dan BR-07 di SRS.
     */
    private fun prosesAbsensi(confidenceScore: Float) {
        sedangMemprosesAbsensi = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sekarang = System.currentTimeMillis()

                val bolehLanjut = validasiAntiDuplikasiUseCase(DUMMY_ID_KARYAWAN, sekarang)
                if (!bolehLanjut) {
                    tampilkanHasil("Absen terlalu cepat, coba lagi sebentar")
                    return@launch
                }

                val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val tanggalHariIni = formatTanggal.format(Date(sekarang))

                val jenisHasil = tentukanJenisAbsensiUseCase(DUMMY_ID_KARYAWAN, tanggalHariIni)

                when (jenisHasil) {
                    is TentukanJenisAbsensiUseCase.Hasil.SudahLengkap -> {
                        tampilkanHasil("Anda sudah menyelesaikan absensi hari ini")
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenMasuk -> {
                        val statusHasil = tentukanStatusUseCase("masuk", sekarang)
                        simpanAbsensi("masuk", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang)
                        tampilkanHasil("Absen Masuk: ${statusHasil.status}")
                    }
                    is TentukanJenisAbsensiUseCase.Hasil.AbsenPulang -> {
                        val statusHasil = tentukanStatusUseCase("pulang", sekarang)
                        simpanAbsensi("pulang", statusHasil.status, statusHasil.selisihMenit, confidenceScore, sekarang)
                        tampilkanHasil("Absen Pulang: ${statusHasil.status}")
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
                idKaryawan = DUMMY_ID_KARYAWAN,
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
        runOnUiThread {
            tvStatus.text = pesan
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}