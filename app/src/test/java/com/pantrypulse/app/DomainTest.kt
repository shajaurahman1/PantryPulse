package com.pantrypulse.app

import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.data.remote.FoodRescueClient
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.CategoryGuesser
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ExpiryStatus
import com.pantrypulse.app.domain.HeadlineBuilder
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.RecipeMatcher
import com.pantrypulse.app.notifications.ExpiryNotifier
import com.pantrypulse.app.ui.components.stubFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    private val today = LocalDate.of(2026, 11, 13) // a Friday
    private fun product(name: String, days: Long?, category: Category = Category.OTHER) =
        ProductEntity(householdId = 1, name = name, addedBy = 1, category = category, expiryDate = days?.let { today.plusDays(it) })

    @Test fun expiryStatuses() {
        assertEquals(ExpiryStatus.EXPIRED, Expiry.status(today.minusDays(1), 3, today))
        assertEquals(ExpiryStatus.TODAY, Expiry.status(today, 3, today))
        assertEquals(ExpiryStatus.SOON, Expiry.status(today.plusDays(3), 3, today))
        assertEquals(ExpiryStatus.FRESH, Expiry.status(today.plusDays(4), 3, today))
        assertEquals(ExpiryStatus.NO_DATE, Expiry.status(null, 3, today))
    }

    @Test fun expiryLabels() {
        assertEquals("Expires today", Expiry.label(today, today))
        assertEquals("Expires tomorrow", Expiry.label(today.plusDays(1), today))
        assertEquals("Expired 3 days ago", Expiry.label(today.minusDays(3), today))
        assertEquals("5 days left", Expiry.label(today.plusDays(5), today))
    }

    @Test fun ticketStub() {
        assertEquals("0" to "DAYS", stubFor(today, today))
        assertEquals("1" to "DAY", stubFor(today.plusDays(1), today))
        assertEquals("2" to "DAYS AGO", stubFor(today.minusDays(2), today))
        assertEquals("3" to "MONTHS", stubFor(today.plusDays(95), today))
        assertEquals("—" to "NO DATE", stubFor(null, today))
    }

    @Test fun headlineMatchesDesign() {
        val items = listOf(product("Spinach", 0), product("Yogurt", 1), product("Paneer", 2), product("Milk", 3), product("Rice", 200), product("Old bread", -2))
        val h = HeadlineBuilder.build(items, warningDays = 7, today = today)
        assertEquals(4, h.count)
        assertEquals("things want using up before next Friday.", h.text)
        assertEquals("1 item is already past its date.", h.expiredNote)
        val h3 = HeadlineBuilder.build(items, warningDays = 3, today = today)
        assertEquals("things want using up before Monday.", h3.text)
    }

    @Test fun headlineSingularAndEmpty() {
        assertEquals("thing wants using up before tomorrow.", HeadlineBuilder.build(listOf(product("Milk", 1)), 1, today).text)
        val empty = HeadlineBuilder.build(emptyList(), 3, today)
        assertEquals(0, empty.count)
        assertNull(empty.expiredNote)
    }

    @Test fun weekCounts() {
        val counts = HeadlineBuilder.weekCounts(listOf(product("a", 0), product("b", 0), product("c", 2), product("d", 9)), today)
        assertEquals(listOf(2, 0, 1, 0, 0, 0, 0), counts)
    }

    @Test fun categoryGuessing() {
        assertEquals(Category.DAIRY, CategoryGuesser.guess("Amul Taaza Milk"))
        assertEquals(Category.MEDICINE, CategoryGuesser.guess("Paracetamol 500mg tablets"))
        assertEquals(ItemKind.COSMETICS, CategoryGuesser.guess("SPF 50 Sunscreen").kind)
        assertEquals(Category.EGGS, CategoryGuesser.guess("Free range eggs x12"))
        assertEquals(Category.OTHER, CategoryGuesser.guess("Mystery box"))
    }

    @Test fun recipesPreferExpiringFood() {
        val s = RecipeMatcher.suggest(RecipeMatcher.builtIn, listOf("Bananas", "Eggs", "Milk", "Rice"), listOf("Bananas"))
        assertTrue(s.isNotEmpty())
        assertTrue(s.first().usesExpiring.contains("banana"))
        assertEquals("chicken", RecipeMatcher.ingredientKeyword("Chicken thighs 1kg"))
    }

    @Test fun notificationText() {
        val now = LocalDate.now()
        val (title, body) = ExpiryNotifier.content(listOf(
            ProductEntity(householdId = 1, name = "Milk", addedBy = 1, expiryDate = now.plusDays(1)),
            ProductEntity(householdId = 1, name = "Bread", addedBy = 1, expiryDate = now.minusDays(1)),
        ))
        assertEquals("1 item expiring soon, 1 expired", title)
        assertTrue(body.contains("Milk"))
    }

    @Test fun distance() {
        val d = FoodRescueClient.distance(51.5007, -0.1246, 51.5033, -0.1196)
        assertTrue(d in 400.0..500.0)
    }
}
