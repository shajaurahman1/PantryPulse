package com.pantrypulse.app

import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.OcrLine
import com.pantrypulse.app.domain.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    private val receipt = """
        FRESHMART SUPERSTORE
        123 High Street  Tel 0123 456789
        DATE 12/11/2026  TIME 18:42
        WHOLE MILK 2L            1.85
        2 x GREEK YOGURT         3.20
        BANANAS 1.2 KG           1.44
        CHICKEN BREAST 500G      4.99
        5012345678900 SOURDOUGH BREAD 2.50
        BASMATI RICE 1KG         2.10
        DISCOUNT                -0.50
        SUBTOTAL                15.58
        VAT                      0.00
        TOTAL                   15.58
        VISA                    15.58
        THANK YOU FOR SHOPPING
    """.trimIndent()

    @Test fun extractsOnlyItems() {
        val items = ReceiptParser.parseText(receipt)
        assertEquals(listOf("Whole Milk", "Greek Yogurt", "Bananas", "Chicken Breast", "Sourdough Bread", "Basmati Rice"), items.map { it.name })
    }

    @Test fun readsQuantitiesWeightsAndPrices() {
        val items = ReceiptParser.parseText(receipt).associateBy { it.name }
        assertEquals(2.0, items.getValue("Greek Yogurt").quantity, 0.0)
        assertEquals(1.2, items.getValue("Bananas").quantity, 0.0)
        assertEquals("kg", items.getValue("Bananas").unit)
        assertEquals(500.0, items.getValue("Chicken Breast").quantity, 0.0)
        assertEquals("g", items.getValue("Chicken Breast").unit)
        assertEquals(2.0, items.getValue("Whole Milk").quantity, 0.0)
        assertEquals("L", items.getValue("Whole Milk").unit)
        assertEquals(4.99, items.getValue("Chicken Breast").price!!, 0.001)
    }

    @Test fun guessesCategories() {
        val items = ReceiptParser.parseText(receipt).associateBy { it.name }
        assertEquals(Category.DAIRY, items.getValue("Whole Milk").category)
        assertEquals(Category.FRUIT, items.getValue("Bananas").category)
        assertEquals(Category.MEAT, items.getValue("Chicken Breast").category)
        assertEquals(Category.BAKERY, items.getValue("Sourdough Bread").category)
        assertEquals(Category.GRAINS, items.getValue("Basmati Rice").category)
    }

    @Test fun rejoinsNameAndPriceBlocksOnTheSameRow() {
        // OCR often returns names and prices as separate blocks.
        val lines = listOf(
            OcrLine("TOMATOES", 10, 100, 200, 130),
            OcrLine("0.99", 400, 102, 460, 131),
            OcrLine("CHEDDAR CHEESE", 10, 140, 260, 170),
            OcrLine("3.49", 400, 141, 460, 171),
            OcrLine("TOTAL", 10, 200, 100, 230),
            OcrLine("4.48", 400, 200, 460, 230),
        )
        val items = ReceiptParser.parseLines(lines)
        assertEquals(listOf("Tomatoes", "Cheddar Cheese"), items.map { it.name })
    }

    @Test fun acceptsPlainListsWithoutPrices() {
        val items = ReceiptParser.parseText("Eggs\nSpinach\nPaneer 250g")
        assertEquals(3, items.size)
        assertTrue(items.any { it.name == "Paneer" && it.unit == "g" })
    }
}
