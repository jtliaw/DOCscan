package com.example.docscan

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** 一份已保存的文档：一个文件夹，里面有 doc.pdf、page_1.jpg…、meta.json */
data class Doc(
    val id: String,
    val dir: File,
    val title: String,
    val created: Long,
    val text: String,
    val pages: Int
) {
    val pdf: File get() = File(dir, "doc.pdf")
    val hasOriginal: Boolean get() = File(dir, "page_1_orig.jpg").exists()
    fun page(i: Int) = File(dir, "page_${i + 1}.jpg")
}

object DocStore {
    private fun root(ctx: Context) = File(ctx.filesDir, "docs").apply { mkdirs() }

    fun list(ctx: Context): List<Doc> =
        (root(ctx).listFiles() ?: emptyArray())
            .filter { File(it, "meta.json").exists() }
            .mapNotNull { load(it) }
            .sortedByDescending { it.created }

    private fun load(dir: File): Doc? = try {
        val j = JSONObject(File(dir, "meta.json").readText())
        Doc(dir.name, dir, j.getString("title"), j.getLong("created"),
            j.optString("text", ""), j.getInt("pages"))
    } catch (e: Exception) {
        null
    }

    fun save(doc: Doc) {
        File(doc.dir, "meta.json").writeText(
            JSONObject().put("title", doc.title).put("created", doc.created)
                .put("text", doc.text).put("pages", doc.pages).toString()
        )
    }

    fun create(ctx: Context, pageUris: List<Uri>, pdfUri: Uri?, title: String): Doc {
        val id = System.currentTimeMillis().toString()
        val dir = File(root(ctx), id).apply { mkdirs() }
        pageUris.forEachIndexed { i, u ->
            ctx.contentResolver.openInputStream(u)!!.use { input ->
                File(dir, "page_${i + 1}.jpg").outputStream().use { input.copyTo(it) }
            }
        }
        if (pdfUri != null) {
            ctx.contentResolver.openInputStream(pdfUri)!!.use { input ->
                File(dir, "doc.pdf").outputStream().use { input.copyTo(it) }
            }
        }
        val doc = Doc(id, dir, title, id.toLong(), "", pageUris.size)
        save(doc)
        return doc
    }

    /** 把新的页面接在已有文档后面（文字清空，因为页数变了，需要重新识别） */
    fun append(ctx: Context, doc: Doc, pageUris: List<Uri>): Doc {
        var n = doc.pages
        pageUris.forEach { u ->
            n++
            ctx.contentResolver.openInputStream(u)!!.use { input ->
                File(doc.dir, "page_$n.jpg").outputStream().use { input.copyTo(it) }
            }
        }
        val d = doc.copy(pages = n, text = "")
        save(d)
        return d
    }

    fun delete(doc: Doc) {
        doc.dir.deleteRecursively()
    }
}
