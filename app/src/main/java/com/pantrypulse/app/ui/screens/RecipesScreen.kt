package com.pantrypulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ExpiryStatus
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.RecipeMatcher
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EmptyState
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.SectionTitle
import com.pantrypulse.app.ui.theme.PulseTheme

@Composable
fun RecipesScreen(vm: MainViewModel, rvm: RecipesViewModel, focus: String?, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val c = PulseTheme.colors
    val products by vm.products.collectAsState()
    val session by vm.session.collectAsState()
    val warning = session?.warningDays ?: Expiry.DEFAULT_WARNING_DAYS
    val food = products.filter { it.category.kind == ItemKind.FOOD }
    val expiring = food.filter { Expiry.status(it.expiryDate, warning + 2) in setOf(ExpiryStatus.TODAY, ExpiryStatus.SOON, ExpiryStatus.EXPIRED) }
    val suggestions = RecipeMatcher.suggest(RecipeMatcher.builtIn, food.map { it.name }, expiring.map { it.name })
    val keywords = (listOfNotNull(focus?.let(RecipeMatcher::ingredientKeyword)) +
        expiring.mapNotNull { RecipeMatcher.ingredientKeyword(it.name) } +
        food.mapNotNull { RecipeMatcher.ingredientKeyword(it.name) }).distinct()

    LaunchedEffect(keywords.firstOrNull()) {
        if (rvm.ingredient == null || (focus != null && keywords.firstOrNull() != null)) keywords.firstOrNull()?.let(rvm::search)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 20.dp, bottom = contentPadding.calculateBottomPadding() + 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            EyebrowText("Recipes")
            Text("Cook it before it goes.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 6.dp))
            if (expiring.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Using up: " + expiring.take(4).joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyLarge, color = c.muted,
                )
            }
        }
        if (food.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.RestaurantMenu, "No ingredients yet", "Add food to your pantry and we'll suggest recipes that use what's about to expire.")
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionTitle("From your pantry") }
            items(suggestions.take(8), key = { it.recipe.id }) { s ->
                PulseCard(onClick = { onOpen(s.recipe.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.recipe.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Schedule, null, tint = c.muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${s.recipe.minutes} min", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    if (s.usesExpiring.isNotEmpty()) {
                        Text("Rescues " + s.usesExpiring.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = c.accent)
                    }
                    Text("You have " + s.matched.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        if (keywords.isNotEmpty()) {
            item {
                SectionTitle("More ideas online")
                ChipRow { keywords.take(10).forEach { k -> PillChip(k.replaceFirstChar { it.uppercase() }, rvm.ingredient == k, { rvm.search(k) }) } }
            }
            when {
                rvm.loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
                rvm.error != null -> item { Text(rvm.error!!, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                rvm.online.isEmpty() && rvm.ingredient != null -> item { Text("No online recipes for ${rvm.ingredient}.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                else -> items(rvm.online.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { r ->
                            PulseCard(Modifier.weight(1f), padding = 10.dp, onClick = { onOpen(r.id) }) {
                                AsyncImage(r.imageUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { Text("Online recipes from TheMealDB", style = MaterialTheme.typography.labelSmall, color = c.muted) }
        }
    }
}
