package com.pantrypulse.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pantrypulse.app.data.community.ClaimMode
import com.pantrypulse.app.data.community.CommunityJson
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.ui.community.deadlineLabel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Parses responses captured from the real backend (backend/test/fixtures.mjs). */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [34])
class CommunityJsonTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/community/$name.json")!!.readText())

    @Test fun shopAccount() {
        val u = CommunityJson.user(fixture("auth").getJSONObject("user"))
        assertTrue(u.isShop)
        assertEquals("Fresh Mart", u.shopName)
        assertEquals("8am–9pm", u.openingHours)
        assertEquals(12.9716, u.lat!!, 1e-9)
    }

    @Test fun publicFeedHasNoPrivateDetails() {
        val feed = CommunityJson.listings(fixture("nearby"))
        assertEquals(2, feed.size)
        val bread = feed.first { it.title == "Sourdough loaf" }
        assertEquals(ClaimMode.APPROVAL, bread.claimMode)
        assertEquals(ListingStatus.AVAILABLE, bread.status)
        assertTrue(bread.donorIsShop)
        assertEquals("Fresh Mart", bread.donorName)
        assertNull("no exact pickup in the public feed", bread.pickup)
        assertEquals("under 1 km", bread.distanceLabel)
        assertFalse(bread.isMine)
    }

    @Test fun donorSeesRequestQueue() {
        val mine = CommunityJson.listings(fixture("mine_with_request"))
        val bread = mine.first { it.title == "Sourdough loaf" }
        assertTrue(bread.isMine)
        assertEquals(1, bread.pendingRequests.size)
        assertEquals("Ravi", bread.pendingRequests.single().name)
        assertEquals("Can collect at 6", bread.pendingRequests.single().message)
        assertEquals(42L, bread.clientProductId)
        assertEquals("5 Market St", bread.pickup!!.address)
    }

    @Test fun confirmedClaimRevealsAddressAndCode() {
        val claim = CommunityJson.listings(fixture("claims_reserved")).single()
        assertTrue(claim.claimedByMe)
        assertEquals(ListingStatus.RESERVED, claim.status)
        assertEquals("5 Market St", claim.pickup!!.address)
        assertEquals("Ask at the till", claim.pickup!!.instructions)
        assertTrue(claim.pickup!!.code!!.matches(Regex("\\d{4}")))
        assertEquals("accepted", claim.myRequestStatus)
    }

    @Test fun eventsAndStats() {
        val events = CommunityJson.events(fixture("events"))
        assertTrue(events.any { it.type == "accepted" && it.message.contains("Sourdough loaf") })
        val stats = CommunityJson.stats(fixture("stats"))
        assertEquals(1, stats.available)
        assertEquals(1, stats.reserved)
    }

    @Test fun deadlineLabels() {
        val zone = ZoneId.of("Asia/Kolkata")
        val today = LocalDate.of(2026, 11, 13)
        val at = { d: LocalDate, h: Int -> d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli() }
        assertEquals("today 8:00 pm", deadlineLabel(at(today, 20), zone, today))
        assertEquals("tomorrow 12:00 pm", deadlineLabel(at(today.plusDays(1), 12), zone, today))
        assertEquals("Mon 16 Nov, 9:00 am", deadlineLabel(at(today.plusDays(3), 9), zone, today))
    }
}
