package com.example.sarisaristore.ui.feature.utang

import org.junit.Assert.assertEquals
import org.junit.Test

class UtangCustomerRouteTest {
    @Test
    fun `formatBorrowedItemLines trims lines and removes blanks`() {
        val formatted = formatBorrowedItemLines(
            """
                1   x   yelo

                  1 x buns  
                
                2    x   marlboro
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "1 x yelo",
                "1 x buns",
                "2 x marlboro",
            ),
            formatted,
        )
    }

    @Test
    fun `formatBorrowedItemLines falls back to cleaned single line text`() {
        val formatted = formatBorrowedItemLines("   1    x    coffee   ")

        assertEquals(listOf("1 x coffee"), formatted)
    }
}
