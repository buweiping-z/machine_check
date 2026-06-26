package com.machine_check.inspection.ui.components

import com.google.zxing.Binarizer
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitArray
import com.google.zxing.common.BitMatrix

/**
 * 将灰度 IntArray 包装为 ZXing LuminanceSource
 * 仅用于满足 Binarizer 构造函数签名，实际像素数据不被 ZXing 用于二值化
 */
class GrayscaleLuminanceSource(
    private val pixels: IntArray,
    width: Int,
    height: Int
) : LuminanceSource(width, height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val result = row ?: ByteArray(width)
        val offset = y * width
        for (x in 0 until width) result[x] = pixels[offset + x].toByte()
        return result
    }

    override fun getMatrix(): ByteArray {
        val result = ByteArray(width * height)
        for (i in pixels.indices) result[i] = pixels[i].toByte()
        return result
    }

    override fun isCropSupported(): Boolean = false
}

/**
 * 身份二值化器 — 跳过 ZXing 内部二值化，直接透传预处理好的 BitMatrix
 * 防止 HybridBinarizer 覆盖 Otsu/Sauvola 的二值化+取反结果
 */
class IdentityBinarizer(
    source: LuminanceSource,
    private val prebuiltMatrix: BitMatrix
) : Binarizer(source) {

    override fun getBlackRow(y: Int, row: BitArray?): BitArray {
        val array = row ?: BitArray(prebuiltMatrix.width)
        array.clear()
        for (x in 0 until prebuiltMatrix.width) {
            if (prebuiltMatrix.get(x, y)) array.set(x)
        }
        return array
    }

    override fun getBlackMatrix(): BitMatrix = prebuiltMatrix

    override fun createBinarizer(source: LuminanceSource): Binarizer {
        return IdentityBinarizer(source, prebuiltMatrix)
    }
}
