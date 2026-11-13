package com.pantrypulse.app.data.community

import com.pantrypulse.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A failed API call with the backend's own error code and friendly message. */
class CommunityException(val status: Int, val code: String, message: String) : Exception(message)

data class AuthResult(val token: String, val user: CommunityUser)

data class ShopProfile(val shopName: String, val shopAddress: String, val openingHours: String, val lat: Double?, val lon: Double?)

/** The community backend (see backend/ in the repository). Implemented over HTTPS; faked in tests. */
interface CommunityApi {
    suspend fun connect(email: String, password: String, name: String, shop: ShopProfile?): AuthResult
    suspend fun me(token: String): CommunityUser
    suspend fun updateProfile(token: String, isShop: Boolean, shop: ShopProfile?, name: String? = null): CommunityUser
    suspend fun createListing(token: String, listing: NewListing): Listing
    suspend fun createBatch(token: String, listings: List<NewListing>): List<Listing>
    suspend fun nearby(token: String, lat: Double, lon: Double, radiusKm: Double, sortByUrgency: Boolean): List<Listing>
    suspend fun listing(token: String, id: String): Listing
    suspend fun myListings(token: String): List<Listing>
    suspend fun myClaims(token: String): List<Listing>
    suspend fun claim(token: String, id: String): Listing
    suspend fun request(token: String, id: String, message: String): Listing
    suspend fun withdrawRequest(token: String, id: String): Listing
    suspend fun acceptRequest(token: String, id: String, requestId: String): Listing
    suspend fun rejectRequest(token: String, id: String, requestId: String): Listing
    suspend fun release(token: String, id: String, newDeadline: Long?): Listing
    suspend fun cancelClaim(token: String, id: String)
    suspend fun markPickedUp(token: String, id: String): Listing
    suspend fun cancelListing(token: String, id: String): Listing
    suspend fun events(token: String, after: Long): List<CommunityEvent>
    suspend fun stats(token: String): DonorStats
}

class HttpCommunityApi(private val baseUrl: String = BuildConfig.COMMUNITY_API_URL) : CommunityApi {

    private suspend fun call(method: String, path: String, token: String? = null, body: Any? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "PantryPulse-Android/${BuildConfig.VERSION_NAME}")
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (code !in 200..299) {
                    throw CommunityException(code, json.optString("error", "http_$code"), json.optString("message", "Server error ($code)"))
                }
                json
            } catch (e: IOException) {
                throw CommunityException(0, "offline", "Can't reach PantryPulse. Check your internet connection.")
            } finally {
                conn.disconnect()
            }
        }

    private fun q(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun ShopProfile.into(o: JSONObject) = o.apply {
        put("shopName", shopName); put("shopAddress", shopAddress); put("openingHours", openingHours)
        if (lat != null && lon != null) { put("lat", lat); put("lon", lon) }
    }

    override suspend fun connect(email: String, password: String, name: String, shop: ShopProfile?): AuthResult {
        val body = JSONObject().put("email", email).put("password", password).put("name", name)
            .put("accountType", if (shop != null) "shop" else "person")
        shop?.into(body)
        val r = call("POST", "/auth/connect", body = body)
        return AuthResult(r.getString("token"), CommunityJson.user(r.getJSONObject("user")))
    }

    override suspend fun me(token: String) = CommunityJson.user(call("GET", "/me", token).getJSONObject("user"))

    override suspend fun updateProfile(token: String, isShop: Boolean, shop: ShopProfile?, name: String?): CommunityUser {
        val body = JSONObject().put("accountType", if (isShop) "shop" else "person")
        name?.let { body.put("name", it) }
        shop?.into(body)
        return CommunityJson.user(call("PATCH", "/me", token, body).getJSONObject("user"))
    }

    override suspend fun createListing(token: String, listing: NewListing) =
        CommunityJson.listing(call("POST", "/listings", token, listing.toJson()).getJSONObject("listing"))

    override suspend fun createBatch(token: String, listings: List<NewListing>): List<Listing> {
        val items = JSONArray().apply { listings.forEach { put(it.toJson()) } }
        return CommunityJson.listings(call("POST", "/listings/batch", token, JSONObject().put("items", items)))
    }

    override suspend fun nearby(token: String, lat: Double, lon: Double, radiusKm: Double, sortByUrgency: Boolean) =
        CommunityJson.listings(call("GET", "/listings/nearby?lat=$lat&lon=$lon&radiusKm=$radiusKm&sort=${if (sortByUrgency) "urgency" else "distance"}", token))

    override suspend fun listing(token: String, id: String) = CommunityJson.listing(call("GET", "/listings/${q(id)}", token).getJSONObject("listing"))
    override suspend fun myListings(token: String) = CommunityJson.listings(call("GET", "/listings/mine", token))
    override suspend fun myClaims(token: String) = CommunityJson.listings(call("GET", "/claims/mine", token))
    override suspend fun claim(token: String, id: String) = CommunityJson.listing(call("POST", "/listings/${q(id)}/claim", token, JSONObject()).getJSONObject("listing"))
    override suspend fun request(token: String, id: String, message: String) =
        CommunityJson.listing(call("POST", "/listings/${q(id)}/requests", token, JSONObject().put("message", message)).getJSONObject("listing"))
    override suspend fun withdrawRequest(token: String, id: String) =
        CommunityJson.listing(call("DELETE", "/listings/${q(id)}/requests", token, JSONObject()).getJSONObject("listing"))
    override suspend fun acceptRequest(token: String, id: String, requestId: String) =
        CommunityJson.listing(call("POST", "/listings/${q(id)}/requests/${q(requestId)}/accept", token, JSONObject()).getJSONObject("listing"))
    override suspend fun rejectRequest(token: String, id: String, requestId: String) =
        CommunityJson.listing(call("POST", "/listings/${q(id)}/requests/${q(requestId)}/reject", token, JSONObject()).getJSONObject("listing"))
    override suspend fun release(token: String, id: String, newDeadline: Long?): Listing {
        val body = JSONObject().apply { newDeadline?.let { put("pickupDeadline", it) } }
        return CommunityJson.listing(call("POST", "/listings/${q(id)}/release", token, body).getJSONObject("listing"))
    }
    override suspend fun cancelClaim(token: String, id: String) { call("POST", "/listings/${q(id)}/cancel-claim", token, JSONObject()) }
    override suspend fun markPickedUp(token: String, id: String) =
        CommunityJson.listing(call("POST", "/listings/${q(id)}/picked-up", token, JSONObject()).getJSONObject("listing"))
    override suspend fun cancelListing(token: String, id: String) =
        CommunityJson.listing(call("POST", "/listings/${q(id)}/cancel", token, JSONObject()).getJSONObject("listing"))
    override suspend fun events(token: String, after: Long) = CommunityJson.events(call("GET", "/events?after=$after", token))
    override suspend fun stats(token: String) = CommunityJson.stats(call("GET", "/me/stats", token))
}
