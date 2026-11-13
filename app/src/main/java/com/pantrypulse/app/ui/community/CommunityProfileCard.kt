package com.pantrypulse.app.ui.community

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pantrypulse.app.data.community.ShopProfile
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ActionRow
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Community status and the shopkeeper profile, shown on the Profile screen. */
@Composable
fun CommunityProfileCard(vm: MainViewModel, cvm: CommunityViewModel, onOpenGiveaways: () -> Unit) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by cvm.state.collectAsState()
    val u = state.user
    var isShop by remember(u) { mutableStateOf(u?.isShop == true) }
    var name by remember(u) { mutableStateOf(u?.shopName.orEmpty()) }
    var address by remember(u) { mutableStateOf(u?.shopAddress.orEmpty()) }
    var hours by remember(u) { mutableStateOf(u?.openingHours.orEmpty()) }
    var lat by remember(u) { mutableStateOf(u?.lat) }
    var lon by remember(u) { mutableStateOf(u?.lon) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cvm.message = { vm.message(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) scope.launch(Dispatchers.Main) { cvm.currentLocation(context)?.let { lat = it.lat; lon = it.lon } }
    }

    PulseCard {
        EyebrowText(if (state.connected) "Online · ${u?.email}" else "Offline")
        if (!state.connected) {
            Text("Connect to give away food and claim free food nearby.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            PrimaryButton("Open Giveaways", onOpenGiveaways, accent = true)
            return@PulseCard
        }
        ActionRow(Icons.Outlined.Storefront, if (u?.isShop == true) "Shop dashboard" else "My giveaways", onOpenGiveaways,
            if (u?.isShop == true) "Post surplus, approve requests, confirm pickups" else "Listings, requests and claims")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shop account", style = MaterialTheme.typography.titleSmall)
                Text("Show your shop name and opening hours on giveaways", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Switch(isShop, { isShop = it }, colors = SwitchDefaults.colors(checkedThumbColor = c.card, checkedTrackColor = c.accent, uncheckedTrackColor = c.paper))
        }
        if (isShop) {
            PulseTextField(name, { name = it.take(80) }, "Shop name")
            PulseTextField(address, { address = it.take(200) }, "Shop address (shared after a claim)")
            PulseTextField(hours, { hours = it.take(120) }, "Opening hours")
            SecondaryButton(if (lat != null) "Shop location saved ✓ — update" else "Use my location as the shop location", {
                if (cvm.container.location.hasPermission(context)) scope.launch(Dispatchers.Main) { cvm.currentLocation(context)?.let { lat = it.lat; lon = it.lon } }
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }, icon = Icons.Outlined.MyLocation)
        }
        val changed = isShop != (u?.isShop == true) || (isShop && (name != u?.shopName.orEmpty() || address != u?.shopAddress.orEmpty() ||
            hours != u?.openingHours.orEmpty() || lat != u?.lat || lon != u?.lon))
        if (changed) {
            PrimaryButton(if (saving) "Saving…" else "Save", {
                saving = true
                cvm.saveShop(isShop, if (isShop) ShopProfile(name.trim(), address.trim(), hours.trim(), lat, lon) else null) { err ->
                    saving = false
                    vm.message(err ?: if (isShop) "Shop profile saved." else "Switched to a personal account.")
                }
            }, Modifier.fillMaxWidth(), accent = true, enabled = !saving && (!isShop || name.isNotBlank()))
        }
    }
}
