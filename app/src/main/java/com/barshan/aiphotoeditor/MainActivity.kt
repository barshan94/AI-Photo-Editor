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
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val selectedPhotos = remember { mutableStateListOf<PhotoItem>() }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableIntStateOf(0) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var batchPreset by remember { mutableStateOf("Current edits") }
    var batchSummary by remember { mutableStateOf<String?>(null) }
    var activePhoto by remember { mutableStateOf<PhotoItem?>(null) }
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
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1.07f) }
    var saturation by remember { mutableFloatStateOf(1.06f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var cropLabel by remember { mutableStateOf("Original") }
    var cropOffsetX by remember { mutableFloatStateOf(0f) }
    var cropOffsetY by remember { mutableFloatStateOf(0f) }
    var selectedStyle by remember { mutableStateOf("Auto") }
    var statusMessage by remember { mutableStateOf("Choose photos to begin. Analysis and edits run on this device.") }
    var photoAnalysisResult by remember { mutableStateOf<PhotoAnalysisResult?>(null) }
    var isRunningPhotoAnalysis by remember { mutableStateOf(false) }

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
        cropOffsetY = cropOffsetY
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
        cropOffsetX = settings.cropOffsetX
        cropOffsetY = settings.cropOffsetY
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
        when (style) {
            "Natural" -> { brightness = 0f; contrast = 1.03f; saturation = 1.02f; warmth = 0f }
            "Vivid" -> { brightness = 0f; contrast = 1.13f; saturation = 1.28f; warmth = 0f }
            "Warm" -> { brightness = 2f; contrast = 1.06f; saturation = 1.08f; warmth = 12f }
            "Cool" -> { brightness = 0f; contrast = 1.05f; saturation = 1.04f; warmth = -10f }
            "Mono" -> { brightness = 0f; contrast = 1.10f; saturation = 0f; warmth = 0f }
            else -> { brightness = 0f; contrast = 1.07f; saturation = 1.06f; warmth = 0f }
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
        compareBeforeAfter = false
        previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewBitmap = null
        showEnhanced = true
        isPreparingPreview = true
        statusMessage = "Preparing live preview…"
    }

    LaunchedEffect(activePhoto?.uri) {
        resetPreviewZoom()
        photoAnalysisResult = null
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
    LaunchedEffect(activePhoto?.uri, brightness, contrast, saturation, warmth, cropLabel, cropOffsetX, cropOffsetY, selectedStyle) {
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
                if (selectedStyle == "AI Portrait") {
                    val aiResult = SubjectAwareEnhancer.enhance(original!!, settings)
                    adjusted = aiResult.bitmap
                    aiNotice = aiResult.message
                } else {
                    adjusted = withContext(Dispatchers.Default) {
                        AutoEnhancer.enhance(original!!, settings)
                    }
                }
                previewBitmap?.let { if (!it.isRecycled) it.recycle() }
                previewBitmap = adjusted
                adjusted = null
                showEnhanced = true
                statusMessage = aiNotice ?: if (selectedStyle == "AI Portrait") {
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

    fun exportOne(photo: PhotoItem, settings: EditSettings, useAiPortrait: Boolean = false) {
        if (isExporting) return
        isExporting = true
        statusMessage = "Rendering full-resolution export…"
        scope.launch {
            var original: Bitmap? = null
            var enhanced: Bitmap? = null
            var aiNotice: String? = null
            try {
                original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri, maxDimension = Int.MAX_VALUE)
                }
                if (useAiPortrait) {
                    val aiResult = SubjectAwareEnhancer.enhance(original!!, settings)
                    enhanced = aiResult.bitmap
                    aiNotice = aiResult.message
                } else {
                    enhanced = withContext(Dispatchers.Default) {
                        AutoEnhancer.enhance(original!!, settings)
                    }
                }
                val width = enhanced!!.width
                val height = enhanced!!.height
                withContext(Dispatchers.IO) {
                    AutoEnhancer.saveJpeg(context.contentResolver, enhanced!!)
                }
                statusMessage = (aiNotice?.let { "$it " } ?: if (useAiPortrait) {
                    "AI Portrait enhancement applied. "
                } else "") + "Saved full-resolution " + width + "x" + height +
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
                original?.let { if (!it.isRecycled) it.recycle() }
                isExporting = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
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
                Text("BETA 2.1", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Your moments. At their best.", color = BrightText, fontSize = 27.sp,
                lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Rank your shots, preview a gentle auto-edit, and export new copies.",
                modifier = Modifier.padding(top = 5.dp),
                color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
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
                            val useAiPortrait = batchPreset == "AI Portrait"
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
                                            if (useAiPortrait) {
                                                val aiResult = SubjectAwareEnhancer.enhance(original!!, settingsForBatch)
                                                enhanced = aiResult.bitmap
                                                if (!aiResult.usedAi) aiFallbacks++
                                            } else {
                                                enhanced = withContext(Dispatchers.Default) {
                                                    AutoEnhancer.enhance(original!!, settingsForBatch)
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
                    Text("  Enhance all", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (activePhoto == null && selectedPhotos.isNotEmpty()) {
                Text("BATCH PRESET", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BatchExportPolicy.presetNames.forEach { preset ->
                        Button(onClick = { batchPreset = preset; batchSummary = null },
                            enabled = !isExporting && !isAnalyzing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (batchPreset == preset) Mint else Color(0xFF293333),
                                contentColor = if (batchPreset == preset) Ink else BrightText
                            ), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text(preset, fontSize = 10.sp) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedPhotos.count { it.decision != "Reject" }.toString() + " photos to export · " +
                            selectedPhotos.count { it.decision == "Reject" } + " rejected will be skipped",
                            color = SoftText, fontSize = 10.sp)
                        Text(if (batchPreset == "Current edits") "Uses the settings currently open in the editor." else
                            "Applies " + batchPreset + " consistently to every eligible photo.",
                            color = SoftText, fontSize = 10.sp)
                    }
                    Button(onClick = {
                        val sample = selectedPhotos.firstOrNull { it.decision != "Reject" }
                        if (sample != null) {
                            openEditor(sample)
                            if (batchPreset != "Current edits") {
                                applyEditSnapshot(BatchExportPolicy.settingsForPreset(batchPreset))
                                selectedStyle = batchPreset
                            }
                            statusMessage = "Batch preview sample: " + batchPreset + ". Return to the grid to export the reviewed batch."
                        }
                    }, enabled = !isExporting && !isAnalyzing && selectedPhotos.any { it.decision != "Reject" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = Mint),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                    ) { Text("Preview sample", fontSize = 10.sp) }
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

            if (activePhoto != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EDIT PREVIEW", color = BrightText, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(if (showEnhanced) "ENHANCED" else "ORIGINAL",
                                color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    activePhoto = null
                                    previewBitmap?.let { if (!it.isRecycled) it.recycle() }
                                    previewBitmap = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293333), contentColor = BrightText),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 1.dp)
                            ) { Text("All photos", fontSize = 10.sp) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                .clip(RoundedCornerShape(14.dp)).background(Ink)
                                .onSizeChanged { size ->
                                    previewWidthPx = size.width
                                    previewHeightPx = size.height
                                }
                                .then(
                                    if (cropLabel != "Original") {
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
                                        Modifier.transformable(state = previewTransformState)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val active = activePhoto
                            if (active != null) {
                                if (compareBeforeAfter && previewBitmap != null) {
                                    AsyncImage(model = previewBitmap, contentDescription = "After edit comparison",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    AsyncImage(model = active.uri, contentDescription = "Before edit comparison",
                                        modifier = Modifier.fillMaxSize().drawWithContent {
                                            clipRect(right = size.width * comparePosition) { this@drawWithContent.drawContent() }
                                        }, contentScale = ContentScale.Fit)
                                    Box(modifier = Modifier.fillMaxWidth(comparePosition).height(2.dp)
                                        .align(Alignment.CenterStart).background(Mint))
                                    Text("BEFORE", color = BrightText, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                            .background(Ink.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).padding(5.dp))
                                    Text("AFTER", color = BrightText, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                            .background(Ink.copy(alpha = 0.8f), RoundedCornerShape(5.dp)).padding(5.dp))
                                } else if (showEnhanced && previewBitmap != null) {
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
                                if (cropLabel == "Original") "Pinch to zoom · drag to inspect"
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
                        val current = activePhoto
                        if (current != null && current.score != null) {
                            Text("TECHNICAL SCORE  " + current.score + "/100",
                                color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 7.dp))
                            Text("Sharpness " + current.sharpness + "  ·  Exposure " + current.exposure +
                                "  ·  Contrast " + current.contrast,
                                color = SoftText, fontSize = 10.sp,
                                modifier = Modifier.padding(top = 3.dp))
                            Text(current.note, color = SoftText, fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        if (current != null) {
                            Text("YOUR DECISION", color = BrightText, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Keep", "Review", "Reject").forEach { decision ->
                                    Button(
                                        onClick = { setPhotoDecision(current, decision) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (current.decision == decision) {
                                                when (decision) {
                                                    "Keep" -> Mint
                                                    "Reject" -> Color(0xFF8B4242)
                                                    else -> Color(0xFF52605D)
                                                }
                                            } else Color(0xFF293333),
                                            contentColor = if (current.decision == decision && decision == "Keep") Ink else BrightText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text(decision, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
                                enabled = previewBitmap != null, modifier = Modifier.weight(0.85f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (compareBeforeAfter) Mint else Color(0xFF293333),
                                    contentColor = if (compareBeforeAfter) Ink else BrightText),
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)
                            ) { Text(if (compareBeforeAfter) "Close compare" else "Compare", fontSize = 10.sp) }
                            Button(
                                onClick = { showEnhanced = !showEnhanced; compareBeforeAfter = false },
                                enabled = previewBitmap != null,
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
                                    if (photo != null) exportOne(photo, currentSettings(), selectedStyle == "AI Portrait")
                                },
                                enabled = previewBitmap != null && !isExporting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Export copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
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
                                            statusMessage = "AI analysis complete. Review the findings before choosing edits."
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
                                    if (photoAnalysisResult == null) "  Analyse this photo with AI" else "  Analyse again",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        photoAnalysisResult?.let { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF202929)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text("AI PHOTO ANALYSIS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${result.width} × ${result.height} px · ${result.faceCount} detected face(s)",
                                        color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(result.subjectSummary, color = BrightText, fontSize = 12.sp, lineHeight = 17.sp)
                                    Text("SCENE LABELS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        result.labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                                            ?: "No confident scene labels returned. This is not proof that the image has no subject.",
                                        color = SoftText, fontSize = 11.sp, lineHeight = 16.sp
                                    )
                                    Text("LIGHTING CHECK", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(result.lightingSummary, color = SoftText, fontSize = 11.sp, lineHeight = 16.sp)
                                    Text("SUGGESTED NEXT STEPS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    result.recommendations.forEach { recommendation ->
                                        Text("• $recommendation", color = BrightText, fontSize = 11.sp, lineHeight = 16.sp)
                                    }
                                    Text(
                                        "Guidance only: labels and face detection can be wrong. Nothing was changed or exported.",
                                        color = SoftText, fontSize = 10.sp, lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("STYLE PRESETS", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(if (showCropGuides) "Guides on" else "Guides off", color = Mint, fontSize = 10.sp,
                                modifier = Modifier.clickable { showCropGuides = !showCropGuides }.padding(4.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Auto", "Natural", "Vivid", "Warm", "Cool", "Mono", "AI Portrait").forEach { style ->
                                Button(
                                    onClick = { setStyle(style) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedStyle == style) Mint else Color(0xFF293333),
                                        contentColor = if (selectedStyle == style) Ink else BrightText
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text(style, fontSize = 11.sp) }
                            }
                        }
                        Text("BRIGHTNESS  " + brightness.toInt(), color = SoftText, fontSize = 11.sp)
                        Slider(value = brightness, onValueChange = {
                            recordEditChange()
                            brightness = it
                            selectedStyle = "Custom"
                        }, valueRange = -25f..25f)
                        Text("CONTRAST  " + String.format(java.util.Locale.US, "%.2f", contrast),
                            color = SoftText, fontSize = 11.sp)
                        Slider(value = contrast, onValueChange = {
                            recordEditChange()
                            contrast = it
                            selectedStyle = "Custom"
                        }, valueRange = 0.75f..1.40f)
                        Text("SATURATION  " + String.format(java.util.Locale.US, "%.2f", saturation),
                            color = SoftText, fontSize = 11.sp)
                        Slider(value = saturation, onValueChange = {
                            recordEditChange()
                            saturation = it
                            selectedStyle = "Custom"
                        }, valueRange = 0f..1.65f)
                        Text("WARMTH  " + warmth.toInt(), color = SoftText, fontSize = 11.sp)
                        Slider(value = warmth, onValueChange = {
                            recordEditChange()
                            warmth = it
                            selectedStyle = "Custom"
                        }, valueRange = -24f..24f)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("CROP FOR", color = BrightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Original", "Square", "Portrait 4:5", "Story 9:16", "Landscape 16:9").forEach { crop ->
                                Button(
                                    onClick = { recordEditChange(); cropLabel = crop },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (cropLabel == crop) Mint else Color(0xFF293333),
                                        contentColor = if (cropLabel == crop) Ink else BrightText
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) { Text(crop, fontSize = 10.sp) }
                            }
                        }
                        if (cropLabel != "Original") {
                            Text("CROP HORIZONTAL  " + (cropOffsetX * 100).toInt() + "%", color = SoftText, fontSize = 11.sp)
                            Slider(
                                value = cropOffsetX,
                                onValueChange = { recordEditChange(); cropOffsetX = it },
                                valueRange = -1f..1f
                            )
                            Text("CROP VERTICAL  " + (cropOffsetY * 100).toInt() + "%", color = SoftText, fontSize = 11.sp)
                            Slider(
                                value = cropOffsetY,
                                onValueChange = { recordEditChange(); cropOffsetY = it },
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
                    items(visiblePhotos, key = { it.uri.toString() }) { photo ->
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(13.dp))
                                .background(if (activePhoto?.uri == photo.uri) Color(0xFF263B38) else Panel)
                                .clickable { openEditor(photo) }
                                .padding(5.dp)
                        ) {
                            Box {
                                AsyncImage(model = photo.uri, contentDescription = "Photo",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)
                                        .clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop)
                                if (activePhoto?.uri == photo.uri) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Editing this photo",
                                        tint = Mint,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                                    )
                                }
                            }
                            Text(
                                if (photo.score == null) "Analysing…" else "Score " + photo.score + "/100",
                                color = if ((photo.score ?: 0) >= 70) Mint else BrightText,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
                            )
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
                            Text(photo.note, color = SoftText, fontSize = 9.sp, lineHeight = 12.sp,
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
