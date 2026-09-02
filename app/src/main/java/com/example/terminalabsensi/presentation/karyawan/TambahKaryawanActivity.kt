package com.example.terminalabsensi.presentation.karyawan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
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
import com.example.terminalabsensi.data.local.dao.SampelWajahDao
import com.example.terminalabsensi.data.local.entity.Karyawan
import com.example.terminalabsensi.data.local.entity.SampelWajah
import com.example.terminalabsensi.domain.usecase.ValidasiEnrollmentUseCase
import com.example.terminalabsensi.facerecognition.FaceDetector
import com.example.terminalabsensi.facerecognition.FaceDetectorYNWrapper
import com.example.terminalabsensi.facerecognition.FaceEmbedder
import com.example.terminalabsensi.facerecognition.FaceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TambahKaryawanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TambahKaryawanActivity"
        private val SUDUT_CAPTURE = listOf("depan", "kiri", "kanan")
    }

    private val faceDetector: FaceDetector by inject()
    private val faceDetectorYN: FaceDetectorYNWrapper by inject()
    private val faceEmbedder: FaceEmbedder by inject()
    private val karyawanDao: KaryawanDao by inject()
    private val sampelWajahDao: SampelWajahDao by inject()
    private val validasiEnrollmentUseCase: ValidasiEnrollmentUseCase by inject()

    private lateinit var previewView: PreviewView
    private lateinit var etIdKaryawan: EditText
    private lateinit var etNama: EditText
    private lateinit var etJabatan: EditText
    private lateinit var tvInstruksi: TextView
    private lateinit var tvProgress: TextView
    private lateinit var btnAmbilSampel: Button
    private lateinit var btnSimpan: Button

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    // State enrollment
    @Volatile private var lastEmbedding: FloatArray? = null
    @Volatile private var lastFaceDetected = false
    private val sampelTersimpan = mutableListOf<FloatArray>()
    @Volatile private var mintaAmbilSampel = false

    private val requestCameraPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Izin kamera dibutuhkan", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tambah_karyawan)

        previewView = findViewById(R.id.previewView)
        etIdKaryawan = findViewById(R.id.etIdKaryawan)
        etNama = findViewById(R.id.etNama)
        etJabatan = findViewById(R.id.etJabatan)
        tvInstruksi = findViewById(R.id.tvInstruksi)
        tvProgress = findViewById(R.id.tvProgress)
        btnAmbilSampel = findViewById(R.id.btnAmbilSampel)
        btnSimpan = findViewById(R.id.btnSimpan)

        btnAmbilSampel.setOnClickListener { onKlikAmbilSampel() }
        btnSimpan.setOnClickListener { onKlikSimpan() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            faceDetector.setup(R.raw.haarcascade_frontalface_alt2)
            faceDetectorYN.setup(R.raw.face_detection_yunet)
            faceEmbedder.setup(R.raw.face_recognition_sface)
        }

        if (hasCameraPermission()) startCamera() else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
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

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, ::processFrame) }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal bind kamera", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!faceDetector.isReady() || !faceDetectorYN.isReady() || !faceEmbedder.isReady()) {
                return
            }

            var grayMat = FaceUtils.imageProxyToGrayMat(imageProxy)
            grayMat = FaceUtils.rotateMat(grayMat, imageProxy.imageInfo.rotationDegrees)
            val faces = faceDetector.detectFaces(grayMat)
            grayMat.release()

            lastFaceDetected = faces.size == 1

            if (faces.size == 1) {
                var colorMat = FaceUtils.imageProxyToColorMat(imageProxy)
                colorMat = FaceUtils.rotateMat(colorMat, imageProxy.imageInfo.rotationDegrees)

                faceDetectorYN.setInputSize(colorMat.width(), colorMat.height())
                val detections = faceDetectorYN.detect(colorMat)

                if (detections != null && detections.rows() > 0) {
                    val embedding = faceEmbedder.extractEmbeddingAligned(colorMat, detections.row(0))
                    lastEmbedding = embedding
                    detections.release()
                } else {
                    lastEmbedding = null
                }
                colorMat.release()
            } else {
                lastEmbedding = null
            }

            if (mintaAmbilSampel) {
                mintaAmbilSampel = false
                simpanSampelSaatIni(faces.size)
            }

            runOnUiThread {
                tvInstruksi.text = when {
                    faces.size > 1 -> "Pastikan hanya 1 wajah dalam frame"
                    faces.isEmpty() -> "Wajah tidak terdeteksi"
                    lastEmbedding == null -> "Posisikan wajah lebih jelas"
                    else -> "Wajah OK — sudut: ${sudutSaatIni()}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memproses frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun sudutSaatIni(): String {
        return SUDUT_CAPTURE.getOrElse(sampelTersimpan.size) { "selesai" }
    }

    private fun onKlikAmbilSampel() {
        if (sampelTersimpan.size >= SUDUT_CAPTURE.size) {
            Toast.makeText(this, "Sampel sudah lengkap", Toast.LENGTH_SHORT).show()
            return
        }
        if (!lastFaceDetected) {
            Toast.makeText(this, "Pastikan wajah terdeteksi dengan jelas", Toast.LENGTH_SHORT).show()
            return
        }
        mintaAmbilSampel = true
    }

    private fun simpanSampelSaatIni(jumlahWajahTerdeteksi: Int) {
        if (jumlahWajahTerdeteksi != 1) {
            runOnUiThread {
                Toast.makeText(this, "Pastikan hanya satu wajah dalam frame", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val embedding = lastEmbedding
        if (embedding == null) {
            runOnUiThread {
                Toast.makeText(this, "Gagal mengekstrak wajah, coba lagi", Toast.LENGTH_SHORT).show()
            }
            return
        }

        sampelTersimpan.add(embedding.copyOf())

        runOnUiThread {
            tvProgress.text = "Sampel: ${sampelTersimpan.size}/${SUDUT_CAPTURE.size}"
            Toast.makeText(
                this,
                "Sampel sudut ${SUDUT_CAPTURE[sampelTersimpan.size - 1]} tersimpan",
                Toast.LENGTH_SHORT
            ).show()

            if (sampelTersimpan.size >= SUDUT_CAPTURE.size) {
                btnAmbilSampel.isEnabled = false
                btnSimpan.isEnabled = true
                tvInstruksi.text = "Sampel lengkap. Tekan Simpan Karyawan"
            }
        }
    }

    private fun onKlikSimpan() {
        val id = etIdKaryawan.text.toString().trim()
        val nama = etNama.text.toString().trim()
        val jabatan = etJabatan.text.toString().trim().ifBlank { null }

        CoroutineScope(Dispatchers.IO).launch {
            val hasilValidasi = validasiEnrollmentUseCase.validasiDataKaryawan(id, nama)

            val pesanError = when (hasilValidasi) {
                ValidasiEnrollmentUseCase.HasilValidasiData.IdKosong -> "ID Karyawan tidak boleh kosong"
                ValidasiEnrollmentUseCase.HasilValidasiData.NamaKosong -> "Nama tidak boleh kosong"
                ValidasiEnrollmentUseCase.HasilValidasiData.IdSudahDipakai -> "ID Karyawan sudah dipakai"
                ValidasiEnrollmentUseCase.HasilValidasiData.Valid -> null
            }

            if (pesanError != null) {
                runOnUiThread { Toast.makeText(this@TambahKaryawanActivity, pesanError, Toast.LENGTH_LONG).show() }
                return@launch
            }

            if (!validasiEnrollmentUseCase.validasiJumlahSampel(sampelTersimpan.size)) {
                runOnUiThread {
                    Toast.makeText(this@TambahKaryawanActivity, "Sampel wajah belum cukup", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            try {
                karyawanDao.insert(Karyawan(idKaryawan = id, nama = nama, jabatan = jabatan))

                sampelTersimpan.forEachIndexed { index, embedding ->
                    sampelWajahDao.insert(
                        SampelWajah(
                            idSampel = UUID.randomUUID().toString(),
                            idKaryawan = id,
                            fiturWajah = embedding,
                            sudutCapture = SUDUT_CAPTURE[index]
                        )
                    )
                }

                runOnUiThread {
                    Toast.makeText(this@TambahKaryawanActivity, "Karyawan berhasil ditambahkan", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal simpan karyawan", e)
                runOnUiThread {
                    Toast.makeText(this@TambahKaryawanActivity, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}