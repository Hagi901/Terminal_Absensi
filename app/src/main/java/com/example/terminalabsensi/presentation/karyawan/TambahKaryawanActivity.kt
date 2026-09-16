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

    @Volatile private var lastEmbedding: FloatArray? = null
    @Volatile private var jumlahWajahTerdeteksi = 0
    @Volatile private var lastPoseDetected: String = "depan" // <-- Tambahkan baris ini
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
            if (!faceDetectorYN.isReady() || !faceEmbedder.isReady()) return

            var colorMat = FaceUtils.imageProxyToColorMat(imageProxy)
            colorMat = FaceUtils.rotateMat(colorMat, imageProxy.imageInfo.rotationDegrees)

            faceDetectorYN.setInputSize(colorMat.width(), colorMat.height())
            val detections = faceDetectorYN.detect(colorMat)

            jumlahWajahTerdeteksi = detections?.rows() ?: 0

            // Di processFrame() TambahKaryawanActivity.kt:
            if (jumlahWajahTerdeteksi == 1) {
                val faceRow = detections!!.row(0)
                val poseDetected = faceDetectorYN.deteksiPoseWajah(faceRow)
                val embedding = faceEmbedder.extractEmbeddingAligned(colorMat, faceRow)
                lastEmbedding = embedding
                lastPoseDetected = poseDetected
            } else {
                lastEmbedding = null
            }

            detections?.release()
            colorMat.release()

            if (mintaAmbilSampel) {
                mintaAmbilSampel = false
                simpanSampelSaatIni(jumlahWajahTerdeteksi)
            }

            runOnUiThread {
                val sudutTarget = sudutSaatIni()
                val poseSesuai = lastPoseDetected == sudutTarget

                when {
                    jumlahWajahTerdeteksi > 1 -> {
                        tvInstruksi.text = "Pastikan hanya 1 wajah dalam frame"
                        tvInstruksi.setTextColor(0xFFFFC107.toInt()) // Kuning peringatan
                        btnAmbilSampel.isEnabled = false
                    }
                    jumlahWajahTerdeteksi == 0 -> {
                        tvInstruksi.text = "Wajah tidak terdeteksi, posisikan ke kamera"
                        tvInstruksi.setTextColor(0xFFFFFFFF.toInt()) // Putih
                        btnAmbilSampel.isEnabled = false
                    }
                    lastEmbedding == null -> {
                        tvInstruksi.text = "Posisikan wajah lebih jelas"
                        tvInstruksi.setTextColor(0xFFFFFFFF.toInt())
                        btnAmbilSampel.isEnabled = false
                    }
                    !poseSesuai -> {
                        tvInstruksi.text = "Silakan menolehkan wajah ke arah: ${sudutTarget.uppercase()}"
                        tvInstruksi.setTextColor(0xFFFFCA28.toInt()) // Oranye instruksi
                        btnAmbilSampel.isEnabled = false
                    }
                    else -> {
                        // KONFIRMASI BERHASIL DETEKSI POSE
                        tvInstruksi.text = "✓ Wajah ${sudutTarget.uppercase()} Terdeteksi! Tekan Ambil Sampel"
                        tvInstruksi.setTextColor(0xFF4CAF50.toInt()) // Hijau sukses
                        btnAmbilSampel.isEnabled = true // Tombol baru bisa diklik jika pose sudah sesuai
                    }
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
        val sudutTarget = sudutSaatIni()
        if (sampelTersimpan.size >= SUDUT_CAPTURE.size) {
            Toast.makeText(this, "Sampel sudah lengkap", Toast.LENGTH_SHORT).show()
            return
        }
        if (lastEmbedding == null) {
            Toast.makeText(this, "Pastikan wajah terdeteksi dengan jelas", Toast.LENGTH_SHORT).show()
            return
        }
        if (lastPoseDetected != sudutTarget) {
            Toast.makeText(this, "Pose wajah belum sesuai. Silakan menoleh ke ${sudutTarget.uppercase()}", Toast.LENGTH_SHORT).show()
            return
        }
        mintaAmbilSampel = true
    }

    private fun simpanSampelSaatIni(jumlahWajah: Int) {
        if (jumlahWajah != 1) {
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