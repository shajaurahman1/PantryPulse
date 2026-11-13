package com.pantrypulse.app.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.ReceiptItem
import com.pantrypulse.app.domain.ReceiptParser
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.formatQty
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One editable line in the receipt review list. */
data class ReceiptDraft(
    val include: Boolean,
    val name: String,
    val quantity: Double,
    val unit: String,
    val category: Category,
    val expiry: LocalDate,
) {
    companion object {
        fun from(item: ReceiptItem, today: LocalDate = LocalDate.now()) =
            ReceiptDraft(true, item.name, item.quantity, item.unit, item.category, item.category.suggestedExpiry(today))
    }
}

@Composable
fun ReceiptScanScreen(vm: MainViewModel, onDone: () -> Unit, onClose: () -> Unit) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf<ReceiptDraft>() }
    var reviewing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pasteText by remember { mutableStateOf<String?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    fun showItems(items: List<ReceiptItem>) {
        drafts.clear()
        drafts.addAll(items.map { ReceiptDraft.from(it) })
        problem = if (items.isEmpty()) "We couldn't find grocery lines on that receipt. Try a flatter, well-lit photo, or paste the text." else null
        reviewing = items.isNotEmpty()
    }

    fun read(image: InputImage, release: () -> Unit = {}) {
        busy = true
        scope.launch(Dispatchers.Main) {
            try {
                showItems(ReceiptParser.parseLines(recognizeText(image).toOcrLines()))
            } catch (e: Exception) {
                problem = "Couldn't read the receipt: ${e.message}"
            } finally {
                release()
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(InputImage.fromFilePath(context, uri))
    }

    if (reviewing) {
        ReceiptReview(vm, drafts, onBack = { reviewing = false }, onDone = onDone)
    } else {
        CameraPermissionGate("Snap your paper receipt to add everything you bought at once. The photo is read on your phone and never uploaded.") {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CameraPreview(Modifier.fillMaxSize(), imageCapture = capture)
                Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClose)
                    Spacer(Modifier.weight(1f))
                    RoundButton(Icons.Outlined.ContentPaste, "Paste receipt text", { pasteText = "" })
                    Spacer(Modifier.width(10.dp))
                    RoundButton(Icons.Outlined.PhotoLibrary, "Pick a screenshot", {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PulseCard(padding = 16.dp) {
                        Text("Scan a receipt", style = MaterialTheme.typography.titleMedium)
                        Text(
                            problem ?: "Lay the receipt flat and fill the screen. For a digital receipt, pick a screenshot or paste the text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (problem != null) MaterialTheme.colorScheme.error else c.muted,
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(c.accent).border(5.dp, Color.White, CircleShape)
                            .clickable(enabled = !busy) {
                                busy = true
                                takePhoto(context, capture) { image, proxy ->
                                    if (image == null) { busy = false; problem = "Couldn't take the photo. Try again." }
                                    else read(image) { proxy?.close() }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }

    pasteText?.let { text ->
        AlertDialog(
            onDismissRequest = { pasteText = null },
            title = { Text("Paste a digital receipt") },
            text = { PulseTextField(text, { pasteText = it }, "Receipt or order text", singleLine = false, minLines = 6) },
            confirmButton = { TextButton(onClick = { showItems(ReceiptParser.parseText(text)); pasteText = null }) { Text("Read items", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pasteText = null }) { Text("Cancel", color = c.muted) } },
        )
    }
}

@Composable
private fun ReceiptReview(vm: MainViewModel, drafts: MutableList<ReceiptDraft>, onBack: () -> Unit, onDone: () -> Unit) {
    val c = PulseTheme.colors
    val scope = rememberCoroutineScope()
    val locations by vm.locations.collectAsState()
    val count = drafts.count { it.include && it.name.isNotBlank() }
    Column(Modifier.fillMaxSize().background(c.paper).statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Scan again", onBack, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Check your items", style = MaterialTheme.typography.titleLarge)
                Text("Expiry dates are typical shelf lives — adjust any later.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(drafts) { i, d ->
                PulseCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d.include, { drafts[i] = d.copy(include = it) }, colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.ink))
                        PulseTextField(d.name, { drafts[i] = d.copy(name = it) }, "Item", Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            Text(
                                "${d.category.emoji} ${d.category.label}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.paper).clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            DropdownMenu(open, { open = false }) {
                                Category.entries.forEach { cat ->
                                    DropdownMenuItem({ Text("${cat.emoji} ${cat.label}") }, {
                                        drafts[i] = d.copy(category = cat, expiry = cat.suggestedExpiry()); open = false
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${formatQty(d.quantity, d.unit)} · exp. ${d.expiry.format(DateTimeFormatter.ofPattern("d MMM"))}",
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            EyebrowText("$count of ${drafts.size} selected", Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Select all", { drafts.indices.forEach { drafts[it] = drafts[it].copy(include = true) } })
                PrimaryButton("Add $count to pantry", {
                    scope.launch(Dispatchers.Main) {
                        val s = vm.session.value ?: return@launch
                        val hh = s.householdId ?: return@launch
                        val uid = s.userId ?: return@launch
                        val today = LocalDate.now()
                        vm.container.pantry.saveAll(drafts.filter { it.include && it.name.isNotBlank() }.map { d ->
                            ProductEntity(
                                householdId = hh, name = d.name.trim(), category = d.category, quantity = d.quantity, unit = d.unit,
                                expiryDate = d.expiry, purchaseDate = today, addedBy = uid,
                                locationId = locations.firstOrNull { it.name.equals(d.category.defaultLocation.label, true) }?.id,
                            )
                        })
                        vm.message("$count items added from your receipt.")
                        onDone()
                    }
                }, Modifier.weight(1f), accent = true, enabled = count > 0)
            }
        }
    }
}
