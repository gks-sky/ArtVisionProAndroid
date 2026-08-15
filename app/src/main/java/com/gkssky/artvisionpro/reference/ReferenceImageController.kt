package com.gkssky.artvisionpro.reference

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.gkssky.artvisionpro.camera.CameraTransformState
import com.gkssky.artvisionpro.camera.TransformMode
import com.gkssky.artvisionpro.camera.transformed
import com.gkssky.artvisionpro.processing.ValueMode
import com.gkssky.artvisionpro.processing.ValueProcessor
import com.gkssky.artvisionpro.processing.HistogramAnalyzer
import com.gkssky.artvisionpro.processing.ValueThresholds
import com.gkssky.artvisionpro.processing.ValueSettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ReferenceImageController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestGeneration = AtomicInteger()
    private val processingGeneration = AtomicInteger()
    private val mutableState = MutableStateFlow(ReferenceImageState())
    val state: StateFlow<ReferenceImageState> = mutableState.asStateFlow()
    private val mutableTransform = MutableStateFlow(ReferenceTransformState())
    val transform: StateFlow<ReferenceTransformState> = mutableTransform.asStateFlow()
    private val mutableCameraTransform = MutableStateFlow(CameraTransformState())
    val cameraTransform: StateFlow<CameraTransformState> = mutableCameraTransform.asStateFlow()
    private val mutableTraceTransform = MutableStateFlow(TraceTransformState())
    val traceTransform: StateFlow<TraceTransformState> = mutableTraceTransform.asStateFlow()
    private val mutableMode = MutableStateFlow(TransformMode.CAMERA)
    val mode: StateFlow<TransformMode> = mutableMode.asStateFlow()
    private val mutableControlsHidden = MutableStateFlow(false)
    val controlsHidden: StateFlow<Boolean> = mutableControlsHidden.asStateFlow()
    private val mutableMoveModeActive = MutableStateFlow(false)
    val moveModeActive: StateFlow<Boolean> = mutableMoveModeActive.asStateFlow()
    private val mutableToolsExpanded = MutableStateFlow(false)
    val toolsExpanded: StateFlow<Boolean> = mutableToolsExpanded.asStateFlow()
    private val mutableBreakdownExpanded = MutableStateFlow(false)
    val breakdownExpanded: StateFlow<Boolean> = mutableBreakdownExpanded.asStateFlow()
    private val mutableValueSettingsExpanded = MutableStateFlow(false)
    val valueSettingsExpanded: StateFlow<Boolean> = mutableValueSettingsExpanded.asStateFlow()
    private val mutableVerticalFlipEnabled = MutableStateFlow(preferences.getBoolean(KEY_VERTICAL_FLIP, false))
    val verticalFlipEnabled: StateFlow<Boolean> = mutableVerticalFlipEnabled.asStateFlow()
    private val mutableHorizontalFlipEnabled = MutableStateFlow(preferences.getBoolean(KEY_HORIZONTAL_FLIP, false))
    val horizontalFlipEnabled: StateFlow<Boolean> = mutableHorizontalFlipEnabled.asStateFlow()
    private val mutableStrobeControlsExpanded = MutableStateFlow(false)
    val strobeControlsExpanded: StateFlow<Boolean> = mutableStrobeControlsExpanded.asStateFlow()
    private val mutableStrobeEnabled = MutableStateFlow(preferences.getBoolean(KEY_STROBE_ENABLED, false))
    val strobeEnabled: StateFlow<Boolean> = mutableStrobeEnabled.asStateFlow()
    private val mutableStrobeIntervalMs = MutableStateFlow(
        preferences.getInt(KEY_STROBE_INTERVAL_MS, DEFAULT_STROBE_INTERVAL_MS)
            .coerceIn(MIN_STROBE_INTERVAL_MS, MAX_STROBE_INTERVAL_MS)
    )
    val strobeIntervalMs: StateFlow<Int> = mutableStrobeIntervalMs.asStateFlow()
    private val mutableValueMode = MutableStateFlow(ValueMode.ORIGINAL)
    val valueMode: StateFlow<ValueMode> = mutableValueMode.asStateFlow()
    private val mutableValueThresholds = MutableStateFlow(ValueThresholds())
    val valueThresholds: StateFlow<ValueThresholds> = mutableValueThresholds.asStateFlow()
    private val mutableValueSettings = MutableStateFlow(ValueSettingsState(
        simplifyAmount = preferences.getInt(KEY_SIMPLIFY, ValueSettingsState.DEFAULT_SIMPLIFY_AMOUNT),
        noiseRemovalAmount = preferences.getInt(KEY_NOISE_REMOVAL, ValueSettingsState.DEFAULT_NOISE_REMOVAL_AMOUNT),
        edgeSmoothnessAmount = preferences.getInt(KEY_EDGE_SMOOTHNESS, ValueSettingsState.DEFAULT_EDGE_SMOOTHNESS_AMOUNT),
    ).bounded())
    val valueSettings: StateFlow<ValueSettingsState> = mutableValueSettings.asStateFlow()
    private var automaticThresholds = ValueThresholds()
    private var pendingProcess: Runnable? = null
    private val processedCache = LinkedHashMap<String, android.graphics.Bitmap>()

    init {
        preferences.getString(KEY_URI, null)?.let { load(Uri.parse(it), persistPermission = false) }
    }

    fun select(uri: Uri, persistPermission: Boolean) = load(uri, persistPermission)

    fun setOpacity(opacity: Float) {
        mutableState.value = mutableState.value.withOpacity(opacity)
    }

    fun activateReferenceMode() {
        if (mutableState.value.hasReference) {
            mutableMode.value = TransformMode.REFERENCE
            mutableState.value = mutableState.value.copy(errorMessage = null)
        } else {
            mutableState.value = mutableState.value.copy(errorMessage = "Open a reference image first.")
        }
    }

    fun activateCameraMode() {
        mutableMode.value = TransformMode.CAMERA
    }

    fun toggleControlsVisibility() {
        mutableControlsHidden.value = !mutableControlsHidden.value
    }

    fun toggleMoveMode() {
        mutableMoveModeActive.value = !mutableMoveModeActive.value
    }

    fun toggleTools() {
        mutableToolsExpanded.value = !mutableToolsExpanded.value
    }

    fun toggleBreakdown() {
        mutableBreakdownExpanded.value = !mutableBreakdownExpanded.value
    }

    fun toggleValueSettings() {
        mutableValueSettingsExpanded.value = !mutableValueSettingsExpanded.value
    }

    fun toggleVerticalFlip() {
        val enabled = !mutableVerticalFlipEnabled.value
        mutableVerticalFlipEnabled.value = enabled
        preferences.edit().putBoolean(KEY_VERTICAL_FLIP, enabled).apply()
    }

    fun toggleHorizontalFlip() {
        val enabled = !mutableHorizontalFlipEnabled.value
        mutableHorizontalFlipEnabled.value = enabled
        preferences.edit().putBoolean(KEY_HORIZONTAL_FLIP, enabled).apply()
    }

    fun toggleStrobeControls() {
        mutableStrobeControlsExpanded.value = !mutableStrobeControlsExpanded.value
    }

    fun toggleStrobe() {
        if (!mutableState.value.hasReference) return
        val enabled = !mutableStrobeEnabled.value
        mutableStrobeEnabled.value = enabled
        preferences.edit().putBoolean(KEY_STROBE_ENABLED, enabled).apply()
    }

    fun setStrobeInterval(intervalMs: Int) {
        val bounded = intervalMs.coerceIn(MIN_STROBE_INTERVAL_MS, MAX_STROBE_INTERVAL_MS)
        mutableStrobeIntervalMs.value = bounded
        preferences.edit().putInt(KEY_STROBE_INTERVAL_MS, bounded).apply()
    }

    fun selectValueMode(mode: ValueMode) {
        mutableValueMode.value = mode
        if (mode == ValueMode.ORIGINAL) mutableValueSettingsExpanded.value = false
        processCurrentMode(immediate = true)
    }

    fun setValueThreshold(mode: ValueMode, index: Int, value: Int) {
        val current = mutableValueThresholds.value
        mutableValueThresholds.value = when (mode) {
            ValueMode.TWO_VALUES -> current.copy(two = value)
            ValueMode.THREE_VALUES -> if (index == 0) current.copy(threeDark = value) else current.copy(threeLight = value)
            ValueMode.FIVE_VALUES -> when (index) {
                0 -> current.copy(fiveT1 = value)
                1 -> current.copy(fiveT2 = value)
                2 -> current.copy(fiveT3 = value)
                else -> current.copy(fiveT4 = value)
            }
            else -> current
        }.ordered()
        processCurrentMode(immediate = false)
    }

    fun setSimplifyAmount(value: Int) = updateValueSettings(mutableValueSettings.value.copy(simplifyAmount = value))

    fun setNoiseRemovalAmount(value: Int) = updateValueSettings(mutableValueSettings.value.copy(noiseRemovalAmount = value))

    fun setEdgeSmoothnessAmount(value: Int) = updateValueSettings(mutableValueSettings.value.copy(edgeSmoothnessAmount = value))

    private fun updateValueSettings(settings: ValueSettingsState) {
        val bounded = settings.bounded()
        mutableValueSettings.value = bounded
        preferences.edit().putInt(KEY_SIMPLIFY, bounded.simplifyAmount).putInt(KEY_NOISE_REMOVAL, bounded.noiseRemovalAmount).putInt(KEY_EDGE_SMOOTHNESS, bounded.edgeSmoothnessAmount).apply()
        processCurrentMode(immediate = false)
    }

    fun autoValueThresholds() = analyzeCurrentImage()

    fun resetValueThresholds() {
        mutableValueThresholds.value = automaticThresholds
        mutableValueSettings.value = ValueSettingsState()
        preferences.edit().remove(KEY_SIMPLIFY).remove(KEY_NOISE_REMOVAL).remove(KEY_EDGE_SMOOTHNESS).apply()
        processCurrentMode(immediate = true)
    }

    private fun analyzeCurrentImage() {
        val source = mutableState.value.bitmap ?: return
        val generation = processingGeneration.incrementAndGet()
        executor.execute {
            val result = runCatching { HistogramAnalyzer.automaticThresholds(HistogramAnalyzer.histogram(source)) }
            mainHandler.post {
                if (generation != processingGeneration.get() || mutableState.value.bitmap !== source) return@post
                result.onSuccess { thresholds ->
                    automaticThresholds = thresholds
                    mutableValueThresholds.value = thresholds
                    processedCache.clear()
                    processCurrentMode(immediate = true)
                }
            }
        }
    }

    private fun processCurrentMode(immediate: Boolean) {
        pendingProcess?.let(mainHandler::removeCallbacks)
        val action = Runnable { processCurrentModeNow() }
        pendingProcess = action
        if (immediate) action.run() else mainHandler.postDelayed(action, VALUE_PREVIEW_DEBOUNCE_MS)
    }

    private fun processCurrentModeNow() {
        pendingProcess = null
        val source = mutableState.value.bitmap ?: return
        val mode = mutableValueMode.value
        val settings = mutableValueThresholds.value
        val artisticSettings = mutableValueSettings.value
        val generation = processingGeneration.incrementAndGet()
        if (mode == ValueMode.ORIGINAL) {
            mutableState.value = mutableState.value.copy(processedBitmap = null)
            return
        }
        val cacheKey = "$mode:${settings.forMode(mode).joinToString()}:$artisticSettings"
        processedCache[cacheKey]?.let {
            mutableState.value = mutableState.value.copy(processedBitmap = it)
            return
        }
        executor.execute {
            val result = runCatching { ValueProcessor.applyValueMode(source, mode, settings, artisticSettings) }
            mainHandler.post {
                if (generation != processingGeneration.get() || mutableValueMode.value != mode ||
                    mutableValueThresholds.value != settings || mutableValueSettings.value != artisticSettings || mutableState.value.bitmap !== source) return@post
                result.onSuccess { processed ->
                    if (processedCache.size >= 8) processedCache.remove(processedCache.keys.first())
                    processedCache[cacheKey] = processed
                    mutableState.value = mutableState.value.copy(processedBitmap = processed)
                }.onFailure { error ->
                    mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Unable to apply Values.")
                }
            }
        }
    }

    fun applyGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (!mutableMoveModeActive.value || !mutableState.value.hasReference ||
            mutableMode.value != TransformMode.REFERENCE
        ) return
        mutableTransform.value = mutableTransform.value.transformed(
            centroidX, centroidY, panX, panY, zoom, rotation, viewportWidth, viewportHeight,
        )
    }

    fun applyCameraGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (!mutableMoveModeActive.value || mutableMode.value != TransformMode.CAMERA) return
        mutableCameraTransform.value = mutableCameraTransform.value.transformed(
            centroidX, centroidY, panX, panY, zoom, rotation, viewportWidth, viewportHeight,
        )
    }

    fun applyTraceGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        if (mutableMoveModeActive.value) return
        mutableTraceTransform.value = mutableTraceTransform.value.transformed(
            centroidX, centroidY, panX, panY, zoom, viewportWidth, viewportHeight,
        )
    }

    fun resetTransform() {
        resetImageToViewport()
    }

    /**
     * Restores only the user-applied reference transform. The UI's centered,
     * scale-to-fit base placement is derived from the current preview viewport,
     * so zero deltas exactly match a newly opened image in every orientation.
     */
    fun resetImageToViewport() {
        if (mutableState.value.hasReference) mutableTransform.value = ReferenceTransformState()
    }

    fun resetActiveTransform() {
        if (!mutableMoveModeActive.value) mutableTraceTransform.value = TraceTransformState()
        else if (mutableMode.value == TransformMode.REFERENCE) resetTransform()
        else mutableCameraTransform.value = CameraTransformState()
    }

    fun remove() {
        requestGeneration.incrementAndGet()
        preferences.edit().remove(KEY_URI).apply()
        mutableState.value = mutableState.value.removed()
        mutableTransform.value = ReferenceTransformState()
        mutableMode.value = TransformMode.CAMERA
        processingGeneration.incrementAndGet()
        processedCache.clear()
        mutableValueMode.value = ValueMode.ORIGINAL
        mutableStrobeEnabled.value = false
        preferences.edit().putBoolean(KEY_STROBE_ENABLED, false).apply()
    }

    private fun load(uri: Uri, persistPermission: Boolean) {
        val generation = requestGeneration.incrementAndGet()
        mutableState.value = mutableState.value.loading(uri)
        if (persistPermission) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        executor.execute {
            val result = runCatching {
                val bitmap = decode(uri)
                bitmap to HistogramAnalyzer.automaticThresholds(HistogramAnalyzer.histogram(bitmap))
            }
            mainHandler.post {
                if (requestGeneration.get() != generation) return@post
                result.onSuccess { (bitmap, thresholds) ->
                    processingGeneration.incrementAndGet()
                    automaticThresholds = thresholds
                    mutableValueThresholds.value = thresholds
                    processedCache.clear()
                    preferences.edit().putString(KEY_URI, uri.toString()).apply()
                    mutableState.value = mutableState.value.copy(
                        uri = uri,
                        bitmap = bitmap,
                        processedBitmap = null,
                        isLoading = false,
                        errorMessage = null,
                    )
                    resetImageToViewport()
                    mutableMode.value = TransformMode.REFERENCE
                    processCurrentMode(immediate = true)
                }.onFailure { error ->
                    preferences.edit().remove(KEY_URI).apply()
                    mutableState.value = mutableState.value.loadFailed(
                        error.message ?: "Unable to load the selected image.",
                    )
                }
            }
        }
    }

    private fun decode(uri: Uri) = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val mimeType = info.mimeType?.lowercase()
        require(mimeType in SUPPORTED_MIME_TYPES) { "Choose a JPEG, PNG, or WEBP image." }
        val size = info.size
        val largestDimension = maxOf(size.width, size.height)
        if (largestDimension > MAX_DECODE_DIMENSION) {
            val scale = MAX_DECODE_DIMENSION.toFloat() / largestDimension
            decoder.setTargetSize(
                (size.width * scale).toInt().coerceAtLeast(1),
                (size.height * scale).toInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
    }

    override fun close() {
        requestGeneration.incrementAndGet()
        executor.shutdownNow()
        pendingProcess?.let(mainHandler::removeCallbacks)
    }

    private companion object {
        const val PREFERENCES = "reference_image"
        const val KEY_URI = "uri"
        const val KEY_VERTICAL_FLIP = "vertical_flip"
        const val KEY_HORIZONTAL_FLIP = "horizontal_flip"
        const val KEY_STROBE_ENABLED = "strobe_enabled"
        const val KEY_STROBE_INTERVAL_MS = "strobe_interval_ms"
        const val KEY_SIMPLIFY = "value_simplify"
        const val KEY_NOISE_REMOVAL = "value_noise_removal"
        const val KEY_EDGE_SMOOTHNESS = "value_edge_smoothness"
        const val DEFAULT_STROBE_INTERVAL_MS = 500
        const val MIN_STROBE_INTERVAL_MS = 100
        const val MAX_STROBE_INTERVAL_MS = 2500
        const val MAX_DECODE_DIMENSION = 2048
        const val VALUE_PREVIEW_DEBOUNCE_MS = 75L
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
