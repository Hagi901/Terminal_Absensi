package com.example.terminalabsensi.presentation.attendance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Rect as CvRect

/**
 * View transparan untuk menggambar kotak (bounding box) di sekitar
 * wajah yang terdeteksi, ditumpuk di atas PreviewView kamera.
 *
 * Karena koordinat wajah dihitung dari frame Mat OpenCV (ukuran bisa
 * beda dengan ukuran PreviewView di layar), perlu faktor skala yang
 * di-set lewat setSourceSize().
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<CvRect> = emptyList()
    private var sourceWidth: Int = 1
    private var sourceHeight: Int = 1

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    /**
     * Ukuran frame sumber (Mat) tempat koordinat wajah dihitung.
     * Wajib dipanggil sebelum updateFaces() supaya skala gambar benar.
     */
    fun setSourceSize(width: Int, height: Int) {
        sourceWidth = if (width > 0) width else 1
        sourceHeight = if (height > 0) height else 1
    }

    fun updateFaces(newFaces: List<CvRect>) {
        faces = newFaces
        postInvalidate()
    }

    fun clear() {
        faces = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        for (face in faces) {
            val rectF = RectF(
                face.x * scaleX,
                face.y * scaleY,
                (face.x + face.width) * scaleX,
                (face.y + face.height) * scaleY
            )
            canvas.drawRect(rectF, boxPaint)
        }
    }
}