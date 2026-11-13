package com.pantrypulse.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.data.db.ShoppingItemEntity
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.EmptyState
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.SectionTitle
import com.pantrypulse.app.ui.components.formatQty
import com.pantrypulse.app.ui.theme.PulseTheme

@Composable
fun ShoppingScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val items by vm.shopping.collectAsState()
    val household by vm.household.collectAsState()
    val members by vm.members.collectAsState()
    var newItem by rememberSaveable { mutableStateOf("") }
    val open = items.filter { !it.checked }
    val done = items.filter { it.checked }

    fun add() {
        if (newItem.isNotBlank()) { vm.addShoppingItem(newItem.trim()); newItem = "" }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 20.dp, bottom = contentPadding.calculateBottomPadding() + 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            EyebrowText("Shared list · ${household?.name.orEmpty()}")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Shopping", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).padding(top = 6.dp))
                if (items.isNotEmpty()) RoundButton(Icons.Outlined.Share, "Share list", {
                    val text = buildString {
                        append("🛒 ${household?.name ?: "Our"} shopping list\n")
                        open.forEach { append("• ${it.name} (${formatQty(it.quantity, it.unit)})\n") }
                        append("\nSent from PantryPulse")
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share shopping list"))
                })
            }
            Text(
                if (members.size > 1) "Everyone in ${household?.name} sees this list — ${members.size} people." else "Invite people from your profile to share this list.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseTextField(newItem, { newItem = it }, "Add an item", Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
                Spacer(Modifier.width(10.dp))
                RoundButton(Icons.Outlined.Add, "Add to shopping list", { add() }, filled = true)
            }
        }
        if (items.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.ShoppingBasket, "Nothing to buy",
                    "When something runs out, tap “Add to list” and it lands here for everyone in your household.")
            }
        }
        items(open, key = { it.id }) { ShoppingRow(it, vm) }
        if (done.isNotEmpty()) {
            item {
                SectionTitle("In the basket · ${done.size}", action = "Clear", onAction = { vm.clearChecked() })
            }
            items(done, key = { it.id }) { ShoppingRow(it, vm) }
            item {
                PrimaryButton("Move ${done.size} bought to pantry", { vm.moveBoughtToPantry() }, Modifier.fillMaxWidth().padding(top = 6.dp), accent = true, icon = Icons.Outlined.Inventory2)
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItemEntity, vm: MainViewModel) {
    val c = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(22.dp)).clickable { vm.toggleShopping(item) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("shopping_${item.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (item.checked) c.accent else c.card)
                .border(1.5.dp, if (item.checked) c.accent else c.hairline, CircleShape)
                .semantics { contentDescription = if (item.checked) "Bought" else "Not bought" },
            contentAlignment = Alignment.Center,
        ) { if (item.checked) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.ink) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name, style = MaterialTheme.typography.titleSmall,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) c.muted else c.ink,
            )
            Text("${item.category.emoji} ${formatQty(item.quantity, item.unit)} · added by ${item.addedByName.substringBefore(' ')}", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        IconButton(onClick = { vm.deleteShopping(item) }) { Icon(Icons.Outlined.Close, "Remove ${item.name}", tint = c.muted) }
    }
}
