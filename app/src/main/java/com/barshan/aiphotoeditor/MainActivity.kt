package com.barshan.aiphotoeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Ink = Color(0xFF101313)
private val Panel = Color(0xFF1A2020)
private val Mint = Color(0xFF78E0D0)
private val SoftText = Color(0xFFA7B5B2)
private val BrightText = Color(0xFFF2F7F5)

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
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedPhotos.clear()
        selectedPhotos.addAll(uris)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Mint),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Photo Editor",
                        tint = Ink,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("FRAME / AI", color = BrightText, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("YOUR PERSONAL PHOTO EDITOR", color = SoftText,
                        fontSize = 9.sp, letterSpacing = 1.1.sp)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text("Your moments.\nAt their best.", color = BrightText, fontSize = 36.sp,
                lineHeight = 41.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Find your strongest shots and make them social-ready, without spending your evening editing.",
                color = SoftText, fontSize = 15.sp, lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.ImageSearch,
                            contentDescription = null, tint = Mint, modifier = Modifier.size(23.dp)
                        )
                        Text("  START WITH YOUR PHOTOS", color = BrightText, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                    }
                    Text(
                        if (selectedPhotos.isEmpty())
                            "Choose a batch of photos to begin your first editing session."
                        else "${selectedPhotos.size} photo${if (selectedPhotos.size == 1) "" else "s"} selected. Your originals stay untouched.",
                        color = SoftText, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Button(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null
                        )
                        Text(if (selectedPhotos.isEmpty()) "Choose photos" else "Change selection",
                            modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedPhotos.isEmpty()) "YOUR WORKSPACE" else "SELECTED PHOTOS",
                    color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${selectedPhotos.size} items", color = SoftText, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedPhotos.isEmpty()) {
                EmptyWorkspace(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedPhotos) { uri ->
                        Box {
                            AsyncImage(model = uri, contentDescription = "Selected photo",
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.78f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop)
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected", tint = Mint,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                            )
                        }
                    }
                }
            }
            Text("PRIVATE BY DESIGN  •  ORIGINALS PRESERVED",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                color = SoftText, fontSize = 9.sp, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun EmptyWorkspace(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Color(0xFF151A1A)), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(Panel),
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(imageVector = Icons.Rounded.Tune,
                    contentDescription = null, tint = Mint, modifier = Modifier.size(30.dp))
            }
            Text("A better edit starts here", color = BrightText, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Text(
                "Your selected images will appear here. Photo analysis and automatic edits are coming in the next milestone.",
                color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
            )
        }
    }
}
