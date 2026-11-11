package com.example.docscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object Enhance {
    private val lut = IntArray(256) { (255.0 * Math.pow(it / 255.0, 2.2)).toInt() }

    /**
     * 去掉背景颜色和阴影，让纸变白、字变黑。
     * keepColor = false：黑白文字；keepColor = true：保留颜色的优化图（纸变白，彩色印章等仍保留）。
     */
    fun clean(src: Bitmap, keepColor: Boolean): Bitmap {
        val maxDim = 2500
        val s = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val bmp = if (s < 1f)
            Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
        else src
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()
        val orig = if (keepColor) px.copyOf() else null

        for (i in px.indices) {
            val c = px[i]
            px[i] = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        }
        val iw = w + 1
        val integ = IntArray(iw * (h + 1))
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) {
                row += px[y * w + x]
                integ[(y + 1) * iw + x + 1] = integ[y * iw + x + 1] + row
            }
        }
        val r = maxOf(12, maxOf(w, h) / 24)
        for (y in 0 until h) {
            val y0 = maxOf(0, y - r)
            val y1 = minOf(h, y + r + 1)
            for (x in 0 until w) {
                val x0 = maxOf(0, x - r)
                val x1 = minOf(w, x + r + 1)
                val area = (x1 - x0) * (y1 - y0)
                val sum = integ[y1 * iw + x1] - integ[y0 * iw + x1] - integ[y1 * iw + x0] + integ[y0 * iw + x0]
                val t = sum.toFloat() / area * 0.85f
                val g = px[y * w + x]
                val v = if (g >= t) 255 else lut[(g * 255f / t).toInt().coerceIn(0, 255)]
                if (orig == null) {
                    px[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                } else {
                    val c = orig[y * w + x]
                    val gain = if (g == 0) 1f else v.toFloat() / g
                    val k = v / 255f * 0.8f // 越亮越接近纯白，去掉纸张的色偏
                    val rr = (((c shr 16) and 255) * gain * (1 - k) + v * k).toInt().coerceIn(0, 255)
                    val gg = (((c shr 8) and 255) * gain * (1 - k) + v * k).toInt().coerceIn(0, 255)
                    val bb = ((c and 255) * gain * (1 - k) + v * k).toInt().coerceIn(0, 255)
                    px[y * w + x] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }


    private val lut13 = IntArray(256) { (255.0 * Math.pow(it / 255.0, 1.3)).toInt() }

    private fun boxBlur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val t = FloatArray(a.size)
        val o = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f; var n = 0
            for (k in maxOf(0, x - r)..minOf(w - 1, x + r)) { sum += a[y * w + k]; n++ }
            t[y * w + x] = sum / n
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f; var n = 0
            for (k in maxOf(0, y - r)..minOf(h - 1, y + r)) { sum += t[k * w + x]; n++ }
            o[y * w + x] = sum / n
        }
        return o
    }

    /**
     * 「优化」：保留颜色的清理。估计每一块纸张的底色，把底色拉成白色，
     * 所以阴影和发黄的纸会变白，而红色印章、蓝色笔迹等颜色会保留（不做黑白二值化）。
     */
    fun enhanceColor(src: Bitmap): Bitmap {
        val s = minOf(1f, 2200f / maxOf(src.width, src.height))
        val bmp = if (s < 1f)
            Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
        else src
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val sw = maxOf(1, w / 8)
        val sh = maxOf(1, h / 8)
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        if (bmp !== src) bmp.recycle()
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        small.recycle()

        // 每个小格子：窗口里「最亮」的像素胜出，并带着它自己的颜色（= 纸张底色）
        val key = LongArray(sw * sh) {
            val c = sp[it]
            val r = (c shr 16) and 255
            val g = (c shr 8) and 255
            val b = c and 255
            (((r * 299 + g * 587 + b * 114) / 1000).toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        }
        val rr = 4
        val tmp = LongArray(sw * sh)
        val mx = LongArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var m = 0L
            for (k in maxOf(0, x - rr)..minOf(sw - 1, x + rr)) m = maxOf(m, key[y * sw + k])
            tmp[y * sw + x] = m
        }
        for (y in 0 until sh) for (x in 0 until sw) {
            var m = 0L
            for (k in maxOf(0, y - rr)..minOf(sh - 1, y + rr)) m = maxOf(m, tmp[k * sw + x])
            mx[y * sw + x] = m
        }
        val fr = boxBlur(FloatArray(sw * sh) { ((mx[it] shr 16) and 255).toFloat() }, sw, sh, rr)
        val fg = boxBlur(FloatArray(sw * sh) { ((mx[it] shr 8) and 255).toFloat() }, sw, sh, rr)
        val fb = boxBlur(FloatArray(sw * sh) { (mx[it] and 255).toFloat() }, sw, sh, rr)
        val bgSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        bgSmall.setPixels(
            IntArray(sw * sh) { (0xFF shl 24) or (fr[it].toInt() shl 16) or (fg[it].toInt() shl 8) or fb[it].toInt() },
            0, sw, 0, 0, sw, sh
        )
        val bgBmp = Bitmap.createScaledBitmap(bgSmall, w, h, true)
        bgSmall.recycle()
        val bgPx = IntArray(w * h)
        bgBmp.getPixels(bgPx, 0, w, 0, 0, w, h)
        bgBmp.recycle()

        for (i in px.indices) {
            val c = px[i]
            val b = bgPx[i]
            val r = (((c shr 16) and 255) * 255 / maxOf(70, (b shr 16) and 255)).coerceIn(0, 255)
            val g = (((c shr 8) and 255) * 255 / maxOf(70, (b shr 8) and 255)).coerceIn(0, 255)
            val bl = ((c and 255) * 255 / maxOf(70, b and 255)).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (lut13[r] shl 16) or (lut13[g] shl 8) or lut13[bl]
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** 透视矫正：把四个角（左上、右上、右下、左下，x,y 依次排列）围成的区域拉成长方形 */
    fun warp(src: Bitmap, p: FloatArray): Bitmap {
        fun dist(a: Int, b: Int) = Math.hypot((p[a * 2] - p[b * 2]).toDouble(), (p[a * 2 + 1] - p[b * 2 + 1]).toDouble()).toFloat()
        var w = ((dist(0, 1) + dist(3, 2)) / 2f).toInt().coerceAtLeast(50)
        var h = ((dist(0, 3) + dist(1, 2)) / 2f).toInt().coerceAtLeast(50)
        val k = minOf(1f, 3000f / maxOf(w, h))
        w = (w * k).toInt().coerceAtLeast(50)
        h = (h * k).toInt().coerceAtLeast(50)
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val m = Matrix()
        m.setPolyToPoly(p, 0, dst, 0, 4)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
