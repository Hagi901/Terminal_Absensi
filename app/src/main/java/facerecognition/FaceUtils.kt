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
     * Mat berwarna (BGR), dibutuhkan oleh FaceEmbedder/FaceDetectorYN
     * (butuh input berwarna, beda dari FaceDetector Haar yang cukup grayscale).
     *
     * PENTING: Plane U dan V pada YUV_420_888 seringkali punya pixelStride
     * > 1 (data tidak rapat berurutan di memori). Kode ini membaca piksel
     * satu per satu sesuai rowStride & pixelStride masing-masing plane,
     * lalu menyusun ulang menjadi format NV21 (Y penuh + VU berselang-seling)
     * yang benar sebelum dikonversi ke BGR.
     */
    fun imageProxyToColorMat(imageProxy: ImageProxy): Mat {
        require(imageProxy.format == ImageFormat.YUV_420_888) {
            "Format harus YUV_420_888"
        }

        val width = imageProxy.width
        val height = imageProxy.height

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            for (col in 0 until width) {
                nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[pos++] = vBuffer.get(vIndex)
                nv21[pos++] = uBuffer.get(uIndex)
            }
        }

        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val colorMat = Mat()
        Imgproc.cvtColor(yuvMat, colorMat, Imgproc.COLOR_YUV2BGR_NV21)
        yuvMat.release()

        return colorMat
    }
}