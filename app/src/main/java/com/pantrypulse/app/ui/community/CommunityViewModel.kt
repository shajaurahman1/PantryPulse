package com.pantrypulse.app.ui.community

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.data.community.CommunityException
import com.pantrypulse.app.data.community.CommunityState
import com.pantrypulse.app.data.community.DonorStats
import com.pantrypulse.app.data.community.LatLon
import com.pantrypulse.app.data.community.Listing
import com.pantrypulse.app.data.community.NewListing
import com.pantrypulse.app.data.community.ShopProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GiveawayTab(val label: String) { NEARBY("Nearby"), GIVING("Giving"), GETTING("Getting") }

class CommunityViewModel(val container: AppContainer) : ViewModel() {
    private val repo = container.community

    val state: StateFlow<CommunityState> = repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, CommunityState(null, null, null))

    var tab by mutableStateOf(GiveawayTab.NEARBY)
    var radiusKm by mutableStateOf(5.0)
        private set
    var sortByUrgency by mutableStateOf(false)
        private set
    var location by mutableStateOf<LatLon?>(null)
        private set
    var nearby by mutableStateOf<List<Listing>>(emptyList())
        private set
    var mine by mutableStateOf<List<Listing>>(emptyList())
        private set
    var claims by mutableStateOf<List<Listing>>(emptyList())
        private set
    var stats by mutableStateOf<DonorStats?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Ids with an action in flight, so buttons can't be double-tapped. */
    var busy by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun friendly(e: Throwable) = when (e) {
        is CommunityException -> e.message ?: "Something went wrong"
        else -> e.message ?: "Something went wrong"
    }

    fun connect(email: String, password: String, name: String, shop: ShopProfile?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { repo.connect(email, password, name, shop) }.exceptionOrNull()?.let(::friendly))
            refreshAll()
        }
    }

    fun locate(context: Context) {
        viewModelScope.launch {
            loading = true
            val here = container.location.current(context)
            if (here == null) {
                error = "Couldn't get your location. Turn on location services and try again."
                loading = false
            } else {
                location = here
                loadNearby()
            }
        }
    }

    fun setRadius(km: Double) { radiusKm = km; viewModelScope.launch { loadNearby() } }
    fun setUrgency(urgent: Boolean) { sortByUrgency = urgent; viewModelScope.launch { loadNearby() } }

    private suspend fun loadNearby() {
        val here = location ?: return
        if (!state.value.connected) return
        loading = true
        error = null
        runCatching { repo.nearby(here.lat, here.lon, radiusKm, sortByUrgency) }
            .onSuccess { nearby = it }
            .onFailure { error = friendly(it) }
        loading = false
    }

    fun refreshAll() {
        viewModelScope.launch {
            if (!state.value.connected) return@launch
            loadNearby()
            runCatching { repo.myListings() }.onSuccess { mine = it }.onFailure { error = friendly(it) }
            runCatching { repo.myClaims() }.onSuccess { claims = it }
            runCatching { repo.stats() }.onSuccess { stats = it }
        }
    }

    /** Runs a listing action once at a time and refreshes everything afterwards. */
    private fun act(id: String, success: String?, block: suspend () -> Unit, onError: ((String) -> Unit)? = null, after: () -> Unit = {}) {
        if (id in busy) return
        busy = busy + id
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let { m -> message(m) }; after() }
                .onFailure { e -> (onError ?: message)(friendly(e)) }
            busy = busy - id
            refreshAll()
        }
    }

    var message: (String) -> Unit = {}

    fun claim(l: Listing, onClaimed: (Listing) -> Unit = {}) = act(l.id, null, {
        val claimed = repo.claim(l.id)
        onClaimed(claimed)
        message("Reserved! The pickup address and code are in Getting.")
    })
    fun request(l: Listing, text: String) = act(l.id, "Request sent. We'll let you know if ${l.donorName} picks you.", { repo.request(l.id, text) })
    fun withdraw(l: Listing) = act(l.id, "Request withdrawn.", { repo.withdrawRequest(l.id) })
    fun accept(l: Listing, requestId: String) = act(l.id, "Accepted — everyone else in the queue has been told.", { repo.accept(l.id, requestId) })
    fun reject(l: Listing, requestId: String) = act(l.id, "Request declined.", { repo.reject(l.id, requestId) })
    fun release(l: Listing, newDeadline: Long?) = act(l.id, "${l.title} is back on the feed.", { repo.release(l.id, newDeadline) })
    fun cancelClaim(l: Listing) = act(l.id, "Reservation cancelled.", { repo.cancelClaim(l.id) })
    fun pickedUp(l: Listing) = act(l.id, "Marked as picked up. Thanks for rescuing food!", { repo.markPickedUp(l.id) })
    fun cancelListing(l: Listing) = act(l.id, "Giveaway withdrawn.", { repo.cancelListing(l.id) })

    fun create(listings: List<NewListing>, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val err = runCatching {
                if (listings.size == 1) repo.createListing(listings.first()) else repo.createBatch(listings)
            }.exceptionOrNull()?.let(::friendly)
            onDone(err)
            if (err == null) { tab = GiveawayTab.GIVING; refreshAll() }
        }
    }

    fun saveShop(isShop: Boolean, shop: ShopProfile?, onDone: (String?) -> Unit) {
        viewModelScope.launch { onDone(runCatching { repo.updateProfile(isShop, shop) }.exceptionOrNull()?.let(::friendly)) }
    }

    suspend fun currentLocation(context: Context): LatLon? = location ?: container.location.current(context)?.also { location = it }
}
