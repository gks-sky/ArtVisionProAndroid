package com.gkssky.artvisionpro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gkssky.artvisionpro.R
import com.gkssky.artvisionpro.camera.CameraController
import com.gkssky.artvisionpro.camera.CameraSourceType
import com.gkssky.artvisionpro.camera.CameraStatus
import com.gkssky.artvisionpro.camera.TransformMode
import com.gkssky.artvisionpro.export.ReferenceExportManager
import com.gkssky.artvisionpro.export.ReferenceExportOption
import com.gkssky.artvisionpro.export.ValueExportConfiguration
import com.gkssky.artvisionpro.reference.ReferenceImageController
import com.gkssky.artvisionpro.reference.fitInside
import com.gkssky.artvisionpro.processing.ValueMode
import com.gkssky.artvisionpro.processing.ValueThresholds
import com.gkssky.artvisionpro.processing.ValueSettingsState
import com.gkssky.artvisionpro.ui.components.CameraPreview
import com.gkssky.artvisionpro.ui.components.CameraSelector
import com.gkssky.artvisionpro.ui.components.UvcCameraPreview
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(referenceImageController: ReferenceImageController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context, lifecycleOwner) { CameraController(context, lifecycleOwner) }
    val state by controller.state.collectAsState()
    val referenceState by referenceImageController.state.collectAsState()
    val referenceTransform by referenceImageController.transform.collectAsState()
    val cameraTransform by referenceImageController.cameraTransform.collectAsState()
    val traceTransform by referenceImageController.traceTransform.collectAsState()
    val transformMode by referenceImageController.mode.collectAsState()
    val controlsHidden by referenceImageController.controlsHidden.collectAsState()
    val moveModeActive by referenceImageController.moveModeActive.collectAsState()
    val toolsExpanded by referenceImageController.toolsExpanded.collectAsState()
    val breakdownExpanded by referenceImageController.breakdownExpanded.collectAsState()
    val verticalFlipEnabled by referenceImageController.verticalFlipEnabled.collectAsState()
    val horizontalFlipEnabled by referenceImageController.horizontalFlipEnabled.collectAsState()
    val strobeControlsExpanded by referenceImageController.strobeControlsExpanded.collectAsState()
    val strobeEnabled by referenceImageController.strobeEnabled.collectAsState()
    val strobeIntervalMs by referenceImageController.strobeIntervalMs.collectAsState()
    val valueMode by referenceImageController.valueMode.collectAsState()
    val valueThresholds by referenceImageController.valueThresholds.collectAsState()
    val valueSettings by referenceImageController.valueSettings.collectAsState()
    val valueSettingsExpanded by referenceImageController.valueSettingsExpanded.collectAsState()
    val exportManager = remember(context) { ReferenceExportManager(context.applicationContext) }
    val exportScope = rememberCoroutineScope()
    var exportInProgress by remember { mutableStateOf(false) }
    var pendingExportOption by remember { mutableStateOf<ReferenceExportOption?>(null) }
    var appActive by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val strobeAlpha = remember { Animatable(1f) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        controller::onPermissionResult,
    )
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { referenceImageController.select(it, persistPermission = true) }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { referenceImageController.select(it, persistPermission = true) }
    }
    val performExport: (ReferenceExportOption) -> Unit = { option ->
        val sourceUri = referenceState.uri
        if (sourceUri != null && !exportInProgress) {
            exportInProgress = true
            exportScope.launch {
                val result = runCatching {
                    exportManager.export(
                        sourceUri = sourceUri,
                        configuration = ValueExportConfiguration(valueMode, valueThresholds, valueSettings),
                        option = option,
                    )
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) {
                        "Saved to Pictures/ArtVision Pro"
                    } else {
                        "Export failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
                exportInProgress = false
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val option = pendingExportOption
        pendingExportOption = null
        if (granted && option != null) performExport(option)
        else if (!granted) Toast.makeText(context, "Storage permission is required to export.", Toast.LENGTH_SHORT).show()
    }
    val requestExport: (ReferenceExportOption) -> Unit = { option ->
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingExportOption = option
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performExport(option)
        }
    }
    val openReference = {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            documentPickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
    }

    DisposableEffect(controller) { onDispose(controller::close) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            appActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(strobeEnabled, strobeIntervalMs, referenceState.hasReference, appActive) {
        if (strobeEnabled && referenceState.hasReference && appActive) {
            val phaseDurationMs = (strobeIntervalMs / 4).coerceAtLeast(1)
            val finalHoldMs = (strobeIntervalMs - phaseDurationMs * 3).coerceAtLeast(1)
            strobeAlpha.snapTo(0f)
            while (true) {
                strobeAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(phaseDurationMs, easing = FastOutSlowInEasing),
                )
                delay(phaseDurationMs.toLong())
                strobeAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(phaseDurationMs, easing = FastOutSlowInEasing),
                )
                delay(finalHoldMs.toLong())
            }
        } else {
            strobeAlpha.snapTo(1f)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (controlsHidden) {
                HiddenControlsToolbar(referenceImageController::toggleControlsVisibility)
            } else ReferenceToolbar(
                hasReference = referenceState.hasReference,
                isLoading = referenceState.isLoading,
                transformMode = transformMode,
                moveModeActive = moveModeActive,
                toolsExpanded = toolsExpanded,
                breakdownExpanded = breakdownExpanded,
                strobeControlsExpanded = strobeControlsExpanded,
                strobeEnabled = strobeEnabled,
                strobeIntervalMs = strobeIntervalMs,
                valueMode = valueMode,
                valueThresholds = valueThresholds,
                valueSettings = valueSettings,
                valueSettingsExpanded = valueSettingsExpanded,
                verticalFlipEnabled = verticalFlipEnabled,
                horizontalFlipEnabled = horizontalFlipEnabled,
                opacity = referenceState.opacity,
                onOpen = openReference,
                onRemove = referenceImageController::remove,
                onActivateReferenceMode = referenceImageController::activateReferenceMode,
                onActivateCameraMode = referenceImageController::activateCameraMode,
                onToggleMoveMode = referenceImageController::toggleMoveMode,
                onToggleControlsVisibility = referenceImageController::toggleControlsVisibility,
                onToggleTools = referenceImageController::toggleTools,
                onToggleBreakdown = referenceImageController::toggleBreakdown,
                onToggleStrobeControls = referenceImageController::toggleStrobeControls,
                onToggleStrobe = referenceImageController::toggleStrobe,
                onStrobeIntervalChange = referenceImageController::setStrobeInterval,
                onToggleVerticalFlip = referenceImageController::toggleVerticalFlip,
                onToggleHorizontalFlip = referenceImageController::toggleHorizontalFlip,
                onValueModeSelected = referenceImageController::selectValueMode,
                onToggleValueSettings = referenceImageController::toggleValueSettings,
                onValueThresholdChange = referenceImageController::setValueThreshold,
                onSimplifyChange = referenceImageController::setSimplifyAmount,
                onNoiseRemovalChange = referenceImageController::setNoiseRemovalAmount,
                onEdgeSmoothnessChange = referenceImageController::setEdgeSmoothnessAmount,
                onAutoValues = referenceImageController::autoValueThresholds,
                onResetValues = referenceImageController::resetValueThresholds,
                onOpacityChange = referenceImageController::setOpacity,
                cameraSelector = {
                    CameraSelector(
                        state.cameras,
                        state.selectedCamera,
                        controller::selectCamera,
                        modifier = Modifier.height(48.dp),
                    )
                },
                onRefreshCameras = controller::refreshCameras,
            )
        },
        bottomBar = {
            if (!controlsHidden) ReferenceBottomPanel(
                onReset = referenceImageController::resetImageToViewport,
                hasReference = referenceState.hasReference,
                valueMode = valueMode,
                exportInProgress = exportInProgress,
                onExport = requestExport,
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).background(Black),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = traceTransform.offsetX
                    translationY = traceTransform.offsetY
                    scaleX = traceTransform.scale
                    scaleY = traceTransform.scale
                },
            ) {
            if (state.status == CameraStatus.PERMISSION_REQUIRED &&
                state.selectedCamera?.sourceType != CameraSourceType.USB_UVC
            ) {
                // Permission UI is rendered outside the shared trace layer below.
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = cameraTransform.offsetX
                        translationY = cameraTransform.offsetY
                        scaleX = if (horizontalFlipEnabled) -cameraTransform.scale else cameraTransform.scale
                        scaleY = if (verticalFlipEnabled) -cameraTransform.scale else cameraTransform.scale
                        rotationZ = cameraTransform.rotation
                    },
                ) {
                    if (state.selectedCamera?.sourceType == CameraSourceType.USB_UVC) {
                        UvcCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                    } else {
                        CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            referenceState.displayBitmap?.let { bitmap ->
                val borderInsetPx = with(density) { 8.dp.toPx() }
                val fitted = fitInside(
                    bitmap.width,
                    bitmap.height,
                    (viewportWidthPx - borderInsetPx).toInt(),
                    (viewportHeightPx - borderInsetPx).toInt(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(with(density) { fitted.first.toDp() }, with(density) { fitted.second.toDp() })
                        .graphicsLayer {
                            translationX = referenceTransform.offsetX
                            translationY = referenceTransform.offsetY
                            scaleX = referenceTransform.scale
                            scaleY = referenceTransform.scale
                            rotationZ = referenceTransform.rotation
                        }
                        .then(
                            if (moveModeActive && transformMode == TransformMode.REFERENCE) {
                                Modifier.border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(4.dp))
                            } else Modifier,
                        ),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Reference image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        alpha = referenceState.opacity * strobeAlpha.value,
                    )
                    if (moveModeActive && transformMode == TransformMode.REFERENCE) SelectionHandles()
                }
            }
            }

            if (state.status == CameraStatus.PERMISSION_REQUIRED &&
                state.selectedCamera?.sourceType != CameraSourceType.USB_UVC
            ) {
                PermissionPanel(onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }

            if (!moveModeActive || transformMode == TransformMode.CAMERA || referenceState.hasReference) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(
                        referenceImageController,
                        transformMode,
                        moveModeActive,
                        viewportWidthPx,
                        viewportHeightPx,
                    ) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            if (!moveModeActive) {
                                referenceImageController.applyTraceGesture(
                                    centroid.x, centroid.y, pan.x, pan.y, zoom,
                                    viewportWidthPx, viewportHeightPx,
                                )
                            } else if (transformMode == TransformMode.REFERENCE) {
                                referenceImageController.applyGesture(
                                    centroid.x, centroid.y, pan.x, pan.y, zoom, rotation,
                                    viewportWidthPx, viewportHeightPx,
                                )
                            } else {
                                referenceImageController.applyCameraGesture(
                                    centroid.x, centroid.y, pan.x, pan.y, zoom, rotation,
                                    viewportWidthPx, viewportHeightPx,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReferenceToolbar(
    hasReference: Boolean,
    isLoading: Boolean,
    transformMode: TransformMode,
    moveModeActive: Boolean,
    toolsExpanded: Boolean,
    breakdownExpanded: Boolean,
    strobeControlsExpanded: Boolean,
    strobeEnabled: Boolean,
    strobeIntervalMs: Int,
    valueMode: ValueMode,
    valueThresholds: ValueThresholds,
    valueSettings: ValueSettingsState,
    valueSettingsExpanded: Boolean,
    verticalFlipEnabled: Boolean,
    horizontalFlipEnabled: Boolean,
    opacity: Float,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onActivateReferenceMode: () -> Unit,
    onActivateCameraMode: () -> Unit,
    onToggleMoveMode: () -> Unit,
    onToggleControlsVisibility: () -> Unit,
    onToggleTools: () -> Unit,
    onToggleBreakdown: () -> Unit,
    onToggleStrobeControls: () -> Unit,
    onToggleStrobe: () -> Unit,
    onStrobeIntervalChange: (Int) -> Unit,
    onToggleVerticalFlip: () -> Unit,
    onToggleHorizontalFlip: () -> Unit,
    onValueModeSelected: (ValueMode) -> Unit,
    onToggleValueSettings: () -> Unit,
    onValueThresholdChange: (ValueMode, Int, Int) -> Unit,
    onSimplifyChange: (Int) -> Unit,
    onNoiseRemovalChange: (Int) -> Unit,
    onEdgeSmoothnessChange: (Int) -> Unit,
    onAutoValues: () -> Unit,
    onResetValues: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    cameraSelector: @Composable () -> Unit,
    onRefreshCameras: () -> Unit,
) {
    BoxWithConstraints {
        val singleRow = maxWidth >= 900.dp
        val rowCount = (if (singleRow) 1 else 2) +
            (if (toolsExpanded) 1 else 0) +
            (if (toolsExpanded && strobeControlsExpanded) 1 else 0) +
            (if (toolsExpanded && breakdownExpanded) 1 else 0) +
            (if (toolsExpanded && breakdownExpanded && valueSettingsExpanded && valueMode.thresholdCount > 0) 1 else 0)
        TopAppBar(
        title = {
            Column(Modifier.fillMaxWidth().padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactToolbarButton("Open", android.R.drawable.ic_menu_gallery, "Open reference", !isLoading, onOpen)
                    CompactToolbarButton(
                        if (moveModeActive) "Moving" else "Locked Trace",
                        android.R.drawable.ic_menu_directions,
                        "Toggle independent move mode",
                        true, onToggleMoveMode, active = moveModeActive,
                    )
                    if (moveModeActive) {
                        ModeSelector(
                            mode = transformMode,
                            referenceEnabled = hasReference,
                            onReference = onActivateReferenceMode,
                            onCamera = onActivateCameraMode,
                        )
                    }
                    Box(modifier = Modifier.size(width = 160.dp, height = 48.dp)) { cameraSelector() }
                    if (singleRow) {
                        OpacityControl(hasReference, opacity, onOpacityChange, Modifier.weight(1f))
                        CompactToolbarButton(
                            "Tools", android.R.drawable.ic_menu_manage, "Toggle tools",
                            true, onToggleTools, active = toolsExpanded,
                        )
                        CompactToolbarButton(
                            "Hide", android.R.drawable.ic_menu_view, "Hide controls",
                            true, onToggleControlsVisibility,
                        )
                        ToolbarOverflowMenu(hasReference, onRemove, onRefreshCameras)
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
                if (!singleRow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OpacityControl(hasReference, opacity, onOpacityChange, Modifier.weight(1f))
                        CompactToolbarButton(
                            "Tools", android.R.drawable.ic_menu_manage, "Toggle tools",
                            true, onToggleTools, active = toolsExpanded,
                        )
                        CompactToolbarButton(
                            "Hide", android.R.drawable.ic_menu_view, "Hide controls",
                            true, onToggleControlsVisibility,
                        )
                        ToolbarOverflowMenu(hasReference, onRemove, onRefreshCameras)
                    }
                }
                if (toolsExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactToolbarButton(
                            "Strobe", android.R.drawable.ic_menu_slideshow, "Show Strobe controls",
                            true, onToggleStrobeControls, active = strobeEnabled, fixedWidth = 110.dp,
                        )
                        CompactToolbarButton(
                            "Values", android.R.drawable.ic_menu_sort_by_size, "Toggle Values",
                            true, onToggleBreakdown, active = breakdownExpanded, fixedWidth = 110.dp,
                        )
                        CompactToolbarButton(
                            "Vertical Flip", android.R.drawable.ic_menu_revert, "Toggle vertical camera preview flip",
                            true, onToggleVerticalFlip, active = verticalFlipEnabled, fixedWidth = 120.dp,
                        )
                        CompactToolbarButton(
                            "Horizontal Flip", android.R.drawable.ic_menu_revert, "Toggle horizontal camera preview flip",
                            true, onToggleHorizontalFlip, active = horizontalFlipEnabled, fixedWidth = 130.dp,
                        )
                    }
                }
                if (toolsExpanded && strobeControlsExpanded) {
                    StrobeControlRow(
                        hasReference = hasReference,
                        enabled = strobeEnabled,
                        intervalMs = strobeIntervalMs,
                        onToggle = onToggleStrobe,
                        onIntervalChange = onStrobeIntervalChange,
                    )
                }
                if (toolsExpanded && breakdownExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ValueMode.entries.forEach { mode ->
                            CompactToolbarButton(
                                mode.label,
                                android.R.drawable.ic_menu_gallery,
                                "Use ${mode.label}",
                                hasReference,
                                { onValueModeSelected(mode) },
                                active = valueMode == mode,
                                fixedWidth = 104.dp,
                            )
                        }
                        CompactToolbarButton(
                            "Value Settings",
                            android.R.drawable.ic_menu_preferences,
                            "Show Value Settings",
                            hasReference && valueMode.thresholdCount > 0,
                            onToggleValueSettings,
                            active = valueSettingsExpanded,
                            fixedWidth = 132.dp,
                        )
                    }
                }
                if (toolsExpanded && breakdownExpanded && valueSettingsExpanded) {
                    ValueSettingsPanel(
                        mode = valueMode,
                        thresholds = valueThresholds,
                        onThresholdChange = onValueThresholdChange,
                        settings = valueSettings,
                        onSimplifyChange = onSimplifyChange,
                        onNoiseRemovalChange = onNoiseRemovalChange,
                        onEdgeSmoothnessChange = onEdgeSmoothnessChange,
                        onAuto = onAutoValues,
                        onReset = onResetValues,
                    )
                }
            }
        },
        expandedHeight = (rowCount * 52 - 4).dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        )
    }
}

private val ValueMode.thresholdCount: Int
    get() = when (this) {
        ValueMode.TWO_VALUES -> 1
        ValueMode.THREE_VALUES -> 2
        ValueMode.FIVE_VALUES -> 4
        else -> 0
    }

@Composable
private fun ValueSettingsPanel(
    mode: ValueMode,
    thresholds: ValueThresholds,
    settings: ValueSettingsState,
    onThresholdChange: (ValueMode, Int, Int) -> Unit,
    onSimplifyChange: (Int) -> Unit,
    onNoiseRemovalChange: (Int) -> Unit,
    onEdgeSmoothnessChange: (Int) -> Unit,
    onAuto: () -> Unit,
    onReset: () -> Unit,
) {
    val labels = when (mode) {
        ValueMode.TWO_VALUES -> listOf("Dark / Light")
        ValueMode.THREE_VALUES -> listOf("Dark", "Light")
        ValueMode.FIVE_VALUES -> listOf("T1", "T2", "T3", "T4")
        else -> emptyList()
    }
    val values = thresholds.forMode(mode)
    val sliders = buildList {
        labels.forEachIndexed { index, label ->
            add(ValueSliderItem(label, values[index], 0..255) { onThresholdChange(mode, index, it) })
        }
        add(ValueSliderItem("Simplify", settings.simplifyAmount, 0..100, onSimplifyChange))
        add(ValueSliderItem("Noise Removal", settings.noiseRemovalAmount, 0..100, onNoiseRemovalChange))
        add(ValueSliderItem("Edge Smoothness", settings.edgeSmoothnessAmount, 0..100, onEdgeSmoothnessChange))
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sliderStride = with(LocalDensity.current) { ValueSliderWidth.toPx() }

    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ValueSettingsArrowButton(
            previous = true,
            enabled = listState.canScrollBackward,
        ) {
            scope.launch { listState.animateScrollBy(-sliderStride, tween(durationMillis = 200)) }
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            state = listState,
            userScrollEnabled = false,
        ) {
            items(sliders.size) { index ->
                val slider = sliders[index]
                InlineValueSlider(slider.label, slider.value, slider.range, slider.onChange)
            }
        }
        CompactTextButton("Auto", onAuto)
        CompactTextButton("Reset", onReset)
        ValueSettingsArrowButton(
            previous = false,
            enabled = listState.canScrollForward,
        ) {
            scope.launch { listState.animateScrollBy(sliderStride, tween(durationMillis = 200)) }
        }
    }
}

private val ValueSliderWidth = 252.dp

private data class ValueSliderItem(
    val label: String,
    val value: Int,
    val range: IntRange,
    val onChange: (Int) -> Unit,
)

@Composable
private fun ValueSettingsArrowButton(previous: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (previous) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (previous) "Previous Value setting" else "Next Value setting",
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun InlineValueSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.width(ValueSliderWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.width(82.dp), fontSize = 11.sp, maxLines = 1)
        CompactControlSlider(value.toFloat(), { onChange(it.toInt()) }, Modifier.width(120.dp), valueRange = range.first.toFloat()..range.last.toFloat())
        Text(if (range.last == 100) "$value%" else value.toString(), Modifier.width(34.dp), fontSize = 11.sp, maxLines = 1)
    }
}
@Composable
private fun CompactTextButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) { Text(label, fontSize = 11.sp) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HiddenControlsToolbar(onShow: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                CompactToolbarButton(
                    "Show",
                    android.R.drawable.ic_menu_view,
                    "Show controls",
                    true,
                    onShow,
                    visualAlpha = 0.5f,
                )
            }
        },
        expandedHeight = 48.dp,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ModeSelector(
    mode: TransformMode,
    referenceEnabled: Boolean,
    onReference: () -> Unit,
    onCamera: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CompactToolbarButton(
            "Image", android.R.drawable.ic_menu_crop, "Image mode",
            referenceEnabled, onReference, active = mode == TransformMode.REFERENCE,
        )
        CompactToolbarButton(
            "Camera", android.R.drawable.ic_menu_camera, "Camera mode",
            true, onCamera, active = mode == TransformMode.CAMERA,
        )
    }
}

