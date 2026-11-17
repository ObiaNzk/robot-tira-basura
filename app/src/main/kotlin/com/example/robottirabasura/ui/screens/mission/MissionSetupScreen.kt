package com.example.robottirabasura.ui.screens.mission

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.robottirabasura.R
import com.example.robottirabasura.domain.model.QrKind
import com.example.robottirabasura.domain.model.QrTarget
import com.example.robottirabasura.ui.components.CommandButton
import com.example.robottirabasura.ui.components.CommandStyle
import com.example.robottirabasura.ui.components.ConfirmDialog
import com.example.robottirabasura.ui.components.SiteMapCanvas
import com.example.robottirabasura.ui.components.StatusChip
import com.example.robottirabasura.ui.components.Tone
import com.example.robottirabasura.ui.components.label
import com.example.robottirabasura.ui.preview.PreviewData
import com.example.robottirabasura.ui.theme.RobotTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@Composable
fun MissionSetupRoute(onOpenSupervision: () -> Unit, viewModel: MissionSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }
    LaunchedEffect(state.camera) {
        if (state.camera == CameraState.PIDIENDO_PERMISO) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.onCameraPermissionResult(true) else permission.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is MissionSetupEffect.OpenSupervision) onOpenSupervision() }
    }
    MissionSetupContent(
        state = state,
        onOpenCamera = viewModel::onOpenCamera,
        onCloseCamera = viewModel::onCloseCamera,
        onSimulateCode = viewModel::onCodeScanned,
        onClear = viewModel::onClear,
        onStart = viewModel::onStart,
        onConfirmStart = viewModel::onConfirmStart,
        onDismissStart = viewModel::onDismissStart,
        camera = { QrCameraPreview(onCode = viewModel::onCodeScanned) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissionSetupContent(
    state: MissionSetupUiState,
    onOpenCamera: () -> Unit,
    onCloseCamera: () -> Unit,
    onSimulateCode: (String) -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
    onConfirmStart: () -> Unit,
    onDismissStart: () -> Unit,
    camera: @Composable () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.mission_title), style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetCard(stringResource(R.string.mission_bags), state.bolsas, Icons.Default.ShoppingBag, Modifier.weight(1f))
                TargetCard(stringResource(R.string.mission_bin), state.tacho, Icons.Default.DeleteOutline, Modifier.weight(1f))
            }
        }
        item {
            when (state.camera) {
                CameraState.ESCANEANDO -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    ) {
                        camera()
                        Text(
                            stringResource(R.string.mission_scan_hint),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        )
                    }
                    Text(stringResource(R.string.mission_privacy), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onCloseCamera, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mission_stop_scan))
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.camera == CameraState.SIN_PERMISO) {
                        Text(stringResource(R.string.mission_no_permission), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text(stringResource(R.string.mission_scan), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            Column {
                Text(stringResource(R.string.mission_simulate), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.demoCodes.forEach { code ->
                        AssistChip(onClick = { onSimulateCode(code) }, label = { Text(code.removePrefix("RTB:")) })
                    }
                }
            }
        }
        state.feedback?.let { feedback ->
            item {
                when (feedback) {
                    is ScanFeedback.Read -> StatusChip(
                        stringResource(
                            if (feedback.target.kind == QrKind.BOLSAS) R.string.mission_read_bags else R.string.mission_read_bin,
                            feedback.target.id,
                        ),
                        Tone.OK,
                    )
                    is ScanFeedback.Invalid -> StatusChip(stringResource(feedback.error.label), Tone.ERROR)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mission_route), style = MaterialTheme.typography.titleMedium)
                    when (val preview = state.preview) {
                        PlanPreview.NeedsQr -> Text(stringResource(R.string.disabled_qr))
                        is PlanPreview.Unreachable -> Text(
                            stringResource(R.string.mission_unreachable, stringResource(preview.leg.label)),
                            color = MaterialTheme.colorScheme.error,
                        )
                        is PlanPreview.Ready -> {
                            Text(stringResource(R.string.mission_route_value, preview.distanceMeters, preview.etaSeconds))
                            Text(stringResource(R.string.mission_macro), style = MaterialTheme.typography.labelLarge)
                            preview.steps.forEachIndexed { i, step ->
                                Text("${i + 1}. " + stringResource(step.label), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    SiteMapCanvas(
                        site = state.site,
                        description = stringResource(R.string.mission_route),
                        robot = state.telemetry?.position,
                        headingDegrees = state.telemetry?.headingDegrees ?: 90,
                        route = state.route,
                        bags = state.bolsas?.cell,
                        bin = state.tacho?.cell,
                        obstacles = state.telemetry?.extraObstacles.orEmpty(),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val status = state.startStatus) {
                    StartStatus.Idle -> Unit
                    StartStatus.Sending -> StatusChip(stringResource(R.string.mission_starting), Tone.NEUTRAL)
                    is StartStatus.Rejected -> Text(
                        stringResource(R.string.mission_rejected, status.reason?.let { stringResource(it.label) }.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    StartStatus.NoAnswer -> Text(stringResource(R.string.mission_no_answer), color = MaterialTheme.colorScheme.error)
                }
                CommandButton(stringResource(R.string.cmd_start), state.start, onStart, style = CommandStyle.PRIMARY)
                if (state.bolsas != null || state.tacho != null) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(stringResource(R.string.mission_clear), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
    if (state.confirmingStart) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_title_start),
            body = stringResource(R.string.confirm_body_start),
            onConfirm = onConfirmStart,
            onDismiss = onDismissStart,
        )
    }
}

@Composable
private fun TargetCard(title: String, target: QrTarget?, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            if (target == null) {
                StatusChip(stringResource(R.string.mission_missing), Tone.NEUTRAL)
            } else {
                StatusChip(stringResource(R.string.mission_target, target.id, target.cell.x, target.cell.y), Tone.OK)
            }
        }
    }
}

/** CameraX preview plus an ML Kit analyzer (bundled model: works offline) that reports every QR it reads. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentOnCode by rememberUpdatedState(onCode)
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(Unit) {
        onDispose {
            // Release the camera as soon as the preview leaves the screen.
            if (providerFuture.isDone) providerFuture.get().unbindAll()
            scanner.close()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            // TextureView-based preview: a SurfaceView ignores Compose clipping and draws over the rest of the list.
            val previewView = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
            providerFuture.addListener({
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes -> codes.mapNotNull { it.rawValue }.forEach(currentOnCode) }
                        .addOnCompleteListener { proxy.close() }
                }
                providerFuture.get().apply {
                    unbindAll()
                    bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@ComposePreview(showBackground = true, heightDp = 1200)
@Composable
private fun MissionSetupPreview() {
    RobotTheme {
        MissionSetupContent(
            state = PreviewData.missionSetupReady,
            onOpenCamera = {}, onCloseCamera = {}, onSimulateCode = {}, onClear = {}, onStart = {},
            onConfirmStart = {}, onDismissStart = {}, camera = {},
        )
    }
}
