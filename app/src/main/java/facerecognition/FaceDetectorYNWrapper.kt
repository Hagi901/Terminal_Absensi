package com.example.terminalabsensi.facerecognition

import android.content.Context
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.objdetect.FaceDetectorYN
import java.io.File
import java.io.FileOutputStream

/**
 * FaceDetectorYNWrapper membungkus FaceDetectorYN (model YuNet) untuk
 * mendeteksi wajah SEKALIGUS 5 titik landmark (mata kiri, mata kanan,
 * hidung, sudut mulut kiri, sudut mulut kanan).
 *
 * Landmark ini dibutuhkan oleh FaceRecognizerSF.alignCrop() agar wajah
 * diluruskan dengan benar sebelum ekstraksi embedding -- inilah yang
 * membedakan hasil sebelumnya (score tidak bisa membedakan orang) dengan
 * pendekatan ini (align dulu, baru ekstrak).
 */
class FaceDetectorYNWrapper(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorYNWrapper"
    }

    private var detector: FaceDetectorYN? = null
    private var isInitialized = false
    var lastError: String? = null
        private set

    /**
     * Menyalin model .onnx dari res/raw ke internal storage, lalu memuatnya.
     * Harus dipanggil di background thread (ada operasi I/O).
     *
     * @param inputSize ukuran frame yang akan dianalisis (harus di-set ulang
     *                  setiap kali ukuran frame kamera berubah, lewat setInputSize()).
     */
    fun setup(modelRawResId: Int, inputWidth: Int = 320, inputHeight: Int = 320): Boolean {
        if (isInitialized) return true

        try {
            val modelDir = context.getDir("models", Context.MODE_PRIVATE)
            val modelFile = File(modelDir, "face_detector_yn.onnx")

            context.resources.openRawResource(modelRawResId).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }

            detector = FaceDetectorYN.create(
                modelFile.absolutePath,
                "",
                Size(inputWidth.toDouble(), inputHeight.toDouble()),
                0.1f,   // score threshold: minimal confidence agar dianggap wajah
                0.3f,   // nms threshold: untuk menyaring box yang tumpang tindih
                5000    // top_k: jumlah kandidat maksimum sebelum NMS
            )

            isInitialized = true
            Log.i(TAG, "FaceDetectorYN berhasil dimuat dari ${modelFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saat setup FaceDetectorYN", e)
            return false
        }
    }

    /**
     * WAJIB dipanggil setiap kali ukuran frame yang dianalisis berbeda dari
     * setup() sebelumnya -- YuNet butuh tahu ukuran input pasti.
     */
    fun setInputSize(width: Int, height: Int) {
        detector?.setInputSize(Size(width.toDouble(), height.toDouble()))
    }

    /**
     * Mendeteksi wajah beserta landmark pada frame BERWARNA.
     * Mengembalikan Mat hasil deteksi mentah (format FaceDetectorYN OpenCV):
     * setiap baris = 1 wajah, dengan 15 kolom:
     * [x, y, w, h, mata_kanan_x, mata_kanan_y, mata_kiri_x, mata_kiri_y,
     *  hidung_x, hidung_y, mulut_kanan_x, mulut_kanan_y, mulut_kiri_x, mulut_kiri_y, score]
     *
     * @param colorFrame Mat berwarna (BGR), UKURANNYA HARUS SAMA dengan yang
     *                   di-set lewat setInputSize().
     * @return Mat hasil deteksi, atau null jika tidak ada wajah / gagal.
     */
    fun detect(colorFrame: Mat): Mat? {
        val det = detector
        if (det == null || !isInitialized) {
            Log.w(TAG, "FaceDetectorYN belum di-setup, panggil setup() dulu")
            return null
        }

        return try {
            val faces = Mat()
            det.detect(colorFrame, faces)
            if (faces.rows() == 0) {
                lastError = null
                null
            } else {
                faces
            }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Error saat deteksi wajah YuNet", e)
            null
        }
    }

    fun isReady(): Boolean = isInitialized
}