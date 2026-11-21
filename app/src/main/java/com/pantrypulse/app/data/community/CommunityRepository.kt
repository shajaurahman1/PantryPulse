package com.pantrypulse.app.data.community

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.repo.SessionStore
import com.pantrypulse.app.domain.ProductStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.communityStore: DataStore<Preferences> by preferencesDataStore(name = "pantrypulse_community")

/** Online status of the signed-in local profile. */
data class CommunityState(val email: String?, val token: String?, val user: CommunityUser?) {
    val connected get() = token != null && user != null
    val isShop get() = user?.isShop == true
}

class NotConnectedException : Exception("Connect your profile to the PantryPulse community first.")

/**
 * Keeps one online session per local profile (keyed by email) so several people sharing
 * a phone each have their own community identity, and wraps every backend call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityRepository(
    private val context: Context,
    val api: CommunityApi,
    private val session: SessionStore,
    private val db: AppDatabase,
) {
    private fun tokenKey(email: String) = stringPreferencesKey("token_${email.lowercase()}")
    private fun userKey(email: String) = stringPreferencesKey("user_${email.lowercase()}")
    private fun eventKey(email: String) = longPreferencesKey("last_event_${email.lowercase()}")

    private val localEmail: Flow<String?> = session.session.map { it.userId }.flatMapLatest { id ->
        if (id == null) flowOf(null) else db.userDao().observe(id).map { it?.email }
    }

    val state: Flow<CommunityState> = combine(localEmail, context.communityStore.data) { email, prefs ->
        if (email == null) CommunityState(null, null, null) else CommunityState(
            email,
            prefs[tokenKey(email)],
            prefs[userKey(email)]?.let { runCatching { CommunityJson.user(JSONObject(it)) }.getOrNull() },
        )
    }

    suspend fun current(): CommunityState = state.first()

    private suspend fun token(): String = current().token ?: throw NotConnectedException()

    private suspend fun saveUser(email: String, user: CommunityUser, token: String? = null) {
        val json = JSONObject().apply {
            put("id", user.id); put("email", user.email); put("name", user.name)
            put("accountType", if (user.isShop) "shop" else "person")
            put("shopName", user.shopName ?: JSONObject.NULL); put("shopAddress", user.shopAddress ?: JSONObject.NULL)
            put("openingHours", user.openingHours ?: JSONObject.NULL)
            put("lat", user.lat ?: JSONObject.NULL); put("lon", user.lon ?: JSONObject.NULL)
            put("notifyRadiusKm", user.notifyRadiusKm)
        }
        context.communityStore.edit { p ->
            token?.let { p[tokenKey(email)] = it }
            p[userKey(email)] = json.toString()
        }
    }

    /** Signs in to (or creates) the online account that matches a local profile. */
    suspend fun connect(email: String, password: String, name: String, shop: ShopProfile? = null): CommunityUser {
        val result = api.connect(email.trim().lowercase(), password, name, shop)
        saveUser(email.trim().lowercase(), result.user, result.token)
        return result.user
    }

    suspend fun disconnect() {
        val email = current().email ?: return
        context.communityStore.edit { it.remove(tokenKey(email)); it.remove(userKey(email)) }
    }

    suspend fun updateProfile(isShop: Boolean, shop: ShopProfile?): CommunityUser {
        val s = current()
        val user = api.updateProfile(token(), isShop, shop)
        saveUser(s.email!!, user)
        return user
    }

    /** Location is rounded to ~1 km before it leaves the phone; the server never needs more. */
    private fun coarse(v: Double) = Math.round(v * 100) / 100.0

    /** Turns nearby-giveaway alerts on or off (radiusKm = 0). Shop accounts keep their shop location. */
    suspend fun setAlerts(radiusKm: Double, lat: Double?, lon: Double?): CommunityUser {
        val s = current()
        val user = api.updateAlerts(token(), radiusKm, if (s.isShop) null else lat?.let(::coarse), if (s.isShop) null else lon?.let(::coarse))
        saveUser(s.email!!, user)
        return user
    }

    /** Keeps the server's approximate location current while alerts are on. Called from the background worker. */
    suspend fun refreshAlertLocation(lat: Double, lon: Double) {
        val s = current()
        val u = s.user ?: return
        if (u.isShop || u.notifyRadiusKm <= 0.0) return
        if (u.lat == coarse(lat) && u.lon == coarse(lon)) return
        setAlerts(u.notifyRadiusKm, lat, lon)
    }

    suspend fun createListing(listing: NewListing) = api.createListing(token(), listing)
    suspend fun createBatch(listings: List<NewListing>) = api.createBatch(token(), listings)
    suspend fun nearby(lat: Double, lon: Double, radiusKm: Double, urgency: Boolean) = api.nearby(token(), lat, lon, radiusKm, urgency)
    suspend fun listing(id: String) = api.listing(token(), id)
    suspend fun myClaims() = api.myClaims(token())
    suspend fun claim(id: String) = api.claim(token(), id)
    suspend fun request(id: String, message: String) = api.request(token(), id, message)
    suspend fun withdrawRequest(id: String) = api.withdrawRequest(token(), id)
    suspend fun accept(id: String, requestId: String) = api.acceptRequest(token(), id, requestId)
    suspend fun reject(id: String, requestId: String) = api.rejectRequest(token(), id, requestId)
    suspend fun release(id: String, newDeadline: Long?) = api.release(token(), id, newDeadline)
    suspend fun cancelClaim(id: String) = api.cancelClaim(token(), id)
    suspend fun markPickedUp(id: String) = api.markPickedUp(token(), id)
    suspend fun cancelListing(id: String) = api.cancelListing(token(), id)
    suspend fun stats() = api.stats(token())

    /**
     * Loads the donor's listings and, for any that were picked up and came from a pantry
     * item, records that pantry item as donated so the local inventory stays accurate.
     */
    suspend fun myListings(): List<Listing> {
        val listings = api.myListings(token())
        listings.filter { it.status == ListingStatus.PICKED_UP && it.clientProductId != null }.forEach { l ->
            val product = db.productDao().findById(l.clientProductId!!)
            if (product != null && product.status == ProductStatus.ACTIVE) {
                db.productDao().update(product.copy(status = ProductStatus.DONATED, finishedAt = System.currentTimeMillis()))
            }
        }
        return listings
    }

    /** New activity since the last check, for notifications. */
    suspend fun newEvents(): List<CommunityEvent> {
        val s = current()
        val email = s.email ?: return emptyList()
        val token = s.token ?: return emptyList()
        val after = context.communityStore.data.first()[eventKey(email)] ?: 0L
        val events = api.events(token, after)
        events.maxOfOrNull { it.id }?.let { max -> context.communityStore.edit { it[eventKey(email)] = max } }
        return events
    }
}
