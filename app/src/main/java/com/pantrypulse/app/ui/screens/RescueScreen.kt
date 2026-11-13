package com.pantrypulse.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pantrypulse.app.data.remote.Place
import com.pantrypulse.app.data.remote.RescueSpot
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.theme.PulseTheme
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun RescueScreen(vm: MainViewModel, rvm: RescueViewModel, onBack: () -> Unit) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) rvm.useDeviceLocation(context)
        else vm.message("Location permission denied — search for your town instead.")
    }

    fun shareItems() {
        val text = buildString {
            append("Free food to a good home 🍎\n")
            rvm.items.forEach { append("• ${it.name} — ${Expiry.label(it.expiryDate).lowercase()}\n") }
            append("\nMessage me if you can collect. (Shared via PantryPulse)")
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Offer food to neighbours"))
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, size = 44.dp) }
        item {
            EyebrowText("Food rescue")
            Text("Don't bin it.\nPass it on.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 6.dp))
        }
        if (rvm.items.isNotEmpty()) item {
            PulseCard {
                EyebrowText("Ready to share · ${rvm.items.size}")
                rvm.items.take(6).forEach { p ->
                    Row {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(Expiry.label(p.expiryDate), style = MaterialTheme.typography.bodySmall, color = c.accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Offer to neighbours", { shareItems() }, Modifier.weight(1f), icon = Icons.Outlined.Share)
                }
                PrimaryButton("I've donated these", {
                    rvm.markAllDonated { n -> vm.message("Thank you! $n item${if (n > 1) "s" else ""} rescued from the bin."); onBack() }
                }, Modifier.fillMaxWidth(), accent = true)
            }
        }
        item {
            PulseCard {
                EyebrowText("Find drop-off points")
                PrimaryButton("Use my location", {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }, Modifier.fillMaxWidth(), icon = Icons.Outlined.MyLocation)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseTextField(query, { query = it }, "Or search a town / postcode", Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { rvm.searchPlace(query) }))
                    Spacer(Modifier.width(10.dp))
                    RoundButton(Icons.Outlined.Search, "Search", { rvm.searchPlace(query) }, filled = true)
                }
                rvm.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (rvm.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
        rvm.place?.let { place ->
            if (!rvm.loading) {
                item { RescueMap(place, rvm.spots) }
                item {
                    EyebrowText(
                        if (rvm.spots.isEmpty()) "No listed spots within ${rvm.radiusKm} km"
                        else "${rvm.spots.size} places within ${rvm.radiusKm} km of ${place.name.substringBefore(',')}",
                    )
                }
                items(rvm.spots, key = { it.id }) { SpotCard(it) }
                if (rvm.searched && rvm.spots.size < 3) item {
                    PulseCard {
                        Text(if (rvm.spots.isEmpty()) "Nothing mapped nearby yet" else "Want more options?", style = MaterialTheme.typography.titleSmall)
                        Text("Food banks aren't mapped everywhere. Try a wider search, check your maps app, or offer the food to neighbours.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        if (rvm.radiusKm < 50) SecondaryButton("Search ${if (rvm.radiusKm < 25) 25 else 50} km", { rvm.widen() })
                        SecondaryButton("Search “food bank” in Maps", {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lon}?q=food+bank"))) }
                                .onFailure { vm.message("No maps app installed.") }
                        }, icon = Icons.Outlined.Directions)
                    }
                }
                item { Text("Map data © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun RescueMap(place: Place, spots: List<RescueSpot>) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp)),
        update = { m ->
            m.overlays.clear()
            m.controller.setZoom(if (spots.isEmpty()) 12.0 else 13.0)
            m.controller.setCenter(GeoPoint(place.lat, place.lon))
            m.overlays.add(Marker(m).apply {
                position = GeoPoint(place.lat, place.lon)
                title = place.name.substringBefore(',')
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
            spots.forEach { s ->
                m.overlays.add(Marker(m).apply {
                    position = GeoPoint(s.lat, s.lon)
                    title = s.name
                    snippet = s.kind
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            m.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

@Composable
private fun SpotCard(spot: RescueSpot) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    PulseCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.titleSmall)
                Text("${spot.kind} · ${formatDistance(spot.distanceMeters)}", style = MaterialTheme.typography.bodySmall, color = c.accent)
            }
        }
        spot.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
        spot.openingHours?.let { Text("Open: $it", style = MaterialTheme.typography.bodySmall, color = c.muted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Directions", {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${spot.lat},${spot.lon}?q=${spot.lat},${spot.lon}(${Uri.encode(spot.name)})")))
                }
            }, icon = Icons.Outlined.Directions)
            spot.phone?.let { phone ->
                RoundButton(Icons.Outlined.Call, "Call ${spot.name}", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                }, size = 48.dp)
            }
            spot.website?.let { site ->
                RoundButton(Icons.Outlined.Language, "Website of ${spot.name}", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (site.startsWith("http")) site else "https://$site"))) }
                }, size = 48.dp)
            }
        }
    }
}

fun formatDistance(meters: Double): String = if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