@Composable
private fun CompactToolbarButton(
    label: String,
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    visualAlpha: Float = 1f,
    fixedWidth: Dp? = null,
) {
    Box(
        modifier = Modifier.height(48.dp)
            .then(if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(30.dp)
                .then(if (fixedWidth != null) Modifier.fillMaxWidth() else Modifier)
                .alpha(if (enabled) visualAlpha else 0.38f),
            shape = RoundedCornerShape(6.dp),
            color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            contentColor = if (active) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(iconRes), contentDescription, Modifier.size(16.dp))
                Text(label, maxLines = 1, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ToolbarOverflowMenu(
    hasReference: Boolean,
    onRemove: () -> Unit,
    onRefreshCameras: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Remove Reference") },
                leadingIcon = {
                    Icon(painterResource(android.R.drawable.ic_menu_delete), contentDescription = null)
                },
                enabled = hasReference,
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text("Refresh Cameras") },
                leadingIcon = {
                    Icon(painterResource(android.R.drawable.ic_menu_rotate), contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onRefreshCameras()
                },
            )
        }
    }
}

@Composable
private fun OpacityControl(
    enabled: Boolean,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Opacity", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
        CompactControlSlider(
            value = opacity,
            onValueChange = onOpacityChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = 0f..1f,
        )
        Text(
            "${(opacity * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StrobeControlRow(
    hasReference: Boolean,
    enabled: Boolean,
    intervalMs: Int,
    onToggle: () -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactToolbarButton(
            "Strobe", android.R.drawable.ic_menu_slideshow, "Toggle reference image Strobe",
            hasReference, onToggle, active = enabled, fixedWidth = 100.dp,
        )
        if (hasReference) {
            Text("Slow", fontSize = 11.sp, maxLines = 1)
            CompactControlSlider(
                value = (2600 - intervalMs).toFloat(),
                onValueChange = { onIntervalChange(2600 - it.toInt()) },
                modifier = Modifier.weight(1f),
                valueRange = 100f..2500f,
            )
            Text("Fast", fontSize = 11.sp, maxLines = 1)
            Text(
                "$intervalMs ms",
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1,
            )
        } else {
            Text(
                "Open an image to use Strobe.",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompactControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(24.dp),
            enabled = enabled,
            valueRange = valueRange,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    modifier = Modifier.size(11.dp).offset(y = 2.dp),
                    enabled = enabled,
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    enabled = enabled,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                )
            },
        )
    }
}

