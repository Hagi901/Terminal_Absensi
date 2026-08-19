package com.example.terminalabsensi.facerecognition

import android.content.Context
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream

/**
 * FaceDetector membungkus CascadeClassifier OpenCV (Haar/LBP Cascade)
 * untuk mendeteksi lokasi wajah pada sebuah frame kamera.
 *
 * Cascade file (haarcascade_frontalface_alt2.xml) harus ditaruh di
 * res/raw/ terlebih dahulu. File aslinya bisa diambil dari:
 * OpenCV-android-sdk/sdk/etc/haarcascades/haarcascade_frontalface_alt2.xml
 */
class FaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetector"

        // Ukuran minimum wajah yang dideteksi, relatif terhadap ukuran frame.
        // Semakin besar nilainya, semakin cepat deteksi tapi wajah kecil/jauh
        // bisa terlewat.
        private const val MIN_FACE_SIZE_RATIO = 0.15
    }

    private var cascadeClassifier: CascadeClassifier? = null
    private var isInitialized = false

    /**
     * Menyalin cascade file dari res/raw ke internal storage (folder cascade/)
     * lalu memuatnya ke CascadeClassifier. OpenCV CascadeClassifier hanya bisa
     * membaca dari path file di disk, bukan langsung dari resource id.
     *
     * Panggil sekali saja, idealnya di background thread (bukan main thread)
     * karena ada operasi I/O.
     */
    fun setup(cascadeRawResId: Int): Boolean {
        if (isInitialized) return true

        try {
            val cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE)
            val cascadeFile = File(cascadeDir, "face_cascade.xml")

            context.resources.openRawResource(cascadeRawResId).use { input ->
                FileOutputStream(cascadeFile).use { output ->
                    input.copyTo(output)
                }
            }

            val classifier = CascadeClassifier(cascadeFile.absolutePath)
            if (classifier.empty()) {
                Log.e(TAG, "Gagal memuat cascade classifier, file kosong/rusak")
                return false
            }

            cascadeClassifier = classifier
            isInitialized = true
            Log.i(TAG, "Cascade classifier berhasil dimuat dari ${cascadeFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saat setup FaceDetector", e)
            return false
        }
    }

    /**
     * Mendeteksi wajah pada sebuah frame grayscale (Mat).
     * Mengembalikan list Rect (koordinat wajah) dalam koordinat frame asli.
     *
     * @param grayFrame Mat dalam format grayscale (CV_8UC1). Konversi dari
     *                  frame CameraX (YUV/RGBA) harus dilakukan sebelum
     *                  memanggil fungsi ini.
     */
    fun detectFaces(grayFrame: Mat): List<Rect> {
        val classifier = cascadeClassifier
        if (classifier == null || !isInitialized) {
            Log.w(TAG, "FaceDetector belum di-setup, panggil setup() dulu")
            return emptyList()
        }

        val faces = MatOfRect()
        val minFaceSize = (grayFrame.height() * MIN_FACE_SIZE_RATIO).toInt()

        classifier.detectMultiScale(
            grayFrame,
            faces,
            1.1,            // scaleFactor
            4,              // minNeighbors
            0,              // flags (deprecated, biarkan 0)
            Size(minFaceSize.toDouble(), minFaceSize.toDouble()), // minSize
            Size()          // maxSize (kosong = tanpa batas atas)
        )

        return faces.toArray().toList()
    }

    fun isReady(): Boolean = isInitialized
}