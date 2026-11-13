package com.pantrypulse.app.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pantrypulse.app.domain.DateTextParser
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Reads the printed expiry date from a pack. Frames are read live a couple of times a
 * second; every date found is offered as a chip so the user confirms the right one.
 */
@Composable
fun ExpiryScanScreen(onResult: (LocalDate) -> Unit, onClose: () -> Unit) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateListOf<DateTextParser.Candidate>() }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var status by remember { mutableStateOf("Hold the date inside the frame — e.g. “EXP 12/08/26”") }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun accept(candidates: List<DateTextParser.Candidate>) {
        candidates.forEach { cand ->
            val i = found.indexOfFirst { it.date == cand.date }
            if (i == -1) found.add(cand) else if (cand.score > found[i].score) found[i] = cand
        }
        found.sortWith(compareByDescending<DateTextParser.Candidate> { it.score }.thenByDescending { it.date })
        if (selected == null) selected = found.firstOrNull()?.date
        if (found.isNotEmpty()) status = "Found ${found.size} date${if (found.size > 1) "s" else ""}. Tap the expiry date."
    }

    val analyzer = remember {
        var last = 0L
        ImageAnalysis.Analyzer { proxy ->
            val now = System.currentTimeMillis()
            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
            val media = proxy.image
            if (media == null || now - last < 450) { proxy.close(); return@Analyzer }
            last = now
            recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { text -> DateTextParser.parse(text.text).takeIf { it.isNotEmpty() }?.let(::accept) }
                .addOnCompleteListener { proxy.close() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main) {
            status = "Reading photo…"
            try {
                val dates = DateTextParser.parse(recognizeText(InputImage.fromFilePath(context, uri)).text)
                if (dates.isEmpty()) status = "No date found in that photo. Try a sharper, closer shot." else accept(dates)
            } catch (e: Exception) {
                status = "Couldn't read that photo."
            }
        }
    }

    CameraPermissionGate("PantryPulse reads the printed expiry date from your photo, on your phone. Nothing is uploaded.") {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize(), analyzer = analyzer)
            Box(
                Modifier.align(Alignment.Center).padding(bottom = 120.dp).fillMaxWidth(0.85f).height(120.dp)
                    .border(3.dp, c.accent, RoundedCornerShape(24.dp)),
            )
            Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClose)
                Spacer(Modifier.weight(1f))
                RoundButton(Icons.Outlined.PhotoLibrary, "Pick a photo", {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                PulseCard(padding = 16.dp) {
                    Text("Scan expiry date", style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    if (found.isNotEmpty()) {
                        ChipRow {
                            found.take(6).forEach { cand ->
                                PillChip(cand.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), selected == cand.date, { selected = cand.date })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (found.isNotEmpty()) SecondaryButton("Reset", { found.clear(); selected = null }, Modifier.weight(1f))
                        PrimaryButton(
                            selected?.let { "Use · ${Expiry.label(it).lowercase()}" } ?: "Waiting for a date…",
                            { selected?.let(onResult) }, Modifier.weight(2f), accent = true, enabled = selected != null,
                        )
                    }
                }
            }
        }
    }
}
