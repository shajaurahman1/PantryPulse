package com.pantrypulse.app.ui.community

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.ui.theme.PulseTheme

/**
 * Floating shortcut to Giveaways. When free food is available close by it expands to
 * "N free nearby · about X km" (nearest first); otherwise it is just the icon.
 */
@Composable
fun GiveawayFab(cvm: CommunityViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PulseTheme.colors
    val available = cvm.nearby.filter { it.status == ListingStatus.AVAILABLE }
    val closest = available.minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
    val label = when {
        closest == null -> "Giveaways"
        else -> "${available.size} free nearby" + (closest.distanceLabel?.let { " · $it" } ?: "")
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = closest != null,
        icon = { Icon(Icons.Outlined.VolunteerActivism, contentDescription = "Open giveaways") },
        text = { Text(label) },
        containerColor = c.accent,
        contentColor = Color.White,
        modifier = modifier.testTag("fab_giveaways"),
    )
}
