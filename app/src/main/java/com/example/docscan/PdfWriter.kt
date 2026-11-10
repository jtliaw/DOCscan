package com.example.docscan

import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale

/** 把 JPEG 页面直接嵌入 PDF（不重新压缩，文件小、画质不损失）。 */
object PdfWriter {
    fun write(pages: List<File>, out: File) {
        val n = pages.size
        val total = 2 + 3 * n
        val offs = LongArray(total + 1)
        out.outputStream().buffered().use { o ->
            var pos = 0L
            fun w(b: ByteArray) { o.write(b); pos += b.size }
            fun w(s: String) = w(s.toByteArray(Charsets.ISO_8859_1))

            w("%PDF-1.4\n")
            offs[1] = pos
            w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            val kids = (0 until n).joinToString(" ") { "${3 + 3 * it} 0 R" }
            offs[2] = pos
            w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $n >>\nendobj\n")

            pages.forEachIndexed { i, f ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                val iw = bounds.outWidth
                val ih = bounds.outHeight
                val ph = String.format(Locale.US, "%.2f", 595f * ih / iw)
                val jpg = f.readBytes()
                val pageId = 3 + 3 * i
                val contId = pageId + 1
                val imgId = pageId + 2

                offs[pageId] = pos
                w("$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 $ph] " +
                    "/Resources << /XObject << /Im0 $imgId 0 R >> >> /Contents $contId 0 R >>\nendobj\n")

                val content = "q 595 0 0 $ph 0 0 cm /Im0 Do Q"
                offs[contId] = pos
                w("$contId 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")

                offs[imgId] = pos
                w("$imgId 0 obj\n<< /Type /XObject /Subtype /Image /Width $iw /Height $ih " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.size} >>\nstream\n")
                w(jpg)
                w("\nendstream\nendobj\n")
            }

            val xref = pos
            w("xref\n0 ${total + 1}\n")
            w("0000000000 65535 f \n")
            for (id in 1..total) w(String.format(Locale.US, "%010d 00000 n \n", offs[id]))
            w("trailer\n<< /Size ${total + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }
}