@Composable
private fun ReferenceBottomPanel(
    onReset: () -> Unit,
    hasReference: Boolean,
    valueMode: ValueMode,
    exportInProgress: Boolean,
    onExport: (ReferenceExportOption) -> Unit,
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        ),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                BottomControlButton(
                    label = "Reset Image",
                    iconRes = android.R.drawable.ic_menu_revert,
                    contentDescription = "Reset image transform",
                    enabled = hasReference,
                    onClick = onReset,
                )
                ExportBottomControl(
                    mode = valueMode,
                    enabled = hasReference && !exportInProgress,
                    inProgress = exportInProgress,
                    onExport = onExport,
                )
            }
    }
}

@Composable
private fun ExportBottomControl(
    mode: ValueMode,
    enabled: Boolean,
    inProgress: Boolean,
    onExport: (ReferenceExportOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BottomControlButton(
            label = "Export",
            iconRes = android.R.drawable.ic_menu_save,
            contentDescription = "Export reference image",
            enabled = enabled,
            inProgress = inProgress,
            onClick = {
                if (mode == ValueMode.THREE_VALUES || mode == ValueMode.FIVE_VALUES) expanded = true
                else onExport(ReferenceExportOption.CURRENT_VIEW)
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            exportOptionsFor(mode).forEach { (label, option) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onExport(option)
                    },
                )
            }
        }
    }
}

