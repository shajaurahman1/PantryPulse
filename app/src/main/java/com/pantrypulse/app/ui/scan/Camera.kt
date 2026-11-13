package com.pantrypulse.app.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pantrypulse.app.domain.OcrLine
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Shows [content] once camera access is granted, otherwise explains why it is needed. */
@Composable
fun CameraPermissionGate(reason: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    if (granted) {
        content()
    } else {
        val c = PulseTheme.colors
        Column(
            Modifier.fillMaxSize().background(c.paper).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = c.accent, modifier = Modifier.padding(bottom = 12.dp))
            Text("Camera access needed", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
            if (denied) {
                Spacer(Modifier.height(8.dp))
                Text("If you chose \"Don't allow\", enable the camera for PantryPulse in your phone's Settings › Apps.", style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Allow camera", { launcher.launch(Manifest.permission.CAMERA) }, accent = true)
        }
    }
}

/**
 * CameraX preview bound to the screen's lifecycle. Supply an [analyzer] for live frame
 * processing and/or an [imageCapture] use case for still photos.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null,
    imageCapture: ImageCapture? = null,
    onCamera: (Camera) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)
            if (analyzer != null) {
                useCases += ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, analyzer) }
            }
            if (imageCapture != null) useCases += imageCapture
            try {
                provider?.unbindAll()
                val camera = provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
                camera?.let(onCamera)
            } catch (_: Exception) {
                // No usable back camera (e.g. emulator); the preview simply stays blank.
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, modifier)
}

private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

suspend fun recognizeText(image: InputImage): MlText = suspendCancellableCoroutine { cont ->
    recognizer.process(image)
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resumeWithException(it) }
}

fun MlText.toOcrLines(): List<OcrLine> = textBlocks.flatMap { block ->
    block.lines.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        OcrLine(line.text, box.left, box.top, box.right, box.bottom)
    }
}

/** Takes a still photo and returns it as an ML Kit image (the proxy is closed after use by [use]). */
fun takePhoto(context: Context, capture: ImageCapture, use: (InputImage?, ImageProxy?) -> Unit) {
    capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun onCaptureSuccess(image: ImageProxy) {
            val media = image.image
            if (media == null) { image.close(); use(null, null); return }
            use(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees), image)
        }

        override fun onError(exception: ImageCaptureException) = use(null, null)
    })
}
