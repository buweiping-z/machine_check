package com.machine_check.inspection.ui.components

import com.google.zxing.common.BitMatrix
import kotlin.math.sqrt

/**
 * 二值化处理管道 — Otsu 全局阈值 + Sauvola 自适应阈值
 * 均内置取反操作，为 ZXing DataMatrixReader 准备适合 PCB 刻印码的 BitMatrix
 * 纯 Kotlin，无 Android 依赖，可直接单元测试
 */
object BinarizationPipeline {

    /**
     * Otsu 全局阈值二值化 + 取反
     * 最大化类间方差寻找阈值，光照均匀时效果最好
     */
    fun otsuBinarize(pixels: IntArray, width: Int, height: Int): BitMatrix {
        val total = width * height

        // 256 级灰度直方图
        val histogram = IntArray(256)
        for (p in pixels) histogram[p.coerceIn(0, 255)]++

        // 寻找最大类间方差的阈值
        var bestT = 128
        var maxVar = 0.0
        var sumTotal = 0L
        for (i in 0..255) sumTotal += i.toLong() * histogram[i]

        var wBg = 0
        var sumBg = 0L
        for (t in 0..255) {
            wBg += histogram[t]
            if (wBg == 0 || wBg == total) continue
            sumBg += t.toLong() * histogram[t]
            val wFg = total - wBg
            val sumFg = sumTotal - sumBg
            val meanBg = sumBg.toDouble() / wBg
            val meanFg = sumFg.toDouble() / wFg
            val variance = wBg.toDouble() * wFg * (meanBg - meanFg) * (meanBg - meanFg)
            if (variance > maxVar) { maxVar = variance; bestT = t }
        }

        // 二值化: 暗(<=t)=0, 亮(>t)=1 → 取反: 暗→true, 亮→false
        val matrix = BitMatrix(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] <= bestT) matrix.set(x, y)
            }
        }
        return matrix
    }

    /**
     * Sauvola 自适应阈值二值化 + 取反
     * 逐像素局部窗口计算，适应光照不均场景
     * 仅处理中心 50% 区域（性能优化，假设条码对准中心）
     */
    fun sauvolaBinarize(
        pixels: IntArray, width: Int, height: Int,
        windowSize: Int = 15, k: Double = 0.5, R: Double = 128.0
    ): BitMatrix {
        val cropX = width / 4
        val cropY = height / 4
        val cropW = width / 2
        val cropH = height / 2
        val halfWin = windowSize / 2

        val matrix = BitMatrix(cropW, cropH)

        for (cy in 0 until cropH) {
            for (cx in 0 until cropW) {
                val ox = cropX + cx
                val oy = cropY + cy

                var sum = 0.0; var sumSq = 0.0; var count = 0
                val wl = (ox - halfWin).coerceAtLeast(0)
                val wt = (oy - halfWin).coerceAtLeast(0)
                val wr = (ox + halfWin).coerceAtMost(width - 1)
                val wb = (oy + halfWin).coerceAtMost(height - 1)

                for (wy in wt..wb) {
                    val rowOff = wy * width
                    for (wx in wl..wr) {
                        val v = pixels[rowOff + wx].toDouble()
                        sum += v; sumSq += v * v; count++
                    }
                }
                val mean = sum / count
                val variance = (sumSq / count) - mean * mean
                val stdDev = sqrt(variance.coerceAtLeast(0.0))
                // 完全均匀区域 (stdDev ≈ 0) 使用均值作为阈值，否则使用 Sauvola 公式
                val threshold = if (stdDev < 1e-10) mean
                                else mean * (1.0 + k * (stdDev / R - 1.0))

                if (pixels[oy * width + ox] <= threshold) matrix.set(cx, cy)
            }
        }
        return matrix
    }
}
