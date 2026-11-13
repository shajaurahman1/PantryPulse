package com.pantrypulse.app.domain

import kotlin.math.max
import kotlin.math.min

/** A line of OCR text with its position on the image. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = max(1, bottom - top)
}

data class ReceiptItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double?,
    val category: Category,
)

/**
 * Turns the text of a supermarket receipt (paper photo or screenshot of a digital one)
 * into a list of grocery items.
 *
 * Receipt OCR usually returns the item name and its price as separate text blocks on
 * the same row, so lines are first re-joined into rows by vertical position. Rows that
 * are totals, payment, tax or store details are dropped; the rest become items.
 */
object ReceiptParser {

    private val skipWords = Regex(
        "\\b(SUB ?TOTAL|TOTAL|TAX|VAT|GST|CGST|SGST|CASH|CHANGE|CARD|VISA|MASTERCARD|AMEX|DEBIT|CREDIT|" +
            "BALANCE|DISCOUNT|SAVINGS|SAVED|YOU SAVED|THANK|THANKS|TEL|PHONE|WWW|HTTP|RECEIPT|INVOICE|" +
            "DATE|TIME|STORE|CASHIER|TILL|TERMINAL|AUTH|APPROVED|ITEMS?|QTY|AMOUNT|PRICE|ROUNDING|" +
            "LOYALTY|POINTS|MEMBER|ORDER|TRANSACTION|REF|TXN|GSTIN|BILL|SUBTOTAL|NET|DUE|PAID|" +
            "TENDER|REFUND|COUPON|VOUCHER|WELCOME|ADDRESS|EMAIL|VAT NO|REG|SIGNATURE|CUSTOMER)\\b"
    )
    private val priceAtEnd = Regex("(-?\\d{1,5}[.,]\\d{2})\\s*[A-Z*]{0,2}\\s*$")
    private val qtyPrefix = Regex("^(\\d{1,3})\\s*[xX@*]\\s+")
    private val qtySuffix = Regex("\\s+[xX]\\s*(\\d{1,3})$")
    private val qtyAtPrice = Regex("^(\\d{1,3})\\s*[@xX]\\s*\\d+[.,]\\d{2}")
    private val weight = Regex("(\\d+(?:[.,]\\d+)?)\\s*(KG|G|GM|GMS|L|LTR|ML|LB|OZ)\\b")
    private val longCode = Regex("\\b\\d{5,}\\b")
    private val leadingCode = Regex("^[#*]?\\d{1,4}\\s+(?=[A-Z])")

    fun groupIntoRows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val sameRow = row != null && row.any { other ->
                val overlap = min(other.bottom, line.bottom) - max(other.top, line.top)
                overlap > min(other.height, line.height) * 0.5
            }
            if (sameRow) row!!.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }

    fun parseLines(lines: List<OcrLine>): List<ReceiptItem> = parseRows(groupIntoRows(lines))

    fun parseText(text: String): List<ReceiptItem> = parseRows(text.lines())

    fun parseRows(rows: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val anyPrices = rows.any { priceAtEnd.containsMatchIn(it.uppercase().trim()) }
        var pendingQty: Double? = null
        for (raw in rows) {
            val row = raw.uppercase().replace(Regex("\\s+"), " ").trim()
            if (row.length < 3) continue

            // "2 @ 1.50" rows on their own line apply to the item that follows (or precedes).
            val qtyLine = qtyAtPrice.find(row)
            if (qtyLine != null && row.count { it.isLetter() } < 3) {
                val q = qtyLine.groupValues[1].toDouble()
                if (items.isNotEmpty() && items.last().quantity == 1.0) {
                    items[items.lastIndex] = items.last().copy(quantity = q)
                } else {
                    pendingQty = q
                }
                continue
            }

            if (skipWords.containsMatchIn(row)) continue

            val priceMatch = priceAtEnd.find(row)
            val price = priceMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            if (price != null && price < 0) continue // discounts / refunds
            var name = if (priceMatch != null) row.substring(0, priceMatch.range.first) else row

            var quantity = pendingQty ?: 1.0
            pendingQty = null
            var unit = "pcs"
            qtyPrefix.find(name)?.let { quantity = it.groupValues[1].toDouble(); name = name.removeRange(it.range) }
            qtySuffix.find(name)?.let { quantity = it.groupValues[1].toDouble(); name = name.removeRange(it.range) }
            weight.find(name)?.let {
                val amount = it.groupValues[1].replace(',', '.').toDoubleOrNull()
                val u = it.groupValues[2]
                if (amount != null && amount > 0) {
                    when (u) {
                        "KG", "LB" -> { quantity = amount; unit = u.lowercase() }
                        "L", "LTR" -> { quantity = amount; unit = "L" }
                        "G", "GM", "GMS" -> { quantity = amount; unit = "g" }
                        "ML" -> { quantity = amount; unit = "ml" }
                        "OZ" -> { quantity = amount; unit = "oz" }
                    }
                }
                name = name.removeRange(it.range)
            }

            name = name.replace(longCode, " ")
                .replace(leadingCode, "")
                .replace(Regex("[^A-Z0-9&'%/ \\-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', '/')

            val letters = name.count { it.isLetter() }
            if (letters < 3) continue
            if (price == null) {
                // On a real receipt every item row has a price; rows without one are
                // headers, addresses or wrapped text. Plain lists with no prices at all
                // (a typed note, a digital order summary) are accepted row by row.
                if (anyPrices) continue
                if (name.split(' ').size > 6) continue
            }

            val pretty = prettify(name)
            items += ReceiptItem(pretty, quantity, unit, price, CategoryGuesser.guess(pretty))
        }
        return items
    }

    private fun prettify(name: String): String = name.lowercase().split(' ').joinToString(" ") { word ->
        if (word.length <= 2 && word.all { it.isLetter() } && word !in setOf("of", "in", "a")) word.uppercase()
        else word.replaceFirstChar { it.titlecase() }
    }
}
