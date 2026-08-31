package com.example.terminalabsensi.facerecognition

import android.content.Context
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream

/**
 * FaceEmbedder membungkus FaceRecognizerSF (model SFace) untuk mengubah
 * wajah yang sudah terdeteksi (Rect dari FaceDetector) menjadi embedding
 * (128 angka float) yang bisa dibandingkan antar wajah.
 *
 * Catatan: versi ini menggunakan crop + resize langsung dari bounding box
 * cascade classifier (tanpa 5-landmark alignment dari YuNet), sebagai
 * pendekatan MVP. Bisa di-upgrade ke alignment penuh nanti jika akurasi
 * dirasa kurang.
 */
class FaceEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "FaceEmbedder"

        // Ukuran input yang diharapkan model SFace
        private const val INPUT_SIZE = 112
    }

    private var recognizer: FaceRecognizerSF? = null
    private var isInitialized = false
    var lastError: String? = null
        private set

    /**
     * Menyalin model .onnx dari res/raw ke internal storage, lalu memuatnya
     * ke FaceRecognizerSF. Sama seperti FaceDetector, harus dipanggil di
     * background thread karena ada operasi I/O.
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
                "",                          // config path, kosongkan untuk ONNX
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
     * Mengekstrak embedding dari satu wajah pada frame.
     *
     * @param colorFrame Mat BERWARNA (BGR/RGB), BUKAN grayscale — model
     *                   SFace butuh input berwarna, beda dari cascade
     *                   classifier yang butuh grayscale.
     * @param faceRect Bounding box wajah hasil deteksi (dari FaceDetector).
     * @return FloatArray embedding (128 dimensi), atau null jika gagal.
     */
    fun extractEmbedding(colorFrame: Mat, faceRect: Rect): FloatArray? {
        val rec = recognizer
        if (rec == null || !isInitialized) {
            Log.w(TAG, "FaceEmbedder belum di-setup, panggil setup() dulu")
            return null
        }

        try {
            // Crop wajah dari frame penuh sesuai bounding box
            val faceCrop = Mat(colorFrame, faceRect)

            // Resize ke ukuran input yang diharapkan model (112x112)
            val resized = Mat()
            Imgproc.resize(faceCrop, resized, org.opencv.core.Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()))

            // Ekstraksi fitur (embedding)
            val featureMat = Mat()
            rec.feature(resized, featureMat)

            // Konversi Mat embedding (1 baris, 128 kolom) ke FloatArray
            val embedding = FloatArray(featureMat.cols())
            featureMat.get(0, 0, embedding)

            faceCrop.release()
            resized.release()
            featureMat.release()

            return embedding
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Error saat ekstraksi embedding", e)
            return null
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
     * Mengekstrak embedding dengan alignment penuh menggunakan landmark
     * dari FaceDetectorYN (5 titik: mata kanan, mata kiri, hidung, sudut
     * mulut kanan, sudut mulut kiri). Ini versi yang lebih akurat dibanding
     * extractEmbedding() biasa, karena wajah diluruskan dulu sebelum diekstrak.
     *
     * @param colorFrame Mat BERWARNA (BGR) frame penuh.
     * @param faceRow satu baris hasil deteksi dari FaceDetectorYNWrapper.detect()
     *                (Mat dengan 1 baris, 15 kolom: x,y,w,h + 5 landmark + score).
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
