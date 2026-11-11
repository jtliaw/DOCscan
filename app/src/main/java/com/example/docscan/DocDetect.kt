package com.example.docscan

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** 自动检测纸张边缘：找出画面里最大的四边形。 */
object DocDetect {
    private var ready: Boolean? = null

    private fun init(): Boolean {
        if (ready == null) ready = try { OpenCVLoader.initLocal() } catch (e: Throwable) { false }
        return ready == true
    }

    /** 返回四个角（左上、右上、右下、左下）的 x,y（原图坐标）；找不到就返回 null */
    fun find(src: Bitmap): FloatArray? {
        if (!init()) return null
        return try { detect(src) } catch (e: Throwable) { null }
    }

    private fun detect(src: Bitmap): FloatArray? {
        val scale = 500f / maxOf(src.width, src.height)
        val small = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true
        )
        val rgba = Mat()
        Utils.bitmapToMat(small, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val area = small.width.toDouble() * small.height
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))

        var best: Array<Point>? = null
        var bestArea = 0.0
        for ((lo, hi) in listOf(30.0 to 90.0, 50.0 to 150.0, 75.0 to 200.0)) {
            val edges = Mat()
            Imgproc.Canny(gray, edges, lo, hi)
            Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), 2)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            for (c in contours) {
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                if (approx.total() == 4L) {
                    val pts = approx.toArray()
                    if (Imgproc.isContourConvex(MatOfPoint(*pts))) {
                        val a = Imgproc.contourArea(approx)
                        if (a > 0.12 * area && a < 0.95 * area && a > bestArea) {
                            bestArea = a
                            best = pts
                        }
                    }
                }
            }
        }
        val b = best ?: return null

        // 按顺时针排序，并让第一个点是最靠左上的
        val cx = b.sumOf { it.x } / 4
        val cy = b.sumOf { it.y } / 4
        val sorted = b.sortedBy { Math.atan2(it.y - cy, it.x - cx) }
        val start = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        val k = 1.0 / scale
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val p = sorted[(start + i) % 4]
            out[i * 2] = (p.x * k).toFloat()
            out[i * 2 + 1] = (p.y * k).toFloat()
        }
        return out
    }
}
