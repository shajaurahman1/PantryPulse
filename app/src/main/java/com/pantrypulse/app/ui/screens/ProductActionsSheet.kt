package com.pantrypulse.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ActionRow
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.theme.PulseTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductActionsSheet(
    product: ProductEntity,
    meta: String,
    onDismiss: () -> Unit,
    vm: MainViewModel,
    onEdit: () -> Unit,
    onRescue: () -> Unit,
    onRecipes: () -> Unit,
    onGiveAway: () -> Unit,
) {
    val c = PulseTheme.colors
    val isFood = product.category.kind == ItemKind.FOOD
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EyebrowText(Expiry.label(product.expiryDate), color = c.accent)
            Text(product.name, style = MaterialTheme.typography.headlineMedium)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(bottom = 10.dp))
            ActionRow(Icons.Outlined.RemoveCircleOutline, "Use one", { onDismiss(); vm.useOne(product) }, "Takes one off the quantity")
            ActionRow(Icons.Outlined.DoneAll, "Mark as finished", { onDismiss(); vm.markFinished(product) }, "We'll offer to add it to the shopping list")
            ActionRow(Icons.Outlined.AddShoppingCart, "Add to shopping list", { onDismiss(); vm.addToShopping(product) })
            if (isFood) {
                ActionRow(Icons.Outlined.RestaurantMenu, "Find a recipe", onRecipes, "Ideas that use ${product.name}")
                ActionRow(Icons.Outlined.VolunteerActivism, "Give away nearby", onGiveAway, "Let someone close by claim it for free", tint = c.accent)
                ActionRow(Icons.Outlined.Map, "Food banks & community fridges", onRescue, "Drop-off points on the map")
            }
            if (product.category.kind == ItemKind.COSMETICS) {
                ActionRow(Icons.Outlined.VolunteerActivism, "Give away nearby", onGiveAway, "Let someone close by claim it for free", tint = c.accent)
            }
            ActionRow(Icons.Outlined.Edit, "Edit details", onEdit)
            ActionRow(Icons.Outlined.WarningAmber, "Threw it away", { onDismiss(); vm.markWasted(product) }, "Logged so you can track waste")
            ActionRow(Icons.Outlined.DeleteOutline, "Delete", { onDismiss(); vm.delete(product) }, tint = MaterialTheme.colorScheme.error)
        }
    }
}
