package com.pantrypulse.app

import android.content.Context
import com.pantrypulse.app.data.community.AuthResult
import com.pantrypulse.app.data.community.ClaimMode
import com.pantrypulse.app.data.community.ClaimRequest
import com.pantrypulse.app.data.community.CommunityApi
import com.pantrypulse.app.data.community.CommunityEvent
import com.pantrypulse.app.data.community.CommunityException
import com.pantrypulse.app.data.community.CommunityUser
import com.pantrypulse.app.data.community.DonorStats
import com.pantrypulse.app.data.community.LatLon
import com.pantrypulse.app.data.community.Listing
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.data.community.LocationProvider
import com.pantrypulse.app.data.community.NewListing
import com.pantrypulse.app.data.community.Pickup
import com.pantrypulse.app.data.community.ShopProfile
import java.util.UUID

/** In-memory stand-in for the backend, with the same claim rules, for UI tests. */
class FakeCommunityApi : CommunityApi {
    val listings = mutableListOf<Listing>()
    var me: CommunityUser? = null
    val created = mutableListOf<NewListing>()

    fun seed(title: String, mode: ClaimMode = ClaimMode.DIRECT, km: Double = 1.5, shop: Boolean = false, hours: Double = 5.0) {
        listings += Listing(
            id = UUID.randomUUID().toString(), title = title, category = "BAKERY", quantityText = "1 loaf", notes = "Unopened",
            itemExpiry = null, pickupDeadline = System.currentTimeMillis() + (hours * 3_600_000).toLong(), areaLabel = "Near City Library",
            claimMode = mode, status = ListingStatus.AVAILABLE, donorName = if (shop) "Fresh Mart" else "Asha", donorIsShop = shop,
            donorOpeningHours = if (shop) "8am–9pm" else null, approxLat = 12.97, approxLon = 77.59, distanceKm = km,
            distanceLabel = "about $km km", isMine = false, claimedByMe = false, myRequestStatus = null, pickup = null,
            claimedByName = null, requests = emptyList(), clientProductId = null,
        )
    }

    private fun user(email: String, name: String, shop: ShopProfile?) = CommunityUser(
        "u1", email, name, shop != null, shop?.shopName, shop?.shopAddress, shop?.openingHours, shop?.lat, shop?.lon,
    )

    override suspend fun connect(email: String, password: String, name: String, shop: ShopProfile?): AuthResult {
        me = me?.takeIf { it.email == email } ?: user(email, name, shop)
        return AuthResult("token-$email", me!!)
    }

    override suspend fun me(token: String) = me!!
    override suspend fun updateProfile(token: String, isShop: Boolean, shop: ShopProfile?, name: String?): CommunityUser {
        me = me!!.copy(isShop = isShop, shopName = shop?.shopName, shopAddress = shop?.shopAddress, openingHours = shop?.openingHours, lat = shop?.lat, lon = shop?.lon)
        return me!!
    }

    private fun fromDraft(d: NewListing) = Listing(
        UUID.randomUUID().toString(), d.title, d.category, d.quantityText, d.notes, d.itemExpiry, d.pickupDeadline, d.areaLabel,
        d.claimMode, ListingStatus.AVAILABLE, me!!.shopName ?: me!!.name, me!!.isShop, me!!.openingHours, d.lat!!, d.lon!!, null, null,
        isMine = true, claimedByMe = false, myRequestStatus = null, pickup = Pickup(d.lat, d.lon, d.pickupAddress, d.pickupInstructions, null),
        claimedByName = null, requests = emptyList(), clientProductId = d.clientProductId,
    )

    override suspend fun createListing(token: String, listing: NewListing): Listing {
        created += listing
        return fromDraft(listing).also { listings += it }
    }
    override suspend fun createBatch(token: String, listings: List<NewListing>) = listings.map { createListing(token, it) }

