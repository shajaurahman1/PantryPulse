package com.pantrypulse.app.domain

import com.pantrypulse.app.data.db.ProductEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The big "4 things want using up before Friday." headline on the home screen. */
data class Headline(val count: Int, val text: String, val expiredNote: String?)

object HeadlineBuilder {
    fun build(products: List<ProductEntity>, warningDays: Int, today: LocalDate = LocalDate.now()): Headline {
        val soon = products.count {
            val s = Expiry.status(it.expiryDate, warningDays, today)
            s == ExpiryStatus.TODAY || s == ExpiryStatus.SOON
        }
        val expired = products.count { Expiry.status(it.expiryDate, warningDays, today) == ExpiryStatus.EXPIRED }
        val deadline = today.plusDays(warningDays.toLong())
        val whenText = when (warningDays) {
            0 -> "today"
            1 -> "tomorrow"
            in 2..6 -> deadline.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> "next ${deadline.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
        }
        val text = when (soon) {
            0 -> "things need rescuing before $whenText."
            1 -> "thing wants using up before $whenText."
            else -> "things want using up before $whenText."
        }
        val note = when (expired) {
            0 -> null
            1 -> "1 item is already past its date."
            else -> "$expired items are already past their date."
        }
        return Headline(soon, text, note)
    }

    /** Items expiring on each of the next seven days, starting today. */
    fun weekCounts(products: List<ProductEntity>, today: LocalDate = LocalDate.now()): List<Int> =
        (0..6).map { offset -> products.count { it.expiryDate == today.plusDays(offset.toLong()) } }
}
