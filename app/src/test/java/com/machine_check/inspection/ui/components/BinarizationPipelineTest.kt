package com.machine_check.inspection.ui.components

import org.junit.Assert.*
import org.junit.Test

class BinarizationPipelineTest {

    @Test
    fun `otsuBinarize outputs correct dimensions`() {
        val pixels = IntArray(200) { if (it < 100) 0 else 200 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 20, 10)
        assertEquals(20, result.width)
        assertEquals(10, result.height)
    }

    @Test
    fun `otsuBinarize inverts — dark input becomes true`() {
        val pixels = IntArray(100) { 0 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertTrue("dark pixel should become true after invert", result.get(5, 5))
    }

    @Test
    fun `otsuBinarize inverts — bright input becomes false`() {
        val pixels = IntArray(100) { 255 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertFalse("bright pixel should become false after invert", result.get(5, 5))
    }

    @Test
    fun `otsuBinarize separates black and white regions`() {
        val pixels = IntArray(100) { if (it % 10 < 5) 0 else 255 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertTrue(result.get(1, 5))   // left (dark) → true after invert
        assertFalse(result.get(7, 5))  // right (bright) → false after invert
    }

    @Test
    fun `otsuBinarize handles uniform image without crashing`() {
        val pixels = IntArray(100) { 128 }
        val result = BinarizationPipeline.otsuBinarize(pixels, 10, 10)
        assertNotNull(result)
    }

    @Test
    fun `sauvolaBinarize outputs center crop dimensions`() {
        val pixels = IntArray(1600) { 128 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 40, 40)
        assertEquals(20, result.width)   // 40 / 2
        assertEquals(20, result.height)
    }

    @Test
    fun `sauvolaBinarize inverts — dark input becomes true`() {
        val pixels = IntArray(1600) { 10 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 40, 40)
        assertTrue(result.get(10, 10))
    }

    @Test
    fun `sauvolaBinarize handles minimum size`() {
        val pixels = IntArray(100) { 128 }
        val result = BinarizationPipeline.sauvolaBinarize(pixels, 10, 10, windowSize = 3)
        assertTrue(result.width > 0 && result.height > 0)
    }
}
