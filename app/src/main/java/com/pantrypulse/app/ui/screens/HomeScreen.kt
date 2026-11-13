package com.pantrypulse.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.data.db.LocationEntity
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ExpiryStatus
import com.pantrypulse.app.domain.HeadlineBuilder
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.ProductStatus
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.BoxedNumberHeadline
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EmptyState
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.InitialsAvatar
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseLogo
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SectionTitle
import com.pantrypulse.app.ui.components.SegmentedPills
import com.pantrypulse.app.ui.components.TicketCard
import com.pantrypulse.app.ui.components.WeekPulseChart
import com.pantrypulse.app.ui.components.formatQty
import com.pantrypulse.app.ui.theme.PulseTheme
import java.time.LocalDate

enum class SortOrder(val label: String) { EXPIRY("Expiry date, soonest first"), NAME("Name, A to Z"), RECENT("Recently added") }
enum class StatusFilter(val label: String) { ALL("Everything"), SOON("Expiring soon"), EXPIRED("Past date"), NO_DATE("No date") }

fun productMeta(p: ProductEntity, locations: List<LocationEntity>): String =
    listOfNotNull(p.category.kind.label, formatQty(p.quantity, p.unit), locations.firstOrNull { it.id == p.locationId }?.name)
        .joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onEdit: (Long) -> Unit,
    onProfile: () -> Unit,
    onRescue: (Long?) -> Unit,
    onRecipesFor: (String) -> Unit,
    onAdd: () -> Unit,
    onGiveAway: (Long) -> Unit,
) {
    val c = PulseTheme.colors
    val products by vm.products.collectAsState()
    val locations by vm.locations.collectAsState()
    val user by vm.user.collectAsState()
    val session by vm.session.collectAsState()
    val history by vm.history.collectAsState()
    val warningDays = session?.warningDays ?: Expiry.DEFAULT_WARNING_DAYS

    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var locationId by rememberSaveable { mutableLongStateOf(-1L) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOrder.EXPIRY) }
    var statusFilter by rememberSaveable { mutableStateOf(StatusFilter.ALL) }
    var showFilters by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ProductEntity?>(null) }

    val kinds = listOf<ItemKind?>(null) + ItemKind.entries
    val kind = kinds[kindIndex]
    val ofKind = products.filter { kind == null || it.category.kind == kind }
    val visible = ofKind
        .filter { locationId == -1L || it.locationId == locationId }
        .filter { query.isBlank() || it.name.contains(query, true) || (it.brand?.contains(query, true) == true) }
        .filter {
            val s = Expiry.status(it.expiryDate, warningDays)
            when (statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.SOON -> s == ExpiryStatus.SOON || s == ExpiryStatus.TODAY
                StatusFilter.EXPIRED -> s == ExpiryStatus.EXPIRED
                StatusFilter.NO_DATE -> s == ExpiryStatus.NO_DATE
            }
        }
        .let { list ->
            when (sort) {
                SortOrder.EXPIRY -> list
                SortOrder.NAME -> list.sortedBy { it.name.lowercase() }
                SortOrder.RECENT -> list.sortedByDescending { it.createdAt }
            }
        }
    val headline = HeadlineBuilder.build(products, warningDays)
    val today = LocalDate.now()
    val week = HeadlineBuilder.weekCounts(products, today)
    val expiringSoon = products.filter { val s = Expiry.status(it.expiryDate, warningDays); s == ExpiryStatus.SOON || s == ExpiryStatus.TODAY }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper).testTag("home_list"),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseLogo(Modifier.weight(1f))
                RoundButton(Icons.Outlined.Tune, "Sort and filter", { showFilters = true })
                Spacer(Modifier.width(10.dp))
                InitialsAvatar(user?.displayName ?: "", onClick = onProfile)
            }
        }
        item {
            Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                BoxedNumberHeadline(headline.count, headline.text)
                if (headline.expiredNote != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(headline.expiredNote, style = MaterialTheme.typography.bodyLarge, color = c.muted)
                }
            }
        }
        item {
            PulseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EyebrowText("This week's pulse", Modifier.weight(1f))
                    EyebrowText("${week.sum()} expiring", color = c.muted)
                }
                Spacer(Modifier.height(4.dp))
                WeekPulseChart(week, today)
            }
        }
        item {
            SegmentedPills(listOf("All") + ItemKind.entries.map { it.label }, kindIndex, { kindIndex = it; locationId = -1L })
        }
        item {
            ChipRow {
                PillChip("All", locationId == -1L, { locationId = -1L }, count = ofKind.size)
                locations.forEach { loc ->
                    val n = ofKind.count { it.locationId == loc.id }
                    if (n > 0 || locationId == loc.id) PillChip(loc.name, locationId == loc.id, { locationId = loc.id }, count = n)
                }
            }
        }
        if (query.isNotBlank() || statusFilter != StatusFilter.ALL) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(query.takeIf { it.isNotBlank() }?.let { "“$it”" }, statusFilter.takeIf { it != StatusFilter.ALL }?.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { query = ""; statusFilter = StatusFilter.ALL }) { Text("Clear filters", color = c.accent) }
                }
            }
        }
        if (products.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Inventory2, "Your pantry is empty",
                    "Scan a barcode, snap a receipt or type in what you have. We'll remind you before anything expires.",
                    actionLabel = "Add your first item", onAction = onAdd,
                )
            }
        } else if (visible.isEmpty()) {
            item { EmptyState(Icons.Outlined.SearchOff, "Nothing here", "No items match these filters.") }
        }
        items(visible, key = { it.id }) { p ->
            TicketCard(p, productMeta(p, locations), warningDays, onClick = { selected = p }, onCheck = { vm.useOne(p) })
        }
        if (expiringSoon.isNotEmpty()) {
            item {
                PulseCard(onClick = { onRescue(null) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(c.peachSoft).padding(10.dp)) {
                            Icon(Icons.Outlined.VolunteerActivism, null, tint = c.accent)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Going away or won't use it?", style = MaterialTheme.typography.titleSmall)
                            Text("Give it to someone nearby, or find food banks & community fridges", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
            }
        }
        val recent = history.take(5)
        if (recent.isNotEmpty()) {
            item { SectionTitle("Recently finished") }
            items(recent, key = { "h${it.id}" }) { p ->
                PulseCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (p.status) {
                                    ProductStatus.DONATED -> "Donated"
                                    ProductStatus.WASTED -> "Thrown away"
                                    else -> "Used up"
                                },
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                        }
                        TextButton(onClick = { vm.restore(p) }) { Text("Restore", color = c.muted) }
                        PrimaryButton("+ List", { vm.addToShopping(p) }, accent = true)
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = c.paper) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sort & filter", style = MaterialTheme.typography.headlineSmall)
                PulseTextField(query, { query = it }, "Search by name or brand",
                    trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } } } else null)
                EyebrowText("Sort by")
                ChipRow { SortOrder.entries.forEach { PillChip(it.label.substringBefore(","), sort == it, { sort = it }) } }
                EyebrowText("Show")
                ChipRow { StatusFilter.entries.forEach { PillChip(it.label, statusFilter == it, { statusFilter = it }) } }
                PrimaryButton("Done", { showFilters = false }, Modifier.fillMaxWidth())
            }
        }
    }

    selected?.let { p ->
        ProductActionsSheet(
            product = products.firstOrNull { it.id == p.id } ?: p,
            meta = productMeta(p, locations),
            onDismiss = { selected = null },
            vm = vm,
            onEdit = { selected = null; onEdit(p.id) },
            onRescue = { selected = null; onRescue(p.id) },
            onRecipes = { selected = null; onRecipesFor(p.name) },
            onGiveAway = { selected = null; onGiveAway(p.id) },
        )
    }
}
