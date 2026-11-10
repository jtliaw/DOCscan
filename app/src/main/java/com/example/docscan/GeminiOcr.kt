package com.example.docscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 调用 Gemini 识别图片文字（接口写法沿用你的 Artocarpus 项目）。必须在子线程调用。 */
object GeminiOcr {
    private const val PROMPT =
        "Transcribe all the text in this image exactly as written. The text may be handwritten and may " +
        "mix languages (for example Chinese, English, Malay, Arabic or others) and numbers. " +
        "Output line by line in the original order, keeping the original language and line breaks. " +
        "Do not translate, explain or add anything. If a line has several columns (like a table), " +
        "separate the columns with |. Be especially careful with email addresses, phone numbers, " +
        "amounts and dates. If there is no text, output nothing."

    fun recognize(ctx: Context, uri: Uri, apiKey: String, model: String): String {
        val jpeg = loadJpeg(ctx, uri)
        val parts = JSONArray()
            .put(JSONObject().put("text", PROMPT))
            .put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("mediaResolution", "MEDIA_RESOLUTION_HIGH"))

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 90000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", apiKey)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception(explain(ctx, code) + "\n" + text.take(200))
            val cands = JSONObject(text).optJSONArray("candidates")
            if (cands == null || cands.length() == 0)
                throw Exception(ctx.getString(R.string.gem_blocked) + "\n" + text.take(200))
            val ps = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: return ""
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text", ""))
            return sb.toString().trim()
        } finally {
            c.disconnect()
        }
    }

    private fun explain(ctx: Context, code: Int) = when (code) {
        400 -> ctx.getString(R.string.gem_400)
        401 -> ctx.getString(R.string.gem_401)
        403 -> ctx.getString(R.string.gem_403)
        404 -> ctx.getString(R.string.gem_404)
        429 -> ctx.getString(R.string.gem_429)
        else -> ctx.getString(R.string.gem_server, code)
    }

    /** Downscale to 2048 px on the long side to reduce upload size */
    private fun loadJpeg(ctx: Context, uri: Uri): ByteArray {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val scale = minOf(1f, 2048f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f)
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 88, bos)
        return bos.toByteArray()
    }
}
