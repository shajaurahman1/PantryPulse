package com.pantrypulse.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.data.remote.Place
import com.pantrypulse.app.data.remote.RescueSpot
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.ProductStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class RescueViewModel(private val container: AppContainer, private val productId: Long?) : ViewModel() {
    var items by mutableStateOf<List<ProductEntity>>(emptyList())
        private set
    var place by mutableStateOf<Place?>(null)
        private set
    var spots by mutableStateOf<List<RescueSpot>>(emptyList())
        private set
    var radiusKm by mutableStateOf(10)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searched by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            items = if (productId != null) listOfNotNull(container.pantry.product(productId))
            else {
                val hh = container.session.current().householdId ?: return@launch
                container.pantry.expiringWithin(hh, 7).filter { it.category.kind == ItemKind.FOOD && it.status == ProductStatus.ACTIVE }
            }
        }
    }

    fun useDeviceLocation(context: Context) {
        viewModelScope.launch {
            loading = true
            error = null
            val loc = currentLocation(context)
            if (loc == null) {
                loading = false
                error = "Couldn't get your location. Turn on location services or search for your town."
                return@launch
            }
            place = Place("Your location", loc.latitude, loc.longitude)
            load()
        }
    }

    fun searchPlace(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val p = container.foodRescue.geocode(query)
                if (p == null) { error = "Couldn't find “$query”."; loading = false; return@launch }
                place = p
                load()
            } catch (e: Exception) {
                error = "Search needs an internet connection."
                loading = false
            }
        }
    }

    fun widen() {
        radiusKm = if (radiusKm < 25) 25 else 50
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val p = place ?: return
        loading = true
        error = null
        try {
            spots = container.foodRescue.nearby(p.lat, p.lon, radiusKm * 1000)
        } catch (e: Exception) {
            error = "Couldn't reach the OpenStreetMap service. Check your connection and try again."
        }
        searched = true
        loading = false
    }

    fun markAllDonated(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            items.forEach { container.pantry.setStatus(it.id, ProductStatus.DONATED) }
            val n = items.size
            items = emptyList()
            onDone(n)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60 * 1000 }
            .maxByOrNull { it.time }?.let { return it }
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull() ?: return null
        }
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, { cont.resume(it) }, context.mainLooper)
                }
            }
        }
        return fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }
}
