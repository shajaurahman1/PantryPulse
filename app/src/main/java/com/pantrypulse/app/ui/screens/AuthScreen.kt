package com.pantrypulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.data.community.ShopProfile
import com.pantrypulse.app.data.db.UserEntity
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.InitialsAvatar
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseLogo
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.SegmentedPills
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@Composable
fun AuthScreen(vm: MainViewModel) {
    val c = PulseTheme.colors
    val scope = rememberCoroutineScope()
    val profiles by vm.allUsers.collectAsState()
    var mode by rememberSaveable { mutableIntStateOf(if (profiles.isEmpty()) 1 else 0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var isShop by rememberSaveable { mutableStateOf(false) }
    var shopName by rememberSaveable { mutableStateOf("") }
    var shopAddress by rememberSaveable { mutableStateOf("") }
    var openingHours by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch(Dispatchers.Main) {
            try {
                if (mode == 1 && isShop && shopName.isBlank()) throw IllegalArgumentException("Please enter your shop's name.")
                val user = if (mode == 1) vm.container.auth.signUp(name, email, password)
                else vm.container.auth.signIn(email, password)
                // Link the profile to the online community (giveaways). Works offline too:
                // if this fails, the Giveaways tab asks to connect later.
                val shop = if (mode == 1 && isShop) ShopProfile(shopName.trim(), shopAddress.trim(), openingHours.trim(), null, null) else null
                vm.connectCommunity(user.email, password, user.displayName, shop)
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.paper).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PulseLogo(Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            buildAnnotatedString {
                append("Waste less.\nEat ")
                withStyle(SpanStyle(background = c.accent)) { append(" everything. ") }
            },
            style = MaterialTheme.typography.displaySmall,
            color = c.ink,
        )
        Text(
            "Track expiry dates, get nudged before food goes off, and share one pantry with the people you live or work with.",
            style = MaterialTheme.typography.bodyLarge, color = c.muted,
        )

        if (profiles.isNotEmpty() && mode == 0) {
            Text("PROFILES ON THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = c.muted)
            ChipRow {
                profiles.forEach { p -> ProfileChip(p, selected = email.equals(p.email, true)) { email = p.email; error = null } }
            }
        }

        SegmentedPills(listOf("Sign in", "Create account"), mode, { mode = it; error = null })

        if (mode == 1) {
            PulseTextField(name, { name = it }, "Your name",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        }
        if (mode == 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("I run a shop", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text("Shops get a dashboard to post surplus stock for local people.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(isShop, { isShop = it }, modifier = Modifier.testTag("shop_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = c.card, checkedTrackColor = c.accent, uncheckedTrackColor = c.card))
            }
            if (isShop) {
                PulseTextField(shopName, { shopName = it }, "Shop name")
                PulseTextField(shopAddress, { shopAddress = it }, "Shop address")
                PulseTextField(openingHours, { openingHours = it }, "Opening hours, e.g. 8am–9pm")
            }
        }
        PulseTextField(email, { email = it.trim() }, "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PulseTextField(
            password, { password = it }, "Password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password")
                }
            },
            supportingText = if (mode == 1) "At least 6 characters" else null,
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("auth_error"))
        }
        if (busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        } else {
            PrimaryButton(if (mode == 1) "Create account" else "Sign in", { submit() }, Modifier.fillMaxWidth(), accent = true)
        }
        Text(
            "Your pantry is stored privately on this phone. Family, roommates or office colleagues can each have a profile here and share a household.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
    }
}

@Composable
private fun ProfileChip(user: UserEntity, selected: Boolean, onClick: () -> Unit) {
    val c = PulseTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.ink else c.card)
            .clickable(onClick = onClick).padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(user.displayName, size = 34.dp, color = if (selected) c.accent else Color(user.avatarColor))
        Spacer(Modifier.width(8.dp))
        Text(user.displayName, style = MaterialTheme.typography.labelLarge, color = if (selected) c.paper else c.ink)
    }
}
