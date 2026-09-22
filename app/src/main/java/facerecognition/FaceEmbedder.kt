package com.example.terminalabsensi.facerecognition

import android.content.Context
import android.util.Log
import org.opencv.core.Mat
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream

/**
 * FaceEmbedder membungkus FaceRecognizerSF (model SFace) untuk mengubah
 * wajah yang sudah terdeteksi dan di-align (alignCrop) menggunakan landmark dari YuNet
 * menjadi feature vector / embedding (128 angka float) untuk pencocokan wajah.
 */
class FaceEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "FaceEmbedder"
    }

    private var recognizer: FaceRecognizerSF? = null
    private var isInitialized = false
    var lastError: String? = null
        private set

    /**
     * Menyalin model .onnx dari res/raw ke internal storage, lalu memuatnya
     * ke FaceRecognizerSF. Harus dipanggil di background thread karena
     * ada operasi I/O.
     */
    fun setup(modelRawResId: Int): Boolean {
        if (isInitialized) return true

        try {
            val modelDir = context.getDir("models", Context.MODE_PRIVATE)
            val modelFile = File(modelDir, "face_embedder.onnx")

            context.resources.openRawResource(modelRawResId).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }

            recognizer = FaceRecognizerSF.create(
                modelFile.absolutePath,
                "",                                     // config path, kosongkan untuk ONNX
                org.opencv.dnn.Dnn.DNN_BACKEND_OPENCV,  // backend eksplisit: OpenCV (bukan Halide)
                org.opencv.dnn.Dnn.DNN_TARGET_CPU       // target eksplisit: CPU
            )

            isInitialized = true
            Log.i(TAG, "FaceEmbedder berhasil dimuat dari ${modelFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saat setup FaceEmbedder", e)
            return false
        }
    }

    /**
     * Membandingkan dua embedding, mengembalikan cosine similarity score.
     * Skor mendekati 1.0 = sangat mirip (kemungkinan orang yang sama).
     * Skor mendekati 0.0 atau negatif = berbeda.
     */
    fun compare(embedding1: FloatArray, embedding2: FloatArray): Float {
        val rec = recognizer ?: return 0f

        val mat1 = Mat(1, embedding1.size, org.opencv.core.CvType.CV_32F)
        mat1.put(0, 0, embedding1)
        val mat2 = Mat(1, embedding2.size, org.opencv.core.CvType.CV_32F)
        mat2.put(0, 0, embedding2)

        val score = rec.match(mat1, mat2, FaceRecognizerSF.FR_COSINE)

        mat1.release()
        mat2.release()

        return score.toFloat()
    }

    /**
     * Mengekstrak embedding dari satu wajah. Wajah diluruskan lebih dulu
     * (alignCrop) memakai 5 landmark dari FaceDetectorYN: mata kanan, mata kiri,
     * hidung, sudut mulut kanan, dan sudut mulut kiri.
     *
     * @param colorFrame Mat BERWARNA (BGR) frame penuh.
     * @param faceRow satu baris hasil deteksi dari FaceDetectorYNWrapper.detect()
     *                (Mat dengan 1 baris, 15 kolom: x,y,w,h + 5 landmark + score).
     * @return FloatArray embedding (128 dimensi), atau null jika gagal.
     */
    fun extractEmbeddingAligned(colorFrame: Mat, faceRow: Mat): FloatArray? {
        val rec = recognizer
        if (rec == null || !isInitialized) {
            Log.w(TAG, "FaceEmbedder belum di-setup, panggil setup() dulu")
            return null
        }

        try {
            val alignedFace = Mat()
            rec.alignCrop(colorFrame, faceRow, alignedFace)

            val featureMat = Mat()
            rec.feature(alignedFace, featureMat)

            val embedding = FloatArray(featureMat.cols())
            featureMat.get(0, 0, embedding)

            alignedFace.release()
            featureMat.release()

            return embedding
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Error saat ekstraksi embedding (aligned)", e)
            return null
        }
    }

    fun isReady(): Boolean = isInitialized
}