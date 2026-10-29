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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
private fun EditorImageAction(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Color(0xFF293333) else Color(0xFF202626))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, color = if (enabled) Mint else SoftText.copy(alpha = 0.45f),
            fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
    }
}

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
    private fun flushEditorCheckpoint() {
        // Compose writes checkpoints with apply() as controls change. commit() here blocks
        // until pending SharedPreferences writes reach disk when Android backgrounds the app.
        getSharedPreferences("frame_ai_recovery", android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong("last_lifecycle_flush_ms", System.currentTimeMillis())
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Queue silent background updates; WorkManager waits for unmetered connectivity.
        TrendPackUpdateScheduler.schedule(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, onPrimary = Ink, background = Ink,
                surface = Panel, onSurface = BrightText, secondary = Mint
            )) {
                PhotoEditorHome()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        flushEditorCheckpoint()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        flushEditorCheckpoint()
        super.onStop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoEditorHome() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var remoteCaptionEntries by remember {
        mutableStateOf(CaptionCatalogRepository.readCachedEntries(context))
    }
    var aiEditRules by remember {
        mutableStateOf(AiEditRulesRepository.readCachedRules(context))
    }
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
    var lastExportedUri by remember { mutableStateOf<Uri?>(null) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var operationProgress by remember { mutableIntStateOf(0) }
    var operationStage by remember { mutableStateOf("Ready") }
    var batchPreset by remember { mutableStateOf("Current edits") }
    var batchAiLook by remember { mutableStateOf("Natural") }
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
    var showEnhanced by remember { mutableStateOf(recoveryPrefs.getBoolean("show_enhanced", true)) }
    var compareBeforeAfter by remember { mutableStateOf(false) }
    var comparePosition by remember { mutableFloatStateOf(0.5f) }
    var showCropGuides by remember { mutableStateOf(true) }
    val undoHistory = remember { mutableStateListOf<EditSettings>() }
    val redoHistory = remember { mutableStateListOf<EditSettings>() }
    var brightness by remember { mutableFloatStateOf(recoveryPrefs.getFloat("brightness", 0f)) }
    var contrast by remember { mutableFloatStateOf(recoveryPrefs.getFloat("contrast", 1.07f)) }
    var saturation by remember { mutableFloatStateOf(recoveryPrefs.getFloat("saturation", 1.06f)) }
    var warmth by remember { mutableFloatStateOf(recoveryPrefs.getFloat("warmth", 0f)) }
    var activeAdjustment by remember { mutableStateOf("Brightness") }
    var backgroundBlur by remember { mutableFloatStateOf(recoveryPrefs.getFloat("background_blur", 0f)) }
    var backgroundEnhancement by remember { mutableFloatStateOf(recoveryPrefs.getFloat("background_enhancement", 0f)) }
    var subjectBrightness by remember { mutableFloatStateOf(recoveryPrefs.getFloat("subject_brightness", 0f)) }
    var darkerAreaLift by remember { mutableFloatStateOf(recoveryPrefs.getFloat("darker_area_lift", 0f)) }
    var skinSmoothing by remember { mutableFloatStateOf(recoveryPrefs.getFloat("skin_smoothing", 0f)) }
    var makeupIntensity by remember { mutableFloatStateOf(recoveryPrefs.getFloat("makeup_intensity", 0f)) }
    var removalBrushMode by remember { mutableStateOf(recoveryPrefs.getBoolean("removal_brush_mode", false)) }
    var removalBrushSize by remember { mutableFloatStateOf(recoveryPrefs.getFloat("removal_brush_size", 24f)) }
    var automaticRemovalMask by remember { mutableStateOf<Bitmap?>(null) }
    var isSelectingRemovalTarget by remember { mutableStateOf(false) }
    val removalStrokes = remember {
        mutableStateListOf<List<Offset>>().apply {
            val saved = recoveryPrefs.getString("removal_strokes", "").orEmpty()
            saved.split("|").filter { it.isNotBlank() }.forEach { encodedStroke ->
                val points = encodedStroke.split(";").mapNotNull { encodedPoint ->
                    val parts = encodedPoint.split(",")
                    if (parts.size != 2) null else {
                        val x = parts[0].toFloatOrNull()
                        val y = parts[1].toFloatOrNull()
                        if (x == null || y == null) null else Offset(x, y)
                    }
                }
                if (points.isNotEmpty()) add(points)
            }
        }
    }
    var currentRemovalStroke by remember {
        mutableStateOf(
            recoveryPrefs.getString("current_removal_stroke", "").orEmpty()
                .split(";").mapNotNull { encodedPoint ->
                    val parts = encodedPoint.split(",")
                    if (parts.size != 2) null else {
                        val x = parts[0].toFloatOrNull()
                        val y = parts[1].toFloatOrNull()
                        if (x == null || y == null) null else Offset(x, y)
                    }
                }
        )
    }
    var cropLabel by remember { mutableStateOf(recoveryPrefs.getString("crop_label", "Original") ?: "Original") }
    var cropOffsetX by remember { mutableFloatStateOf(recoveryPrefs.getFloat("crop_offset_x", 0f)) }
    var cropOffsetY by remember { mutableFloatStateOf(recoveryPrefs.getFloat("crop_offset_y", 0f)) }
    var freeCropRatio by remember { mutableFloatStateOf(recoveryPrefs.getFloat("free_crop_ratio", 1f)) }
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
    var showPerspectiveControls by remember { mutableStateOf(recoveryPrefs.getBoolean("show_perspective_controls", false)) }
    var selectedStyle by remember { mutableStateOf(recoveryPrefs.getString("selected_style", "Auto") ?: "Auto") }
    var activeEditorPanel by remember { mutableStateOf(recoveryPrefs.getString("active_editor_panel", "Universal Edit") ?: "Universal Edit") }
    var activeAiSubpanel by remember { mutableStateOf(recoveryPrefs.getString("active_ai_subpanel", "Subject") ?: "Subject") }
    var activeCropSubpanel by remember { mutableStateOf("Crop") }
    var activeAiTool by remember { mutableStateOf("Retouch") }
    var aiEditLook by remember { mutableStateOf("Balanced") }
    var showSingleAiLookOptions by remember { mutableStateOf(false) }
    var showResetLearning by remember { mutableStateOf(false) }
    var showDeleteLearningConfirmation by remember { mutableStateOf(false) }
    var showBatchAiOptions by remember { mutableStateOf(false) }
    var batchEditedSettingsByUri by remember { mutableStateOf<Map<Uri, EditSettings>>(emptyMap()) }
    var batchManualOverrides by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var editorOpeningSettings by remember { mutableStateOf<EditSettings?>(null) }
    var activePerspectiveCorner by remember { mutableStateOf("Top-left") }
    var editedScore by remember { mutableStateOf<PhotoQualityScoring.Score?>(null) }
    var originalPreviewScore by remember { mutableStateOf<PhotoQualityScoring.Score?>(null) }
    var isShowingOriginal by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Choose photos to begin. Analysis and edits run on this device.") }
    var photoAnalysisResult by remember { mutableStateOf<PhotoAnalysisResult?>(null) }
    var isRunningPhotoAnalysis by remember { mutableStateOf(false) }

    var captionSuggestions by remember {
        mutableStateOf(
            recoveryPrefs.getString("caption_english", "").orEmpty().takeIf { it.isNotBlank() }?.let { savedEnglish ->
                CaptionSuggestionEngine.Suggestions(
                    detectedLabels = emptyList(),
                    english = listOf(savedEnglish),
                    bengali = listOf(recoveryPrefs.getString("caption_bengali", "").orEmpty())
                )
            }
        )
    }
    var captionEnglish by remember {
        mutableStateOf(recoveryPrefs.getString("caption_english", "").orEmpty().ifBlank { "A moment worth keeping." })
    }
    var captionBengali by remember {
        mutableStateOf(recoveryPrefs.getString("caption_bengali", "").orEmpty().ifBlank { "মনে রাখার মতো একটি মুহূর্ত।" })
    }
    var captionIndex by remember { mutableIntStateOf(0) }
    var captionGenerationRound by remember { mutableIntStateOf(0) }
    var captionPhotoUri by remember { mutableStateOf<String?>(recoveryPrefs.getString("caption_photo_uri", null)) }
    var captionSourceSignature by remember { mutableStateOf("") }
    var isGeneratingCaptions by remember { mutableStateOf(false) }
    var showBatchCaptionChoice by remember { mutableStateOf(false) }
    var showIndividualBatchCaptionPicker by remember { mutableStateOf(false) }
    var batchCaptionResults by remember { mutableStateOf<Map<Uri, List<Pair<String, String>>>>(emptyMap()) }
    var expandedBatchCaptionOptions by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var batchCaptionTheme by remember { mutableStateOf("Auto") }
    var batchCaptionRound by remember { mutableIntStateOf(0) }
    var isGeneratingBatchCaptions by remember { mutableStateOf(false) }
    var captionShareLanguage by remember { mutableStateOf("Both") }
    var originalHoldPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var captionTheme by remember { mutableStateOf(recoveryPrefs.getString("caption_theme", "Auto") ?: "Auto") }
    var captionAspectRatio by remember { mutableFloatStateOf(recoveryPrefs.getFloat("caption_aspect_ratio", 1f)) }
    var captionSearchQuery by remember { mutableStateOf(recoveryPrefs.getString("caption_search_query", "").orEmpty()) }
    var captionLibraryCategory by remember { mutableStateOf(recoveryPrefs.getString("caption_library_category", "All") ?: "All") }

    fun applyCaptionSuggestions(labels: List<Pair<String, Float>>, ratio: Float) {
        captionAspectRatio = ratio
        val generated = CaptionSuggestionEngine.fromLabels(labels, ratio, captionTheme)
        val libraryMatches = CaptionLibrary.recommend(generated.detectedLabels, captionTheme, limit = 5)
        val detected = generated.detectedLabels.joinToString(" ").lowercase()
        val remoteMatches = remoteCaptionEntries.filter { entry ->
            entry.category.equals(captionTheme, ignoreCase = true) ||
                (captionTheme == "Auto" && entry.category.equals("Aesthetic", ignoreCase = true)) ||
                entry.keywords.any { keyword ->
                    keyword.length > 2 && detected.isNotBlank() && detected.contains(keyword, ignoreCase = true)
                }
        }.take(5)
        val captionPairs = (generated.english.zip(generated.bengali) +
            libraryMatches.map { it.text to it.bengali } +
            remoteMatches.mapIndexed { index, entry ->
                entry.text to (libraryMatches.getOrNull(index)?.bengali
                    ?: generated.bengali.getOrNull(index % generated.bengali.size)
                    ?: "মনে রাখার মতো একটি মুহূর্ত।")
            }).distinctBy { it.first }
        val safePairs = (captionPairs + listOf(
            "A moment worth keeping." to "মনে রাখার মতো একটি মুহূর্ত।",
            "Little moments, lasting memories." to "ছোট ছোট মুহূর্ত, চিরদিনের স্মৃতি।",
            "Just living this moment." to "এই মুহূর্তটুকু উপভোগ করছি।"
        )).distinctBy { it.first }.filter { it.first.isNotBlank() }
        // Rotate the suggestion order on each regeneration so the selected result changes,
        // while preserving each English/Bengali pair.
        val rotation = if (safePairs.isEmpty()) 0 else captionGenerationRound.mod(safePairs.size)
        val rotatedPairs = if (safePairs.isEmpty()) safePairs else safePairs.drop(rotation) + safePairs.take(rotation)
        val english = rotatedPairs.map { it.first }
        val bengali = rotatedPairs.map { it.second }
        val suggestions = CaptionSuggestionEngine.Suggestions(
            detectedLabels = generated.detectedLabels,
            english = english,
            bengali = bengali
        )
        captionSuggestions = suggestions
        captionIndex = 0
        captionEnglish = suggestions.english.firstOrNull() ?: "A moment worth keeping."
        captionBengali = suggestions.bengali.firstOrNull() ?: "মনে রাখার মতো একটি মুহূর্ত।"
        isGeneratingCaptions = false
    }

    fun captionEditSignature(): String = listOf(
        brightness, contrast, saturation, warmth, cropLabel, cropOffsetX, cropOffsetY,
        rotationDegrees, straightenDegrees, mirrorHorizontal, mirrorVertical,
        perspectiveTopLeftX, perspectiveTopLeftY, perspectiveTopRightX, perspectiveTopRightY,
        perspectiveBottomRightX, perspectiveBottomRightY, perspectiveBottomLeftX, perspectiveBottomLeftY,
        backgroundBlur, backgroundEnhancement, subjectBrightness, darkerAreaLift, skinSmoothing, makeupIntensity
    ).joinToString("|")

    suspend fun createBatchCaption(photo: PhotoItem, theme: String, round: Int): List<Pair<String, String>> {
        var bitmap: Bitmap? = null
        try {
            bitmap = withContext(Dispatchers.IO) {
                AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 640)
            }
            val workingBitmap = bitmap ?: return listOf("A moment worth keeping." to "মনে রাখার মতো একটি মুহূর্ত।")
            val ratio = workingBitmap.width.toFloat() / workingBitmap.height.coerceAtLeast(1).toFloat()
            val analysis = PhotoAnalysisEngine.analyze(workingBitmap)
            val labels = analysis.labels.map { label -> label.substringBefore(" (") to 0.9f }
            val ideas = CaptionSuggestionEngine.fromLabels(labels, ratio, theme)
            val count = minOf(ideas.english.size, ideas.bengali.size)
            if (count <= 0) return listOf("A moment worth keeping." to "মনে রাখার মতো একটি মুহূর্ত।")
            return (0 until minOf(3, count)).map { offset ->
                val index = (round + offset).mod(count)
                ideas.english[index] to ideas.bengali[index]
            }.distinctBy { it.first }
        } finally {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    fun generateBatchCaptions(photos: List<PhotoItem>, theme: String = batchCaptionTheme, round: Int = batchCaptionRound) {
        val eligible = photos.filter { it.decision != "Reject" }
        if (eligible.isEmpty() || isGeneratingBatchCaptions) return
        isGeneratingBatchCaptions = true
        statusMessage = "Preparing photo-specific captions…"
        scope.launch {
            try {
                for ((index, photo) in eligible.withIndex()) {
                    try {
                        val options = createBatchCaption(photo, theme, round + index)
                        batchCaptionResults = batchCaptionResults + (photo.uri to options)
                    } catch (_: Exception) {
                        batchCaptionResults = batchCaptionResults + (photo.uri to
                            listOf("A moment worth keeping." to "মনে রাখার মতো একটি মুহূর্ত।"))
                    }
                }
                statusMessage = "Photo-specific $theme captions are ready. Generate more to see alternatives."
            } finally {
                isGeneratingBatchCaptions = false
            }
        }
    }

    fun suggestCaptions(bitmapOverride: Bitmap? = null) {
        captionGenerationRound += 1
        val bitmap = bitmapOverride ?: previewBitmap
        if (bitmap == null || bitmap.isRecycled) {
            statusMessage = "Prepare a photo preview before suggesting captions."
            return
        }
        captionPhotoUri = activePhoto?.uri?.toString()
        captionSourceSignature = captionEditSignature()
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
        // Put real text in both fields immediately; ML Kit replaces these starters when analysis finishes.
        val fallback = CaptionSuggestionEngine.fromLabels(emptyList(), ratio, captionTheme)
        captionSuggestions = fallback
        captionIndex = 0
        captionEnglish = fallback.english.firstOrNull() ?: "A moment worth keeping."
        captionBengali = fallback.bengali.firstOrNull() ?: "মনে রাখার মতো একটি মুহূর্ত।"
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

    fun suggestMoreCaptions() {
        val existing = captionSuggestions
        val count = minOf(existing?.english?.size ?: 0, existing?.bengali?.size ?: 0)
        if (existing == null || count < 2) {
            suggestCaptions()
            return
        }
        val nextIndex = (captionIndex + 1).mod(count)
        captionIndex = nextIndex
        captionEnglish = existing.english[nextIndex]
        captionBengali = existing.bengali[nextIndex]
        statusMessage = "Showing caption suggestion " + (nextIndex + 1) + " of " + count +
            ". Tap Suggest more again to cycle through alternatives."
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

    // Durable session checkpoint: every editable value and caption is written whenever it changes.
    // This intentionally stores settings/text/URI references only; original image bytes are never overwritten.
    LaunchedEffect(
        selectedPhotos.map { it.uri.toString() }, activePhoto?.uri?.toString(),
        brightness, contrast, saturation, warmth, backgroundBlur, backgroundEnhancement,
        subjectBrightness, darkerAreaLift, skinSmoothing, makeupIntensity,
        cropLabel, cropOffsetX, cropOffsetY, freeCropRatio, rotationDegrees, straightenDegrees,
        mirrorHorizontal, mirrorVertical, selectedStyle, showEnhanced, showCropGuides,
        showPerspectiveControls, removalBrushMode, removalBrushSize,
        perspectiveTopLeftX, perspectiveTopLeftY, perspectiveTopRightX, perspectiveTopRightY,
        perspectiveBottomRightX, perspectiveBottomRightY, perspectiveBottomLeftX, perspectiveBottomLeftY,
        removalStrokes.toList(), currentRemovalStroke,
        activeEditorPanel, activeAiSubpanel, captionEnglish, captionBengali, captionTheme,
        captionAspectRatio, captionSearchQuery, captionLibraryCategory
    ) {
        recoveryPrefs.edit()
            .putString("selected_photo_uris", selectedPhotos.joinToString("\\n") { it.uri.toString() })
            .putString("active_photo_uri", activePhoto?.uri?.toString())
            .putFloat("brightness", brightness).putFloat("contrast", contrast)
            .putFloat("saturation", saturation).putFloat("warmth", warmth)
            .putFloat("background_blur", backgroundBlur).putFloat("background_enhancement", backgroundEnhancement)
            .putFloat("subject_brightness", subjectBrightness).putFloat("darker_area_lift", darkerAreaLift)
            .putFloat("skin_smoothing", skinSmoothing).putFloat("makeup_intensity", makeupIntensity)
            .putString("crop_label", cropLabel).putFloat("crop_offset_x", cropOffsetX)
            .putFloat("free_crop_ratio", freeCropRatio)
            .putFloat("crop_offset_y", cropOffsetY).putFloat("rotation_degrees", rotationDegrees)
            .putFloat("straighten_degrees", straightenDegrees).putBoolean("mirror_horizontal", mirrorHorizontal)
            .putBoolean("mirror_vertical", mirrorVertical)
            .putFloat("perspective_tl_x", perspectiveTopLeftX).putFloat("perspective_tl_y", perspectiveTopLeftY)
            .putFloat("perspective_tr_x", perspectiveTopRightX).putFloat("perspective_tr_y", perspectiveTopRightY)
            .putFloat("perspective_br_x", perspectiveBottomRightX).putFloat("perspective_br_y", perspectiveBottomRightY)
            .putFloat("perspective_bl_x", perspectiveBottomLeftX).putFloat("perspective_bl_y", perspectiveBottomLeftY)
            .putBoolean("show_enhanced", showEnhanced)
            .putBoolean("show_perspective_controls", showPerspectiveControls)
            .putBoolean("removal_brush_mode", removalBrushMode).putFloat("removal_brush_size", removalBrushSize)
            .putString("removal_strokes", (removalStrokes.toList() + listOf(currentRemovalStroke).filter { it.isNotEmpty() })
                .joinToString("|") { stroke -> stroke.joinToString(";") { point -> "${point.x},${point.y}" } })
            .putString("current_removal_stroke", "")
            .putString("selected_style", selectedStyle)
            .putString("active_editor_panel", activeEditorPanel).putString("active_ai_subpanel", activeAiSubpanel)
            .putString("caption_english", captionEnglish).putString("caption_bengali", captionBengali)
            .putString("caption_theme", captionTheme).putFloat("caption_aspect_ratio", captionAspectRatio)
            .putString("caption_photo_uri", captionPhotoUri)
            .putString("caption_search_query", captionSearchQuery)
            .putString("caption_library_category", captionLibraryCategory)
            .putLong("last_session_checkpoint_ms", System.currentTimeMillis())
            .apply()

        // apply() updates memory immediately; this debounced commit flushes the full
        // checkpoint to disk off the UI thread after the user pauses dragging/editing.
        delay(350)
        withContext(Dispatchers.IO) {
            recoveryPrefs.edit()
                .putLong("last_durable_checkpoint_ms", System.currentTimeMillis())
                .commit()
        }
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
            "Free" -> freeCropRatio.coerceIn(0.25f, 4f)
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
            // Add to the existing selection rather than replacing it.
            val newlyAddedUris = uris.distinct().filter { uri ->
                selectedPhotos.none { it.uri == uri }
            }
            newlyAddedUris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // The picker can still grant temporary access for the current session.
                }
                selectedPhotos.add(PhotoItem(uri = uri))
            }
            if (newlyAddedUris.isNotEmpty()) {
                isAnalyzing = true
                analysisProgress = 0
                statusMessage = "Adding and analysing " + newlyAddedUris.size + " new photo(s)…"
            } else {
                statusMessage = "Those photos are already selected."
            }
            scope.launch {
                val urisToAnalyze = newlyAddedUris
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
        batchEditedSettingsByUri[photo.uri]?.let { applyEditSnapshot(it) }
        editorOpeningSettings = currentSettings()
        showSingleAiLookOptions = false
        showResetLearning = false
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

    fun applyAiLook(settings: EditSettings, look: String): EditSettings = when (look) {
        "Saturated" -> settings.copy(
            saturation = (settings.saturation * aiEditRules.saturatedSaturationMultiplier).coerceIn(0.85f, 1.55f),
            contrast = (settings.contrast + aiEditRules.saturatedContrastAdd).coerceIn(0.85f, 1.25f)
        )
        "Cool" -> settings.copy(
            warmth = (settings.warmth + aiEditRules.coolWarmthDelta).coerceIn(-20f, 20f),
            saturation = (settings.saturation * aiEditRules.coolSaturationMultiplier).coerceIn(0.85f, 1.45f)
        )
        else -> settings.copy(
            saturation = (settings.saturation * aiEditRules.balancedSaturationMultiplier).coerceIn(0.85f, 1.45f),
            contrast = (settings.contrast + aiEditRules.balancedContrastAdd).coerceIn(0.85f, 1.25f)
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
    fun previewBatchPreset(preset: String, aiLook: String = batchAiLook) {
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
                        val settings = if (photo.uri in batchManualOverrides) {
                            batchEditedSettingsByUri[photo.uri] ?: EditSettings(contrast = 1f, saturation = 1f)
                        } else if (preset == "AI Edit") {
                            applyAiLook(aiSettingsFor(PhotoAnalysisEngine.analyze(original!!)), aiLook)
                        } else if (preset == "Current edits") {
                            batchEditedSettingsByUri[photo.uri] ?: EditSettings(contrast = 1f, saturation = 1f)
                        } else if (preset == "Auto Level") {
                            EditSettings(straightenDegrees = AutoLeveler.estimateCorrectionDegrees(original!!) ?: 0f)
                        } else {
                            BatchExportPolicy.settingsForPreset(preset)
                        }
                        if (photo.uri !in batchManualOverrides) {
                            batchEditedSettingsByUri = batchEditedSettingsByUri + (photo.uri to settings)
                        }
                        val useSubjectAware = (preset == "AI Edit" && photo.uri !in batchManualOverrides) ||
                            preset == "AI Portrait" ||
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
        // Preserve scene-adaptive analysis; apply only the selected overall color preference.
        val lookSettings = applyAiLook(
            EditSettings(contrast = contrast, saturation = saturation, warmth = warmth),
            aiEditLook
        )
        contrast = lookSettings.contrast
        saturation = lookSettings.saturation
        warmth = lookSettings.warmth
        selectedStyle = sceneStyle
        showEnhanced = true
        compareBeforeAfter = false
        photoAnalysisResult = result
        statusMessage = "AI Edit applied using scene and lighting analysis."
    }
    fun applyMyStyle() {
        activeAiTool = "My style"
        if (learnedProfile.acceptedEdits > 0) {
            recordEditChange()
            applyEditSnapshot(learnedProfile.toSettings())
            selectedStyle = "Personal"
            showEnhanced = true
            compareBeforeAfter = false
            statusMessage = "Applied your personal edit suggestion based on ${learnedProfile.preferredEditType}. Review it before exporting."
        } else {
            recordEditChange()
            applyEditSnapshot(currentSettings().copy(contrast = 1.07f, saturation = 1.06f))
            selectedStyle = "Personal"
            showEnhanced = true
            compareBeforeAfter = false
            statusMessage = "Applied a natural starting style. Export an edit you like to teach My Style your preferences."
        }
    }

    // Run AI scene analysis only after the user chooses a look and taps Edit.
    fun runAiEdit() {
        val photo = activePhoto ?: return
        if (isRunningPhotoAnalysis || isExporting) return
        isRunningPhotoAnalysis = true
        photoAnalysisResult = null
        statusMessage = "Inspecting subjects, scene labels, and lighting on this device…"
        scope.launch {
            var analysisBitmap: Bitmap? = null
            try {
                analysisBitmap = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 1400)
                }
                val result = PhotoAnalysisEngine.analyze(analysisBitmap!!)
                photoAnalysisResult = result
                applyAiSuggestedEdit(result)
                applyEditSnapshot(applyAiLook(currentSettings(), aiEditLook))
                selectedStyle = "Custom"
                showEnhanced = true
                statusMessage = "$aiEditLook AI edit applied. Review it before exporting."
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
            val editedPhoto = activePhoto!!
            val editedSettings = currentSettings()
            batchEditedSettingsByUri = batchEditedSettingsByUri + (editedPhoto.uri to editedSettings)
            if (editorOpeningSettings != editedSettings) {
                batchManualOverrides = batchManualOverrides + editedPhoto.uri
            }
            editorOpeningSettings = null
            activePhoto = null
            resetPreviewZoom()
            previewBatchPreset("Current edits")
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
                // Keep the interactive preview deliberately smaller than export output to reduce peak RAM.
                original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 1440)
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
                if (captionPhotoUri != photo.uri.toString() || captionEnglish.isBlank() || captionBengali.isBlank()) {
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
            } catch (error: OutOfMemoryError) {
                statusMessage = "Preview needed more memory than Android could provide. Your last checkpointed edit settings are preserved; reopen the photo or try a smaller image."
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
                        maxDimension = if (useAiUpscale) 1536 else Int.MAX_VALUE
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
                operationStage = "Saving edited copy to Pictures/PixelCraft"
                val width = enhanced!!.width
                val height = enhanced!!.height
                val savedUri = withContext(Dispatchers.IO) {
                    AutoEnhancer.saveJpeg(context.contentResolver, enhanced!!)
                }
                lastExportedUri = savedUri
                val learnedEditType = when {
                    selectedStyle == "Personal" -> learnedProfile.preferredEditType
                    selectedStyle == "Custom" -> "AI Edit · $aiEditLook"
                    else -> selectedStyle
                }
                learnedProfile = PersonalEditLearning.recordAccepted(context, settings, learnedEditType)
                operationProgress = 100
                operationStage = "Finished · original photo preserved"
                statusMessage = (aiNotice?.let { "$it " } ?: if (useAiPortrait) {
                    "AI Portrait enhancement applied. "
                } else "") + (if (useAiUpscale) "Saved AI-upscaled " else "Saved full-resolution ") + width + "x" + height +
                    " copy to Pictures/PixelCraft. Original untouched."
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
        if (strokes.none { it.isNotEmpty() } && automaticRemovalMask == null) {
            statusMessage = "Select a person or paint over the unwanted object in the preview."
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
                original = if (selectedStyle == "Object Removal" && previewBitmap != null && !previewBitmap!!.isRecycled) {
                    Bitmap.createBitmap(previewBitmap!!)
                } else {
                    withContext(Dispatchers.IO) {
                        AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = 2400)
                    }
                }
                operationProgress = 20
                operationStage = "Reconstructing the marked area"
                val result = LamaInpainter.inpaint(context, original!!, strokes, removalBrushSize, automaticRemovalMask)
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
                // Keep the mask so the user can run another repair pass on the same area.
                // The next pass uses the repaired preview as its source instead of the original.
                statusMessage = "Object updated. You can refine the same area again without repainting."
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
                }
                Spacer(modifier = Modifier.weight(1f))
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1.25f).clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF111818)).padding(9.dp)
                                ) {
                                    Text(
                                        if (displayed == null || baseline == null) "TECHNICAL SCORE · CALCULATING…" else if (isShowingOriginal)
                                            "ORIGINAL SCORE  ${displayed.score}/100" else
                                            "EDITED SCORE  ${displayed.score}/100  (Δ ${if (delta!! > 0) "+" else ""}$delta · original ${baseline.score})",
                                        color = when { !isShowingOriginal && delta != null && delta > 0 -> Mint; !isShowingOriginal && delta != null && delta < 0 -> Color(0xFFFF8B8B); else -> BrightText },
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp
                                    )
                                    Text(
                                        if (displayed == null) "Measuring preview…" else
                                            "Sharpness ${displayed.sharpness} · Exposure ${displayed.exposure} · Contrast ${displayed.contrast}",
                                        color = SoftText, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp), lineHeight = 12.sp
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                    EditorImageAction("↶", "Undo", undoHistory.isNotEmpty() && !isExporting) { undoEdit() }
                                    EditorImageAction("↷", "Redo", redoHistory.isNotEmpty() && !isExporting) { redoEdit() }
                                    EditorImageAction("⟲", "Reset", activePhoto != null && !isExporting) {
                                        recordEditChange()
                                        brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
                                        backgroundBlur = 0f; backgroundEnhancement = 0f; subjectBrightness = 0f
                                        darkerAreaLift = 0f; skinSmoothing = 0f; makeupIntensity = 0f
                                        cropLabel = "Original"; cropOffsetX = 0f; cropOffsetY = 0f
                                        rotationDegrees = 0f; straightenDegrees = 0f
                                        mirrorHorizontal = false; mirrorVertical = false
                                        perspectiveTopLeftX = 0f; perspectiveTopLeftY = 0f
                                        perspectiveTopRightX = 0f; perspectiveTopRightY = 0f
                                        perspectiveBottomRightX = 0f; perspectiveBottomRightY = 0f
                                        perspectiveBottomLeftX = 0f; perspectiveBottomLeftY = 0f
                                        showPerspectiveControls = false; removalBrushMode = false
                                        removalStrokes.clear(); currentRemovalStroke = emptyList()
                                        selectedStyle = "Auto"; showEnhanced = true; compareBeforeAfter = false
                                        statusMessage = "All edits reset. Original photo remains unchanged."
                                    }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
EditorImageAction("⇩", "Export", previewBitmap != null && !isExporting && selectedStyle != "Original") {
                                        val photo = activePhoto
                                        if (photo != null) exportOne(photo, currentSettings(),
                                            selectedStyle == "AI Portrait" || backgroundBlur > 0f ||
                                                backgroundEnhancement > 0f || subjectBrightness > 0f ||
                                                darkerAreaLift > 0f || skinSmoothing > 0f || makeupIntensity > 0f)
                                    }
                                    EditorImageAction("↗", "Share", lastExportedUri != null && !isExporting) {
                                        val uri = lastExportedUri
                                        if (uri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/jpeg"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                val sharedCaption = when (captionShareLanguage) {
                                                    "English" -> captionEnglish.trim()
                                                    "বাংলা" -> captionBengali.trim()
                                                    else -> listOf(captionEnglish.trim(), captionBengali.trim())
                                                        .filter { it.isNotBlank() }.joinToString("\n\n")
                                                }
                                                if (sharedCaption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, sharedCaption)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share edited photo"))
                                        } else {
                                            statusMessage = "Export an edited copy first, then tap Share."
                                        }
                                    }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
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
                            if (activePhoto != null && selectedPhotos.size > 1) {
                                val photoIndex = selectedPhotos.indexOfFirst { it.uri == activePhoto?.uri }
                                Box(
                                    modifier = Modifier.align(Alignment.CenterStart)
                                        .padding(start = 6.dp).size(width = 38.dp, height = 52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ink.copy(alpha = 0.72f))
                                        .clickable(enabled = photoIndex > 0 && !isExporting) {
                                            if (photoIndex > 0) openEditor(selectedPhotos[photoIndex - 1])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("‹", color = if (photoIndex > 0) BrightText else SoftText.copy(alpha = 0.35f),
                                        fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light)
                                }
                                Box(
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .padding(end = 6.dp).size(width = 38.dp, height = 52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ink.copy(alpha = 0.72f))
                                        .clickable(enabled = photoIndex in 0 until selectedPhotos.lastIndex && !isExporting) {
                                            if (photoIndex in 0 until selectedPhotos.lastIndex) openEditor(selectedPhotos[photoIndex + 1])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("›", color = if (photoIndex in 0 until selectedPhotos.lastIndex) BrightText else SoftText.copy(alpha = 0.35f),
                                        fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light)
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
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Universal Edit", "AI Edit", "Presets", "Crop & Rotate", "Caption").forEach { panel ->
                                    Button(
                                        onClick = {
                                            activeEditorPanel = panel
                                            compareBeforeAfter = false
                                            if (panel == "Caption") {
                                                val photo = activePhoto
                                                val bitmap = previewBitmap
                                                if (photo != null && bitmap != null && !bitmap.isRecycled &&
                                                    (captionPhotoUri != photo.uri.toString() || captionSuggestions?.english.isNullOrEmpty() || captionSourceSignature != captionEditSignature())) {
                                                    captionPhotoUri = photo.uri.toString()
                                                    suggestCaptions(bitmap)
                                                }
                                            }
                                        },
                                        modifier = Modifier.width(96.dp).heightIn(min = 44.dp),
                                        shape = RoundedCornerShape(11.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeEditorPanel == panel) Mint else Color(0xFF293333),
                                            contentColor = if (activeEditorPanel == panel) Ink else BrightText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 7.dp)
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
            if (activeEditorPanel == "Caption") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("CAPTION IDEAS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val visibleCaptionIdeas = captionSuggestions?.english?.take(3).orEmpty()
                    if (visibleCaptionIdeas.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            visibleCaptionIdeas.forEachIndexed { index, idea ->
                                Button(
                                    onClick = {
                                        captionIndex = index
                                        captionEnglish = idea
                                        captionBengali = captionSuggestions?.bengali?.getOrNull(index).orEmpty()
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (captionEnglish == idea) Color(0xFF263B38) else Color(0xFF202626),
                                        contentColor = BrightText
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(idea, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3,
                                        modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    } else {
                        Text("A moment worth keeping.", color = BrightText, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("English", "বাংলা", "Both").forEach { language ->
                            Button(
                                onClick = { captionShareLanguage = language },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (captionShareLanguage == language) Mint else Color(0xFF293333),
                                    contentColor = if (captionShareLanguage == language) Ink else BrightText
                                )
                            ) { Text(language, fontSize = 11.sp) }
                        }
                    }
                    if (captionShareLanguage == "English" || captionShareLanguage == "Both") {
                    OutlinedTextField(
                        value = captionEnglish, onValueChange = { captionEnglish = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 88.dp),
                        label = { Text("English caption", color = SoftText) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = BrightText, fontSize = 14.sp),
                        minLines = 1, maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BrightText, unfocusedTextColor = BrightText,
                            focusedBorderColor = Mint, unfocusedBorderColor = SoftText,
                            focusedLabelColor = Mint, unfocusedLabelColor = SoftText, cursorColor = Mint
                        )
                    )
                    }
                    if (captionShareLanguage == "বাংলা" || captionShareLanguage == "Both") {
                    OutlinedTextField(
                        value = captionBengali, onValueChange = { captionBengali = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 88.dp),
                        label = { Text("বাংলা ক্যাপশন", color = SoftText) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = BrightText, fontSize = 14.sp),
                        minLines = 1, maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BrightText, unfocusedTextColor = BrightText,
                            focusedBorderColor = Mint, unfocusedBorderColor = SoftText,
                            focusedLabelColor = Mint, unfocusedLabelColor = SoftText, cursorColor = Mint
                        )
                    )
                    }
                    Button(onClick = { suggestMoreCaptions() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                        enabled = previewBitmap != null && !isGeneratingCaptions,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text(if (isGeneratingCaptions) "Suggesting…" else "Suggest more", fontSize = 11.sp) }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Auto", "Portrait", "Friends", "Festival", "Celebration", "Romantic", "Funny", "Devotional", "Travel", "Family", "Aesthetic").forEach { theme ->
                            Button(onClick = {
                                captionTheme = theme
                                val existing = captionSuggestions
                                if (existing != null) applyCaptionSuggestions(existing.detectedLabels.map { it to 0.9f }, captionAspectRatio)
                                else suggestCaptions()
                            }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (captionTheme == theme) Mint else Color(0xFF293333),
                                    contentColor = if (captionTheme == theme) Ink else BrightText
                                )) { Text(theme, fontSize = 10.sp, maxLines = 1) }
                        }
                    }
                }
            }
                                                        if (activeEditorPanel == "AI Edit") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Subject", "Background", "Remove", "Enhance").forEach { group ->
                                        Button(
                                            onClick = { activeAiSubpanel = group; activeAiTool = when (group) { "Subject" -> "Retouch"; "Background" -> "Blur"; "Remove" -> "Mark object"; else -> "Upscale" }; compareBeforeAfter = false },
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
                                if (activeAiSubpanel == "Enhance") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                activePhoto?.let { photo ->
                                                    if (!isExporting) exportOne(photo, currentSettings(), useAiUpscale = true)
                                                }
                                            },
                                            enabled = activePhoto != null && !isExporting,
                                            modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                        ) { Text(if (isExporting && activePhoto != null) "Upscaling…" else "AI Upscale", fontSize = 11.sp) }
                                        Button(
                                            onClick = {
                                                if (showResetLearning) showDeleteLearningConfirmation = true
                                                else applyMyStyle()
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 38.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (showResetLearning) {
                                                            showDeleteLearningConfirmation = true
                                                        } else {
                                                            applyMyStyle()
                                                        }
                                                    },
                                                    onLongClick = {
                                                        activeAiTool = "My style"
                                                        showResetLearning = true
                                                    }
                                                ),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                        ) { Text(if (showResetLearning) "Delete learning" else "My style", fontSize = 11.sp, maxLines = 1) }
                                    }
                                }
                                if (isExporting && activePhoto != null && activeAiSubpanel == "Enhance") {
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
                                        onClick = {
                                            removalStrokes.clear()
                                            currentRemovalStroke = emptyList()
                                            automaticRemovalMask?.let { if (!it.isRecycled) it.recycle() }
                                            automaticRemovalMask = null
                                            statusMessage = "Object selection cleared."
                                        },
                                        enabled = (removalStrokes.isNotEmpty() || automaticRemovalMask != null) && !isExporting,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                    ) { Text("Clear mask") }
                                }
                                Button(
                                    onClick = {
                                        val bitmap = previewBitmap
                                        if (bitmap == null || bitmap.isRecycled || isSelectingRemovalTarget || isExporting) {
                                            statusMessage = "Open a photo preview first."
                                        } else {
                                            isSelectingRemovalTarget = true
                                            statusMessage = "Finding the person in this photo…"
                                            scope.launch {
                                                try {
                                                    val segmentation = MulticlassSegmentationEngine.segment(context, bitmap)
                                                    val maskPixels = IntArray(bitmap.width * bitmap.height)
                                                    for (y in 0 until bitmap.height) {
                                                        val sy = y * segmentation.height / bitmap.height
                                                        for (x in 0 until bitmap.width) {
                                                            val sx = x * segmentation.width / bitmap.width
                                                            val category = segmentation.classAt(sx, sy)
                                                            maskPixels[y * bitmap.width + x] =
                                                                if (category in MulticlassSegmentationEngine.HAIR..MulticlassSegmentationEngine.CLOTHES) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT
                                                        }
                                                    }
                                                    val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                                                    mask.setPixels(maskPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                                                    automaticRemovalMask?.let { if (!it.isRecycled) it.recycle() }
                                                    automaticRemovalMask = mask
                                                    statusMessage = "Person selected. Tap Remove marked area to remove it, or paint over another object."
                                                } catch (error: Exception) {
                                                    statusMessage = "Could not select a person in this photo. You can still mark the object manually."
                                                } finally {
                                                    isSelectingRemovalTarget = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = activePhoto != null && !isExporting && !isSelectingRemovalTarget,
                                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                ) { Text(if (isSelectingRemovalTarget) "Selecting…" else "Select person", fontSize = 11.sp) }
                                if (removalBrushMode || removalStrokes.isNotEmpty() || automaticRemovalMask != null) {
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
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Blur", "Enhance").forEach { tool ->
                                            Button(onClick = { activeAiTool = tool }, modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (activeAiTool == tool) Mint else Color(0xFF293333), contentColor = if (activeAiTool == tool) Ink else BrightText),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) { Text(tool, fontSize = 10.sp) }
                                        }
                                    }
                                    if (activeAiTool == "Enhance") {
                                        Text("Background enhancement  ${backgroundEnhancement.toInt()}", color = SoftText, fontSize = 11.sp)
                                        Slider(value = backgroundEnhancement, onValueChange = { recordEditChange(); backgroundEnhancement = it; selectedStyle = if (it > 0f || backgroundBlur > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..10f)
                                    } else {
                                        Text("Background blur  ${backgroundBlur.toInt()}", color = SoftText, fontSize = 11.sp)
                                        Slider(value = backgroundBlur, onValueChange = { recordEditChange(); backgroundBlur = it; selectedStyle = if (it > 0f || subjectBrightness > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..20f)
                                    }
                                }
                                if (activeAiSubpanel == "Subject") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf("Retouch", "Skin lightening", "Skin softening", "Natural makeup").forEach { tool ->
                                            Button(onClick = { activeAiTool = tool }, modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (activeAiTool == tool) Mint else Color(0xFF293333), contentColor = if (activeAiTool == tool) Ink else BrightText),
                                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)) {
                                                Text(tool, fontSize = 8.sp, maxLines = 2, lineHeight = 9.sp)
                                            }
                                        }
                                    }
                                    when (activeAiTool) {
                                        "Retouch" -> {
                                            Text("Retouch · adaptive skin lift  ${darkerAreaLift.toInt()}", color = SoftText, fontSize = 11.sp)
                                            Slider(value = darkerAreaLift, onValueChange = { recordEditChange(); darkerAreaLift = it; selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..30f)
                                        }
                                        "Skin lightening" -> {
                                            Text("Skin lightening  ${subjectBrightness.toInt()}", color = SoftText, fontSize = 11.sp)
                                            Slider(value = subjectBrightness, onValueChange = { recordEditChange(); subjectBrightness = it; selectedStyle = if (it > 0f || backgroundBlur > 0f || darkerAreaLift > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..25f)
                                        }
                                        "Skin softening" -> {
                                            Text("Skin softening · natural texture  ${skinSmoothing.toInt()}", color = SoftText, fontSize = 11.sp)
                                            Slider(value = skinSmoothing, onValueChange = { recordEditChange(); skinSmoothing = it; selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f || darkerAreaLift > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..10f)
                                        }
                                        "Natural makeup" -> {
                                            Text("Natural makeup  ${makeupIntensity.toInt()}", color = SoftText, fontSize = 11.sp)
                                            Slider(value = makeupIntensity, onValueChange = { recordEditChange(); makeupIntensity = it; selectedStyle = if (it > 0f || backgroundBlur > 0f || subjectBrightness > 0f || darkerAreaLift > 0f || skinSmoothing > 0f) "AI Portrait" else "Custom"; showEnhanced = true }, valueRange = 0f..10f)
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (activeEditorPanel == "Crop & Rotate") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Auto Level", "Crop", "Rotate", "Straighten", "Perspective").forEach { tool ->
                                    Button(onClick = {
                                        activeCropSubpanel = tool
                                        if (tool == "Auto Level") {
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
                                                    statusMessage = if (correction == 0f) "This photo already looks level." else "Level corrected by ${String.format(java.util.Locale.US, "%.1f", correction)}°."
                                                }
                                            }
                                        }
                                    }, modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (activeCropSubpanel == tool) Mint else Color(0xFF293333), contentColor = if (activeCropSubpanel == tool) Ink else BrightText),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
                                        Text(tool, fontSize = 8.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (activeCropSubpanel == "Rotate") {
                            Text("ROTATE & MIRROR", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { recordEditChange(); rotationDegrees = (rotationDegrees - 90f).mod(360f); selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("↶ Rotate", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); rotationDegrees = (rotationDegrees + 90f).mod(360f); selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)) { Text("Rotate ↻", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); mirrorHorizontal = !mirrorHorizontal; selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mirrorHorizontal) Mint else Color(0xFF293333), contentColor = if (mirrorHorizontal) Ink else BrightText)) { Text("Mirror H", fontSize = 10.sp) }
                                Button(onClick = { recordEditChange(); mirrorVertical = !mirrorVertical; selectedStyle = "Custom"; showEnhanced = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mirrorVertical) Mint else Color(0xFF293333), contentColor = if (mirrorVertical) Ink else BrightText)) { Text("Mirror V", fontSize = 10.sp) }
                            }
                            }
                            if (activeCropSubpanel == "Straighten") {
                            Text("STRAIGHTEN  ${String.format(java.util.Locale.US, "%.1f", straightenDegrees)}°", color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
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
                            }
                            if (activeCropSubpanel == "Crop") {
                            Text("CROP RATIO", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Original", "Free", "Square", "Portrait 4:5", "Story 9:16", "Landscape 16:9").forEach { crop ->
                                    Button(onClick = { recordEditChange(); cropLabel = crop; cropOffsetX = 0f; cropOffsetY = 0f; selectedStyle = "Custom"; showEnhanced = true; compareBeforeAfter = false },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (cropLabel == crop) Mint else Color(0xFF293333), contentColor = if (cropLabel == crop) Ink else BrightText),
                                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)) { Text(crop, fontSize = 10.sp) }
                                }
                            }
                            if (cropLabel == "Free") {
                                Text("FREE CROP WIDTH / HEIGHT  ${String.format(java.util.Locale.US, "%.2f", freeCropRatio)}", color = SoftText, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                                Slider(
                                    value = freeCropRatio,
                                    onValueChange = { freeCropRatio = it; selectedStyle = "Custom"; showEnhanced = true },
                                    onValueChangeFinished = { recordEditChange() },
                                    valueRange = 0.25f..4f
                                )
                                Text("Drag the photo to position the crop, then adjust this ratio freely.", color = SoftText, fontSize = 10.sp)
                            }
                            }
                            if (activeCropSubpanel == "Perspective") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Top-left", "Top-right", "Bottom-right", "Bottom-left").forEach { corner ->
                                        Button(onClick = { activePerspectiveCorner = corner; showPerspectiveControls = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp), shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (activePerspectiveCorner == corner) Mint else Color(0xFF293333), contentColor = if (activePerspectiveCorner == corner) Ink else BrightText),
                                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 3.dp)
                                        ) { Text(corner, fontSize = 8.sp, maxLines = 1) }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { showPerspectiveControls = !showPerspectiveControls }, modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                    ) { Text(if (showPerspectiveControls) "Hide controls" else "Show controls", fontSize = 10.sp) }
                                    Button(onClick = {
                                        recordEditChange()
                                        perspectiveTopLeftX = 0f; perspectiveTopLeftY = 0f
                                        perspectiveTopRightX = 0f; perspectiveTopRightY = 0f
                                        perspectiveBottomRightX = 0f; perspectiveBottomRightY = 0f
                                        perspectiveBottomLeftX = 0f; perspectiveBottomLeftY = 0f
                                        selectedStyle = "Custom"; showEnhanced = true
                                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                    ) { Text("Reset corners", fontSize = 10.sp) }
                                }
                                if (showPerspectiveControls) {
                                    val cornerX = when (activePerspectiveCorner) {
                                        "Top-left" -> perspectiveTopLeftX
                                        "Top-right" -> perspectiveTopRightX
                                        "Bottom-right" -> perspectiveBottomRightX
                                        else -> perspectiveBottomLeftX
                                    }
                                    val cornerY = when (activePerspectiveCorner) {
                                        "Top-left" -> perspectiveTopLeftY
                                        "Top-right" -> perspectiveTopRightY
                                        "Bottom-right" -> perspectiveBottomRightY
                                        else -> perspectiveBottomLeftY
                                    }
                                    Text("Horizontal  ${(cornerX * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                    Slider(value = cornerX, onValueChange = { value ->
                                        recordEditChange()
                                        when (activePerspectiveCorner) {
                                            "Top-left" -> perspectiveTopLeftX = value
                                            "Top-right" -> perspectiveTopRightX = value
                                            "Bottom-right" -> perspectiveBottomRightX = value
                                            else -> perspectiveBottomLeftX = value
                                        }
                                        selectedStyle = "Custom"; showEnhanced = true
                                    }, valueRange = 0f..0.35f)
                                    Text("Vertical  ${(cornerY * 100).toInt()}%", color = SoftText, fontSize = 10.sp)
                                    Slider(value = cornerY, onValueChange = { value ->
                                        recordEditChange()
                                        when (activePerspectiveCorner) {
                                            "Top-left" -> perspectiveTopLeftY = value
                                            "Top-right" -> perspectiveTopRightY = value
                                            "Bottom-right" -> perspectiveBottomRightY = value
                                            else -> perspectiveBottomLeftY = value
                                        }
                                        selectedStyle = "Custom"; showEnhanced = true
                                    }, valueRange = 0f..0.35f)
                                }
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
                        Spacer(modifier = Modifier.height(4.dp))
                        if (activeEditorPanel == "AI Edit" && activeAiSubpanel == "Enhance") {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("Balanced", "Saturated", "Cool").forEach { look ->
                                                                        Button(
                                                                            onClick = { aiEditLook = look; runAiEdit() },
                                                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                                                            shape = RoundedCornerShape(9.dp),
                                                                            colors = ButtonDefaults.buttonColors(
                                                                                containerColor = if (aiEditLook == look) Mint else Color(0xFF293333),
                                                                                contentColor = if (aiEditLook == look) Ink else BrightText
                                                                            ),
                                                                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp)
                                                                        ) { Text(look, fontSize = 10.sp, maxLines = 1) }
                                                                    }
                                                                }
                            }
                        }
                        if (activeEditorPanel == "Universal Edit") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Brightness", "Saturation", "Contrast", "Warmth").forEach { adjustment ->
                                    Button(
                                        onClick = { activeAdjustment = adjustment },
                                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeAdjustment == adjustment) Mint else Color(0xFF293333),
                                            contentColor = if (activeAdjustment == adjustment) Ink else BrightText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Text(adjustment, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                            }
                            val adjustmentValue = when (activeAdjustment) {
                                "Brightness" -> brightness
                                "Saturation" -> saturation
                                "Contrast" -> contrast
                                else -> warmth
                            }
                            val adjustmentRange = when (activeAdjustment) {
                                "Brightness" -> -25f..25f
                                "Saturation" -> 0f..1.65f
                                "Contrast" -> 0.75f..1.40f
                                else -> -24f..24f
                            }
                            val adjustmentStep = when (activeAdjustment) {
                                "Brightness", "Warmth" -> 0.5f
                                else -> 0.01f
                            }
                            val adjustmentLabel = when (activeAdjustment) {
                                "Brightness", "Warmth" -> String.format(java.util.Locale.US, "%.1f", adjustmentValue)
                                else -> String.format(java.util.Locale.US, "%.2f", adjustmentValue)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        recordEditChange()
                                        val value = (adjustmentValue - adjustmentStep).coerceIn(adjustmentRange.start, adjustmentRange.endInclusive)
                                        when (activeAdjustment) {
                                            "Brightness" -> brightness = value
                                            "Saturation" -> saturation = value
                                            "Contrast" -> contrast = value
                                            else -> warmth = value
                                        }
                                        selectedStyle = "Custom"
                                        showEnhanced = true
                                    },
                                    enabled = adjustmentValue > adjustmentRange.start && !isExporting,
                                    modifier = Modifier.size(width = 38.dp, height = 36.dp),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                ) { Text("−", fontSize = 20.sp) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$activeAdjustment  ·  $adjustmentLabel", color = SoftText, fontSize = 11.sp)
                                    Slider(
                                        value = adjustmentValue,
                                        onValueChange = { newValue ->
                                            recordEditChange()
                                            when (activeAdjustment) {
                                                "Brightness" -> brightness = newValue
                                                "Saturation" -> saturation = newValue
                                                "Contrast" -> contrast = newValue
                                                else -> warmth = newValue
                                            }
                                            selectedStyle = "Custom"
                                            showEnhanced = true
                                        },
                                        valueRange = adjustmentRange,
                                        enabled = !isExporting
                                    )
                                }
                                Button(
                                    onClick = {
                                        recordEditChange()
                                        val value = (adjustmentValue + adjustmentStep).coerceIn(adjustmentRange.start, adjustmentRange.endInclusive)
                                        when (activeAdjustment) {
                                            "Brightness" -> brightness = value
                                            "Saturation" -> saturation = value
                                            "Contrast" -> contrast = value
                                            else -> warmth = value
                                        }
                                        selectedStyle = "Custom"
                                        showEnhanced = true
                                    },
                                    enabled = adjustmentValue < adjustmentRange.endInclusive && !isExporting,
                                    modifier = Modifier.size(width = 38.dp, height = 36.dp),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText)
                                ) { Text("+", fontSize = 20.sp) }
                            }
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
                                            val photoSettings = if (photo.uri in batchManualOverrides) {
                                                batchEditedSettingsByUri[photo.uri] ?: settingsForBatch
                                            } else if (batchPreset == "AI Edit") {
                                                applyAiLook(aiSettingsFor(PhotoAnalysisEngine.analyze(original!!)), batchAiLook)
                                            } else if (batchPreset == "Auto Level") {
                                                EditSettings(straightenDegrees = AutoLeveler.estimateCorrectionDegrees(original!!) ?: 0f)
                                            } else if (batchPreset == "Current edits") {
                                                batchEditedSettingsByUri[photo.uri] ?: EditSettings(contrast = 1f, saturation = 1f)
                                            } else {
                                                settingsForBatch
                                            }
                                            val useAiPortraitForPhoto = (batchPreset == "AI Edit" && photo.uri !in batchManualOverrides) ||
                                                batchPreset == "AI Portrait" ||
                                                photoSettings.backgroundBlur > 0f || photoSettings.backgroundEnhancement > 0f ||
                                                photoSettings.subjectBrightness > 0f || photoSettings.darkerAreaLift > 0f ||
                                                photoSettings.skinSmoothing > 0f || photoSettings.makeupIntensity > 0f
                                            if (useAiPortraitForPhoto) {
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
                // Keep numerous batch controls in a compact, independently scrollable area.
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                Text("CAPTION CATEGORY", color = BrightText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Auto", "Portrait", "Friends", "Festival", "Celebration", "Romantic", "Funny", "Devotional", "Travel", "Family", "Aesthetic", "Nature", "Food").forEach { theme ->
                        Button(
                            onClick = {
                                batchCaptionTheme = theme
                                if (batchCaptionResults.isNotEmpty()) {
                                    batchCaptionRound += 1
                                    generateBatchCaptions(selectedPhotos.toList(), theme, batchCaptionRound)
                                }
                            },
                            enabled = !isGeneratingBatchCaptions,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (batchCaptionTheme == theme) Mint else Color(0xFF293333),
                                contentColor = if (batchCaptionTheme == theme) Ink else BrightText
                            )
                        ) { Text(theme, fontSize = 10.sp, maxLines = 1) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showBatchCaptionChoice = true },
                        enabled = !isExporting && !isAnalyzing && !isGeneratingBatchCaptions,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = Mint)
                    ) { Text("✎ Caption", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = {
                            batchCaptionRound += 1
                            generateBatchCaptions(selectedPhotos.toList(), batchCaptionTheme, batchCaptionRound)
                        },
                        enabled = selectedPhotos.any { it.decision != "Reject" } && !isGeneratingBatchCaptions,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                    ) { Text(if (isGeneratingBatchCaptions) "Generating…" else "Generate more", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (isGeneratingBatchCaptions) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Mint, strokeWidth = 2.dp)
                        Text("Analysing each photo for its own captions…", color = SoftText, fontSize = 10.sp)
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    batchCaptionResults.forEach { (uri, options) ->
                        val photoNumber = selectedPhotos.indexOfFirst { it.uri == uri } + 1
                        val primary = options.firstOrNull()
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Photo " + photoNumber, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (primary != null) {
                                Button(
                                    onClick = {
                                        captionEnglish = primary.first
                                        captionBengali = primary.second
                                        statusMessage = "Caption selected for Photo " + photoNumber + "."
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(9.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202626), contentColor = BrightText),
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        if (captionShareLanguage == "English" || captionShareLanguage == "Both") {
                                            Text(primary.first, fontSize = 11.sp, lineHeight = 14.sp)
                                        }
                                        if (captionShareLanguage == "বাংলা" || captionShareLanguage == "Both") {
                                            Text(primary.second, color = SoftText, fontSize = 11.sp, lineHeight = 14.sp)
                                        }
                                    }
                                }
                            }
                            if (options.size > 1) {
                                TextButton(onClick = {
                                    expandedBatchCaptionOptions = if (uri in expandedBatchCaptionOptions) {
                                        expandedBatchCaptionOptions - uri
                                    } else {
                                        expandedBatchCaptionOptions + uri
                                    }
                                }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text(
                                        if (uri in expandedBatchCaptionOptions) "Hide alternatives"
                                        else "More options (" + (options.size - 1) + ")",
                                        color = SoftText, fontSize = 10.sp
                                    )
                                }
                                if (uri in expandedBatchCaptionOptions) {
                                    options.drop(1).forEachIndexed { optionIndex, pair ->
                                        Button(
                                            onClick = {
                                                captionEnglish = pair.first
                                                captionBengali = pair.second
                                                statusMessage = "Caption option " + (optionIndex + 2) +
                                                    " selected for Photo " + photoNumber + "."
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202626), contentColor = BrightText),
                                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                if (captionShareLanguage == "English" || captionShareLanguage == "Both") {
                                                    Text(pair.first, fontSize = 11.sp, lineHeight = 14.sp)
                                                }
                                                if (captionShareLanguage == "বাংলা" || captionShareLanguage == "Both") {
                                                    Text(pair.second, color = SoftText, fontSize = 11.sp, lineHeight = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { showBatchAiOptions = !showBatchAiOptions },
                    enabled = !isExporting && !isAnalyzing && selectedPhotos.any { it.decision != "Reject" },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 3.dp).heightIn(min = 42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                ) { Text("✦ AI Edit all", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (showBatchAiOptions) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        listOf("Saturated", "Cool", "Natural").forEach { look ->
                            Button(
                                onClick = { batchAiLook = look },
                                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (batchAiLook == look) Mint else Color(0xFF293333),
                                    contentColor = if (batchAiLook == look) Ink else BrightText
                                ),
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp)
                            ) { Text(look, fontSize = 10.sp, maxLines = 1) }
                        }
                        Button(
                            onClick = {
                                previewBatchPreset("AI Edit", batchAiLook)
                                showBatchAiOptions = false
                            },
                            enabled = !isPreparingBatchPreview && !isExporting,
                            modifier = Modifier.weight(0.8f).heightIn(min = 36.dp),
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9CC8FF), contentColor = Ink),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) { Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                if (showBatchCaptionChoice) {
                    AlertDialog(
                        onDismissRequest = { showBatchCaptionChoice = false },
                        title = { Text("Generate captions") },
                        text = { Text("Choose one photo or generate English and Bengali captions for all selected photos.") },
                        confirmButton = { Button(onClick = { showBatchCaptionChoice = false; showIndividualBatchCaptionPicker = true }) { Text("Individual photo") } },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Button(onClick = {
                                    showBatchCaptionChoice = false
                                    batchCaptionRound = 0
                                    batchCaptionResults = emptyMap()
                                    generateBatchCaptions(selectedPhotos.toList(), batchCaptionTheme, 0)
                                }, enabled = !isGeneratingBatchCaptions) { Text("All selected") }
                                Button(onClick = { showBatchCaptionChoice = false }) { Text("Cancel") }
                            }
                        }
                    )
                }
                if (showIndividualBatchCaptionPicker) {
                    AlertDialog(
                        onDismissRequest = { showIndividualBatchCaptionPicker = false },
                        title = { Text("Choose a photo") },
                        text = {
                            Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                                selectedPhotos.filter { it.decision != "Reject" }.forEach { photo ->
                                    Button(onClick = {
                                        batchCaptionRound = 0
                                        batchCaptionResults = batchCaptionResults - photo.uri
                                        generateBatchCaptions(listOf(photo), batchCaptionTheme, 0)
                                        showIndividualBatchCaptionPicker = false
                                    }, enabled = !isGeneratingBatchCaptions,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text("Photo " + (selectedPhotos.indexOf(photo) + 1))
                                    }
                                }
                            }
                        },
                        confirmButton = { Button(onClick = { showIndividualBatchCaptionPicker = false }) { Text("Close") } }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Balanced", "Saturated", "Cool").forEach { look ->
                        Button(
                            onClick = {
                                batchAiLook = look
                                previewBatchPreset("AI Edit", look)
                            },
                            enabled = !isExporting && !isAnalyzing && !isPreparingBatchPreview &&
                                selectedPhotos.any { it.decision != "Reject" },
                            modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (batchAiLook == look && batchPreset == "AI Edit") Mint else Color(0xFF293333),
                                contentColor = if (batchAiLook == look && batchPreset == "AI Edit") Ink else BrightText
                            ),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                        ) { Text(look, fontSize = 10.sp, maxLines = 1) }
                    }
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
                    Text(
                        if (batchPreset == "Current edits") "Uses the settings currently open in the editor."
                        else if (batchPreset == "AI Edit") "AI Edit · $batchAiLook · adapts to each photo."
                        else "Applies " + batchPreset + " consistently to every eligible photo.",
                        color = SoftText, fontSize = 10.sp
                    )
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
                        Button(
                            onClick = { photoPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.material3.Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Choose photos", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
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
                                .pointerInput(photo.uri) {
                                    detectTapGestures(
                                        onTap = { openEditor(photo) },
                                        onLongPress = { originalHoldPhoto = photo },
                                        onPress = {
                                            try {
                                                awaitRelease()
                                            } finally {
                                                originalHoldPhoto = null
                                            }
                                        }
                                    )
                                }
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
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                                        .size(21.dp).clip(RoundedCornerShape(50))
                                        .background(Color(0xD9101313))
                                        .clickable {
                                            selectedPhotos.removeAll { it.uri == photo.uri }
                                            statusMessage = "Photo removed from selection. The original file was not deleted."
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("×", color = BrightText, fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, lineHeight = 18.sp)
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

            if (originalHoldPhoto != null) {
                AlertDialog(
                    onDismissRequest = { originalHoldPhoto = null },
                    title = { Text("Original photo · hold preview") },
                    text = {
                        AsyncImage(
                            model = originalHoldPhoto!!.uri,
                            contentDescription = "Untouched original photo",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
                            contentScale = ContentScale.Fit
                        )
                    },
                    confirmButton = { Button(onClick = { originalHoldPhoto = null }) { Text("Close") } }
                )
            }

            if (showDeleteLearningConfirmation) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteLearningConfirmation = false
                        showResetLearning = false
                    },
                    title = { Text("Delete learned style?") },
                    text = { Text("This removes your saved editing preferences from this device. Photos are not deleted.") },
                    confirmButton = {
                        Button(onClick = {
                            PersonalEditLearning.reset(context)
                            learnedProfile = PersonalEditLearning.load(context)
                            showDeleteLearningConfirmation = false
                            showResetLearning = false
                            statusMessage = "Personal editing learning deleted from this device."
                        }) { Text("Delete learning") }
                    },
                    dismissButton = {
                        Button(onClick = {
                            showDeleteLearningConfirmation = false
                            showResetLearning = false
                        }) { Text("Cancel") }
                    }
                )
            }

            Text(
                statusMessage,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}
