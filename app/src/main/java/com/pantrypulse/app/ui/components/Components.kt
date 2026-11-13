package com.pantrypulse.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Countertops
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ExpiryStatus
import com.pantrypulse.app.ui.theme.Bricolage
import com.pantrypulse.app.ui.theme.DmMono
import com.pantrypulse.app.ui.theme.Eyebrow
import com.pantrypulse.app.ui.theme.PulseTheme
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

fun locationIcon(iconKey: String?): ImageVector = when (iconKey) {
    "fridge" -> Icons.Filled.Kitchen
    "freezer" -> Icons.Filled.AcUnit
    "pantry" -> Icons.Filled.Inventory2
    "cupboard" -> Icons.Filled.Inventory
    "counter" -> Icons.Filled.Countertops
    "bathroom" -> Icons.Filled.Bathtub
    "medicine" -> Icons.Filled.MedicalServices
    else -> Icons.Filled.Place
}

private val qtyFormat = DecimalFormat("#.##")
fun formatQty(qty: Double, unit: String): String = "${qtyFormat.format(qty)} $unit"

/** The pulse mark from the logo, drawn as a stroke. */
@Composable
fun PulseMark(modifier: Modifier = Modifier, color: Color = PulseTheme.colors.ink, stroke: Dp = 2.5.dp) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.55f)
            lineTo(w * 0.3f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.12f)
            lineTo(w * 0.62f, h * 0.92f)
            lineTo(w * 0.72f, h * 0.55f)
            lineTo(w, h * 0.55f)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun PulseLogo(modifier: Modifier = Modifier, color: Color = PulseTheme.colors.ink) {
    Row(modifier.semantics { contentDescription = "PantryPulse" }, verticalAlignment = Alignment.CenterVertically) {
        PulseMark(Modifier.size(width = 30.dp, height = 18.dp), color)
        Spacer(Modifier.width(8.dp))
        Text("pantrypulse", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.6).sp, color = color)
    }
}

/** White rounded card with a hairline border, as used across the design. */
@Composable
fun PulseCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = PulseTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(28.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.hairline),
    ) { Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

@Composable
fun EyebrowText(text: String, modifier: Modifier = Modifier, color: Color = PulseTheme.colors.muted) {
    Text(text.uppercase(), style = Eyebrow, color = color, modifier = modifier)
}

/** Round icon button: white with a border, or filled ink. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 52.dp,
) {
    val colors = PulseTheme.colors
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (filled) colors.ink else colors.card)
            .border(1.dp, if (filled) colors.ink else colors.hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (filled) colors.onInk else colors.ink) }
}

@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp, color: Color = PulseTheme.colors.ink, onClick: (() -> Unit)? = null) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(color)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .semantics { contentDescription = "Profile $name" },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp, color = Color(0xFFF2EFE9))
    }
}

/** Pill chip: ink when selected, plain text otherwise. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null) {
    val colors = PulseTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) colors.ink else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("chip_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count != null) "$label $count" else label,
            fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = if (selected) colors.paper else colors.ink, maxLines = 1,
        )
    }
}

/** The white segmented control holding All / Food / Cosmetics / Medicine. */
@Composable
fun SegmentedPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(50)).padding(6.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (i == selected) colors.ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 13.dp)
                    .testTag("segment_$label"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                    color = if (i == selected) colors.paper else colors.ink,
                )
            }
        }
    }
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/** What the ticket stub shows: a number and a unit, e.g. "2" / "DAYS". */
fun stubFor(expiry: LocalDate?, today: LocalDate = LocalDate.now()): Pair<String, String> {
    val days = Expiry.daysLeft(expiry, today) ?: return "—" to "NO DATE"
    val a = abs(days)
    val suffix = if (days < 0) " AGO" else ""
    return when {
        a < 60 -> "$a" to (if (a == 1L) "DAY" else "DAYS") + suffix
        a < 730 -> "${a / 30}" to (if (a / 30 == 1L) "MONTH" else "MONTHS") + suffix
        else -> "${a / 365}" to "YEARS$suffix"
    }
}

