package com.pantrypulse.app.data.community

import org.json.JSONArray
import org.json.JSONObject

/** Online account shared with the community backend. */
data class CommunityUser(
    val id: String,
    val email: String,
    val name: String,
    val isShop: Boolean,
    val shopName: String?,
    val shopAddress: String?,
    val openingHours: String?,
    val lat: Double?,
    val lon: Double?,
)

enum class ClaimMode(val wire: String) { DIRECT("direct"), APPROVAL("approval") }

enum class ListingStatus(val wire: String, val label: String) {
    AVAILABLE("available", "Available"),
    RESERVED("reserved", "Reserved"),
    PICKED_UP("picked_up", "Picked up"),
    EXPIRED("expired", "Expired"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire } ?: CANCELLED
    }
}

/** Exact pickup details: only sent to the donor and to the person whose claim was confirmed. */
data class Pickup(val lat: Double, val lon: Double, val address: String, val instructions: String, val code: String?)

data class ClaimRequest(val id: String, val name: String, val message: String, val status: String, val createdAt: Long)

data class Listing(
    val id: String,
    val title: String,
    val category: String,
    val quantityText: String,
    val notes: String,
    val itemExpiry: String?,
    val pickupDeadline: Long,
    val areaLabel: String,
    val claimMode: ClaimMode,
    val status: ListingStatus,
    val donorName: String,
    val donorIsShop: Boolean,
    val donorOpeningHours: String?,
    val approxLat: Double,
    val approxLon: Double,
    val distanceKm: Double?,
    val distanceLabel: String?,
    val isMine: Boolean,
    val claimedByMe: Boolean,
    val myRequestStatus: String?,
    val pickup: Pickup?,
    val claimedByName: String?,
    val requests: List<ClaimRequest>,
    val clientProductId: Long?,
) {
    val pendingRequests get() = requests.filter { it.status == "pending" }
}

data class CommunityEvent(val id: Long, val type: String, val listingId: String?, val message: String, val createdAt: Long)

data class DonorStats(val available: Int, val reserved: Int, val pickedUp: Int, val expired: Int, val pendingRequests: Int)

/** Draft sent when creating a giveaway. */
data class NewListing(
    val title: String,
    val category: String,
    val quantityText: String,
    val notes: String,
    val itemExpiry: String?,
    val pickupDeadline: Long,
    val lat: Double?,
    val lon: Double?,
    val areaLabel: String,
    val pickupAddress: String,
    val pickupInstructions: String,
    val claimMode: ClaimMode,
    val clientProductId: Long?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title); put("category", category); put("quantityText", quantityText); put("notes", notes)
        itemExpiry?.let { put("itemExpiry", it) }
        put("pickupDeadline", pickupDeadline)
        lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
        put("areaLabel", areaLabel); put("pickupAddress", pickupAddress); put("pickupInstructions", pickupInstructions)
        put("claimMode", claimMode.wire)
        clientProductId?.let { put("clientProductId", it) }
    }
}

/** Parses the backend's JSON. Kept separate so it can be unit tested against real responses. */
object CommunityJson {
    private fun JSONObject.optStr(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)
    private fun JSONObject.optDbl(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)

    fun user(o: JSONObject) = CommunityUser(
        id = o.getString("id"),
        email = o.getString("email"),
        name = o.getString("name"),
        isShop = o.optString("accountType") == "shop",
        shopName = o.optStr("shopName"),
        shopAddress = o.optStr("shopAddress"),
        openingHours = o.optStr("openingHours"),
        lat = o.optDbl("lat"),
        lon = o.optDbl("lon"),
    )

    fun listing(o: JSONObject): Listing {
        val donor = o.getJSONObject("donor")
        val pickup = o.optJSONObject("pickup")
        val reqs = o.optJSONArray("requests") ?: JSONArray()
        return Listing(
            id = o.getString("id"),
            title = o.getString("title"),
            category = o.optString("category", "OTHER"),
            quantityText = o.optString("quantityText"),
            notes = o.optString("notes"),
            itemExpiry = o.optStr("itemExpiry"),
            pickupDeadline = o.getLong("pickupDeadline"),
            areaLabel = o.optString("areaLabel"),
            claimMode = if (o.optString("claimMode") == "approval") ClaimMode.APPROVAL else ClaimMode.DIRECT,
            status = ListingStatus.of(o.getString("status")),
            donorName = donor.optString("name"),
            donorIsShop = donor.optBoolean("isShop"),
            donorOpeningHours = donor.optStr("openingHours"),
            approxLat = o.optDouble("approxLat"),
            approxLon = o.optDouble("approxLon"),
            distanceKm = o.optDbl("distanceKm"),
            distanceLabel = o.optStr("distanceLabel"),
            isMine = o.optBoolean("isMine"),
            claimedByMe = o.optBoolean("claimedByMe"),
            myRequestStatus = o.optStr("myRequestStatus"),
            pickup = pickup?.let {
                Pickup(it.getDouble("lat"), it.getDouble("lon"), it.optString("address"), it.optString("instructions"), it.optStr("code"))
            },
            claimedByName = o.optJSONObject("claimedBy")?.optString("name"),
            requests = (0 until reqs.length()).map { i ->
                val r = reqs.getJSONObject(i)
                ClaimRequest(r.getString("id"), r.optString("name"), r.optString("message"), r.optString("status"), r.optLong("createdAt"))
            },
            clientProductId = if (o.has("clientProductId") && !o.isNull("clientProductId")) o.getLong("clientProductId") else null,
        )
    }

    fun listings(o: JSONObject): List<Listing> {
        val arr = o.optJSONArray("listings") ?: return emptyList()
        return (0 until arr.length()).map { listing(arr.getJSONObject(it)) }
    }

    fun events(o: JSONObject): List<CommunityEvent> {
        val arr = o.optJSONArray("events") ?: return emptyList()
        return (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            CommunityEvent(e.getLong("id"), e.getString("type"), e.optStr("listingId"), e.getString("message"), e.getLong("createdAt"))
        }
    }

    fun stats(o: JSONObject) = DonorStats(
        o.optInt("available"), o.optInt("reserved"), o.optInt("pickedUp"), o.optInt("expired"), o.optInt("pendingRequests"),
    )
}
