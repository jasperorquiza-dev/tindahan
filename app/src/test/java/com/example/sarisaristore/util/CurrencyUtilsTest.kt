package com.example.sarisaristore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyUtilsTest {
    @Test
    fun `parseToCentavos parses decimal input`() {
        assertEquals(1_250L, CurrencyUtils.parseToCentavos("12.50"))
        assertEquals(100L, CurrencyUtils.parseToCentavos("1"))
    }

    @Test
    fun `parseToCentavos returns null for invalid input`() {
        assertNull(CurrencyUtils.parseToCentavos(""))
        assertNull(CurrencyUtils.parseToCentavos("abc"))
    }

    @Test
    fun `formatCentavos formats peso values`() {
        val formatted = CurrencyUtils.formatCentavos(1_250L)
        assertTrue(formatted.contains("12.50"))
    }
}