    override suspend fun nearby(token: String, lat: Double, lon: Double, radiusKm: Double, sortByUrgency: Boolean) =
        listings.filter { !it.isMine && it.status == ListingStatus.AVAILABLE && (it.distanceKm ?: 0.0) <= radiusKm }
            .sortedBy { if (sortByUrgency) it.pickupDeadline.toDouble() else it.distanceKm ?: 0.0 }

    private fun find(id: String) = listings.indexOfFirst { it.id == id }.also { if (it < 0) throw CommunityException(404, "not_found", "Gone") }
    private fun update(id: String, f: (Listing) -> Listing): Listing { val i = find(id); listings[i] = f(listings[i]); return listings[i] }

    override suspend fun listing(token: String, id: String) = listings[find(id)]
    override suspend fun myListings(token: String) = listings.filter { it.isMine }
    override suspend fun myClaims(token: String) = listings.filter { it.claimedByMe || it.myRequestStatus != null }

    override suspend fun claim(token: String, id: String): Listing {
        val l = listings[find(id)]
        if (l.status != ListingStatus.AVAILABLE) throw CommunityException(409, "already_claimed", "Sorry — someone else just claimed this")
        if (l.claimMode != ClaimMode.DIRECT) throw CommunityException(400, "approval_required", "Send a request instead")
        return update(id) {
            it.copy(status = ListingStatus.RESERVED, claimedByMe = true, pickup = Pickup(12.9716, 77.5946, "12 Residency Road, Flat 4B", "Ring the bell", "4821"))
        }
    }
    override suspend fun request(token: String, id: String, message: String) = update(id) { it.copy(myRequestStatus = "pending") }
    override suspend fun withdrawRequest(token: String, id: String) = update(id) { it.copy(myRequestStatus = "withdrawn") }
    override suspend fun acceptRequest(token: String, id: String, requestId: String) =
        update(id) { it.copy(status = ListingStatus.RESERVED, claimedByName = it.requests.first { r -> r.id == requestId }.name,
            pickup = it.pickup?.copy(code = "7305"), requests = it.requests.map { r -> r.copy(status = if (r.id == requestId) "accepted" else "rejected") }) }
    override suspend fun rejectRequest(token: String, id: String, requestId: String) =
        update(id) { it.copy(requests = it.requests.map { r -> if (r.id == requestId) r.copy(status = "rejected") else r }) }
    override suspend fun release(token: String, id: String, newDeadline: Long?) = update(id) { it.copy(status = ListingStatus.AVAILABLE, claimedByName = null) }
    override suspend fun cancelClaim(token: String, id: String) { update(id) { it.copy(status = ListingStatus.AVAILABLE, claimedByMe = false, pickup = null) } }
    override suspend fun markPickedUp(token: String, id: String) = update(id) { it.copy(status = ListingStatus.PICKED_UP) }
    override suspend fun cancelListing(token: String, id: String) = update(id) { it.copy(status = ListingStatus.CANCELLED) }
    override suspend fun events(token: String, after: Long) = emptyList<CommunityEvent>()
    override suspend fun stats(token: String) = DonorStats(
        listings.count { it.isMine && it.status == ListingStatus.AVAILABLE }, listings.count { it.isMine && it.status == ListingStatus.RESERVED },
        listings.count { it.isMine && it.status == ListingStatus.PICKED_UP }, 0, listings.filter { it.isMine }.sumOf { l -> l.requests.count { it.status == "pending" } },
    )

    /** Adds a pending request from someone else to one of my listings. */
    fun addRequest(title: String, from: String, message: String) {
        val i = listings.indexOfFirst { it.title == title && it.isMine }
        listings[i] = listings[i].copy(requests = listings[i].requests + ClaimRequest(UUID.randomUUID().toString(), from, message, "pending", System.currentTimeMillis()))
    }
}

class FixedLocation(private val point: LatLon = LatLon(12.9716, 77.5946)) : LocationProvider {
    override fun hasPermission(context: Context) = true
    override suspend fun current(context: Context) = point
}
