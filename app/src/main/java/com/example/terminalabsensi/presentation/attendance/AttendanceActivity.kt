package com.example.terminalabsensi.presentation.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.example.terminalabsensi.facerecognition.FaceDetector
import com.example.terminalabsensi.facerecognition.FaceEmbedder
import com.example.terminalabsensi.facerecognition.FaceUtils
import org.koin.android.ext.android.inject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AttendanceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AttendanceActivity"
    }

    private val faceDetector: FaceDetector by inject()
    private val faceEmbedder: FaceEmbedder by inject()

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private lateinit var tvStatus: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

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

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup cascade classifier & face embedder di background thread supaya
        // tidak memblokir UI thread (ada operasi baca/tulis file).
        cameraExecutor.execute {
            val successDetector = faceDetector.setup(R.raw.haarcascade_frontalface_alt2)
            val successEmbedder = faceEmbedder.setup(R.raw.face_recognition_sface)
            if (!successDetector) Log.e(TAG, "Setup FaceDetector gagal")
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

        // Kamera depan, sesuai kebutuhan absensi wajah
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

    /**
     * Dipanggil di background thread (cameraExecutor) untuk setiap frame
     * yang masuk dari kamera. WAJIB memanggil imageProxy.close() di akhir,
     * kalau tidak CameraX akan berhenti mengirim frame baru.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (!faceDetector.isReady()) {
                return
            }

            var grayMat = FaceUtils.imageProxyToGrayMat(imageProxy)
            grayMat = FaceUtils.rotateMat(grayMat, imageProxy.imageInfo.rotationDegrees)

            val faces = faceDetector.detectFaces(grayMat)

            // Teks default, akan ditimpa kalau ada embedding yang berhasil/gagal diekstrak
            var debugText = if (faces.isEmpty()) "Arahkan wajah ke kamera" else "Wajah terdeteksi: ${faces.size}"

            // Uji coba ekstraksi embedding kalau ada wajah terdeteksi & embedder siap
            if (faces.isNotEmpty() && faceEmbedder.isReady()) {
                var colorMat = FaceUtils.imageProxyToColorMat(imageProxy)
                colorMat = FaceUtils.rotateMat(colorMat, imageProxy.imageInfo.rotationDegrees)

                val embedding = faceEmbedder.extractEmbedding(colorMat, faces[0])
                if (embedding != null) {
                    val selfScore = faceEmbedder.compare(embedding, embedding)
                    Log.d(TAG, "Embedding ukuran: ${embedding.size}, self-score: $selfScore")
                    debugText = "Embedding: ${embedding.size} | Score: $selfScore"
                } else {
                    Log.w(TAG, "Ekstraksi embedding gagal")
                    debugText = "Ekstraksi embedding GAGAL"
                }
                colorMat.release()
            } else if (faces.isNotEmpty() && !faceEmbedder.isReady()) {
                debugText = "FaceEmbedder belum siap"
            }

            runOnUiThread {
                faceOverlayView.setSourceSize(grayMat.width(), grayMat.height())
                faceOverlayView.updateFaces(faces)
                tvStatus.text = debugText
            }

            grayMat.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memproses frame", e)
        } finally {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}