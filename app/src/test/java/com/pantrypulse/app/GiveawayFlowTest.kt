package com.pantrypulse.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pantrypulse.app.data.community.ClaimMode
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Giveaway journeys against the in-memory community backend, with screenshots. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class GiveawayFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as TestApp
    private val fake get() = app.fakeCommunity

    @Before fun signedOut() = runBlocking { app.container.session.signOut() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").mkdirs()
        File("build/screenshots/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    private fun signUp(name: String, email: String, shop: String? = null) {
        waitFor("Create account")
        rule.onNodeWithTag("segment_Create account").performClick()
        rule.onNodeWithTag("field_Your name").performTextInput(name)
        if (shop != null) {
            rule.onNodeWithTag("shop_switch").performClick()
            rule.onNodeWithTag("field_Shop name").performTextInput(shop)
            rule.onNodeWithTag("field_Shop address").performTextInput("5 Market St")
            rule.onNodeWithTag("field_Opening hours, e.g. 8am–9pm").performTextInput("8am–9pm")
        }
        rule.onNodeWithTag("field_Email").performScrollTo().performTextInput(email)
        rule.onNodeWithTag("field_Password").performScrollTo().performTextInput("secret12")
        rule.onNodeWithTag("button_Create account").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("headline"), 15_000)
        rule.waitUntil(10_000) { runBlocking { app.container.community.current().connected } }
    }

    @Test fun claimNearbyAndGiveAwayFromPantry() {
        fake.seed("Free sourdough", km = 1.5)
        fake.seed("Bananas", km = 4.0, shop = true, hours = 2.0)
        fake.seed("Lentil soup", mode = ClaimMode.APPROVAL, km = 0.5)
        fake.seed("Far-away rice", km = 20.0)
        signUp("Priya Sharma", "priya@example.com")

        // --- Nearby feed ------------------------------------------------------------
        rule.onNodeWithTag("nav_giveaways").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("giveaway_Free sourdough"), 10_000)
        rule.onNodeWithTag("giveaway_Far-away rice").assertDoesNotExist()
        shot("20_giveaways_nearby")
        rule.onNodeWithTag("chip_25 km").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("giveaway_Far-away rice"), 5_000)
        rule.onNodeWithTag("chip_5 km").performClick()

        // --- Claim a first-come item ------------------------------------------------
        rule.onNodeWithTag("giveaway_Free sourdough").performClick()
        waitFor("Claim this")
        shot("21_giveaway_sheet")
        rule.onNodeWithTag("button_Claim this").performClick()
        waitFor("12 Residency Road")
        rule.onNodeWithText("4821").assertExists()
        shot("22_claimed_pickup_details")

        // --- Request an approval-only item ------------------------------------------
        rule.onNodeWithTag("segment_Nearby").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("giveaway_Lentil soup"), 5_000)
        rule.onNodeWithTag("giveaway_Lentil soup").performClick()
        waitFor("Request this")
        rule.onNodeWithTag("button_Request this").performClick()
        rule.waitUntil(5_000) { fake.listings.first { it.title == "Lentil soup" }.myRequestStatus == "pending" }

        // --- Give away a pantry item with "Require my approval" ----------------------
        val milkId = runBlocking {
            val s = app.container.session.current()
            app.container.pantry.save(ProductEntity(householdId = s.householdId!!, name = "Whole milk", quantity = 1.0, unit = "L",
                category = Category.DAIRY, addedBy = s.userId!!, expiryDate = LocalDate.now().plusDays(1)))
        }
        rule.onNodeWithTag("nav_home").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("product_Whole milk"), 5_000)
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("product_Whole milk"))
        rule.onNodeWithTag("product_Whole milk").performClick()
        waitFor("Give away nearby")
        rule.onNodeWithTag("action_Give away nearby").performClick()
        waitFor("Pass on your Whole milk")
        rule.onNodeWithTag("chip_Unopened").performClick()
        rule.onNodeWithTag("button_Use my location").performScrollTo().performClick()
        waitFor("Location set")
        rule.onNodeWithTag("field_Area shown publicly, e.g. Near City Library").performScrollTo().performTextInput("Near MG Road metro")
        rule.onNodeWithTag("field_Exact address (private)").performScrollTo().performTextInput("12 Residency Road")
        rule.onNodeWithTag("mode_Require my approval").performScrollTo().performClick()
        shot("23_give_away_form")
        rule.onNodeWithTag("button_Post giveaway").performScrollTo().performClick()
        rule.waitUntil(10_000) { fake.created.any { it.title == "Whole milk" } }
        val posted = fake.created.first { it.title == "Whole milk" }
        assertEquals(ClaimMode.APPROVAL, posted.claimMode)
        assertEquals(milkId, posted.clientProductId)
        assertEquals("Unopened", posted.notes)
        assertEquals("12 Residency Road", posted.pickupAddress)

        // --- Donor accepts one request from the queue --------------------------------
        fake.addRequest("Whole milk", "Ravi", "Can collect at 6")
        fake.addRequest("Whole milk", "Meera", "I'm next door")
        rule.waitUntilAtLeastOneExists(hasTestTag("giveaways_list"), 10_000)
        rule.onNodeWithTag("segment_Giving").performClick()
        rule.onNodeWithContentDescription("Refresh giveaways").performClick()
        waitFor("2 REQUESTS — ACCEPT ONE")
        rule.onNodeWithText("Can collect at 6", substring = true).assertExists()
        shot("24_request_queue")
        // Accept Ravi: the listing is reserved and Meera's request is closed automatically.
        rule.onAllNodesWithTag("button_Accept")[0].performClick()
        rule.waitUntil(5_000) { fake.listings.first { it.title == "Whole milk" }.status == ListingStatus.RESERVED }
        val milk = fake.listings.first { it.title == "Whole milk" }
        assertEquals(listOf("accepted", "rejected"), milk.requests.map { it.status })
        waitFor("Ravi is collecting this")
        shot("25_reserved_for_ravi")
    }

    @Test fun shopkeeperPostsSurplus() {
        signUp("Asha Rao", "asha@freshmart.example", shop = "Fresh Mart")
        assertTrue(runBlocking { app.container.community.current().isShop })
        runBlocking {
            val s = app.container.session.current()
            listOf("Croissants" to 1L, "Yogurt cups" to 2L, "Rice 5kg" to 200L).forEach { (n, d) ->
                app.container.pantry.save(ProductEntity(householdId = s.householdId!!, name = n, addedBy = s.userId!!,
                    category = Category.BAKERY, expiryDate = LocalDate.now().plusDays(d)))
            }
        }
        rule.onNodeWithTag("nav_giveaways").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("segment_My shop"), 10_000)
        rule.onNodeWithTag("segment_My shop").performClick()
        waitFor("Shop dashboard")
        shot("26_shop_dashboard")
        rule.onNodeWithTag("button_Post surplus items").performClick()
        waitFor("Post today's surplus")
        // Items expiring within two days are pre-selected.
        waitFor("Post 2 items")
        rule.onNodeWithTag("field_Item, e.g. Day-old croissants").performScrollTo().performTextInput("Day-old bagels")
        rule.onNodeWithTag("button_Add").performScrollTo().performClick()
        waitFor("Post 3 items")
        rule.onNodeWithTag("button_Use my location").performScrollTo().performClick()
        waitFor("Location set")
        shot("27_shop_post_surplus")
        rule.onNodeWithTag("button_Post 3 items").performScrollTo().performClick()
        rule.waitUntil(10_000) { fake.created.size == 3 }
        assertEquals(setOf("Croissants", "Yogurt cups", "Day-old bagels"), fake.created.map { it.title }.toSet())
        assertTrue("shop address is the default pickup address", fake.created.all { it.pickupAddress == "5 Market St" })
        assertTrue(fake.listings.all { it.status == ListingStatus.AVAILABLE })
        waitFor("Giveaways")
        shot("28_shop_live_listings")
    }
}
