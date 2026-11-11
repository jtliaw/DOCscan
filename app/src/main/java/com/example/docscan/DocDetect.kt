package com.example.docscan

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** 自动检测文件的位置。必须在子线程调用。 */
object DocDetect {
    private var ready: Boolean? = null

    private fun init(): Boolean {
        if (ready == null) ready = try { OpenCVLoader.initLocal() } catch (e: Throwable) { false }
        return ready == true
    }

    /**
     * mode 0：自动（先找清晰的四边形边缘，找不到再用 GrabCut 分离前景）
     * mode 1：只用边缘；mode 2：只用 GrabCut（适合纸/书和背景颜色明显不同的情况）
     * 返回四个角（左上、右上、右下、左下）的 x,y（原图坐标）；找不到返回 null。
     */
    fun find(src: Bitmap, mode: Int = 0): FloatArray? {
        if (!init()) return null
        return try {
            when (mode) {
                1 -> byEdges(src)
                2 -> byGrabCut(src)
                else -> byEdges(src) ?: byGrabCut(src)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun byEdges(src: Bitmap): FloatArray? {
        val scale = 500f / maxOf(src.width, src.height)
        val small = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt().coerceAtLeast(8), (src.height * scale).toInt().coerceAtLeast(8), true
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
        return finish(b, scale, src.width, src.height)
    }

    private fun byGrabCut(src: Bitmap): FloatArray? {
        val scale = 320f / maxOf(src.width, src.height)
        val small = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt().coerceAtLeast(8), (src.height * scale).toInt().coerceAtLeast(8), true
        )
        val rgba = Mat()
        Utils.bitmapToMat(small, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        val w = rgb.cols()
        val h = rgb.rows()
        val mx = (w * 0.04).toInt()
        val my = (h * 0.04).toInt()
        val mask = Mat()
        val bgd = Mat()
        val fgd = Mat()
        Imgproc.grabCut(rgb, mask, Rect(mx, my, w - 2 * mx, h - 2 * my), bgd, fgd, 4, Imgproc.GC_INIT_WITH_RECT)

        val fgA = Mat()
        val fgB = Mat()
        val fg = Mat()
        Core.inRange(mask, Scalar(1.0), Scalar(1.0), fgA) // 确定的前景
        Core.inRange(mask, Scalar(3.0), Scalar(3.0), fgB) // 可能的前景
        Core.bitwise_or(fgA, fgB, fg)
        Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(fg, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val c = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
        val idx = MatOfInt()
        Imgproc.convexHull(c, idx)
        val cp = c.toArray()
        val hull = MatOfPoint2f(*idx.toArray().map { cp[it] }.toTypedArray())
        val area = w.toDouble() * h
        val ha = Imgproc.contourArea(hull)
        if (ha < 0.12 * area || ha > 0.97 * area) return null

        val peri = Imgproc.arcLength(hull, true)
        for (eps in listOf(0.015, 0.02, 0.03, 0.04, 0.05)) {
            val ap = MatOfPoint2f()
            Imgproc.approxPolyDP(hull, ap, eps * peri, true)
            if (ap.total() == 4L) return finish(ap.toArray(), scale, src.width, src.height)
        }
        val box = arrayOfNulls<Point>(4)
        Imgproc.minAreaRect(hull).points(box)
        return finish(box.requireNoNulls(), scale, src.width, src.height)
    }

    /** 顺时针排序（第一个是最靠左上的角），换回原图坐标，并限制在图片范围内 */
    private fun finish(b: Array<Point>, scale: Float, w: Int, h: Int): FloatArray {
        val cx = b.sumOf { it.x } / 4
        val cy = b.sumOf { it.y } / 4
        val sorted = b.sortedBy { Math.atan2(it.y - cy, it.x - cx) }
        val start = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        val k = 1.0 / scale
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val p = sorted[(start + i) % 4]
            out[i * 2] = (p.x * k).toFloat().coerceIn(0f, w.toFloat())
            out[i * 2 + 1] = (p.y * k).toFloat().coerceIn(0f, h.toFloat())
        }
        return out
    }
}
