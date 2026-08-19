package com.example.terminalabsensi.facerecognition

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object FaceUtils {

    /**
     * Mengambil Y-plane (luminance) dari ImageProxy format YUV_420_888
     * dan mengubahnya jadi Mat grayscale OpenCV (CV_8UC1).
     *
     * Y-plane pada YUV sudah merepresentasikan citra grayscale,
     * sehingga tidak perlu konversi warna penuh (YUV->RGB->Gray)
     * yang lebih berat secara komputasi -- penting untuk performa
     * real-time di tablet kelas menengah.
     *
     * Catatan: rowStride pada Y-plane bisa lebih besar dari width
     * (ada padding), sehingga perlu di-crop per baris.
     */
    fun imageProxyToGrayMat(imageProxy: ImageProxy): Mat {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        val rawMat = Mat(height, rowStride, CvType.CV_8UC1)
        val data = ByteArray(yBuffer.remaining())
        yBuffer.get(data)
        rawMat.put(0, 0, data)

        return if (rowStride == width) {
            rawMat
        } else {
            val cropped = Mat(rawMat, org.opencv.core.Rect(0, 0, width, height))
            val result = cropped.clone()
            rawMat.release()
            cropped.release()
            result
        }
    }

    /**
     * Memutar Mat sesuai rotationDegrees dari ImageInfo CameraX.
     * Frame kamera CameraX seringkali perlu diputar (90/270 derajat)
     * tergantung orientasi sensor kamera vs orientasi layar.
     */
    fun rotateMat(mat: Mat, rotationDegrees: Int): Mat {
        return when (rotationDegrees) {
            90 -> {
                val rotated = Mat()
                Core.rotate(mat, rotated, Core.ROTATE_90_CLOCKWISE)
                mat.release()
                rotated
            }
            180 -> {
                val rotated = Mat()
                Core.rotate(mat, rotated, Core.ROTATE_180)
                mat.release()
                rotated
            }
            270 -> {
                val rotated = Mat()
                Core.rotate(mat, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
                mat.release()
                rotated
            }
            else -> mat
        }
    }

    /**
     * Mengambil seluruh plane YUV dari ImageProxy dan mengonversinya jadi
     * Mat berwarna (BGR), dibutuhkan oleh FaceEmbedder (model SFace butuh
     * input berwarna, beda dari FaceDetector yang cukup grayscale).
     */
    fun imageProxyToColorMat(imageProxy: ImageProxy): Mat {
        require(imageProxy.format == ImageFormat.YUV_420_888) {
            "Format harus YUV_420_888"
        }

        val yPlane = imageProxy.planes[0].buffer
        val uPlane = imageProxy.planes[1].buffer
        val vPlane = imageProxy.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.get(nv21, 0, ySize)
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)

        val yuvMat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val colorMat = Mat()
        Imgproc.cvtColor(yuvMat, colorMat, Imgproc.COLOR_YUV2BGR_NV21)
        yuvMat.release()

        return colorMat
    }
}