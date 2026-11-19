package com.fwdrucik.ragwarsztat.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fwdrucik.ragwarsztat.theme.*

@Composable
fun PdfPageViewerDialog(
    doc: ActiveViewerDoc,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom on page change
    LaunchedEffect(doc.page) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0B0E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Górny pasek nagłówka
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.filename,
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${doc.brand} • Strona ${doc.page} z ${doc.totalPages.coerceAtLeast(1)}",
                            color = IndustrialOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                scale = if (scale > 1.5f) 1f else 2.5f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (scale > 1.5f) "🔍 100%" else "🔍 250%",
                                color = CyanDiagnostics,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Text("✕", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Obszar wyświetlania i powiększania strony PDF (Pinch to zoom / pan)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2
                                    offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (doc.isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = IndustrialOrange, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Renderowanie strony ${doc.page} w wysokiej rozdzielczości...", color = TextSecondaryDark, fontSize = 12.sp)
                        }
                    } else if (doc.bitmap != null) {
                        Image(
                            bitmap = doc.bitmap.asImageBitmap(),
                            contentDescription = "Strona ${doc.page} dokumentu ${doc.filename}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(doc.errorMessage ?: "Błąd ładowania obrazu strony.", color = ErrorRed, fontSize = 12.sp)
                        }
                    }
                }

                // Dolny pasek nawigacji stron
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onPreviousPage,
                        enabled = doc.page > 1 && !doc.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceVariant,
                            disabledContainerColor = Color(0xFF1B1C22)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("◀ Poprzednia", color = if (doc.page > 1) TextPrimaryDark else TextMuted, fontSize = 12.sp)
                    }

                    Text(
                        text = "Strona ${doc.page} / ${doc.totalPages.coerceAtLeast(1)}",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Button(
                        onClick = onNextPage,
                        enabled = doc.page < doc.totalPages && !doc.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceVariant,
                            disabledContainerColor = Color(0xFF1B1C22)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Następna ▶", color = if (doc.page < doc.totalPages) TextPrimaryDark else TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
