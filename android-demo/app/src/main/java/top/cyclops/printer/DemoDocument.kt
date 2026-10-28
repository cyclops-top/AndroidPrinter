package top.cyclops.printer

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

object DemoDocument {
    fun create(directory: File): File {
        val file = File(directory, "demo.pdf")
        val document = PdfDocument()
        try {
            for (number in 1..2) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.rgb(25, 80, 150)
                paint.textSize = 30f
                canvas.drawText("Android IPP Print Demo", 40f, 80f, paint)
                paint.color = Color.BLACK
                paint.textSize = 18f
                canvas.drawText("Page $number / 2 - A4", 40f, 130f, paint)
                canvas.drawText("Generated on Android with PdfDocument", 40f, 175f, paint)
                canvas.drawText("中文测试：网络打印与纸张能力查询", 40f, 220f, paint)
                for (i in 0..5) {
                    paint.color = Color.rgb(i * 40, i * 40, i * 40)
                    canvas.drawRect(40f + i * 80, 270f, 110f + i * 80, 350f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        return file
    }

    fun preview(file: File): Bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}
