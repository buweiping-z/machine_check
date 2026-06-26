package com.machine_check.inspection.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.common.BitMatrix
import org.junit.Assert.*
import org.junit.Test

class IdentityBinarizerTest {

    @Test
    fun `getBlackMatrix returns prebuilt matrix unchanged`() {
        val matrix = BitMatrix(10, 10)
        matrix.set(5, 5)
        val pixels = IntArray(100) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 10, 10)
        val binarizer = IdentityBinarizer(source, matrix)
        val result = BinaryBitmap(binarizer).blackMatrix
        assertSame(matrix, result)
        assertTrue(result.get(5, 5))
        assertFalse(result.get(0, 0))
    }

    @Test
    fun `getBlackRow matches prebuilt matrix`() {
        val matrix = BitMatrix(8, 8)
        matrix.set(1, 3); matrix.set(3, 3); matrix.set(5, 3); matrix.set(7, 3)
        val pixels = IntArray(64) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 8, 8)
        val row = IdentityBinarizer(source, matrix).getBlackRow(3, null)
        assertNotNull(row)
        assertTrue(row!!.get(1)); assertTrue(row.get(3))
        assertTrue(row.get(5)); assertTrue(row.get(7))
        assertFalse(row.get(0)); assertFalse(row.get(2))
    }

    @Test
    fun `createBinarizer returns new IdentityBinarizer with same matrix`() {
        val matrix = BitMatrix(4, 4)
        val pixels = IntArray(16) { 128 }
        val source = GrayscaleLuminanceSource(pixels, 4, 4)
        val original = IdentityBinarizer(source, matrix)
        val rotated = original.createBinarizer(source) as IdentityBinarizer
        assertSame(matrix, rotated.getBlackMatrix())
    }

    @Test
    fun `GrayscaleLuminanceSource reports correct dimensions`() {
        val source = GrayscaleLuminanceSource(IntArray(48) { 0 }, 8, 6)
        assertEquals(8, source.width)
        assertEquals(6, source.height)
    }

    @Test
    fun `GrayscaleLuminanceSource getRow returns correct values`() {
        val pixels = IntArray(16) { it * 10 }
        val source = GrayscaleLuminanceSource(pixels, 4, 4)
        val row = source.getRow(2, null)
        assertEquals(80.toByte(), row!![0])
        assertEquals(90.toByte(), row[1])
        assertEquals(100.toByte(), row[2])
        assertEquals(110.toByte(), row[3])
    }
}