@Composable
private fun stubColors(status: ExpiryStatus): Pair<Color, Color> {
    val c = PulseTheme.colors
    return when (status) {
        ExpiryStatus.EXPIRED -> c.ink to c.accent
        ExpiryStatus.TODAY, ExpiryStatus.SOON -> c.accent to Color(0xFF17140F)
        ExpiryStatus.FRESH -> c.peach to c.ink
        ExpiryStatus.NO_DATE -> c.hairline to c.muted
    }
}

/**
 * The ticket card from the design: a coloured stub with days left, a perforated edge,
 * the product details and a round check button that uses one.
 */
@Composable
fun TicketCard(
    product: ProductEntity,
    meta: String,
    warningDays: Int,
    onClick: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    val status = Expiry.status(product.expiryDate, warningDays)
    val (stubBg, stubFg) = stubColors(status)
    val (number, unit) = stubFor(product.expiryDate)
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(colors.card)
            .border(1.dp, colors.hairline, shape).clickable(onClick = onClick)
            .testTag("product_${product.name}"),
    ) {
        Column(
            Modifier.width(76.dp).fillMaxHeight().background(stubBg).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(number, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 36.sp, color = stubFg)
            Text(unit, fontFamily = DmMono, fontSize = if (unit.length > 8) 9.sp else 12.sp, letterSpacing = 1.sp, color = stubFg, textAlign = TextAlign.Center, maxLines = 1)
        }
        Perforation(Modifier.fillMaxHeight().width(4.dp), stubBg, colors.card)
        Row(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (product.imageUrl != null) {
                AsyncImage(product.imageUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(product.name, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(meta, fontFamily = DmMono, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).border(1.dp, colors.hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onCheck)
                    .semantics { contentDescription = "Use one ${product.name}" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.ink) }
        }
    }
}

/** Dashed tear line between the stub and the body of a ticket. */
@Composable
private fun Perforation(modifier: Modifier, stub: Color, body: Color) {
    Canvas(modifier.background(body)) {
        drawRect(stub, size = size.copy(width = size.width / 2))
        val dash = 6.dp.toPx()
        var y = 3.dp.toPx()
        while (y < size.height) {
            drawLine(body, Offset(size.width / 4, y), Offset(size.width / 4, (y + dash / 2).coerceAtMost(size.height)), strokeWidth = size.width / 2)
            y += dash
        }
    }
}

/** Seven rounded bars for the coming week; the busiest day is highlighted. */
@Composable
fun WeekPulseChart(counts: List<Int>, startDay: LocalDate, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    val top = counts.maxOrNull() ?: 0
    val max = top.coerceAtLeast(1)
    val peak = if (top > 0) counts.indexOf(top) else -1
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, count ->
            val day = startDay.plusDays(i.toLong())
            Column(
                Modifier.weight(1f).semantics { contentDescription = "${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: $count expiring" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val barHeight = 14.dp + (96.dp - 14.dp) * (count.toFloat() / max)
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp).height(barHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == peak) colors.accent else colors.peach),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontFamily = DmMono, fontSize = 13.sp, color = colors.muted,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, color = PulseTheme.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = PulseTheme.colors
    PulseCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(colors.peachSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(actionLabel, onAction)
            }
        }
    }
}

/** Ink pill button (primary) or orange pill (accent). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = PulseTheme.colors
    val bg = when {
        !enabled -> colors.hairline
        accent -> colors.accent
        else -> colors.ink
    }
    val fg = when {
        !enabled -> colors.muted
        accent -> Color(0xFF17140F)
        else -> colors.paper
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("button_$text"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = fg, maxLines = 1)
    }
}

/** Outlined pill button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = PulseTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(50)).background(colors.card).border(1.dp, colors.hairline, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("button_$text"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink, maxLines = 1)
    }
}

/** A tappable row inside a card: icon, title, optional subtitle. */
@Composable
fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit, subtitle: String? = null, tint: Color = PulseTheme.colors.ink) {
    val colors = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag("action_$title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.paper), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}
