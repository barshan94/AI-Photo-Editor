package com.barshan.aiphotoeditor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.pointer.pointerInput
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Ink = Color(0xFF101313)
private val Panel = Color(0xFF1A2020)
private val Mint = Color(0xFF78E0D0)
private val SoftText = Color(0xFFA7B5B2)
private val BrightText = Color(0xFFF2F7F5)

private data class PhotoItem(
    val uri: Uri,
    val score: Int? = null,
    val sharpness: Int = 0,
    val exposure: Int = 0,
    val contrast: Int = 0,
    val note: String = "Waiting for analysis",
    val width: Int = 0,
    val height: Int = 0,
    val fingerprint: String? = null,
    val visualFingerprint: Long? = null,
    val captureTimeMillis: Long? = null,
    val burstGroupId: Long? = null,
    val isExactDuplicate: Boolean = false,
    val isNearDuplicate: Boolean = false,
    /** User triage state; changing it never touches the source image. */
    val decision: String = "Review"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, onPrimary = Ink, background = Ink,
                surface = Panel, onSurface = BrightText, secondary = Mint
            )) {
                PhotoEditorHome()
            }
        }
    }
}

@Composable
private fun PhotoEditorHome() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val recoveryPrefs = remember {
        context.getSharedPreferences("frame_ai_recovery", android.content.Context.MODE_PRIVATE)
    }
    var learnedProfile by remember { mutableStateOf(PersonalEditLearning.load(context)) }
    val recoveredUris = remember {
        recoveryPrefs.getString("selected_photo_uris", "").orEmpty()
            .split("\\n").filter { it.isNotBlank() }.mapNotNull {
                try { Uri.parse(it) } catch (_: Exception) { null }
            }
    }
    val selectedPhotos = remember {
        mutableStateListOf<PhotoItem>().apply { recoveredUris.forEach { add(PhotoItem(uri = it)) } }
    }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableIntStateOf(0) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var operationProgress by remember { mutableIntStateOf(0) }
    var operationStage by remember { mutableStateOf("Ready") }
    var batchPreset by remember { mutableStateOf("Current edits") }
    var batchSummary by remember { mutableStateOf<String?>(null) }
    var batchPreviewImages by remember { mutableStateOf<Map<Uri, ByteArray>>(emptyMap()) }
    var batchPreviewScores by remember { mutableStateOf<Map<Uri, PhotoQualityScoring.Score>>(emptyMap()) }
    var batchOriginalScores by remember { mutableStateOf<Map<Uri, PhotoQualityScoring.Score>>(emptyMap()) }
    var batchPreviewFormats by remember { mutableStateOf<Map<Uri, List<String>>>(emptyMap()) }
    var isPreparingBatchPreview by remember { mutableStateOf(false) }
    var batchPreviewProgress by remember { mutableIntStateOf(0) }
    var activePhoto by remember {
        mutableStateOf(selectedPhotos.firstOrNull { it.uri.toString() == recoveryPrefs.getString("active_photo_uri", null) })
    }
    var showBurstComparison by remember { mutableStateOf(false) }
    var showBurstGroupPicker by remember { mutableStateOf(false) }
    var gridFilter by remember { mutableStateOf("All") }
    var comparisonGroupId by remember { mutableStateOf<Long?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewScale by remember { mutableFloatStateOf(1f) }
    var previewOffsetX by remember { mutableFloatStateOf(0f) }
    var previewOffsetY by remember { mutableFloatStateOf(0f) }
    var previewWidthPx by remember { mutableIntStateOf(0) }
    var previewHeightPx by remember { mutableIntStateOf(0) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    var showEnhanced by remember { mutableStateOf(true) }
    var compareBeforeAfter by remember { mutableStateOf(false) }
    var comparePosition by remember { mutableFloatStateOf(0.5f) }
    var showCropGuides by remember { mutableStateOf(true) }
    val undoHistory = remember { mutableStateListOf<EditSettings>() }
    val redoHistory = remember { mutableStateListOf<EditSettings>() }
    var brightness by remember { mutableFloatStateOf(recoveryPrefs.getFloat("brightness", 0f)) }
    var contrast by remember { mutableFloatStateOf(recoveryPrefs.getFloat("contrast", 1.07f)) }
    var saturation by remember { mutableFloatStateOf(recoveryPrefs.getFloat("saturation", 1.06f)) }
    var warmth by remember { mutableFloatStateOf(recoveryPrefs.getFloat("warmth", 0f)) }
    var backgroundBlur by remember { mutableFloatStateOf(0f) }
    var backgroundEnhancement by remember { mutableFloatStateOf(0f) }
    var subjectBrightness by remember { mutableFloatStateOf(0f) }
    var darkerAreaLift by remember { mutableFloatStateOf(0f) }
    var skinSmoothing by remember { mutableFloatStateOf(0f) }
    var makeupIntensity by remember { mutableFloatStateOf(0f) }
    var removalBrushMode by remember { mutableStateOf(false) }
    var removalBrushSize by remember { mutableFloatStateOf(24f) }
    val removalStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentRemovalStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var cropLabel by remember { mutableStateOf(recoveryPrefs.getString("crop_label", "Original") ?: "Original") }
    var cropOffsetX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("crop_offset_x", 0f)) }
    var cropOffsetY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("crop_offset_y", 0f)) }
    var rotationDegrees by remember { mutableFloatStateOf(recoveryPrefs.getFloat("rotation_degrees", 0f)) }
    var straightenDegrees by remember { mutableFloatStateOf(recoveryPrefs.getFloat("straighten_degrees", 0f)) }
    var mirrorHorizontal by remember { mutableStateOf(recoveryPrefs.getBoolean("mirror_horizontal", false)) }
    var mirrorVertical by remember { mutableStateOf(recoveryPrefs.getBoolean("mirror_vertical", false)) }
    var perspectiveTopLeftX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_tl_x", 0f)) }
    var perspectiveTopLeftY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_tl_y", 0f)) }
    var perspectiveTopRightX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_tr_x", 0f)) }
    var perspectiveTopRightY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_tr_y", 0f)) }
    var perspectiveBottomRightX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_br_x", 0f)) }
    var perspectiveBottomRightY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_br_y", 0f)) }
    var perspectiveBottomLeftX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_bl_x", 0f)) }
    var perspectiveBottomLeftY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("perspective_bl_y", 0f)) }
    var showPerspectiveControls by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf(recoveryPrefs.getString("selected_style", "Auto") ?: "Auto") }
    var activeEditorPanel by remember { mutableStateOf("Universal Edit") }
    var activeAiSubpanel by remember { mutableStateOf("Subject") }
    var editedScore by remember { mutableStateOf<PhotoQualityScoring.Score?>(null) }
    var originalPreviewScore by remember { mutableStateOf<PhotoQualityScoring.Score?>(null) }
    var isShowingOriginal by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Choose photos to begin. Analysis and edits run on this device.") }
    var photoAnalysisResult by remember { mutableStateOf<PhotoAnalysisResult?>(null) }
    var isRunningPhotoAnalysis by remember { mutableStateOf(false) }

    var captionSuggestions by remember { mutableStateOf<CaptionSuggestionEngine.Suggestions?>(null) }
    var captionEnglish by remember { mutableStateOf("") }
    var captionBengali by remember { mutableStateOf("") }
    var captionIndex by remember { mutableIntStateOf(0) }
    var captionPhotoUri by remember { mutableStateOf<String?>(null) }
    var isGeneratingCaptions by remember { mutableStateOf(false) }
    var captionTheme by remember { mutableStateOf("Auto") }
    var captionAspectRatio by remember { mutableFloatStateOf(1f) }

    fun applyCaptionSuggestions(labels: List<Pair<String, Float>>, ratio: Float) {
        captionAspectRatio = ratio
        val suggestions = CaptionSuggestionEngine.fromLabels(labels, ratio, captionTheme)
        captionSuggestions = suggestions
        captionIndex = 0
        captionEnglish = suggestions.english.first()
        captionBengali = suggestions.bengali.first()
        isGeneratingCaptions = false
    }

    fun suggestCaptions(bitmapOverride: Bitmap? = null) {
        val bitmap = bitmapOverride ?: previewBitmap
        if (bitmap == null || bitmap.isRecycled) {
            statusMessage = "Prepare a photo preview before suggesting captions."
            return
        }
        isGeneratingCaptions = true
        statusMessage = "Looking for visual clues on this device…"
        val bitmapForLabels = try { bitmap.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
        if (bitmapForLabels == null) {
            isGeneratingCaptions = false
            statusMessage = "Could not prepare this photo for caption analysis."
            return
        }
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val ratio = bitmapForLabels.width.toFloat() / bitmapForLabels.height.coerceAtLeast(1).toFloat()
        try {
            labeler.process(InputImage.fromBitmap(bitmapForLabels, 0))
                .addOnSuccessListener { labels ->
                    applyCaptionSuggestions(labels.map { it.text to it.confidence }, ratio)
                    statusMessage = if (labels.isEmpty()) {
                        "No clear visual labels found. Neutral caption starters are ready to edit."
                    } else {
                        "Caption starters based on on-device visual labels. Review them before sharing."
                    }
                    labeler.close()
                    if (!bitmapForLabels.isRecycled) bitmapForLabels.recycle()
                }
                .addOnFailureListener {
                    applyCaptionSuggestions(emptyList(), ratio)
                    statusMessage = "Image labels were unavailable; neutral caption starters are ready."
                    labeler.close()
                    if (!bitmapForLabels.isRecycled) bitmapForLabels.recycle()
                }
        } catch (_: Exception) {
            applyCaptionSuggestions(emptyList(), ratio)
            statusMessage = "Could not analyze visual labels. Neutral caption starters are ready."
            labeler.close()
            if (!bitmapForLabels.isRecycled) bitmapForLabels.recycle()
        }
    }

    // Restore technical scores for photos recovered after process death.
    LaunchedEffect(Unit) {
        val photosNeedingScores = selectedPhotos.filter { it.score == null }.map { it.uri }
        for (uri in photosNeedingScores) {
            try {
                val result = withContext(Dispatchers.Default) {
                    PhotoQualityAnalyzer.analyze(context.contentResolver, uri)
                }
                val position = selectedPhotos.indexOfFirst { it.uri == uri }
                if (position >= 0 && selectedPhotos[position].score == null) {
                    selectedPhotos[position] = selectedPhotos[position].copy(
                        score = result.score, sharpness = result.sharpness,
                        exposure = result.exposure, contrast = result.contrast, note = result.note
                    )
                    if (activePhoto?.uri == uri) activePhoto = selectedPhotos[position]
                }
            } catch (_: Exception) {
                statusMessage = "Some recovered photos could not be reopened. Re-select those photos to continue."
            }
        }
        if (photosNeedingScores.isNotEmpty()) statusMessage = "Recovered your previous photo selection and edit settings."
    }

    LaunchedEffect(
        selectedPhotos.map { it.uri.toString() }, activePhoto?.uri?.toString(),
        brightness, contrast, saturation, warmth, cropLabel, cropOffsetX, cropOffsetY, selectedStyle,
        perspectiveTopLeftX, perspectiveTopLeftY, perspectiveTopRightX, perspectiveTopRightY,
        perspectiveBottomRightX, perspectiveBottomRightY, perspectiveBottomLeftX, perspectiveBottomLeftY
    ) {
        recoveryPrefs.edit()
            .putString("selected_photo_uris", selectedPhotos.joinToString("\\n") { it.uri.toString() })
            .putString("active_photo_uri", activePhoto?.uri?.toString())
            .putFloat("brightness", brightness).putFloat("contrast", contrast)
            .putFloat("saturation", saturation).putFloat("warmth", warmth)
            .putString("crop_label", cropLabel).putFloat("crop_offset_x", cropOffsetX)
            .putFloat("crop_offset_y", cropOffsetY).putFloat("rotation_degrees", rotationDegrees)
            .putFloat("straighten_degrees", straightenDegrees).putBoolean("mirror_horizontal", mirrorHorizontal)
            .putBoolean("mirror_vertical", mirrorVertical)
            .putFloat("perspective_tl_x", perspectiveTopLeftX).putFloat("perspective_tl_y", perspectiveTopLeftY)
            .putFloat("perspective_tr_x", perspectiveTopRightX).putFloat("perspective_tr_y", perspectiveTopRightY)
            .putFloat("perspective_br_x", perspectiveBottomRightX).putFloat("perspective_br_y", perspectiveBottomRightY)
            .putFloat("perspective_bl_x", perspectiveBottomLeftX).putFloat("perspective_bl_y", perspectiveBottomLeftY)
            .putString("selected_style", selectedStyle)
            .apply()
    }

    val previewTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (previewScale * zoomChange).coerceIn(1f, 5f)
        previewScale = nextScale
        if (nextScale <= 1.001f) {
            previewOffsetX = 0f
            previewOffsetY = 0f
        } else {
            val maxX = previewWidthPx * (nextScale - 1f) / 2f
            val maxY = previewHeightPx * (nextScale - 1f) / 2f
            previewOffsetX = (previewOffsetX + panChange.x).coerceIn(-maxX, maxX)
            previewOffsetY = (previewOffsetY + panChange.y).coerceIn(-maxY, maxY)
        }
    }

    fun resetPreviewZoom() {
        previewScale = 1f
        previewOffsetX = 0f
        previewOffsetY = 0f
    }

    fun previewNormalizedPosition(position: Offset): Offset? {
        val image = previewBitmap ?: return null
        val boxWidth = previewWidthPx.toFloat().coerceAtLeast(1f)
        val boxHeight = previewHeightPx.toFloat().coerceAtLeast(1f)
        val fit = maxOf(boxWidth / image.width.toFloat(), boxHeight / image.height.toFloat())
        val shownWidth = image.width * fit
        val shownHeight = image.height * fit
        val left = (boxWidth - shownWidth) / 2f
        val top = (boxHeight - shownHeight) / 2f
        if (position.x < left || position.x > left + shownWidth || position.y < top || position.y > top + shownHeight) return null
        return Offset(((position.x - left) / shownWidth).coerceIn(0f, 1f), ((position.y - top) / shownHeight).coerceIn(0f, 1f))
    }

    suspend fun scoreEditedBitmap(bitmap: Bitmap): PhotoQualityScoring.Score =
        withContext(Dispatchers.Default) {
            val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height).toFloat())
            val sampleWidth = (bitmap.width * scale).toInt().coerceAtLeast(3)
            val sampleHeight = (bitmap.height * scale).toInt().coerceAtLeast(3)
            val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
            try {
                val pixels = IntArray(sample.width * sample.height)
                sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
                PhotoQualityScoring.scorePixels(pixels, sample.width, sample.height)
            } finally {
                if (sample !== bitmap && !sample.isRecycled) sample.recycle()
            }
        }

    fun currentSettings(): EditSettings = EditSettings(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth,
        cropRatio = when (cropLabel) {
            "Square" -> 1f
            "Portrait 4:5" -> 4f / 5f
            "Story 9:16" -> 9f / 16f
            "Landscape 16:9" -> 16f / 9f
            else -> null
        },
        cropOffsetX = cropOffsetX,
        cropOffsetY = cropOffsetY,
        rotationDegrees = rotationDegrees,
        straightenDegrees = straightenDegrees,
        mirrorHorizontal = mirrorHorizontal,
        mirrorVertical = mirrorVertical,
        perspectiveTopLeftX = perspectiveTopLeftX,
        perspectiveTopLeftY = perspectiveTopLeftY,
        perspectiveTopRightX = perspectiveTopRightX,
        perspectiveTopRightY = perspectiveTopRightY,
        perspectiveBottomRightX = perspectiveBottomRightX,
        perspectiveBottomRightY = perspectiveBottomRightY,
        perspectiveBottomLeftX = perspectiveBottomLeftX,
        perspectiveBottomLeftY = perspectiveBottomLeftY,
        backgroundBlur = backgroundBlur,
        backgroundEnhancement = backgroundEnhancement,
        subjectBrightness = subjectBrightness,
        darkerAreaLift = darkerAreaLift,
        skinSmoothing = skinSmoothing,
        makeupIntensity = makeupIntensity
    )

    fun recordEditChange() {
        val snapshot = currentSettings()
        if (undoHistory.lastOrNull() != snapshot) {
            undoHistory.add(snapshot)
            if (undoHistory.size > 40) undoHistory.removeAt(0)
        }
        redoHistory.clear()
    }

    fun applyEditSnapshot(settings: EditSettings) {
        brightness = settings.brightness
        contrast = settings.contrast
        saturation = settings.saturation
        warmth = settings.warmth
        backgroundBlur = settings.backgroundBlur
        backgroundEnhancement = settings.backgroundEnhancement
        subjectBrightness = settings.subjectBrightness
        darkerAreaLift = settings.darkerAreaLift
        skinSmoothing = settings.skinSmoothing
        makeupIntensity = settings.makeupIntensity
        cropOffsetX = settings.cropOffsetX
        cropOffsetY = settings.cropOffsetY
        rotationDegrees = settings.rotationDegrees
        straightenDegrees = settings.straightenDegrees
        mirrorHorizontal = settings.mirrorHorizontal
        mirrorVertical = settings.mirrorVertical
        perspectiveTopLeftX = settings.perspectiveTopLeftX
        perspectiveTopLeftY = settings.perspectiveTopLeftY
        perspectiveTopRightX = settings.perspectiveTopRightX
        perspectiveTopRightY = settings.perspectiveTopRightY
        perspectiveBottomRightX = settings.perspectiveBottomRightX
        perspectiveBottomRightY = settings.perspectiveBottomRightY
        perspectiveBottomLeftX = settings.perspectiveBottomLeftX
        perspectiveBottomLeftY = settings.perspectiveBottomLeftY
        cropLabel = when (settings.cropRatio) {
            1f -> "Square"
            0.8f -> "Portrait 4:5"
            0.5625f -> "Story 9:16"
            1.7777778f -> "Landscape 16:9"
            else -> "Original"
        }
        selectedStyle = "Custom"
        compareBeforeAfter = false
    }

    fun undoEdit() {
        if (undoHistory.isNotEmpty()) {
            redoHistory.add(currentSettings())
            applyEditSnapshot(undoHistory.removeAt(undoHistory.lastIndex))
            statusMessage = "Edit undone."
        }
    }

    fun redoEdit() {
        if (redoHistory.isNotEmpty()) {
            undoHistory.add(currentSettings())
            applyEditSnapshot(redoHistory.removeAt(redoHistory.lastIndex))
            statusMessage = "Edit restored."
        }
    }

    fun setStyle(style: String) {
        recordEditChange()
        selectedStyle = style
        showEnhanced = true
        compareBeforeAfter = false
        when (style) {
            "Original" -> {
                brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
                backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f
                skinSmoothing = 0f; makeupIntensity = 0f
                cropLabel = "Original"; cropOffsetX = 0f; cropOffsetY = 0f
                rotationDegrees = 0f; straightenDegrees = 0f; mirrorHorizontal = false; mirrorVertical = false
                perspectiveTopLeftX = 0f; perspectiveTopLeftY = 0f; perspectiveTopRightX = 0f; perspectiveTopRightY = 0f
                perspectiveBottomRightX = 0f; perspectiveBottomRightY = 0f; perspectiveBottomLeftX = 0f; perspectiveBottomLeftY = 0f
            }
            "Natural" -> { brightness = 1f; contrast = 1.02f; saturation = 1.02f; warmth = 0f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            "Vivid" -> { brightness = 0f; contrast = 1.13f; saturation = 1.20f; warmth = 0f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            "Warm" -> { brightness = 2f; contrast = 1.06f; saturation = 1.04f; warmth = 8f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            "Cool" -> { brightness = 0f; contrast = 1.05f; saturation = 1.02f; warmth = -8f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            "Mono" -> { brightness = 0f; contrast = 1.10f; saturation = 0f; warmth = 0f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            "AI Portrait" -> { brightness = 0f; contrast = 1.02f; saturation = 1.02f; warmth = 0f; backgroundBlur = 16f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
            else -> { brightness = 0f; contrast = 1.07f; saturation = 1.02f; warmth = 0f; backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f; darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f }
        }
    }


    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotos.clear()
            activePhoto = null
            previewBitmap = null
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // The picker can still grant temporary access for the current session.
                }
                selectedPhotos.add(PhotoItem(uri = uri))
            }
            isAnalyzing = true
            analysisProgress = 0
            statusMessage = "Analysing photo quality…"
            scope.launch {
                val urisToAnalyze = selectedPhotos.map { it.uri }
                for ((index, uri) in urisToAnalyze.withIndex()) {
                    try {
                        val result = withContext(Dispatchers.Default) {
                            PhotoQualityAnalyzer.analyze(context.contentResolver, uri)
                        }
                        val position = selectedPhotos.indexOfFirst { it.uri == uri }
                        if (position >= 0) {
                            selectedPhotos[position] = PhotoItem(
                                uri = uri,
                                score = result.score,
                                sharpness = result.sharpness,
                                exposure = result.exposure,
                                contrast = result.contrast,
                                note = result.note,
                                width = result.width,
                                height = result.height,
                                fingerprint = try {
                                    withContext(Dispatchers.IO) {
                                        PhotoQualityAnalyzer.fingerprint(context.contentResolver, uri)
                                    }
                                } catch (_: Exception) {
                                    null
                                },
                                visualFingerprint = try {
                                    withContext(Dispatchers.IO) {
                                        PhotoQualityAnalyzer.visualFingerprint(context.contentResolver, uri)
                                    }
                                } catch (_: Exception) {
                                    null
                                },
                                captureTimeMillis = try {
                                    withContext(Dispatchers.IO) {
                                        PhotoQualityAnalyzer.captureTimeMillis(context.contentResolver, uri)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            )
                        }
                    } catch (_: Exception) {
                        val position = selectedPhotos.indexOfFirst { it.uri == uri }
                        if (position >= 0) {
                            selectedPhotos[position] = PhotoItem(
                                uri = uri, score = 0, note = "Could not analyse this file"
                            )
                        }
                    }
                    analysisProgress = index + 1
                }
                val seenFingerprints = mutableSetOf<String>()
                for (index in selectedPhotos.indices) {
                    val photo = selectedPhotos[index]
                    val fingerprint = photo.fingerprint
                    if (fingerprint != null) {
                        selectedPhotos[index] = photo.copy(
                            isExactDuplicate = !seenFingerprints.add(fingerprint)
                        )
                    }
                }
                // Candidate bursts are bounded by the group's first timestamp, not
                // adjacent gaps. Missing capture times are intentionally left ungrouped.
                val burstGroupIds = PhotoGroupingPolicy.burstGroupIds(
                    selectedPhotos.map { it.captureTimeMillis }
                )
                for (index in selectedPhotos.indices) {
                    selectedPhotos[index] = selectedPhotos[index].copy(burstGroupId = burstGroupIds[index])
                }

                // Rank first, then compare against higher-scoring representatives.
                // This makes "remove visually similar" keep the strongest technical shot,
                // rather than whichever file happened to be selected first.
                selectedPhotos.sortByDescending { it.score ?: -1 }
                val bestRepresentatives = mutableListOf<PhotoItem>()
                for (index in selectedPhotos.indices) {
                    val photo = selectedPhotos[index]
                    val visualHash = photo.visualFingerprint
                    if (photo.isExactDuplicate || visualHash == null) continue
                    val similarToBetterShot = bestRepresentatives.any { representative ->
                        representative.fingerprint != photo.fingerprint &&
                            representative.visualFingerprint?.let { representativeHash ->
                                PhotoGroupingPolicy.isVisuallySimilar(visualHash, representativeHash)
                            } == true
                    }
                    if (similarToBetterShot) {
                        selectedPhotos[index] = photo.copy(isNearDuplicate = true)
                    } else {
                        bestRepresentatives.add(photo)
                    }
                }
                isAnalyzing = false
                val best = selectedPhotos.firstOrNull()?.score
                statusMessage = if (best != null && selectedPhotos.isNotEmpty()) {
                    "Ranked " + selectedPhotos.size + " photos. Best technical score: " + best + "/100. Tap any photo to edit."
                } else {
                    "Analysis finished. Tap a photo to preview an edit."
                }
            }
        }
    }

    fun setPhotoDecision(photo: PhotoItem, decision: String) {
        val position = selectedPhotos.indexOfFirst { it.uri == photo.uri }
        if (position >= 0) {
            val updated = selectedPhotos[position].copy(decision = decision)
            selectedPhotos[position] = updated
            if (activePhoto?.uri == photo.uri) activePhoto = updated
            statusMessage = when (decision) {
                "Keep" -> "Marked Keep. This photo will be included in Enhance all."
                "Reject" -> "Marked Reject. This photo will be skipped by Enhance all; the original remains untouched."
                else -> "Marked Review. This photo remains in the batch until you decide."
            }
        }
    }

    fun openEditor(photo: PhotoItem) {
        resetPreviewZoom()
        activePhoto = photo
        editedScore = null
        // Keep the familiar full manual controls immediately available.
        // AI Edit and Presets remain one tap away in the workspace tabs.
        activeEditorPanel = "Universal Edit"
        activeAiSubpanel = "Subject"
        compareBeforeAfter = false
        comparePosition = 0.5f
        previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewBitmap = null

        // Opening a photo is a review action, not an edit. Start from the untouched original.
        brightness = 0f
        contrast = 1f
        saturation = 1f
        warmth = 0f
        backgroundBlur = 0f
        backgroundEnhancement = 0f
        subjectBrightness = 0f
        darkerAreaLift = 0f
        skinSmoothing = 0f
        makeupIntensity = 0f
        removalBrushMode = false
        removalStrokes.clear()
        currentRemovalStroke = emptyList()
        cropLabel = "Original"
        cropOffsetX = 0f
        cropOffsetY = 0f
        rotationDegrees = 0f
        straightenDegrees = 0f
        mirrorHorizontal = false
        mirrorVertical = false
        perspectiveTopLeftX = 0f
        perspectiveTopLeftY = 0f
        perspectiveTopRightX = 0f
        perspectiveTopRightY = 0f
        perspectiveBottomRightX = 0f
        perspectiveBottomRightY = 0f
        perspectiveBottomLeftX = 0f
        perspectiveBottomLeftY = 0f
        showPerspectiveControls = false
        selectedStyle = "Original"
        showEnhanced = true
        undoHistory.clear()
        redoHistory.clear()
        isPreparingPreview = true
        statusMessage = "Opening original photo… Choose an edit when you're ready."
    }

    fun suggestedStyleFor(result: PhotoAnalysisResult): String {
        val labels = result.labels.joinToString(" ").lowercase()
        return when {
            labels.contains("sunset") || labels.contains("sunrise") || labels.contains("food") || labels.contains("meal") -> "Warm"
            labels.contains("water") || labels.contains("ocean") || labels.contains("lake") || labels.contains("sky") || labels.contains("cloud") -> "Cool"
            labels.contains("forest") || labels.contains("tree") || labels.contains("plant") || result.faceCount > 0 -> "Natural"
            labels.contains("building") || labels.contains("architecture") || labels.contains("city") -> "Vivid"
            else -> "Natural"
        }
    }

    /** Convert each photo's own analysis into restrained, scene-aware batch settings. */
    fun aiSettingsFor(result: PhotoAnalysisResult): EditSettings {
        val labels = result.labels.joinToString(" ").lowercase()
        return EditSettings(
            brightness = when {
                result.lightingSummary.contains("looks dark", ignoreCase = true) -> 5f
                result.lightingSummary.contains("looks bright", ignoreCase = true) -> -3f
                else -> 1f
            },
            contrast = 1.04f,
            saturation = if (result.faceCount > 0) 1.035f else 1.07f,
            warmth = when {
                labels.contains("sunset") || labels.contains("sunrise") || labels.contains("food") || labels.contains("meal") -> 4f
                labels.contains("water") || labels.contains("ocean") || labels.contains("lake") || labels.contains("sky") -> -2f
                else -> 0f
            },
            backgroundEnhancement = when {
                labels.contains("forest") || labels.contains("tree") || labels.contains("plant") || labels.contains("nature") -> 4.5f
                labels.contains("sky") || labels.contains("cloud") || labels.contains("water") || labels.contains("ocean") -> 3.5f
                else -> 3f
            },
            backgroundBlur = 0f,
            subjectBrightness = if (result.faceCount > 0) 5f else 0f,
            darkerAreaLift = if (result.faceCount > 0) 5f else 0f,
            skinSmoothing = if (result.faceCount > 0) 1.5f else 0f,
            makeupIntensity = 0f
        )
    }

    fun recommendedFormats(width: Int, height: Int): List<String> {
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        return when {
            ratio in 0.88f..1.12f -> listOf("DP", "Post")
            ratio <= 0.68f -> listOf("Story", "Video")
            ratio in 0.69f..0.86f -> listOf("Post", "DP")
            ratio >= 1.55f -> listOf("Cover", "Video")
            else -> listOf("Post", if (ratio > 1f) "Cover" else "DP")
        }
    }

    /** Build a small, visible preview for every eligible grid photo without opening a single-photo editor. */
    fun previewBatchPreset(preset: String) {
        if (isPreparingBatchPreview || isExporting || isAnalyzing) return
        batchPreset = preset
        batchSummary = null
        batchPreviewImages = emptyMap()
        batchPreviewScores = emptyMap()
        batchOriginalScores = emptyMap()
        batchPreviewFormats = emptyMap()
        val batch = selectedPhotos.filter { it.decision != "Reject" }
        if (batch.isEmpty()) {
            statusMessage = "Choose at least one photo to preview a batch edit."
            return
        }
        isPreparingBatchPreview = true
        batchPreviewProgress = 0
        statusMessage = "Preparing live $preset previews for ${batch.size} photos…"
        scope.launch {
            var failed = 0
            try {
                for ((index, photo) in batch.withIndex()) {
                    var original: Bitmap? = null
                    var enhanced: Bitmap? = null
                    try {
                        original = withContext(Dispatchers.IO) {
                            AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 480)
                        }
                        batchPreviewFormats = batchPreviewFormats + (photo.uri to recommendedFormats(original!!.width, original!!.height))
                        val originalScore = scoreEditedBitmap(original!!)
                        batchOriginalScores = batchOriginalScores + (photo.uri to originalScore)
                        val settings = if (preset == "AI Edit") {
                            aiSettingsFor(PhotoAnalysisEngine.analyze(original!!))
                        } else if (preset == "Current edits") {
                            currentSettings()
                        } else {
                            BatchExportPolicy.settingsForPreset(preset)
                        }
                        val useSubjectAware = preset == "AI Edit" || preset == "AI Portrait" ||
                            settings.backgroundBlur > 0f || settings.backgroundEnhancement > 0f ||
                            settings.subjectBrightness > 0f || settings.darkerAreaLift > 0f ||
                            settings.skinSmoothing > 0f || settings.makeupIntensity > 0f
                        if (useSubjectAware) {
                            enhanced = SubjectAwareEnhancer.enhance(original!!, settings, context).bitmap
                        } else {
                            enhanced = withContext(Dispatchers.Default) {
                                AutoEnhancer.enhance(original!!, settings)
                            }
                        }
                        val score = scoreEditedBitmap(enhanced!!)
                        val bytes = ByteArrayOutputStream().use { stream ->
                            enhanced!!.compress(Bitmap.CompressFormat.JPEG, 76, stream)
                            stream.toByteArray()
                        }
                        batchPreviewImages = batchPreviewImages + (photo.uri to bytes)
                        batchPreviewScores = batchPreviewScores + (photo.uri to score)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failed++
                    } catch (_: OutOfMemoryError) {
                        failed++
                    } finally {
                        enhanced?.let { if (!it.isRecycled && it !== original) it.recycle() }
                        original?.let { if (!it.isRecycled) it.recycle() }
                    }
                    batchPreviewProgress = index + 1
                }
                val ready = batchPreviewImages.size
                statusMessage = "$preset live preview ready for $ready/${batch.size} photos." +
                    if (failed > 0) " $failed preview(s) could not be generated; originals remain visible." else ""
            } finally {
                isPreparingBatchPreview = false
            }
        }
    }

    /** One adaptive edit for portraits, couples, groups, objects and landscapes. */
    fun applyAiSuggestedEdit(result: PhotoAnalysisResult) {
        val labels = result.labels.joinToString(" ").lowercase()
        val sceneStyle = suggestedStyleFor(result)
        setStyle(sceneStyle)

        // Apply a restrained whole-image grade; blur is not the default effect.
        contrast = 1.04f
        saturation = if (result.faceCount > 0) 1.035f else 1.07f
        warmth = when {
            labels.contains("sunset") || labels.contains("sunrise") || labels.contains("food") || labels.contains("meal") -> 4f
            labels.contains("water") || labels.contains("ocean") || labels.contains("lake") || labels.contains("sky") -> -2f
            else -> 0f
        }
        brightness = when {
            result.lightingSummary.contains("looks dark", ignoreCase = true) -> 5f
            result.lightingSummary.contains("looks bright", ignoreCase = true) -> -3f
            else -> 1f
        }
        backgroundEnhancement = when {
            labels.contains("forest") || labels.contains("tree") || labels.contains("plant") || labels.contains("nature") -> 4.5f
            labels.contains("sky") || labels.contains("cloud") || labels.contains("water") || labels.contains("ocean") -> 3.5f
            else -> 3f
        }
        backgroundBlur = 0f

        // Balance detected people in single, couple and group photos.
        if (result.faceCount > 0) {
            subjectBrightness = 5f
            darkerAreaLift = 5f
            skinSmoothing = 1.5f
            makeupIntensity = 0f
        } else {
            subjectBrightness = 0f
            darkerAreaLift = 0f
            skinSmoothing = 0f
            makeupIntensity = 0f
        }
        selectedStyle = sceneStyle
        showEnhanced = true
        compareBeforeAfter = false
        photoAnalysisResult = result
        statusMessage = "AI Edit applied: scene color, contrast, lighting and detected people were balanced together. Review the result and undo or adjust it if needed."
    }
    LaunchedEffect(activePhoto?.uri) {
        resetPreviewZoom()
        isShowingOriginal = false
        photoAnalysisResult = null
        editedScore = null
        originalPreviewScore = null
    }

    // Android system Back returns to the current batch instead of exiting the editor.
    BackHandler(enabled = activePhoto != null || showBurstComparison || showBurstGroupPicker) {
        if (activePhoto != null) {
            activePhoto = null
            resetPreviewZoom()
        } else if (showBurstComparison) {
            showBurstComparison = false
            comparisonGroupId = null
        } else {
            showBurstGroupPicker = false
        }
    }

    // Debounced live preview: controls update the image without an Apply button.
    LaunchedEffect(activePhoto?.uri, brightness, contrast, saturation, warmth, backgroundBlur, backgroundEnhancement, subjectBrightness, darkerAreaLift, skinSmoothing, makeupIntensity, cropLabel, cropOffsetX, cropOffsetY, rotationDegrees, straightenDegrees, mirrorHorizontal, mirrorVertical, perspectiveTopLeftX, perspectiveTopLeftY, perspectiveTopRightX, perspectiveTopRightY, perspectiveBottomRightX, perspectiveBottomRightY, perspectiveBottomLeftX, perspectiveBottomLeftY, selectedStyle) {
        if (selectedStyle == "Object Removal") return@LaunchedEffect
        val photo = activePhoto
        if (photo != null) {
            delay(220)
            isPreparingPreview = true
            val settings = currentSettings()
            var original: Bitmap? = null
            var adjusted: Bitmap? = null
            var aiNotice: String? = null
            try {
                original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 1800)
                }
                originalPreviewScore = scoreEditedBitmap(original!!)
                if (selectedStyle == "Original") {
                    adjusted = Bitmap.createBitmap(original!!)
                } else if (selectedStyle == "AI Portrait" || backgroundBlur > 0f || backgroundEnhancement > 0f || subjectBrightness > 0f || darkerAreaLift > 0f || skinSmoothing > 0f || makeupIntensity > 0f) {
                    val aiResult = SubjectAwareEnhancer.enhance(original!!, settings, context)
                    adjusted = aiResult.bitmap
                    aiNotice = aiResult.message
                } else {
                    adjusted = withContext(Dispatchers.Default) {
                        AutoEnhancer.enhance(original!!, settings)
                    }
                }
                val scoredBitmap = adjusted!!
                editedScore = scoreEditedBitmap(scoredBitmap)
                previewBitmap?.let { if (!it.isRecycled) it.recycle() }
                val captionBitmap = adjusted
                previewBitmap = captionBitmap
                if (captionPhotoUri != photo.uri.toString()) {
                    captionPhotoUri = photo.uri.toString()
                    suggestCaptions(captionBitmap)
                }
                adjusted = null
                showEnhanced = true
                statusMessage = if (selectedStyle == "Original") {
                    "Original preview. Choose a style or adjust the image to create an edit."
                } else aiNotice ?: if (selectedStyle == "AI Portrait" || backgroundBlur > 0f || backgroundEnhancement > 0f || subjectBrightness > 0f || darkerAreaLift > 0f || skinSmoothing > 0f || makeupIntensity > 0f) {
                    "AI Portrait enhancement applied using on-device person segmentation."
                } else "Live preview updated. Export a copy when you're happy."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = "Preview failed: " + (error.message ?: "unsupported image")
            } finally {
                original?.let { if (!it.isRecycled) it.recycle() }
                adjusted?.let { if (!it.isRecycled) it.recycle() }
                isPreparingPreview = false
            }
        }
    }

    fun exportOne(photo: PhotoItem, settings: EditSettings, useAiPortrait: Boolean = false, useAiUpscale: Boolean = false) {
        if (isExporting) return
        isExporting = true
        operationProgress = 2
        operationStage = if (useAiUpscale) "Reading original photo for AI upscale" else "Reading original photo"
        statusMessage = "Preparing export…"
        scope.launch {
            var original: Bitmap? = null
            var enhanced: Bitmap? = null
            var upscaleInput: Bitmap? = null
            var aiNotice: String? = null
            try {
                original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(
                        context.contentResolver, photo.uri,
                        maxDimension = if (useAiUpscale) 1024 else Int.MAX_VALUE
                    )
                }
                operationProgress = 12
                operationStage = if (useAiUpscale) "Preparing AI upscaling" else "Applying edit settings"
                if (selectedStyle == "Object Removal" && previewBitmap != null && !useAiUpscale) {
                    enhanced = Bitmap.createBitmap(previewBitmap!!)
                } else if (useAiUpscale) {
                    val hasSubjectAdjustments = selectedStyle == "AI Portrait" || settings.backgroundBlur > 0f ||
                        settings.backgroundEnhancement > 0f || settings.subjectBrightness > 0f ||
                        settings.darkerAreaLift > 0f || settings.skinSmoothing > 0f || settings.makeupIntensity > 0f
                    operationProgress = 12
                    operationStage = if (hasSubjectAdjustments) "Applying current portrait/background edits" else "Preparing current edit"
                    upscaleInput = when {
                        selectedStyle == "Object Removal" && previewBitmap != null -> Bitmap.createBitmap(previewBitmap!!)
                        selectedStyle == "Original" -> Bitmap.createBitmap(original!!)
                        hasSubjectAdjustments -> SubjectAwareEnhancer.enhance(original!!, settings, context).bitmap
                        else -> withContext(Dispatchers.Default) { AutoEnhancer.enhance(original!!, settings) }
                    }
                    operationProgress = 14
                    operationStage = "Preparing current edits for AI upscaling"
                    val upscaleResult = AiUpscaler.upscale(context, upscaleInput!!) { progress, stage ->
                        withContext(Dispatchers.Main.immediate) {
                            operationProgress = 12 + (progress * 0.78f).toInt()
                            operationStage = stage
                        }
                    }
                    enhanced = upscaleResult.bitmap
                    val upscaleNotice = if (upscaleResult.inputWasResizedForMemory) {
                        "AI upscaling ran locally after resizing the input for phone memory. "
                    } else "AI upscaling ran locally. "
                    aiNotice = (if (selectedStyle != "Original") "Current edit settings applied before AI upscaling. " else "") + upscaleNotice
                } else if (useAiPortrait) {
                    operationProgress = 22
                    operationStage = "Detecting person, skin, and background masks"
                    val aiResult = SubjectAwareEnhancer.enhance(original!!, settings, context)
                    enhanced = aiResult.bitmap
                    aiNotice = aiResult.message
                } else {
                    enhanced = withContext(Dispatchers.Default) {
                        AutoEnhancer.enhance(original!!, settings)
                    }
                }
                if (useAiUpscale && enhanced != null) editedScore = scoreEditedBitmap(enhanced!!)
                operationProgress = 94
                operationStage = "Saving edited copy to Pictures/FRAME AI"
                val width = enhanced!!.width
                val height = enhanced!!.height
                withContext(Dispatchers.IO) {
                    AutoEnhancer.saveJpeg(context.contentResolver, enhanced!!)
                }
                learnedProfile = PersonalEditLearning.recordAccepted(context, settings)
                operationProgress = 100
                operationStage = "Finished · original photo preserved"
                statusMessage = (aiNotice?.let { "$it " } ?: if (useAiPortrait) {
                    "AI Portrait enhancement applied. "
                } else "") + (if (useAiUpscale) "Saved AI-upscaled " else "Saved full-resolution ") + width + "x" + height +
                    " copy to Pictures/FRAME AI. Original untouched."
            } catch (error: CancellationException) {
                statusMessage = "Export cancelled. No original photo was changed."
                throw error
            } catch (error: OutOfMemoryError) {
                statusMessage = "Export needs more memory than this device can provide for this photo. Try a smaller image."
            } catch (error: Exception) {
                statusMessage = "Export failed: " + (error.message ?: "unknown error")
            } finally {
                enhanced?.let { if (!it.isRecycled) it.recycle() }
                upscaleInput?.let { if (!it.isRecycled) it.recycle() }
                original?.let { if (!it.isRecycled) it.recycle() }
                isExporting = false
            }
        }
    }

    fun removeMarkedObject(photo: PhotoItem) {
        if (isExporting) return
        val strokes = removalStrokes.toList()
        if (strokes.none { it.isNotEmpty() }) {
            statusMessage = "Tap Mark object, then paint over the unwanted object or person in the preview."
            return
        }
        isExporting = true
        operationProgress = 3
        operationStage = "Preparing object removal"
        statusMessage = "Removing the marked object on this device…"
        scope.launch {
            var original: Bitmap? = null
            var repaired: Bitmap? = null
            try {
                original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 2400)
                }
                operationProgress = 20
                operationStage = "Reconstructing the marked area"
                val result = LamaInpainter.inpaint(context, original!!, strokes, removalBrushSize)
                repaired = result.bitmap
                editedScore = scoreEditedBitmap(repaired!!)
                operationProgress = 92
                operationStage = "Saving repaired copy"
                withContext(Dispatchers.IO) { AutoEnhancer.saveJpeg(context.contentResolver, repaired!!) }
                previewBitmap?.let { if (!it.isRecycled) it.recycle() }
                previewBitmap = repaired
                repaired = null
                selectedStyle = "Object Removal"
                showEnhanced = true
                compareBeforeAfter = false
                removalBrushMode = false
                removalStrokes.clear()
                currentRemovalStroke = emptyList()
                statusMessage = "Object removal finished locally. Saved the edited copy to Pictures/FRAME AI; original untouched."
            } catch (error: CancellationException) {
                statusMessage = "Object removal cancelled. Original photo remains untouched."
                throw error
            } catch (error: OutOfMemoryError) {
                statusMessage = "Object removal ran out of memory on this photo. Try a smaller image."
            } catch (error: Exception) {
                statusMessage = "Object removal failed: " + (error.message ?: "unknown error")
            } finally {
                repaired?.let { if (!it.isRecycled) it.recycle() }
                original?.let { if (!it.isRecycled) it.recycle() }
                isExporting = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            // Keep the editor screen bounded so its preview stays visible and the
            // dedicated workspace below it can scroll independently. An outer
            // verticalScroll here gives the nested weighted workspace unbounded
            // height and can make the editing controls appear to disappear.
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            if (activePhoto == null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Mint),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Photo Editor",
                        tint = Ink,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 11.dp)) {
                    Text("FRAME / AI", color = BrightText, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("YOUR PERSONAL PHOTO EDITOR", color = SoftText,
                        fontSize = 9.sp, letterSpacing = 1.1.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("BETA 2.2", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            }

            if (activePhoto != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val scorePhoto = activePhoto
                        if (scorePhoto != null) {
                            val live = editedScore
                            val baseline = originalPreviewScore
                            val displayed = if (isShowingOriginal) baseline else live
                            val delta = if (live != null && baseline != null) live.score - baseline.score else null
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF111818)).padding(9.dp)
                            ) {
                                Text(
                                    if (displayed == null || baseline == null) "TECHNICAL SCORE · CALCULATING…" else if (isShowingOriginal)
                                        "ORIGINAL SCORE  ${displayed.score}/100" else
                                        "EDITED SCORE  ${displayed.score}/100  (Δ ${if (delta!! > 0) "+" else ""}$delta · original ${baseline.score})",
                                    color = when { !isShowingOriginal && delta != null && delta > 0 -> Mint; !isShowingOriginal && delta != null && delta < 0 -> Color(0xFFFF8B8B); else -> BrightText },
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (displayed == null) "Measuring the current preview…" else
                                        "Sharpness ${displayed.sharpness} · Exposure ${displayed.exposure} · Contrast ${displayed.contrast}",
                                    color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp)
                                )
                                Text(
                                    if (displayed == null) scorePhoto.note else "Reason: ${displayed.note}",
                                    color = SoftText, fontSize = 9.sp, lineHeight = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val previewAspectRatio = previewBitmap?.let {
                            it.width.toFloat() / it.height.coerceAtLeast(1).toFloat()
                        } ?: activePhoto?.let {
                            if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height.toFloat() else 1f
                        } ?: 1f
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = screenHeight * 0.5f)
                                .aspectRatio(previewAspectRatio.coerceIn(0.25f, 4f), matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(14.dp)).background(Ink)
                                .onSizeChanged { size ->
                                    previewWidthPx = size.width
                                    previewHeightPx = size.height
                                }
                                .then(
                                    if (removalBrushMode) {
                                        Modifier.pointerInput(removalBrushMode, previewWidthPx, previewHeightPx, previewBitmap?.width, previewBitmap?.height) {
                                            detectDragGestures(
                                                onDragStart = { position -> previewNormalizedPosition(position)?.let { currentRemovalStroke = listOf(it) } },
                                                onDragEnd = { if (currentRemovalStroke.isNotEmpty()) removalStrokes.add(currentRemovalStroke); currentRemovalStroke = emptyList() },
                                                onDragCancel = { currentRemovalStroke = emptyList() }
                                            ) { change, _ ->
                                                change.consume()
                                                previewNormalizedPosition(change.position)?.let { currentRemovalStroke = currentRemovalStroke + it }
                                            }
                                        }
                                    } else if (cropLabel != "Original") {
                                        Modifier.pointerInput(cropLabel, previewWidthPx, previewHeightPx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val width = previewWidthPx.coerceAtLeast(1).toFloat()
                                                val height = previewHeightPx.coerceAtLeast(1).toFloat()
                                                cropOffsetX = (cropOffsetX + dragAmount.x / width * 2f).coerceIn(-1f, 1f)
                                                cropOffsetY = (cropOffsetY + dragAmount.y / height * 2f).coerceIn(-1f, 1f)
                                            }
                                        }
                                    } else {
                                        Modifier
                                            .pointerInput(activePhoto?.uri, cropLabel) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        if (previewScale > 1.1f) {
                                                            resetPreviewZoom()
                                                        } else {
                                                            previewScale = 2.5f
                                                            previewOffsetX = 0f
                                                            previewOffsetY = 0f
                                                        }
                                                    },
                                                    onLongPress = { isShowingOriginal = true },
                                                    onPress = {
                                                        try {
                                                            awaitRelease()
                                                        } finally {
                                                            isShowingOriginal = false
                                                        }
                                                    }
                                                )
                                            }
                                            .transformable(state = previewTransformState)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val active = activePhoto
                            if (active != null) {
                                if (isShowingOriginal) {
                                    AsyncImage(model = active.uri, contentDescription = "Original photo held for comparison",
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = previewScale
                                            scaleY = previewScale
                                            translationX = previewOffsetX
                                            translationY = previewOffsetY
                                        }, contentScale = ContentScale.Fit)
                                    Text("ORIGINAL", color = BrightText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                            .background(Ink.copy(alpha = 0.85f), RoundedCornerShape(5.dp)).padding(5.dp))
                                } else if (compareBeforeAfter && previewBitmap != null) {
                                    AsyncImage(model = previewBitmap, contentDescription = "After edit comparison",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    AsyncImage(model = active.uri, contentDescription = "Before edit comparison",
                                        modifier = Modifier.fillMaxSize().drawWithContent {
                                            clipRect(right = size.width * comparePosition) { this@drawWithContent.drawContent() }
                                        }, contentScale = ContentScale.Fit)
                                    Text("BEFORE", color = BrightText, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                            .background(Ink.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).padding(5.dp))
                                    Text("AFTER", color = BrightText, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                            .background(Ink.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).padding(5.dp))
                                } else if (selectedStyle != "Original" && showEnhanced && previewBitmap != null) {
                                    AsyncImage(model = previewBitmap, contentDescription = "Enhanced preview",
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = previewScale
                                            scaleY = previewScale
                                            translationX = previewOffsetX
                                            translationY = previewOffsetY
                                        }, contentScale = ContentScale.Fit)
                                } else {
                                    AsyncImage(model = active.uri, contentDescription = "Original photo",
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = previewScale
                                            scaleY = previewScale
                                            translationX = previewOffsetX
                                            translationY = previewOffsetY
                                        }, contentScale = ContentScale.Fit)
                                }
                            }
                            if (removalBrushMode || removalStrokes.isNotEmpty() || currentRemovalStroke.isNotEmpty()) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val image = previewBitmap
                                    if (image != null && image.width > 0 && image.height > 0) {
                                        val fit = minOf(size.width / image.width.toFloat(), size.height / image.height.toFloat())
                                        val shownWidth = image.width * fit
                                        val shownHeight = image.height * fit
                                        val left = (size.width - shownWidth) / 2f
                                        val top = (size.height - shownHeight) / 2f
                                        val strokeWidth = (removalBrushSize * 2f * shownWidth / 512f).coerceAtLeast(5f)
                                        val allStrokes = removalStrokes.toList() + listOf(currentRemovalStroke).filter { it.isNotEmpty() }
                                        allStrokes.forEach { points ->
                                            if (points.size == 1) {
                                                drawCircle(Color.Red.copy(alpha = 0.55f), strokeWidth / 2f, Offset(left + points[0].x * shownWidth, top + points[0].y * shownHeight))
                                            } else if (points.size > 1) {
                                                val path = Path().apply {
                                                    moveTo(left + points[0].x * shownWidth, top + points[0].y * shownHeight)
                                                    points.drop(1).forEach { lineTo(left + it.x * shownWidth, top + it.y * shownHeight) }
                                                }
                                                drawPath(path, Color.Red.copy(alpha = 0.72f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                            }
                                        }
                                    }
                                }
                            }
                            if (showCropGuides && cropLabel != "Original" && !compareBeforeAfter) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val thirdW = size.width / 3f
                                    val thirdH = size.height / 3f
                                    val guideColor = Color.White.copy(alpha = 0.65f)
                                    for (i in 1..2) {
                                        drawLine(guideColor, androidx.compose.ui.geometry.Offset(thirdW * i, 0f),
                                            androidx.compose.ui.geometry.Offset(thirdW * i, size.height), strokeWidth = 1.dp.toPx())
                                        drawLine(guideColor, androidx.compose.ui.geometry.Offset(0f, thirdH * i),
                                            androidx.compose.ui.geometry.Offset(size.width, thirdH * i), strokeWidth = 1.dp.toPx())
                                    }
                                }
                            }
                            if (isPreparingPreview) CircularProgressIndicator(color = Mint)
                            if (isExporting) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Ink.copy(alpha = 0.76f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Panel.copy(alpha = 0.98f)).padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { operationProgress.coerceIn(0, 100) / 100f },
                                            color = Mint,
                                            trackColor = SoftText.copy(alpha = 0.25f)
                                        )
                                        Text(
                                            operationStage.ifBlank { "Exporting edited photo…" },
                                            color = BrightText, fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        LinearProgressIndicator(
                                            progress = { operationProgress.coerceIn(0, 100) / 100f },
                                            modifier = Modifier.fillMaxWidth(), color = Mint
                                        )
                                        Text("${operationProgress.coerceIn(0, 100)}% · Original photo remains untouched",
                                            color = SoftText, fontSize = 10.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                        if (compareBeforeAfter) {
                            Text("COMPARISON POSITION  " + (comparePosition * 100).toInt() + "%",
                                color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                            Slider(value = comparePosition, onValueChange = { comparePosition = it }, valueRange = 0.05f..0.95f)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (cropLabel == "Original") "Hold to see original · double-tap to zoom"
                                else "Drag preview to reposition crop",
                                color = SoftText, fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (previewScale > 1.01f) {
                                Text("Reset zoom", color = Mint, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { resetPreviewZoom() }.padding(4.dp))
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            Text("EDIT WORKSPACE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Universal Edit", "AI Edit", "Presets", "Crop & Rotate").forEach { panel ->
                                    Button(
                                        onClick = { activeEditorPanel = panel; compareBeforeAfter = false },
                                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeEditorPanel == panel) Mint else Color(0xFF293333),
                                            contentColor = if (activeEditorPanel == panel) Ink else BrightText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            panel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text("Choose one AI editing group at a time to keep the workspace focused.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            if (activeEditorPanel == "AI Edit") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Subject", "Background", "Remove", "Enhance").forEach { group ->
                                        Button(
                                            onClick = { activeAiSubpanel = group; compareBeforeAfter = false },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (activeAiSubpanel == group) Mint else Color(0xFF293333),
                                                contentColor = if (activeAiSubpanel == group) Ink else BrightText
                                            ),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)
                                        ) { Text(group, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                                    }
                                }
                            }
                        if (activeEditorPanel == "AI Edit" && (activeAiSubpanel == "Enhance" || activeAiSubpanel == "Remove")) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2424)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("AI EDIT ACTIONS", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Improve detail, remove unwanted objects, inspect the scene, and build a personal editing style.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                                if (activeAiSubpanel == "Enhance") {
                                Button(
                                    onClick = {
                                        val photo = activePhoto
                                        if (photo != null) exportOne(photo, currentSettings(), useAiUpscale = true)
                                        else statusMessage = "Open a photo first to use AI Upscale."
                                    },
                                    enabled = activePhoto != null && !isExporting,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                                ) { Text(if (isExporting && operationStage.contains("tile", ignoreCase = true)) "Upscaling…" else if (isExporting) "Processing photo…" else "Run AI Upscale 4×") }
                                if (isExporting && activePhoto != null) {
                                    if (operationStage.contains("Reading original", ignoreCase = true) || operationStage.contains("Loading", ignoreCase = true) || operationStage.contains("Applying current", ignoreCase = true) || operationStage.contains("Preparing current", ignoreCase = true) || operationStage.contains("Detecting", ignoreCase = true)) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Mint)
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { operationProgress.coerceIn(0, 100) / 100f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Mint
                                        )
                                    }
                                    Text("$operationProgress% · $operationStage", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 3.dp))
                                }
                                Text("AI upscaling processes image tiles on this device. The input is memory-bounded to about 1024 px on its longest edge, then reconstructed at 4×. The source photo is never overwritten.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                                if (activeAiSubpanel == "Remove") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            removalBrushMode = !removalBrushMode
                                            compareBeforeAfter = false
                                            if (removalBrushMode) {
                                                resetPreviewZoom()
                                                statusMessage = "Paint over the unwanted object/person in red, then tap Remove marked area."
                                            } else statusMessage = "Mask drawing paused. Your red selection is kept until you clear it or remove the object."
                                        },
                                        enabled = activePhoto != null && !isExporting,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (removalBrushMode) Color(0xFFFF6B6B) else Color(0xFF293333), contentColor = if (removalBrushMode) Ink else BrightText)
                                    ) { Text(if (removalBrushMode) "Finish marking" else "Mark object") }
                                    Button(
                                        onClick = { removalStrokes.clear(); currentRemovalStroke = emptyList(); statusMessage = "Object mask cleared." },
                                        enabled = removalStrokes.isNotEmpty() && !isExporting,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                    ) { Text("Clear mask") }
                                }
                                if (removalBrushMode || removalStrokes.isNotEmpty()) {
                                    Text("REMOVAL BRUSH SIZE  " + removalBrushSize.toInt(), color = SoftText, fontSize = 11.sp)
                                    Slider(value = removalBrushSize, onValueChange = { removalBrushSize = it }, valueRange = 10f..60f)
                                    Text("Paint over the whole object, including its shadow. Red strokes mark the area to remove; the app reconstructs it from surrounding content.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                    Button(
                                        onClick = { activePhoto?.let { removeMarkedObject(it) } },
                                        enabled = activePhoto != null && removalStrokes.isNotEmpty() && !isExporting,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                                    ) { Text(if (isExporting) "Removing object…" else "Remove marked area") }
                                }
                                }
                                if (activeAiSubpanel == "Enhance") {
                                Text("PERSONAL EDIT LEARNING · PRIVATE ON THIS DEVICE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (learnedProfile.acceptedEdits == 0) "No accepted edits learned yet. Export an edit you like; the app will remember its settings locally and use them as your personal starting point."
                                    else "Learned from ${learnedProfile.acceptedEdits} exported edit(s). Your personal starting point updates gradually as you export edits you like. Photos are not stored for learning.",
                                    color = SoftText, fontSize = 10.sp, lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 3.dp, bottom = 8.dp)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (learnedProfile.acceptedEdits > 0) {
                                                recordEditChange()
                                                val learned = learnedProfile.toSettings()
                                                brightness = learned.brightness
                                                contrast = learned.contrast
                                                saturation = learned.saturation
                                                warmth = learned.warmth
                                                backgroundBlur = learned.backgroundBlur
                                                backgroundEnhancement = learned.backgroundEnhancement
                                                subjectBrightness = learned.subjectBrightness
                                                darkerAreaLift = learned.darkerAreaLift
                                                skinSmoothing = learned.skinSmoothing
                                                makeupIntensity = learned.makeupIntensity
                                                selectedStyle = "Personal"
                                                showEnhanced = true
                                                compareBeforeAfter = false
                                                statusMessage = "Applied your personal edit suggestion. Review it before exporting."
                                            } else {
                                                statusMessage = "Export an edited copy you like first; then your personal suggestion will be available."
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = learnedProfile.acceptedEdits > 0,
                                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                                    ) { Text("Use my style", fontSize = 10.sp) }
                                    Button(
                                        onClick = {
                                            PersonalEditLearning.reset(context)
                                            learnedProfile = PersonalEditLearning.load(context)
                                            statusMessage = "Personal editing preferences reset on this device."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                                    ) { Text("Reset learning", fontSize = 10.sp) }
                                }
                                Text("Your editing preferences stay on this device. Photos are not stored for learning.", color = SoftText, fontSize = 9.sp, lineHeight = 12.sp)
                                }
                            }
                        }
                        }
                        if (activeEditorPanel == "AI Edit" && (activeAiSubpanel == "Background" || activeAiSubpanel == "Subject")) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF202929)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                if (activeAiSubpanel == "Background") {
                                Text("BACKGROUND EDITING", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Use the slider to blur the background while keeping the detected person clearer. Best for portraits and couples.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                Text("BACKGROUND ENHANCE · SCENE-AWARE  " + backgroundEnhancement.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = backgroundEnhancement, onValueChange = {
                                    recordEditChange()
                                    backgroundEnhancement = it
                                    selectedStyle = if (it > 0f || backgroundBlur > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..10f)
                                Text("Enhances background color separation and local contrast while protecting the detected person.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                Text("BACKGROUND BLUR  " + backgroundBlur.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = backgroundBlur, onValueChange = {
                                    recordEditChange()
                                    backgroundBlur = it
                                    selectedStyle = if (it > 0f || subjectBrightness > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..20f)
                                }
                                if (activeAiSubpanel == "Subject") {
                                Text("SUBJECT RETOUCH", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("DARKER SKIN AREAS · ADAPTIVE LIFT  " + darkerAreaLift.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = darkerAreaLift, onValueChange = {
                                    recordEditChange()
                                    darkerAreaLift = it
                                    selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..30f)
                                Text("Targets darker detected skin pixels more strongly (for example, hands) while applying less lift to already-bright areas like the face.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                Text("OVERALL SKIN LIGHTENING  " + subjectBrightness.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = subjectBrightness, onValueChange = {
                                    recordEditChange()
                                    subjectBrightness = it
                                    selectedStyle = if (it > 0f || backgroundBlur > 0f || darkerAreaLift > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..25f)
                                Text("Targets detected face and body skin separately from darker-area lift. Your photo stays on this device.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                Text("SKIN SOFTENING · KEEP NATURAL TEXTURE  " + skinSmoothing.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = skinSmoothing, onValueChange = {
                                    recordEditChange()
                                    skinSmoothing = it
                                    selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f || darkerAreaLift > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..10f)
                                Text("Skin-only smoothing is stronger at higher settings. Compare before/after and lower the strength if fine texture looks too soft.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                Text("NATURAL MAKEUP · LIP + CHEEK TINT  " + makeupIntensity.toInt(), color = SoftText, fontSize = 11.sp)
                                Slider(value = makeupIntensity, onValueChange = {
                                    recordEditChange()
                                    makeupIntensity = it
                                    selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f || darkerAreaLift > 0f || skinSmoothing > 0f) "AI Portrait" else "Custom"
                                    showEnhanced = true
                                }, valueRange = 0f..10f)
                                Text("Uses detected face contours for a stronger but still translucent lip tint and softly feathered cheek color. It is optional and remains off at zero.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                        }
                        }

                        if (activeEditorPanel == "Crop & Rotate") {
                            Text("CROP & ROTATE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("All framing tools in one place. Edits preview live and remain undoable.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            Text("ROTATE & MIRROR", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { recordEditChange(); rotationDegrees = (rotationDegrees - 90f).mod(360f); selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("↶ Rotate", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); rotationDegrees = (rotationDegrees + 90f).mod(360f); selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("Rotate ↻", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); mirrorHorizontal = !mirrorHorizontal; selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mirrorHorizontal) Mint else Color(0xFF293333), contentColor = if (mirrorHorizontal) Ink else BrightText)) { Text("Mirror H", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); mirrorVertical = !mirrorVertical; selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mirrorVertical) Mint else Color(0xFF293333), contentColor = if (mirrorVertical) Ink else BrightText)) { Text("Mirror V", fontSize = 10.sp) }
                            }
                            Text("STRAIGHTEN  ${String.format(java.util.Locale.US, "%.1f", straightenDegrees)}°", color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            Slider(value = straightenDegrees, onValueChange = { straightenDegrees = it; selectedStyle = "Custom"; showEnhanced = true }, onValueChangeFinished = { recordEditChange() }, valueRange = -15f..15f)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    val bitmap = previewBitmap
                                    if (bitmap == null || bitmap.isRecycled) statusMessage = "Open a photo preview before auto-leveling."
                                    else scope.launch {
                                        val correction = withContext(Dispatchers.Default) { AutoLeveler.estimateCorrectionDegrees(bitmap) }
                                        if (correction == null) statusMessage = "No reliable horizon found. Adjust Straighten manually."
                                        else {
                                            recordEditChange()
                                            straightenDegrees = (straightenDegrees + correction).coerceIn(-15f, 15f)
                                            selectedStyle = "Custom"
                                            showEnhanced = true
                                            statusMessage = if (correction == 0f) "This photo already looks level." else "Level corrected by ${String.format(java.util.Locale.US, "%.1f", correction)}°. Fine-tune if needed."
                                        }
                                    }
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Auto-level", fontSize = 10.sp) }
                                Button(onClick = {
                                    recordEditChange()
                                    cropLabel = "Original"; cropOffsetX = 0f; cropOffsetY = 0f
                                    rotationDegrees = 0f; straightenDegrees = 0f
                                    mirrorHorizontal = false; mirrorVertical = false
                                    perspectiveTopLeftX = 0f; perspectiveTopLeftY = 0f
                                    perspectiveTopRightX = 0f; perspectiveTopRightY = 0f
                                    perspectiveBottomRightX = 0f; perspectiveBottomRightY = 0f
                                    perspectiveBottomLeftX = 0f; perspectiveBottomLeftY = 0f
                                    showPerspectiveControls = false; selectedStyle = "Custom"; showEnhanced = true
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("Reset framing", fontSize = 10.sp) }
                            }
                            Text("CROP RATIO", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Original", "Square", "Portrait 4:5", "Story 9:16", "Landscape 16:9").forEach { crop ->
                                    Button(onClick = { recordEditChange(); cropLabel = crop; cropOffsetX = 0f; cropOffsetY = 0f; selectedStyle = "Custom"; showEnhanced = true; compareBeforeAfter = false },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (cropLabel == crop) Mint else Color(0xFF293333), contentColor = if (cropLabel == crop) Ink else BrightText),
                                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)) { Text(crop, fontSize = 10.sp) }
                                }
                            }
                            Text("PERSPECTIVE CROP", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Text("Move each corner inward independently. The selected quadrilateral is corrected into a rectangle—useful for signs, buildings, screens, or angled shots.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { showPerspectiveControls = !showPerspectiveControls }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = if (showPerspectiveControls) Mint else Color(0xFF293333), contentColor = if (showPerspectiveControls) Ink else BrightText)) { Text(if (showPerspectiveControls) "Hide corner controls" else "Adjust four corners", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); perspectiveTopLeftX = 0f; perspectiveTopLeftY = 0f; perspectiveTopRightX = 0f; perspectiveTopRightY = 0f; perspectiveBottomRightX = 0f; perspectiveBottomRightY = 0f; perspectiveBottomLeftX = 0f; perspectiveBottomLeftY = 0f; selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("Reset corners", fontSize = 10.sp) }
                            }
                            if (showPerspectiveControls) {
                                Text("TOP-LEFT CORNER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Horizontal inset  ${(perspectiveTopLeftX * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveTopLeftX, onValueChange = { recordEditChange(); perspectiveTopLeftX = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("Vertical inset  ${(perspectiveTopLeftY * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveTopLeftY, onValueChange = { recordEditChange(); perspectiveTopLeftY = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("TOP-RIGHT CORNER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Horizontal inset  ${(perspectiveTopRightX * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveTopRightX, onValueChange = { recordEditChange(); perspectiveTopRightX = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("Vertical inset  ${(perspectiveTopRightY * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveTopRightY, onValueChange = { recordEditChange(); perspectiveTopRightY = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("BOTTOM-RIGHT CORNER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Horizontal inset  ${(perspectiveBottomRightX * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveBottomRightX, onValueChange = { recordEditChange(); perspectiveBottomRightX = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("Vertical inset  ${(perspectiveBottomRightY * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveBottomRightY, onValueChange = { recordEditChange(); perspectiveBottomRightY = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("BOTTOM-LEFT CORNER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Horizontal inset  ${(perspectiveBottomLeftX * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveBottomLeftX, onValueChange = { recordEditChange(); perspectiveBottomLeftX = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                                Text("Vertical inset  ${(perspectiveBottomLeftY * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                Slider(value = perspectiveBottomLeftY, onValueChange = { recordEditChange(); perspectiveBottomLeftY = it; selectedStyle = "Custom"; showEnhanced = true }, valueRange = 0f..0.35f)
                            }
                        }
                        if (activeEditorPanel == "Presets") {
                            Text("QUICK EDIT", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Original", "Auto", "Natural", "AI Portrait", "Vivid", "Warm", "Cool", "Mono").forEach { style ->
                                    Button(
                                        onClick = { setStyle(style) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedStyle == style) Mint else Color(0xFF293333),
                                            contentColor = if (selectedStyle == style) Ink else BrightText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) { Text(style, fontSize = 10.sp) }
                                }
                            }

                        }
                        val activeIndex = selectedPhotos.indexOfFirst { it.uri == activePhoto?.uri }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (activeIndex > 0) openEditor(selectedPhotos[activeIndex - 1])
                                },
                                enabled = activeIndex > 0 && !isExporting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 5.dp)
                            ) { Text("← Previous", fontSize = 11.sp) }
                            Text(
                                if (activeIndex >= 0) "${activeIndex + 1} / ${selectedPhotos.size}" else "",
                                color = SoftText, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Button(
                                onClick = {
                                    if (activeIndex >= 0 && activeIndex < selectedPhotos.lastIndex) {
                                        openEditor(selectedPhotos[activeIndex + 1])
                                    }
                                },
                                enabled = activeIndex >= 0 && activeIndex < selectedPhotos.lastIndex && !isExporting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 5.dp)
                            ) { Text("Next →", fontSize = 11.sp) }
                        }
                        Spacer(modifier = Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Button(onClick = { undoEdit() }, enabled = undoHistory.isNotEmpty() && !isExporting,
                                modifier = Modifier.weight(0.65f), shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)
                            ) { Text("Undo", fontSize = 10.sp) }
                            Button(onClick = { redoEdit() }, enabled = redoHistory.isNotEmpty() && !isExporting,
                                modifier = Modifier.weight(0.65f), shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)
                            ) { Text("Redo", fontSize = 10.sp) }
                            Button(onClick = { compareBeforeAfter = !compareBeforeAfter; showEnhanced = true },
                                enabled = previewBitmap != null && selectedStyle != "Original", modifier = Modifier.weight(0.85f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (compareBeforeAfter) Mint else Color(0xFF293333),
                                    contentColor = if (compareBeforeAfter) Ink else BrightText),
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)
                            ) { Text(if (compareBeforeAfter) "Close compare" else "Compare", fontSize = 10.sp) }
                            Button(
                                onClick = { showEnhanced = !showEnhanced; compareBeforeAfter = false },
                                enabled = previewBitmap != null && selectedStyle != "Original",
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(if (showEnhanced) "Show original" else "Show enhanced", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    val photo = activePhoto
                                    if (photo != null) exportOne(photo, currentSettings(), selectedStyle == "AI Portrait" || backgroundBlur > 0f || backgroundEnhancement > 0f || subjectBrightness > 0f || darkerAreaLift > 0f || skinSmoothing > 0f || makeupIntensity > 0f)
                                },
                                enabled = previewBitmap != null && !isExporting && selectedStyle != "Original",
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Export copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (activeEditorPanel == "AI Edit" && activeAiSubpanel == "Enhance") {
                        Button(
                            onClick = {
                                val photo = activePhoto
                                if (photo != null && !isRunningPhotoAnalysis && !isExporting) {
                                    isRunningPhotoAnalysis = true
                                    photoAnalysisResult = null
                                    statusMessage = "Inspecting subjects, scene labels, and lighting on this device…"
                                    scope.launch {
                                        var analysisBitmap: Bitmap? = null
                                        try {
                                            analysisBitmap = withContext(Dispatchers.IO) {
                                                AutoEnhancer.decodeForEditing(
                                                    context.contentResolver, photo.uri, maxDimension = 1400
                                                )
                                            }
                                            val result = PhotoAnalysisEngine.analyze(analysisBitmap!!)
                                            photoAnalysisResult = result
                                            applyAiSuggestedEdit(result)
                                            statusMessage = "AI edit applied. Review the preview, compare it with the original, and undo or adjust anything that looks unnatural."
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            statusMessage = "AI analysis failed: " + (error.message ?: "could not inspect this image")
                                        } finally {
                                            analysisBitmap?.let { if (!it.isRecycled) it.recycle() }
                                            isRunningPhotoAnalysis = false
                                        }
                                    }
                                }
                            },
                            enabled = activePhoto != null && !isRunningPhotoAnalysis && !isExporting,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (photoAnalysisResult != null) Color(0xFF293333) else Mint,
                                contentColor = if (photoAnalysisResult != null) Mint else Ink
                            )
                        ) {
                            if (isRunningPhotoAnalysis) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Mint
                                )
                                Text("  Analysing photo…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                androidx.compose.material3.Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                                Text(
                                    "  AI Edit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        }
                        if (activeEditorPanel == "Universal Edit") {
                            Text("UNIVERSAL EDIT · STANDARD CONTROLS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Brightness, contrast, saturation, warmth, and crop apply alongside any active AI adjustment.", color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("MANUAL ADJUSTMENTS", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(if (showCropGuides) "Guides on" else "Guides off", color = Mint, fontSize = 10.sp,
                                    modifier = Modifier.clickable { showCropGuides = !showCropGuides }.padding(4.dp))
                            }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("CAPTION IDEAS THAT SOUND LIKE YOU", color = BrightText, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Short, casual caption ideas based on visual clues. Pick a vibe or edit the wording so it sounds like you.",
                            color = SoftText, fontSize = 10.sp, lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Auto", "Portrait", "Friends", "Festival", "Celebration", "Romantic", "Funny", "Devotional", "Travel", "Family", "Aesthetic").forEach { theme ->
                                Button(onClick = {
                                    captionTheme = theme
                                    val existing = captionSuggestions
                                    if (existing != null) applyCaptionSuggestions(existing.detectedLabels.map { it to 0.9f }, captionAspectRatio)
                                }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (captionTheme == theme) Mint else Color(0xFF293333), contentColor = if (captionTheme == theme) Ink else BrightText)) {
                                    Text(theme, fontSize = 10.sp)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { suggestCaptions() }, modifier = Modifier.weight(1f), enabled = previewBitmap != null && !isGeneratingCaptions,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text(if (isGeneratingCaptions) "Analyzing…" else "Suggest captions", fontSize = 10.sp) }
                            Button(
                                onClick = {
                                    val suggestions = captionSuggestions
                                    if (suggestions != null) {
                                        captionIndex = (captionIndex + 1) % suggestions.english.size
                                        captionEnglish = suggestions.english[captionIndex]
                                        captionBengali = suggestions.bengali[captionIndex]
                                    } else suggestCaptions()
                                },
                                enabled = captionSuggestions != null && !isGeneratingCaptions,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Regenerate", fontSize = 10.sp) }
                        }
                        if (captionSuggestions != null) {
                            val detected = captionSuggestions?.detectedLabels.orEmpty()
                            if (detected.isNotEmpty()) {
                                Text("Visual clues: " + detected.joinToString(" · "),
                                    color = SoftText, fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 3.dp))
                            }
                            OutlinedTextField(value = captionEnglish, onValueChange = { captionEnglish = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                label = { Text("English caption") }, minLines = 2, maxLines = 4)
                            OutlinedTextField(value = captionBengali, onValueChange = { captionBengali = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                label = { Text("বাংলা ক্যাপশন") }, minLines = 2, maxLines = 4)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { clipboardManager.setText(AnnotatedString(captionEnglish)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("Copy English", fontSize = 10.sp) }
                                Button(onClick = { clipboardManager.setText(AnnotatedString(captionBengali)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("Copy বাংলা", fontSize = 10.sp) }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("BRIGHTNESS  " + brightness.toInt(), color = SoftText, fontSize = 11.sp)
                        Slider(value = brightness, onValueChange = {
                            recordEditChange()
                            brightness = it
                            selectedStyle = "Custom"
                            showEnhanced = true
                        }, valueRange = -25f..25f)
                        Text("CONTRAST  " + String.format(java.util.Locale.US, "%.2f", contrast),
                            color = SoftText, fontSize = 11.sp)
                        Slider(value = contrast, onValueChange = {
                            recordEditChange()
                            contrast = it
                            selectedStyle = "Custom"
                            showEnhanced = true
                        }, valueRange = 0.75f..1.40f)
                        Text("SATURATION  " + String.format(java.util.Locale.US, "%.2f", saturation),
                            color = SoftText, fontSize = 11.sp)
                        Slider(value = saturation, onValueChange = {
                            recordEditChange()
                            saturation = it
                            selectedStyle = "Custom"
                            showEnhanced = true
                        }, valueRange = 0f..1.65f)
                        Text("WARMTH  " + warmth.toInt(), color = SoftText, fontSize = 11.sp)
                        Slider(value = warmth, onValueChange = {
                            recordEditChange()
                            warmth = it
                            selectedStyle = "Custom"
                            showEnhanced = true
                        }, valueRange = -24f..24f)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (cropLabel != "Original") {
                            Text("CROP HORIZONTAL  " + (cropOffsetX * 100).toInt() + "%", color = SoftText, fontSize = 11.sp)
                            Slider(
                                value = cropOffsetX,
                                onValueChange = { recordEditChange(); cropOffsetX = it; selectedStyle = "Custom"; showEnhanced = true },
                                valueRange = -1f..1f
                            )
                            Text("CROP VERTICAL  " + (cropOffsetY * 100).toInt() + "%", color = SoftText, fontSize = 11.sp)
                            Slider(
                                value = cropOffsetY,
                                onValueChange = { recordEditChange(); cropOffsetY = it; selectedStyle = "Custom"; showEnhanced = true },
                                valueRange = -1f..1f
                            )
                            Text("Move the crop window to keep your subject in frame.", color = SoftText, fontSize = 10.sp)
                        } else {
                            Text("Choose a crop ratio to reposition the crop window.", color = SoftText, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    recordEditChange()
                                    setStyle("Auto")
                                    cropLabel = "Original"
                                    cropOffsetX = 0f
                                    cropOffsetY = 0f
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) { Text("Reset edits", fontSize = 11.sp) }
                        }
                        }
                        }
                    }
                }
            }

            if (activePhoto == null) {
                Spacer(modifier = Modifier.height(18.dp))
                Text("Your moments. At their best.", color = BrightText, fontSize = 27.sp,
                    lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Rank your shots, preview a gentle auto-edit, and export new copies.",
                    modifier = Modifier.padding(top = 5.dp),
                    color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            if (activePhoto == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Text("  Choose photos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        if (!isExporting && !isAnalyzing && selectedPhotos.isNotEmpty()) {
                            isExporting = true
                            exportProgress = 0
                            statusMessage = "Enhancing and exporting the batch…"
                            val settingsForBatch = if (batchPreset == "Current edits") currentSettings() else BatchExportPolicy.settingsForPreset(batchPreset)
                            val useAiPortrait = batchPreset == "AI Edit" || batchPreset == "AI Portrait" ||
                                settingsForBatch.backgroundBlur > 0f || settingsForBatch.backgroundEnhancement > 0f ||
                                settingsForBatch.subjectBrightness > 0f || settingsForBatch.darkerAreaLift > 0f ||
                                settingsForBatch.skinSmoothing > 0f || settingsForBatch.makeupIntensity > 0f
                            scope.launch {
                                var succeeded = 0
                                var failed = 0
                                var aiFallbacks = 0
                                val failedNames = mutableListOf<String>()
                                val batch = selectedPhotos.filter { it.decision != "Reject" }
                                val skipped = selectedPhotos.size - batch.size
                                batchSummary = null
                                try {
                                    for ((index, photo) in batch.withIndex()) {
                                        var original: Bitmap? = null
                                        var enhanced: Bitmap? = null
                                        try {
                                            original = withContext(Dispatchers.IO) {
                                                AutoEnhancer.decodeForEditing(
                                                    context.contentResolver, photo.uri, maxDimension = Int.MAX_VALUE
                                                )
                                            }
                                            val photoSettings = if (batchPreset == "AI Edit") {
                                                aiSettingsFor(PhotoAnalysisEngine.analyze(original!!))
                                            } else {
                                                settingsForBatch
                                            }
                                            if (useAiPortrait) {
                                                val aiResult = SubjectAwareEnhancer.enhance(original!!, photoSettings, context)
                                                enhanced = aiResult.bitmap
                                                if (!aiResult.usedAi) aiFallbacks++
                                            } else {
                                                enhanced = withContext(Dispatchers.Default) {
                                                    AutoEnhancer.enhance(original!!, photoSettings)
                                                }
                                            }
                                            withContext(Dispatchers.IO) {
                                                AutoEnhancer.saveJpeg(context.contentResolver, enhanced!!)
                                            }
                                            succeeded++
                                        } catch (error: CancellationException) {
                                            statusMessage = "Batch export cancelled. Previously saved copies remain available; originals are untouched."
                                            throw error
                                        } catch (error: OutOfMemoryError) {
                                            failed++
                                            failedNames.add((photo.uri.lastPathSegment ?: "Photo " + (index + 1)) + " (not enough memory)")
                                        } catch (error: Exception) {
                                            failed++
                                            failedNames.add(photo.uri.lastPathSegment ?: "Photo " + (index + 1))
                                        } finally {
                                            enhanced?.let { if (!it.isRecycled) it.recycle() }
                                            original?.let { if (!it.isRecycled) it.recycle() }
                                        }
                                        exportProgress = index + 1
                                        statusMessage = BatchExportPolicy.progressLabel(index + 1, batch.size, succeeded, failed)
                                    }
                                    batchSummary = BatchExportPolicy.summary(succeeded, failed, skipped, batchPreset, failedNames) +
                                        if (aiFallbacks > 0) "\nAI fallback used for $aiFallbacks photo(s) because person segmentation was unavailable." else ""
                                    statusMessage = (batchSummary ?: "Batch export finished.") + " Originals are untouched."
                                } finally {
                                    isExporting = false
                                }
                            }
                        }
                    },
                    enabled = selectedPhotos.any { it.decision != "Reject" } && !isAnalyzing && !isExporting,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Panel, contentColor = Mint,
                        disabledContainerColor = Panel, disabledContentColor = SoftText
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Text("  Save all", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            }

            if (activePhoto == null && selectedPhotos.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("BATCH PRESET", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            previewBatchPreset("AI Edit")
                        },
                        enabled = !isExporting && !isAnalyzing && selectedPhotos.any { it.decision != "Reject" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                    ) { Text("✦ AI Edit all", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BatchExportPolicy.presetNames.forEach { preset ->
                        Button(onClick = { previewBatchPreset(preset) },
                            enabled = !isExporting && !isAnalyzing && !isPreparingBatchPreview && selectedPhotos.any { it.decision != "Reject" },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (batchPreset == preset) Mint else Color(0xFF293333),
                                contentColor = if (batchPreset == preset) Ink else BrightText
                            ), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text(preset, fontSize = 10.sp) }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(selectedPhotos.count { it.decision != "Reject" }.toString() + " photos to export · " +
                        selectedPhotos.count { it.decision == "Reject" } + " rejected will be skipped",
                        color = SoftText, fontSize = 10.sp)
                    Text(if (batchPreset == "Current edits") "Uses the settings currently open in the editor." else
                        "Applies " + batchPreset + " consistently to every eligible photo.",
                        color = SoftText, fontSize = 10.sp)
                }
                if (isPreparingBatchPreview) {
                    LinearProgressIndicator(
                        progress = { if (selectedPhotos.none { it.decision != "Reject" }) 0f else batchPreviewProgress.toFloat() / selectedPhotos.count { it.decision != "Reject" } },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Mint
                    )
                    Text("Live previews $batchPreviewProgress / ${selectedPhotos.count { it.decision != "Reject" }} · images and scores update as each photo finishes",
                        color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                } else if (batchPreviewScores.isNotEmpty()) {
                    Text("LIVE PREVIEW · ${batchPreviewScores.size} photos updated · scores show measured changes, not guaranteed improvement",
                        color = Mint, fontSize = 10.sp, lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                }
                if (batchSummary != null) {
                    Text(batchSummary!!, color = Mint, fontSize = 10.sp, lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                }
            }

            val keptCount = selectedPhotos.count { it.decision == "Keep" }
            val reviewCount = selectedPhotos.count { it.decision == "Review" }
            val rejectedCount = selectedPhotos.count { it.decision == "Reject" }
            val exactDuplicateCount = selectedPhotos.count { it.isExactDuplicate }
            val nearDuplicateCount = selectedPhotos.count { it.isNearDuplicate }
            val burstGroups = selectedPhotos.filter { it.burstGroupId != null }
                .groupBy { it.burstGroupId }
            val burstExtraCount = burstGroups.values.sumOf { group -> (group.size - 1).coerceAtLeast(0) }
            if (activePhoto == null && exactDuplicateCount > 0) {
                Button(
                    onClick = {
                        selectedPhotos.removeAll { it.isExactDuplicate }
                        statusMessage = "Removed " + exactDuplicateCount +
                            " exact duplicate file(s) from this selection. Your originals are untouched."
                    },
                    enabled = !isAnalyzing && !isExporting,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF293333), contentColor = Mint
                    )
                ) { Text("Remove " + exactDuplicateCount + " exact duplicate(s)") }
            }

            if (activePhoto == null && burstGroups.values.any { it.size >= 2 }) {
                Button(
                    onClick = {
                        showBurstGroupPicker = true
                    },
                    enabled = !isAnalyzing && !isExporting && burstGroups.values.any { it.size >= 2 },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF293333), contentColor = Mint
                    )
                ) { Text("Choose burst group to compare") }
            }

            if (activePhoto == null && burstExtraCount > 0) {
                Button(
                    onClick = {
                        val keepUris = burstGroups.values.mapNotNull { group ->
                            group.maxByOrNull { it.score ?: -1 }?.uri
                        }.toSet()
                        val removeUris = burstGroups.values.flatten()
                            .filter { it.uri !in keepUris }
                            .map { it.uri }
                            .toSet()
                        selectedPhotos.removeAll { it.uri in removeUris }
                        statusMessage = "Kept the highest-scoring photo in each time-based burst candidate set. Removed " +
                            removeUris.size + " item(s) from this selection only; originals are untouched."
                    },
                    enabled = !isAnalyzing && !isExporting,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF293333), contentColor = Mint
                    )
                ) { Text("Keep best from burst candidates · remove " + burstExtraCount) }
            }

            if (activePhoto == null && nearDuplicateCount > 0) {
                Button(
                    onClick = {
                        selectedPhotos.removeAll { it.isNearDuplicate }
                        statusMessage = "Removed " + nearDuplicateCount +
                            " visually similar photo(s) from this selection. Your originals are untouched."
                    },
                    enabled = !isAnalyzing && !isExporting,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF293333), contentColor = Mint
                    )
                ) { Text("Remove " + nearDuplicateCount + " visually similar photo(s)") }
            }

            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (selectedPhotos.isEmpty()) 0f else analysisProgress.toFloat() / selectedPhotos.size },
                    modifier = Modifier.fillMaxWidth(), color = Mint
                )
                Text("Analysing " + analysisProgress + " / " + selectedPhotos.size,
                    color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (isExporting) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (selectedPhotos.none { it.decision != "Reject" }) 0f else exportProgress.toFloat() / selectedPhotos.count { it.decision != "Reject" } },
                    modifier = Modifier.fillMaxWidth(), color = Mint
                )
                Text("Exporting " + exportProgress + " / " + selectedPhotos.count { it.decision != "Reject" } + " (rejected photos skipped)",
                    color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }

            if (activePhoto == null && showBurstGroupPicker && !showBurstComparison) {
                val availableBurstGroups = burstGroups.entries
                    .filter { it.value.size >= 2 }
                    .sortedBy { entry -> entry.value.mapNotNull { it.captureTimeMillis }.minOrNull() ?: Long.MAX_VALUE }
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CHOOSE A BURST GROUP", color = BrightText, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Text("Pick the moment you want to compare. Nothing is removed.",
                                    color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                            Button(
                                onClick = { showBurstGroupPicker = false },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 1.dp)
                            ) { Text("Back", fontSize = 10.sp) }
                        }
                        availableBurstGroups.forEachIndexed { index, entry ->
                            val groupPhotos = entry.value.sortedByDescending { it.score ?: -1 }
                            val bestScore = groupPhotos.firstOrNull()?.score
                            val captureTime = groupPhotos.mapNotNull { it.captureTimeMillis }.minOrNull()
                            val timeLabel = if (captureTime != null) {
                                java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(captureTime))
                            } else {
                                "Capture time unavailable"
                            }
                            Button(
                                onClick = {
                                    comparisonGroupId = entry.key
                                    showBurstGroupPicker = false
                                    showBurstComparison = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Group " + (index + 1) + " · " + groupPhotos.size + " photos",
                                        color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(timeLabel + "  ·  Best score " + (bestScore ?: "—") + "/100",
                                        color = SoftText, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (activePhoto == null && showBurstComparison) {
                val comparisonPhotos = selectedPhotos
                    .filter { it.burstGroupId == comparisonGroupId }
                    .sortedByDescending { it.score ?: -1 }
                    .take(2)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("BURST COMPARISON", color = BrightText, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Text("Choose the frame you prefer. Scores are only technical guidance.",
                                    color = SoftText, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                            Button(
                                onClick = { showBurstComparison = false; comparisonGroupId = null },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF293333), contentColor = BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 1.dp)
                            ) { Text("Back", fontSize = 10.sp) }
                        }
                        if (comparisonPhotos.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                comparisonPhotos.forEachIndexed { index, candidate ->
                                    Column(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF111616)).padding(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        AsyncImage(
                                            model = candidate.uri,
                                            contentDescription = "Burst candidate " + (index + 1),
                                            modifier = Modifier.fillMaxWidth().weight(1f)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            (if (index == 0) "HIGHER SCORE" else "ALTERNATIVE") +
                                                "\nScore: " + (candidate.score ?: "—") + "/100",
                                            color = if (index == 0) Mint else BrightText,
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            lineHeight = 15.sp
                                        )
                                        Text(candidate.note, color = SoftText, fontSize = 9.sp,
                                            lineHeight = 12.sp, maxLines = 3)
                                        Button(
                                            onClick = {
                                                openEditor(candidate)
                                                showBurstComparison = false
                                                comparisonGroupId = null
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (index == 0) Mint else Color(0xFF293333),
                                                contentColor = if (index == 0) Ink else BrightText
                                            ),
                                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp)
                                        ) {
                                            Text("Edit this photo", fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Text(
                                "Nothing is deleted here. Choose a frame to edit, then return to the grid when ready.",
                                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp
                            )
                        } else {
                            Text("These burst candidates are no longer both in the current selection.",
                                color = SoftText, fontSize = 12.sp)
                        }
                    }
                }
            }



            if (activePhoto == null && !showBurstComparison && !showBurstGroupPicker) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedPhotos.isEmpty()) "YOUR WORKSPACE" else "BEST SHOTS FIRST",
                    color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(selectedPhotos.size.toString() + " photos", color = SoftText, fontSize = 11.sp)
            }
            if (selectedPhotos.isNotEmpty()) {
                Text("Keep " + keptCount + "  ·  Review " + reviewCount + "  ·  Reject " + rejectedCount,
                    color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Keep", "Review", "Reject", "Similar", "Burst").forEach { filter ->
                        Button(
                            onClick = { gridFilter = filter },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (gridFilter == filter) Mint else Color(0xFF293333),
                                contentColor = if (gridFilter == filter) Ink else BrightText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text(filter, fontSize = 10.sp) }
                    }
                }
            }
            Text(
                "Scores measure technical quality only—not whether a moment matters to you.",
                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 7.dp)
            )
            }

            if (activePhoto == null && !showBurstComparison && !showBurstGroupPicker && selectedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xFF151A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(22.dp)) {
                        androidx.compose.material3.Icon(Icons.Rounded.Tune, contentDescription = null,
                            tint = Mint, modifier = Modifier.size(34.dp))
                        Text("A better edit starts here", color = BrightText, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Choose a batch. FRAME / AI will score sharpness, exposure and contrast, then let you preview and export enhanced copies.",
                            color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            } else if (activePhoto == null && !showBurstComparison && !showBurstGroupPicker) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val visiblePhotos = selectedPhotos.filter { photo ->
                        when (gridFilter) {
                            "Keep" -> photo.decision == "Keep"
                            "Review" -> photo.decision == "Review"
                            "Reject" -> photo.decision == "Reject"
                            "Similar" -> photo.isNearDuplicate || photo.isExactDuplicate
                            "Burst" -> photo.burstGroupId != null
                            else -> true
                        }
                    }
                    val rankByUri = selectedPhotos.sortedByDescending { it.score ?: -1 }
                        .mapIndexed { index, rankedPhoto -> rankedPhoto.uri to (index + 1) }.toMap()
                    items(visiblePhotos, key = { it.uri.toString() }) { photo ->
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(13.dp))
                                .background(if (activePhoto?.uri == photo.uri) Color(0xFF263B38) else Panel)
                                .clickable { openEditor(photo) }
                                .padding(5.dp)
                        ) {
                            Box {
                                AsyncImage(model = batchPreviewImages[photo.uri] ?: photo.uri,
                                    contentDescription = if (batchPreviewImages.containsKey(photo.uri)) "Live edited batch preview" else "Photo",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)
                                        .clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop)
                                if (batchPreviewImages.containsKey(photo.uri)) {
                                    Text("LIVE", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                            .background(Mint, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 3.dp))
                                }
                                if (activePhoto?.uri == photo.uri) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Editing this photo",
                                        tint = Mint,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                                    )
                                }
                            }
                            val photoRank = rankByUri[photo.uri] ?: 0
                            val liveScore = batchPreviewScores[photo.uri]
                            val baselineScore = batchOriginalScores[photo.uri]
                            val scoreDelta = if (liveScore != null && baselineScore != null) liveScore.score - baselineScore.score else null
                            Text(
                                if (liveScore != null) "LIVE SCORE ${liveScore.score}/100" +
                                    (scoreDelta?.let { " · Δ ${if (it > 0) "+" else ""}${it} · was ${baselineScore?.score ?: "—"}" } ?: "")
                                else if (photo.score == null) "Analysing…" else "#$photoRank · Score ${photo.score}/100",
                                color = when {
                                    scoreDelta != null && scoreDelta > 0 -> Mint
                                    scoreDelta != null && scoreDelta < 0 -> Color(0xFFFF8B8B)
                                    (liveScore?.score ?: photo.score ?: 0) >= 70 -> Mint
                                    else -> BrightText
                                },
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
                            )
                            Text(
                                if (liveScore != null && baselineScore != null)
                                    "Sharp ${liveScore.sharpness} (${if (liveScore.sharpness - baselineScore.sharpness > 0) "+" else ""}${liveScore.sharpness - baselineScore.sharpness}) · Exp ${liveScore.exposure} (${if (liveScore.exposure - baselineScore.exposure > 0) "+" else ""}${liveScore.exposure - baselineScore.exposure}) · Ctr ${liveScore.contrast} (${if (liveScore.contrast - baselineScore.contrast > 0) "+" else ""}${liveScore.contrast - baselineScore.contrast})"
                                else if (liveScore != null) "Sharpness ${liveScore.sharpness} · Exposure ${liveScore.exposure} · Contrast ${liveScore.contrast}"
                                else "Sharpness ${photo.sharpness} · Exposure ${photo.exposure} · Contrast ${photo.contrast}",
                                color = SoftText, fontSize = 8.sp, lineHeight = 11.sp,
                                modifier = Modifier.padding(top = 2.dp, start = 2.dp))
                            val formats = batchPreviewFormats[photo.uri]
                                ?: if (photo.width > 0 && photo.height > 0) recommendedFormats(photo.width, photo.height) else emptyList()
                            if (formats.isNotEmpty()) {
                                Text("BEST FIT · " + formats.joinToString(" · "), color = Color(0xFF9CC8FF),
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp))
                            }
                            Text(photo.decision.uppercase(),
                                color = when (photo.decision) {
                                    "Keep" -> Mint
                                    "Reject" -> Color(0xFFFF8B8B)
                                    else -> Color(0xFFFFC078)
                                }, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp, start = 2.dp))
                            if (photo.isExactDuplicate) {
                                Text("EXACT DUPLICATE", color = Color(0xFFFFC078),
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp))
                            }
                            if (photo.isNearDuplicate) {
                                Text("SIMILAR · LOWER SCORE", color = Color(0xFF9CC8FF),
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp))
                            }
                            if (photo.burstGroupId != null) {
                                Text("BURST CANDIDATE", color = Color(0xFFFFC078),
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp, start = 2.dp))
                            }
                            Text(if (liveScore != null) "Reason: ${liveScore.note}" else photo.note,
                                color = SoftText, fontSize = 9.sp, lineHeight = 12.sp,
                                maxLines = 2, modifier = Modifier.padding(top = 2.dp, start = 2.dp, bottom = 2.dp))
                        }
                    }
                }
            }

            if (activePhoto == null && !showBurstComparison && !showBurstGroupPicker && selectedPhotos.isNotEmpty() &&
                gridFilter != "All" && selectedPhotos.none { photo ->
                    when (gridFilter) {
                        "Keep" -> photo.decision == "Keep"
                        "Review" -> photo.decision == "Review"
                        "Reject" -> photo.decision == "Reject"
                        "Similar" -> photo.isNearDuplicate || photo.isExactDuplicate
                        "Burst" -> photo.burstGroupId != null
                        else -> true
                    }
                }) {
                Text("No photos in the " + gridFilter + " filter yet.", color = SoftText,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Text(
                statusMessage,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}
