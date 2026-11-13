package com.pantrypulse.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pantrypulse.app.data.remote.FoodRescueClient
import com.pantrypulse.app.data.remote.OpenFoodFactsClient
import com.pantrypulse.app.data.repo.AuthException
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.HouseholdType
import com.pantrypulse.app.domain.ProductStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [34])
class RepositoryTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val c get() = app.container

    @Test fun signUpCreatesHouseholdWithStorageSpots() = runBlocking {
        val user = c.auth.signUp("Priya Sharma", "priya@example.com", "secret1")
        val session = c.session.current()
        assertEquals(user.id, session.userId)
        val household = c.db.householdDao().findById(session.householdId!!)!!
        assertEquals("Priya's Home", household.name)
        assertEquals(6, household.inviteCode.length)
        assertEquals(listOf("Fridge", "Freezer", "Pantry", "Counter", "Cupboard", "Bathroom", "Medicine cabinet"),
            c.pantry.locations(household.id).map { it.name })
    }

    @Test fun passwordsAreHashedAndChecked() = runBlocking {
        c.auth.signUp("Sam", "sam@example.com", "hunter22")
        val stored = c.db.userDao().findByEmail("sam@example.com")!!
        assertNotEquals("hunter22", stored.passwordHash)
        c.auth.signOut()
        try { c.auth.signIn("sam@example.com", "wrongpass"); fail() } catch (e: AuthException) { assertEquals("Incorrect password.", e.message) }
        c.auth.signIn("SAM@example.com", "hunter22")
        assertTrue(c.session.current().isSignedIn)
    }

    @Test fun duplicateEmailRejected() = runBlocking {
        c.auth.signUp("A", "a@example.com", "123456")
        try { c.auth.createProfile("B", "A@example.com", "123456"); fail() } catch (e: AuthException) { assertTrue(e.message!!.contains("already exists")) }
    }

    @Test fun householdSharingWithInviteCode() = runBlocking {
        val mum = c.auth.signUp("Mum", "mum@example.com", "123456")
        val family = c.households.createHousehold("Family", HouseholdType.FAMILY, mum.id)
        val kid = c.auth.createProfile("Kid", "kid@example.com", "123456")
        c.auth.signOut()
        c.auth.signIn("kid@example.com", "123456")
        c.households.joinByCode(kid.id, family.inviteCode.lowercase())
        assertEquals(family.id, c.session.current().householdId)
        val members = c.households.observeMembers(family.id).first()
        assertEquals(listOf("Mum", "Kid"), members.map { it.displayName })
        // Both see the same shopping list.
        c.shopping.add(family.id, "Milk", kid.id, "Kid")
        assertEquals(listOf("Milk"), c.shopping.observe(family.id).first().map { it.name })
    }

    @Test fun usingUpFinishesAndOneTapAddsToShoppingList() = runBlocking {
        val u = c.auth.signUp("Lee", "lee@example.com", "123456")
        val hh = c.session.current().householdId!!
        val id = c.pantry.save(ProductEntity(householdId = hh, name = "Greek yogurt", quantity = 2.0, unit = "tub", category = Category.DAIRY, addedBy = u.id, expiryDate = LocalDate.now().plusDays(1)))
        assertEquals(1.0, c.pantry.consume(id)!!.quantity, 0.0)
        val finished = c.pantry.consume(id)!!
        assertEquals(ProductStatus.FINISHED, finished.status)
        assertTrue(c.pantry.observeActive(hh).first().isEmpty())
        assertTrue(c.shopping.addFromProduct(finished, u.id, u.displayName))
        assertFalse("no duplicates", c.shopping.addFromProduct(finished, u.id, u.displayName))
        val item = c.shopping.observe(hh).first().single()
        assertEquals("Greek yogurt", item.name)
        assertEquals(Category.DAIRY, item.category)
        // Buying it again moves it back into the pantry with a suggested expiry date.
        c.shopping.toggle(item)
        assertEquals(1, c.shopping.moveCheckedToPantry(hh, u.id))
        val restocked = c.pantry.observeActive(hh).first().single()
        assertEquals(LocalDate.now().plusDays(7), restocked.expiryDate)
        assertEquals("Fridge", c.pantry.locations(hh).first { it.id == restocked.locationId }.name)
    }

    @Test fun expiringQuery() = runBlocking {
        val u = c.auth.signUp("Ana", "ana@example.com", "123456")
        val hh = c.session.current().householdId!!
        listOf(0L, 2L, 10L).forEach { d ->
            c.pantry.save(ProductEntity(householdId = hh, name = "Item $d", addedBy = u.id, expiryDate = LocalDate.now().plusDays(d)))
        }
        assertEquals(listOf("Item 0", "Item 2"), c.pantry.expiringWithin(hh, 3).map { it.name })
    }

    @Test fun parsesOpenFoodFacts() {
        val json = """{"status":1,"product":{"product_name":"Greek Style Yogurt","brands":"Fage, Other","categories_tags":["en:dairies","en:yogurts"],"image_front_small_url":"https://img/x.jpg","quantity":"500 g"}}"""
        val p = OpenFoodFactsClient.parse("5200435000027", json)!!
        assertEquals("Greek Style Yogurt", p.name)
        assertEquals("Fage", p.brand)
        assertEquals(Category.DAIRY, p.category)
        assertEquals("500 g", p.packageSize)
        assertEquals(null, OpenFoodFactsClient.parse("1", """{"status":0}"""))
    }

    @Test fun parsesOverpassResults() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":51.51,"lon":-0.12,"tags":{"name":"Community Fridge","amenity":"community_fridge","opening_hours":"Mo-Su 08:00-20:00"}},
            {"type":"way","id":2,"center":{"lat":51.50,"lon":-0.125},"tags":{"name":"City Food Bank","social_facility":"food_bank","addr:street":"Main St","phone":"+44 20 0000"}}
        ]}"""
        val spots = FoodRescueClient.parse(json, 51.5, -0.125)
        assertEquals(listOf("City Food Bank", "Community Fridge"), spots.map { it.name })
        assertEquals("Food bank", spots[0].kind)
        assertEquals("Mo-Su 08:00-20:00", spots[1].openingHours)
    }
}
