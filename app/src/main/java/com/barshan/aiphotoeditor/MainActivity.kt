package com.barshan.aiphotoeditor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
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
    val note: String = "Waiting for analysis"
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
    var activePhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    var showEnhanced by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Choose photos to begin. Analysis and edits run on this device.") }

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
                                note = result.note
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
                selectedPhotos.sortByDescending { it.score ?: -1 }
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

    fun openEditor(photo: PhotoItem) {
        activePhoto = photo
        previewBitmap = null
        showEnhanced = true
        isPreparingPreview = true
        statusMessage = "Preparing a preview…"
        scope.launch {
            try {
                val original = withContext(Dispatchers.IO) {
                    AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri)
                }
                val enhanced = withContext(Dispatchers.Default) { AutoEnhancer.enhance(original) }
                original.recycle()
                previewBitmap = enhanced
                isPreparingPreview = false
                statusMessage = "Preview ready. The original photo has not been changed."
            } catch (error: Exception) {
                isPreparingPreview = false
                statusMessage = "Preview failed: " + (error.message ?: "unsupported image")
            }
        }
    }

    fun exportOne(photo: PhotoItem, bitmap: Bitmap) {
        if (isExporting) return
        isExporting = true
        statusMessage = "Saving enhanced copy…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AutoEnhancer.saveJpeg(context.contentResolver, bitmap)
                }
                statusMessage = "Saved to Pictures/FRAME AI. Your original is untouched."
            } catch (error: Exception) {
                statusMessage = "Export failed: " + (error.message ?: "unknown error")
            } finally {
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
                Text("BETA 0.2", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            scope.launch {
                                var succeeded = 0
                                var failed = 0
                                val batch = selectedPhotos.toList()
                                for ((index, photo) in batch.withIndex()) {
                                    try {
                                        val original = withContext(Dispatchers.IO) {
                                            AutoEnhancer.decodeForEditing(context.contentResolver, photo.uri)
                                        }
                                        val enhanced = withContext(Dispatchers.Default) {
                                            AutoEnhancer.enhance(original)
                                        }
                                        original.recycle()
                                        withContext(Dispatchers.IO) {
                                            AutoEnhancer.saveJpeg(context.contentResolver, enhanced)
                                        }
                                        enhanced.recycle()
                                        succeeded++
                                    } catch (_: Exception) {
                                        failed++
                                    }
                                    exportProgress = index + 1
                                }
                                isExporting = false
                                statusMessage = "Batch complete: " + succeeded + " saved to Pictures/FRAME AI" +
                                    if (failed > 0) (", " + failed + " failed") else ". Originals are untouched."
                            }
                        }
                    },
                    enabled = selectedPhotos.isNotEmpty() && !isAnalyzing && !isExporting,
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
                    progress = { if (selectedPhotos.isEmpty()) 0f else exportProgress.toFloat() / selectedPhotos.size },
                    modifier = Modifier.fillMaxWidth(), color = Mint
                )
                Text("Exporting " + exportProgress + " / " + selectedPhotos.size,
                    color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }

            if (activePhoto != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EDIT PREVIEW", color = BrightText, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(if (showEnhanced) "ENHANCED" else "ORIGINAL",
                                color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                                .clip(RoundedCornerShape(14.dp)).background(Ink),
                            contentAlignment = Alignment.Center
                        ) {
                            val active = activePhoto
                            if (active != null) {
                                if (showEnhanced && previewBitmap != null) {
                                    AsyncImage(model = previewBitmap, contentDescription = "Enhanced preview",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else {
                                    AsyncImage(model = active.uri, contentDescription = "Original photo",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                }
                            }
                            if (isPreparingPreview) CircularProgressIndicator(color = Mint)
                        }
                        val current = activePhoto
                        if (current != null && current.score != null) {
                            Text("Technical score: " + current.score + "/100  •  " + current.note,
                                color = SoftText, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 7.dp))
                        }
                        Spacer(modifier = Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showEnhanced = !showEnhanced },
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
                                    val bitmap = previewBitmap
                                    if (photo != null && bitmap != null) exportOne(photo, bitmap)
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedPhotos.isEmpty()) "YOUR WORKSPACE" else "BEST SHOTS FIRST",
                    color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(selectedPhotos.size.toString() + " photos", color = SoftText, fontSize = 11.sp)
            }
            Text(
                "Scores measure technical quality only—not whether a moment matters to you.",
                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 7.dp)
            )

            if (selectedPhotos.isEmpty()) {
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(selectedPhotos, key = { it.uri.toString() }) { photo ->
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
                            Text(photo.note, color = SoftText, fontSize = 9.sp, lineHeight = 12.sp,
                                maxLines = 2, modifier = Modifier.padding(top = 2.dp, start = 2.dp, bottom = 2.dp))
                        }
                    }
                }
            }

            Text(
                statusMessage,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                color = SoftText, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}
