package com.pantrypulse.app

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Category
import kotlinx.coroutines.flow.first
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

/**
 * Drives the real app UI end to end: sign up, see the pantry, use an item until it is
 * finished, move it to the shopping list in one tap, and visit every tab. Screenshots of
 * each screen are written to app/build/screenshots for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as TestApp).container

    @Before fun signedOut() = runBlocking { container.session.signOut() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 10_000)

    @Test fun fullJourney() {
        // --- Sign up -------------------------------------------------------------------
        waitFor("Create account")
        shot("01_sign_in")
        rule.onNodeWithTag("segment_Create account").performClick()
        rule.onNodeWithTag("field_Your name").performTextInput("Priya Sharma")
        rule.onNodeWithTag("field_Email").performTextInput("priya@example.com")
        rule.onNodeWithTag("field_Password").performTextInput("secret12")
        rule.onNodeWithTag("button_Create account").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("headline"), 15_000)

        // --- Seed a realistic pantry like the design ----------------------------------
        runBlocking {
            val s = container.session.current()
            val hh = s.householdId!!
            val locs = container.pantry.locations(hh).associateBy { it.name }
            val today = LocalDate.now()
            fun p(name: String, days: Long?, qty: Double, unit: String, cat: Category, loc: String) = ProductEntity(
                householdId = hh, name = name, quantity = qty, unit = unit, category = cat, addedBy = s.userId!!,
                locationId = locs[loc]?.id, expiryDate = days?.let { today.plusDays(it) },
            )
            container.pantry.saveAll(listOf(
                p("Spinach", 0, 200.0, "g", Category.VEGETABLES, "Fridge"),
                p("Greek yogurt", 1, 2.0, "tubs", Category.DAIRY, "Fridge"),
                p("Paneer", 2, 250.0, "g", Category.DAIRY, "Fridge"),
                p("Whole milk", 3, 1.0, "L", Category.DAIRY, "Fridge"),
                p("Tomatoes", 3, 6.0, "pcs", Category.VEGETABLES, "Fridge"),
                p("Frozen peas", 60, 1.0, "bag", Category.FROZEN, "Freezer"),
                p("Basmati rice", 300, 1.0, "kg", Category.GRAINS, "Pantry"),
                p("Sourdough", -1, 1.0, "loaf", Category.BAKERY, "Counter"),
                p("Sunscreen SPF 50", 120, 1.0, "bottle", Category.SKINCARE, "Bathroom"),
                p("Ibuprofen", 200, 1.0, "strip", Category.MEDICINE, "Medicine cabinet"),
            ))
        }
        rule.waitUntilAtLeastOneExists(hasTestTag("product_Spinach"), 10_000)
        shot("02_home")

        // Headline counts the 5 items expiring within the 3-day window.
        rule.onNodeWithTag("headline").assertExists()
        rule.onNodeWithText("1 item is already past its date.").assertExists()

        // Kind tabs filter the list.
        rule.onNodeWithTag("segment_Medicine").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("product_Ibuprofen"), 5_000)
        rule.onNodeWithTag("product_Spinach").assertDoesNotExist()
        shot("03_home_medicine")
        rule.onNodeWithTag("segment_All").performClick()

        // --- Use the yogurt until it's finished, then one tap to the shopping list -----
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("product_Greek yogurt"))
        rule.onNodeWithContentDescription("Use one Greek yogurt").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Use one Greek yogurt").performClick()
        waitFor("Add to list")
        shot("04_finished_snackbar")
        rule.onNodeWithText("Add to list").performClick()
        rule.waitUntil(10_000) {
            runBlocking { container.shopping.observe(container.session.current().householdId!!).first().any { it.name == "Greek yogurt" } }
        }

        // --- Item actions sheet --------------------------------------------------------
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("product_Paneer"))
        rule.onNodeWithTag("product_Paneer").performClick()
        waitFor("Give away nearby")
        shot("05_item_actions")
        rule.onNodeWithTag("action_Mark as finished").performClick()
        waitFor("Paneer is finished.")

        // --- Shopping tab --------------------------------------------------------------
        rule.onNodeWithTag("nav_shopping").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("shopping_Greek yogurt"), 10_000)
        rule.onNodeWithTag("field_Add an item").performTextInput("Eggs")
        rule.onNodeWithContentDescription("Add to shopping list").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("shopping_Eggs"), 5_000)
        rule.onNodeWithTag("shopping_Eggs").performClick()
        waitFor("Move 1 bought to pantry")
        shot("06_shopping")
        rule.onNodeWithText("Move 1 bought to pantry").performClick()
        rule.waitUntil(10_000) {
            runBlocking { container.pantry.observeActive(container.session.current().householdId!!).first().any { it.name == "Eggs" } }
        }

        // --- Recipes tab ---------------------------------------------------------------
        rule.onNodeWithTag("nav_recipes").performClick()
        waitFor("Cook it before it goes.")
        waitFor("From your pantry")
        shot("07_recipes")

        // --- Add item editor -----------------------------------------------------------
        rule.onNodeWithTag("nav_add").performClick()
        waitFor("Type it in")
        shot("08_add_sheet")
        rule.onNodeWithTag("action_Type it in").performClick()
        waitFor("Add to pantry")
        rule.onNodeWithTag("field_Name").performTextInput("Cheddar")
        rule.onNodeWithTag("field_Name").performImeAction()
        rule.onNodeWithTag("button_Add to pantry").performScrollTo()
        rule.onNodeWithTag("chip_+1 week").performClick()
        rule.waitForIdle()
        shot("09a_after_week")
        rule.onNodeWithText("7 days left").assertExists()
        shot("09_editor")
        rule.onNodeWithTag("button_Add to pantry").performScrollTo().performClick()
        shot("09b_after_save")
        rule.waitUntil(10_000) {
            runBlocking { container.pantry.observeActive(container.session.current().householdId!!).first().any { it.name == "Cheddar" } }
        }
        val cheddar = runBlocking { container.pantry.observeActive(container.session.current().householdId!!).first().first { it.name == "Cheddar" } }
        assertEquals(LocalDate.now().plusWeeks(1), cheddar.expiryDate)

        // --- Profile tab ---------------------------------------------------------------
        rule.onNodeWithTag("nav_profile").performClick()
        waitFor("Invite code")
        shot("10_profile")

        // Sign out returns to the sign-in screen with the profile listed for quick pick.
        rule.onNodeWithTag("nav_profile").assertExists()
        runBlocking { container.auth.signOut() }
        waitFor("PROFILES ON THIS DEVICE")
        assertTrue(runBlocking { !container.session.current().isSignedIn })
    }
}
