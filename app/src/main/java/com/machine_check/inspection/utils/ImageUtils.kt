package com.machine_check.inspection.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.Image
import java.nio.ByteBuffer

/**
 * 图像工具类
 * 提供基础的图像增强功能，不依赖第三方库如 OpenCV，确保编译通过
 */
object ImageUtils {

    /**
     * 增强图像对比度
     * 对于 PCB 激光刻印的二维码，增加对比度有助于提高识别率
     * @param bitmap 原始位图
     * @param contrast 对比度系数，>1.0 增加对比度
     */
    fun enhanceContrast(bitmap: Bitmap, contrast: Float = 1.5f): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, 0f,
                0f, contrast, 0f, 0f, 0f,
                0f, 0f, contrast, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * 从 YUV_420_888 Image 的 Y 平面直接提取灰度像素数组
     * 避免 YUV→ARGB Bitmap→灰度 的浪费转换
     */
    fun extractGrayscaleFromYuv(image: Image): IntArray? {
        val yPlane = image.planes.getOrNull(0) ?: return null
        val yBuffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)

        if (pixelStride == 1) {
            for (y in 0 until height) {
                val offset = y * rowStride
                for (x in 0 until width) {
                    pixels[y * width + x] = yBuffer.get(offset + x).toInt() and 0xFF
                }
            }
        } else {
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                for (x in 0 until width) {
                    pixels[y * width + x] = yBuffer.get(rowOffset + x * pixelStride).toInt() and 0xFF
                }
            }
        }
        return pixels
    }
}

