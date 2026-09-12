package com.example.terminalabsensi.presentation.laporan

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.terminalabsensi.R
import com.example.terminalabsensi.data.local.dao.AbsensiDao
import com.example.terminalabsensi.data.local.dao.KaryawanDao
import com.example.terminalabsensi.data.local.entity.Absensi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStreamWriter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.util.Date

class LaporanActivity : AppCompatActivity() {

    private val absensiDao: AbsensiDao by inject()
    private val karyawanDao: KaryawanDao by inject()

    private lateinit var etTanggalMulai: EditText
    private lateinit var etTanggalAkhir: EditText
    private lateinit var containerLaporan: LinearLayout

    // Menyimpan hasil filter terakhir, dipakai nanti untuk export CSV/PDF
    var dataLaporanTerakhir: List<Pair<Absensi, String>> = emptyList()
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_laporan)

        etTanggalMulai = findViewById(R.id.etTanggalMulai)
        etTanggalAkhir = findViewById(R.id.etTanggalAkhir)
        containerLaporan = findViewById(R.id.containerLaporan)

        setTanggalDefault()

        findViewById<Button>(R.id.btnMingguIni).setOnClickListener { setRentangMingguIni() }
        findViewById<Button>(R.id.btnBulanIni).setOnClickListener { setRentangBulanIni() }
        findViewById<Button>(R.id.btnTampilkan).setOnClickListener { tampilkanLaporan() }

        findViewById<Button>(R.id.btnExportCsv).setOnClickListener {
            Toast.makeText(this, "Tampilkan laporan dulu sebelum export", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnExportPdf).setOnClickListener {
            if (dataLaporanTerakhir.isEmpty()) {
                Toast.makeText(this, "Tidak ada data untuk di-export", Toast.LENGTH_SHORT).show()
            } else {
                exportPdf()
            }
        }
    }

    private fun format() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun setTanggalDefault() {
        setRentangBulanIni()
    }

    private fun setRentangMingguIni() {
        val kalender = Calendar.getInstance()
        kalender.firstDayOfWeek = Calendar.MONDAY
        kalender.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        etTanggalMulai.setText(format().format(kalender.time))
        etTanggalAkhir.setText(format().format(Calendar.getInstance().time))
    }

    private fun setRentangBulanIni() {
        val kalender = Calendar.getInstance()
        kalender.set(Calendar.DAY_OF_MONTH, 1)
        etTanggalMulai.setText(format().format(kalender.time))
        etTanggalAkhir.setText(format().format(Calendar.getInstance().time))
    }

    private fun tampilkanLaporan() {
        val tanggalMulai = etTanggalMulai.text.toString().trim()
        val tanggalAkhir = etTanggalAkhir.text.toString().trim()

        if (tanggalMulai.isBlank() || tanggalAkhir.isBlank()) {
            Toast.makeText(this, "Tanggal mulai dan akhir wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        if (tanggalMulai > tanggalAkhir) {
            Toast.makeText(this, "Tanggal mulai tidak boleh lebih besar dari tanggal akhir", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Ambil sekali (bukan Flow terus-menerus, laporan itu snapshot pada saat ditekan)
            var hasilDenganNama: List<Pair<Absensi, String>> = emptyList()

            absensiDao.getByRentangTanggal(tanggalMulai, tanggalAkhir).collect { daftarAbsensi ->
                val cacheNama = mutableMapOf<String, String>()
                val hasil = mutableListOf<Pair<Absensi, String>>()

                for (absensi in daftarAbsensi) {
                    val nama = cacheNama.getOrPut(absensi.idKaryawan) {
                        karyawanDao.getById(absensi.idKaryawan)?.nama ?: "(Tidak diketahui)"
                    }
                    hasil.add(absensi to nama)
                }

                hasilDenganNama = hasil
                dataLaporanTerakhir = hasil

                runOnUiThread { tampilkanTabel(hasil) }

                // Cukup ambil snapshot pertama, hentikan collect Flow supaya tidak terus mendengarkan
                return@collect
            }
        }
    }

    private fun tampilkanTabel(data: List<Pair<Absensi, String>>) {
        containerLaporan.removeAllViews()

        if (data.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Tidak ada data absensi pada rentang tanggal ini."
            tv.setTextColor(0xFF000000.toInt())
            containerLaporan.addView(tv)
            return
        }

        for ((absensi, nama) in data) {
            val tv = TextView(this)
            val perluPerhatian =
                absensi.status == "Pulang Cepat" && absensi.keterangan == "Tanpa Keterangan"

            val jamText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(absensi.timestamp)
            val keteranganText = absensi.keterangan?.let { " ($it)" } ?: ""

            tv.text =
                "$nama — ${absensi.tanggal} $jamText\n${absensi.jenisAbsen.uppercase()}: ${absensi.status}$keteranganText"
            tv.setTextColor(0xFF000000.toInt())
            tv.setPadding(16, 16, 16, 16)
            tv.gravity = Gravity.START
            tv.setBackgroundColor(if (perluPerhatian) 0xFFFFF3CD.toInt() else 0xFFF0F0F0.toInt())

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            tv.layoutParams = params

            containerLaporan.addView(tv)
        }
    }
        private fun exportCsv() {
            val tanggalMulai = etTanggalMulai.text.toString().trim()
            val tanggalAkhir = etTanggalAkhir.text.toString().trim()
            val namaFile = "absensi_${tanggalMulai}_${tanggalAkhir}.csv"

            val sb = StringBuilder()
            sb.append("ID Karyawan,Nama,Tanggal,Jam,Jenis Absen,Status,Keterangan,Catatan Tambahan\n")

            for ((absensi, nama) in dataLaporanTerakhir) {
                val jam = SimpleDateFormat("HH:mm", Locale.getDefault()).format(absensi.timestamp)
                sb.append(
                    "${absensi.idKaryawan},${csvSafe(nama)},${absensi.tanggal},$jam," +
                            "${absensi.jenisAbsen},${csvSafe(absensi.status)}," +
                            "${csvSafe(absensi.keterangan ?: "")},${csvSafe(absensi.catatanTambahan ?: "")}\n"
                )
            }

            try {
                val uri = simpanFileKeDownloads(namaFile, "text/csv")
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(sb.toString())
                        }
                    }
                    Toast.makeText(this, "CSV berhasil disimpan di folder Download: $namaFile", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Gagal membuat file CSV", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal export CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        /** Menangani beberapa karakter khusus CSV (koma, kutip) supaya file tidak rusak */
        private fun csvSafe(teks: String): String {
            return if (teks.contains(",") || teks.contains("\"")) {
                "\"${teks.replace("\"", "\"\"")}\""
            } else {
                teks
            }
        }

        /**
         * Menyimpan file ke folder Download publik menggunakan MediaStore
         * (cara resmi Android 10+ tanpa perlu izin penyimpanan eksplisit).
         */
        private fun simpanFileKeDownloads(namaFile: String, mimeType: String): android.net.Uri? {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, namaFile)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            return contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        }

    private fun exportPdf() {
        val tanggalMulai = etTanggalMulai.text.toString().trim()
        val tanggalAkhir = etTanggalAkhir.text.toString().trim()
        val namaFile = "laporan_absensi_${tanggalMulai}_${tanggalAkhir}.pdf"

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 dalam point, potret
            val pageHeight = 842
            val margin = 40f

            val paintJudul = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK }
            val paintSubjudul = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val paintHeader = Paint().apply { textSize = 9f; isFakeBoldText = true; color = Color.BLACK }
            val paintIsi = Paint().apply { textSize = 9f; color = Color.BLACK }
            val paintGaris = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            var halamanKe = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, halamanKe).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var y = margin

            fun gambarHeaderHalaman() {
                canvas.drawText("Laporan Absensi — Terminal Absensi", margin, y, paintJudul)
                y += 20f
                canvas.drawText("Periode: $tanggalMulai s/d $tanggalAkhir", margin, y, paintSubjudul)
                y += 14f
                val formatCetak = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                canvas.drawText("Dicetak: ${formatCetak.format(Date())}", margin, y, paintSubjudul)
                y += 20f

                val kolomX = floatArrayOf(margin, margin + 90f, margin + 180f, margin + 230f, margin + 290f, margin + 370f)
                canvas.drawText("Nama", kolomX[0], y, paintHeader)
                canvas.drawText("Tanggal", kolomX[1], y, paintHeader)
                canvas.drawText("Jam", kolomX[2], y, paintHeader)
                canvas.drawText("Jenis", kolomX[3], y, paintHeader)
                canvas.drawText("Status", kolomX[4], y, paintHeader)
                canvas.drawText("Keterangan", kolomX[5], y, paintHeader)
                y += 6f
                canvas.drawLine(margin, y, pageWidth - margin, y, paintGaris)
                y += 14f
            }

            gambarHeaderHalaman()

            val kolomX = floatArrayOf(margin, margin + 90f, margin + 180f, margin + 230f, margin + 290f, margin + 370f)

            for ((absensi, nama) in dataLaporanTerakhir) {
                if (y > pageHeight - margin - 20f) {
                    pdfDocument.finishPage(page)
                    halamanKe++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, halamanKe).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin
                    gambarHeaderHalaman()
                }

                val jam = SimpleDateFormat("HH:mm", Locale.getDefault()).format(absensi.timestamp)
                val keterangan = absensi.keterangan ?: "-"

                canvas.drawText(nama.take(14), kolomX[0], y, paintIsi)
                canvas.drawText(absensi.tanggal, kolomX[1], y, paintIsi)
                canvas.drawText(jam, kolomX[2], y, paintIsi)
                canvas.drawText(absensi.jenisAbsen, kolomX[3], y, paintIsi)
                canvas.drawText(absensi.status.take(12), kolomX[4], y, paintIsi)
                canvas.drawText(keterangan.take(14), kolomX[5], y, paintIsi)
                y += 16f
            }

            pdfDocument.finishPage(page)

            val uri = simpanFileKeDownloads(namaFile, "application/pdf")
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                Toast.makeText(this, "PDF berhasil disimpan di folder Download: $namaFile", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal membuat file PDF", Toast.LENGTH_LONG).show()
            }

            pdfDocument.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal export PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    }
