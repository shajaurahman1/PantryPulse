package com.pantrypulse.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantrypulse.app.BuildConfig
import com.pantrypulse.app.data.db.LocationEntity
import com.pantrypulse.app.domain.HouseholdType
import com.pantrypulse.app.notifications.ExpiryCheckWorker
import com.pantrypulse.app.notifications.ExpiryNotifier
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ActionRow
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.InitialsAvatar
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.SectionTitle
import com.pantrypulse.app.ui.components.locationIcon
import com.pantrypulse.app.ui.theme.DmMono
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private sealed interface ProfileDialog {
    data object EditName : ProfileDialog
    data object ChangePassword : ProfileDialog
    data object Join : ProfileDialog
    data object NewHousehold : ProfileDialog
    data object AddMember : ProfileDialog
    data object RenameHousehold : ProfileDialog
    data object AddLocation : ProfileDialog
    data class RenameLocation(val location: LocationEntity) : ProfileDialog
}

@Composable
fun ProfileScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onRescue: () -> Unit,
    community: (@Composable () -> Unit)? = null,
) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by vm.user.collectAsState()
    val household by vm.household.collectAsState()
    val households by vm.households.collectAsState()
    val members by vm.members.collectAsState()
    val locations by vm.locations.collectAsState()
    val session by vm.session.collectAsState()
    val counts by vm.statusCounts.collectAsState()
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var canNotify by remember { mutableStateOf(ExpiryNotifier.canNotify(context)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }

    fun run(block: suspend () -> Unit) = scope.launch(Dispatchers.Main) {
        try { block(); dialog = null } catch (e: Exception) { vm.message(e.message ?: "Something went wrong") }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 20.dp, bottom = contentPadding.calculateBottomPadding() + 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Profile
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(user?.displayName.orEmpty(), size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(user?.displayName.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                    Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                IconButton(onClick = { dialog = ProfileDialog.EditName }) { Icon(Icons.Outlined.Edit, "Edit name") }
            }
        }

        // Impact
        item {
            val used = counts["FINISHED"] ?: 0
            val donated = counts["DONATED"] ?: 0
            val wasted = counts["WASTED"] ?: 0
            PulseCard {
                EyebrowText("Your impact")
                Row {
                    Stat("$used", "used up", Modifier.weight(1f))
                    Stat("$donated", "donated", Modifier.weight(1f))
                    Stat("$wasted", "wasted", Modifier.weight(1f))
                }
                val total = used + donated + wasted
                if (total > 0) Text("${(used + donated) * 100 / total}% of finished items were eaten or shared, not binned.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        // Household
        item { SectionTitle("Household", action = "Rename", onAction = { dialog = ProfileDialog.RenameHousehold }) }
        item {
            val h = household
            PulseCard {
                EyebrowText(h?.type?.label ?: "")
                Text(h?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text("Invite code", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h?.inviteCode.orEmpty(), fontFamily = DmMono, fontSize = 30.sp, letterSpacing = 6.sp, color = c.accent, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Invite code", h?.inviteCode))
                        vm.message("Invite code copied.")
                    }) { Icon(Icons.Outlined.ContentCopy, "Copy invite code") }
                    IconButton(onClick = {
                        val text = "Join our ${h?.name} pantry on PantryPulse! Open the app, go to Profile › Join with code and enter ${h?.inviteCode}."
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invite"))
                    }) { Icon(Icons.Outlined.Share, "Share invite code") }
                }
                Text(
                    "Everyone with a profile in this household shares one pantry and one shopping list. On a shared phone or kitchen tablet, add each person below.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
                EyebrowText("Members · ${members.size}")
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        InitialsAvatar(m.displayName, size = 38.dp, color = Color(m.avatarColor))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName + if (m.id == user?.id) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                            Text(m.role, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
                ActionRow(Icons.Outlined.PersonAdd, "Add a member profile", { dialog = ProfileDialog.AddMember }, "Family, roommates or colleagues on this device")
                ActionRow(Icons.AutoMirrored.Outlined.Login, "Join with invite code", { dialog = ProfileDialog.Join })
                ActionRow(Icons.Outlined.GroupAdd, "Create a new household", { dialog = ProfileDialog.NewHousehold }, "Family, roommates or office breakroom")
                if (households.size > 1) {
                    EyebrowText("Switch household")
                    ChipRow { households.forEach { hh -> PillChip(hh.name, hh.id == h?.id, { vm.switchHousehold(hh.id) }) } }
                    SecondaryButton("Leave this household", {
                        run { vm.container.households.leave(user!!.id, h!!.id); vm.message("You left ${h.name}.") }
                    })
                }
            }
        }

        if (community != null) {
            item { SectionTitle("Community") }
            item { community() }
        }

        // Storage
        item { SectionTitle("Storage spots", action = "Add", onAction = { dialog = ProfileDialog.AddLocation }) }
        item {
            PulseCard(padding = 12.dp) {
                locations.forEach { loc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(locationIcon(loc.iconKey), null, tint = c.muted, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(loc.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { dialog = ProfileDialog.RenameLocation(loc) }) { Icon(Icons.Outlined.Edit, "Rename ${loc.name}", tint = c.muted) }
                        IconButton(onClick = { run { vm.container.pantry.deleteLocation(loc) } }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${loc.name}", tint = c.muted) }
                    }
                }
            }
        }

        // Alerts
        item { SectionTitle("Expiry alerts") }
        item {
            val s = session
            PulseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.titleSmall)
                        Text("Around 9am when something is close to expiring", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Switch(s?.notificationsEnabled == true, { vm.setNotifications(it) }, colors = pulseSwitch())
                }
                if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                    SecondaryButton("Allow notifications", { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, icon = Icons.Outlined.NotificationsActive)
                }
                val days = s?.warningDays ?: 3
                Text("Warn me $days day${if (days > 1) "s" else ""} before", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = days.toFloat(), onValueChange = { vm.setWarningDays(it.toInt()) }, valueRange = 1f..14f, steps = 12,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.peach, activeTickColor = c.ink, inactiveTickColor = c.accent),
                )
                SecondaryButton("Check now", { ExpiryCheckWorker.runNow(context); vm.message("Checking your pantry… you'll get a notification if anything is close to expiring.") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-add finished items to list", style = MaterialTheme.typography.titleSmall)
                        Text("Skip the tap when something runs out", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Switch(s?.autoAddFinishedToList == true, { vm.setAutoAdd(it) }, colors = pulseSwitch())
                }
            }
        }

        item {
            PulseCard(padding = 12.dp) {
                ActionRow(Icons.Outlined.VolunteerActivism, "Food rescue near me", onRescue, "Food banks, community fridges & sharing points", tint = c.accent)
                ActionRow(Icons.Outlined.Key, "Change password", { dialog = ProfileDialog.ChangePassword })
                ActionRow(Icons.AutoMirrored.Outlined.Logout, "Sign out / switch profile", { vm.signOut() })
            }
        }
        item {
            Text(
                "PantryPulse ${BuildConfig.VERSION_NAME}\nProduct data © Open Food Facts contributors (ODbL) · Recipes from TheMealDB · Maps © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    when (val d = dialog) {
        null -> {}
        ProfileDialog.EditName -> TextDialog("Your name", user?.displayName.orEmpty(), "Save", { dialog = null }) { v ->
            run { vm.container.auth.updateName(user!!.id, v) }
        }
        ProfileDialog.RenameHousehold -> TextDialog("Household name", household?.name.orEmpty(), "Save", { dialog = null }) { v ->
            run { vm.container.households.rename(household!!.id, v) }
        }
        ProfileDialog.AddLocation -> TextDialog("New storage spot", "", "Add", { dialog = null }) { v ->
            run { vm.container.pantry.addLocation(household!!.id, v) }
        }
        is ProfileDialog.RenameLocation -> TextDialog("Rename storage spot", d.location.name, "Save", { dialog = null }) { v ->
            run { vm.container.pantry.renameLocation(d.location, v) }
        }
        ProfileDialog.Join -> TextDialog("Enter invite code", "", "Join", { dialog = null }, hint = "6-character code, e.g. K7Q2MX") { v ->
            run { val h = vm.container.households.joinByCode(user!!.id, v); vm.message("Welcome to ${h.name}!") }
        }
        ProfileDialog.NewHousehold -> NewHouseholdDialog({ dialog = null }) { name, type ->
            run {
                val h = vm.container.households.createHousehold(name, type, user!!.id)
                vm.container.households.switchTo(h.id)
                vm.message("${h.name} created. Share the invite code ${h.inviteCode} with others.")
            }
        }
        ProfileDialog.AddMember -> AddMemberDialog({ dialog = null }) { name, email, password ->
            run {
                val newUser = vm.container.auth.createProfile(name, email, password)
                vm.container.households.addMember(household!!.id, newUser.id)
                vm.message("${newUser.displayName} can now sign in and share this pantry.")
            }
        }
        ProfileDialog.ChangePassword -> ChangePasswordDialog({ dialog = null }) { current, new ->
            run { vm.container.auth.changePassword(user!!.id, current, new); vm.message("Password changed.") }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = PulseTheme.colors.muted)
    }
}

@Composable
private fun pulseSwitch() = SwitchDefaults.colors(
    checkedThumbColor = PulseTheme.colors.card, checkedTrackColor = PulseTheme.colors.accent,
    uncheckedThumbColor = PulseTheme.colors.muted, uncheckedTrackColor = PulseTheme.colors.paper,
)

@Composable
private fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, hint: String = title, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val c = PulseTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        title = { Text(title) },
        text = { PulseTextField(value, { value = it }, hint) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirm, color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun NewHouseholdDialog(onDismiss: () -> Unit, onCreate: (String, HouseholdType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HouseholdType.FAMILY) }
    val c = PulseTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        title = { Text("New household") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseTextField(name, { name = it }, "Name, e.g. Flat 4B or Studio kitchen")
                HouseholdType.entries.forEach { t ->
                    PillChip("${t.label} — ${t.description}", type == t, { type = t }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, type) }) { Text("Create", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val c = PulseTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        title = { Text("Add a member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("They'll sign in on this device with these details and see the same pantry.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                PulseTextField(name, { name = it }, "Name")
                PulseTextField(email, { email = it.trim() }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                PulseTextField(password, { password = it }, "Password", visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, email, password) }) { Text("Add", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onChange: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    val c = PulseTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseTextField(current, { current = it }, "Current password", visualTransformation = PasswordVisualTransformation())
                PulseTextField(new, { new = it }, "New password", visualTransformation = PasswordVisualTransformation(), supportingText = "At least 6 characters")
            }
        },
        confirmButton = { TextButton(onClick = { onChange(current, new) }) { Text("Change", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

