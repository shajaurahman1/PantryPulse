package com.pantrypulse.app.ui.scan

import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pantrypulse.app.domain.ProductCode
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.theme.PulseTheme
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScanScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val c = PulseTheme.colors
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    val delivered = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
            ).build()
        )
    }
    val analyzer = remember {
        ImageAnalysis.Analyzer { proxy ->
            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
            val media = proxy.image
            if (media == null || delivered.get()) { proxy.close(); return@Analyzer }
            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { codes ->
                    // Linear barcodes hold the number directly; QR / Data Matrix hold a link or GS1 string
                    // that has to be unpacked into the product number.
                    val value = codes.firstNotNullOfOrNull { code ->
                        val raw = code.rawValue?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
                        if (code.format == Barcode.FORMAT_QR_CODE || code.format == Barcode.FORMAT_DATA_MATRIX) ProductCode.fromQr(raw) else raw
                    }
                    if (value != null) {
                        if (delivered.compareAndSet(false, true)) onResult(value)
                    } else {
                        hint = if (codes.isNotEmpty()) "That QR code has no product number. Try the barcode on the pack." else null
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }
    }

    CameraPermissionGate("PantryPulse uses the camera to read product barcodes and QR codes. Nothing is recorded or uploaded.") {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize(), analyzer = analyzer, onCamera = { camera = it })
            // Viewfinder
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(180.dp)
                    .border(3.dp, c.accent, RoundedCornerShape(28.dp)),
            )
            Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClose)
                Spacer(Modifier.weight(1f))
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    RoundButton(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, "Toggle torch", {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    })
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                PulseCard(padding = 16.dp) {
                    Text("Point at the barcode or QR code", style = MaterialTheme.typography.titleMedium)
                    Text(hint ?: "It scans automatically. Or type the number:", style = MaterialTheme.typography.bodySmall, color = if (hint != null) c.accent else c.muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PulseTextField(manual, { manual = it.filter(Char::isDigit) }, "Barcode number", Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        PrimaryButton("Use", { if (manual.length >= 6 && delivered.compareAndSet(false, true)) onResult(manual) }, enabled = manual.length >= 6)
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
