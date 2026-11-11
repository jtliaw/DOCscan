package com.example.docscan

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 不依赖第三方库的最小 .xlsx 生成器（单工作表，文本单元格）。 */
object XlsxWriter {
    fun write(rows: List<List<String>>, file: File, sheetName: String = "Sheet1") {
        ZipOutputStream(file.outputStream()).use { z ->
            fun add(name: String, body: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val h = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            add("[Content_Types].xml", h +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>")
            add("_rels/.rels", h +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>")
            add("xl/workbook.xml", h +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + esc(sheetName).replace("\"", "&quot;") + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            add("xl/_rels/workbook.xml.rels", h +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "</Relationships>")

            val sb = StringBuilder(h)
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed { r, cells ->
                sb.append("<row r=\"${r + 1}\">")
                cells.forEachIndexed { c, text ->
                    sb.append("<c r=\"${col(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    sb.append(esc(text)).append("</t></is></c>")
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            add("xl/worksheets/sheet1.xml", sb.toString())
        }
    }

    private fun col(i: Int): String {
        var n = i
        var s = ""
        do {
            s = ('A' + n % 26) + s
            n = n / 26 - 1
        } while (n >= 0)
        return s
    }

    private fun esc(s: String) = s
        .filter { it.code >= 32 || it == '\n' || it == '\t' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