private fun exportOptionsFor(mode: ValueMode): List<Pair<String, ReferenceExportOption>> = buildList {
    add("Export Current View" to ReferenceExportOption.CURRENT_VIEW)
    add("Value Study" to ReferenceExportOption.VALUE_STUDY)
    if (mode == ValueMode.THREE_VALUES) {
        add("Dark Shape" to ReferenceExportOption.SHAPE_0)
        add("Mid Shape" to ReferenceExportOption.SHAPE_1)
        add("Light Shape" to ReferenceExportOption.SHAPE_2)
    } else if (mode == ValueMode.FIVE_VALUES) {
        add("Darkest Shape" to ReferenceExportOption.SHAPE_0)
        add("Dark-Mid Shape" to ReferenceExportOption.SHAPE_1)
        add("Middle Shape" to ReferenceExportOption.SHAPE_2)
        add("Light-Mid Shape" to ReferenceExportOption.SHAPE_3)
        add("Lightest Shape" to ReferenceExportOption.SHAPE_4)
    }
    add("Export All Value Shapes" to ReferenceExportOption.ALL_VALUE_SHAPES)
}

@Composable
private fun BottomControlButton(
    label: String,
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    inProgress: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(width = 140.dp, height = 48.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 140.dp, height = 30.dp).alpha(if (enabled || inProgress) 1f else 0.38f),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(painterResource(iconRes), contentDescription, Modifier.size(16.dp))
                }
                Box(Modifier.size(6.dp))
                Text(label, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SelectionHandles() {
    val yellow = Color(0xFFFFEB3B)
    Box(Modifier.fillMaxSize()) {
        SelectionHandle(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp), yellow)
        SelectionHandle(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp), yellow)
        SelectionHandle(Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp), yellow)
        SelectionHandle(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp), yellow)
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(width = 2.dp, height = 18.dp).background(yellow),
        )
        SelectionHandle(Modifier.align(Alignment.TopCenter).offset(y = (-24).dp), yellow)
    }
}

@Composable
private fun SelectionHandle(modifier: Modifier, color: Color) {
    Box(modifier.size(10.dp).background(color, CircleShape).border(1.dp, Black, CircleShape))
}

@Composable
private fun PermissionPanel(onGrantPermission: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Camera permission required", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.camera_permission_explanation), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onGrantPermission) { Text("Grant Camera Permission") }
    }
}
