package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.SanctuaryGold
import com.example.util.CropPreset
import com.example.util.DrawStroke
import com.example.util.EditedImagePayload
import com.example.util.ImageFilterPreset
import com.example.util.MediaCompressionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private val BRUSH_PALETTE = listOf(
    0xFFFFFFFF.toInt(), // Pure White
    0xFFD4AF37.toInt(), // Sanctuary Gold
    0xFF25D366.toInt(), // Emerald Green
    0xFFFF3B30.toInt(), // Vivid Red
    0xFF0A84FF.toInt(), // Electric Blue
    0xFFFF2D55.toInt(), // Hot Pink
    0xFF111B21.toInt()  // Midnight Ink
)

/**
 * WhatsApp-style Before-Send Image Editing Studio with:
 * - True HD Quality Toggle (1600p High-Clarity vs 864p Data Saver)
 * - Rotate 90° & Aspect Ratio Crop (Free, 1:1, 4:3, 16:9)
 * - 5 Color Filters (Original, Vivid, Warm, Cool, B&W)
 * - Multi-color Freehand Brush Doodle + Undo
 * - Draggable Styled Text Overlay
 * - Encrypted Caption Bar & Send FAB
 */
@Composable
fun ImageEditorDialog(
    sourceBitmap: Bitmap,
    initialCaption: String,
    onDismiss: () -> Unit,
    onSendEditedImage: (caption: String, payload: EditedImagePayload) -> Unit
) {
    val scope = rememberCoroutineScope()

    var isHd by remember { mutableStateOf(true) } // Default HD crisp quality ON for immediate delight
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var cropIndex by remember { mutableIntStateOf(0) }
    var filterPreset by remember { mutableStateOf(ImageFilterPreset.ORIGINAL) }
    var isDrawMode by remember { mutableStateOf(false) }
    var showFilterStrip by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var selectedBrushArgb by remember { mutableIntStateOf(BRUSH_PALETTE[1]) }

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var activeStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var overlayText by remember { mutableStateOf("") }
    var overlayColorArgb by remember { mutableIntStateOf(BRUSH_PALETTE[0]) }
    var overlayPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    var caption by remember { mutableStateOf(initialCaption) }
    var isRenderingSend by remember { mutableStateOf(false) }
    var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val cropPreset = CropPreset.entries[cropIndex % CropPreset.entries.size]

    // Render live base preview bitmap (rotated + cropped + filtered) smoothly at 900px for responsive 60fps canvas
    val liveBaseBitmap = remember(sourceBitmap, rotationDegrees, cropPreset, filterPreset) {
        MediaCompressionUtil.renderEditedBitmap(
            source = sourceBitmap,
            rotationDegrees = rotationDegrees,
            cropPreset = cropPreset,
            filterPreset = filterPreset,
            strokes = emptyList(),
            overlayText = "",
            overlayTextColorArgb = AndroidColor.WHITE,
            overlayNormalizedPos = Offset(0.5f, 0.5f),
            maxDimensionPx = 960
        )
    }

    val previewAspect = remember(liveBaseBitmap) {
        (liveBaseBitmap.width.toFloat() / liveBaseBitmap.height.coerceAtLeast(1).toFloat())
            .coerceIn(0.45f, 2.2f)
    }

    Dialog(
        onDismissRequest = { if (!isRenderingSend) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B141A))
                .imePadding()
        ) {
            // 1. Top Studio Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isRenderingSend,
                    modifier = Modifier.testTag("editor_close_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard edits", tint = Color.White)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // WhatsApp-style HD Toggle Pill
                    Surface(
                        onClick = { isHd = !isHd },
                        color = if (isHd) Color(0xFF00A884) else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("editor_hd_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hd,
                                contentDescription = "Toggle HD Image Quality",
                                tint = if (isHd) Color(0xFF04261C) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHd) "HD ON" else "SD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isHd) Color(0xFF04261C) else Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { cropIndex = (cropIndex + 1) % CropPreset.entries.size },
                        modifier = Modifier.testTag("editor_crop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Crop,
                            contentDescription = "Crop ratio (${cropPreset.label})",
                            tint = if (cropPreset != CropPreset.ORIGINAL) SanctuaryGold else Color.White
                        )
                    }

                    IconButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        modifier = Modifier.testTag("editor_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RotateRight,
                            contentDescription = "Rotate 90 degrees",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { showFilterStrip = !showFilterStrip },
                        modifier = Modifier.testTag("editor_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Color Filters",
                            tint = if (filterPreset != ImageFilterPreset.ORIGINAL) SanctuaryGold else Color.White
                        )
                    }

                    IconButton(
                        onClick = { showTextDialog = true },
                        modifier = Modifier.testTag("editor_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TextFields,
                            contentDescription = "Add Text Overlay",
                            tint = if (overlayText.isNotBlank()) SanctuaryGold else Color.White
                        )
                    }

                    IconButton(
                        onClick = { isDrawMode = !isDrawMode },
                        modifier = Modifier.testTag("editor_draw_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = "Freehand Draw Brush",
                            tint = if (isDrawMode) Color(0xFF25D366) else Color.White
                        )
                    }

                    if (strokes.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    strokes.removeAt(strokes.lastIndex)
                                }
                            },
                            modifier = Modifier.testTag("editor_undo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo Brush Stroke",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Status / Resolution Quality Indicator Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buildString {
                        append(if (isHd) "HD 1600p High-Clarity" else "Standard 864p")
                        append(" • Crop: ${cropPreset.label}")
                        append(" • Filter: ${filterPreset.label}")
                        if (isDrawMode) append(" • Finger Draw Active")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isHd) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.72f)
                )
            }

            // Color Palette Strip when Brush Mode is active
            AnimatedVisibility(visible = isDrawMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BRUSH_PALETTE.forEach { argb ->
                        val isSelected = selectedBrushArgb == argb
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) SanctuaryGold else Color.White.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { selectedBrushArgb = argb },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (argb == 0xFFFFFFFF.toInt() || argb == 0xFFD4AF37.toInt()) Color.Black else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filter Strip when Filter Toggle is open
            AnimatedVisibility(visible = showFilterStrip) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ImageFilterPreset.entries) { preset ->
                        FilterChip(
                            selected = filterPreset == preset,
                            onClick = { filterPreset = preset },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00A884),
                                selectedLabelColor = Color(0xFF04261C),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 2. Interactive Editing Canvas Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(previewAspect)
                        .clip(RoundedCornerShape(14.dp))
                        .onSizeChanged { previewBoxSize = it }
                        .pointerInput(isDrawMode, selectedBrushArgb, previewBoxSize) {
                            if (!isDrawMode || previewBoxSize.width <= 0 || previewBoxSize.height <= 0) return@pointerInput
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val nx = (startOffset.x / previewBoxSize.width).coerceIn(0f, 1f)
                                    val ny = (startOffset.y / previewBoxSize.height).coerceIn(0f, 1f)
                                    activeStrokePoints = listOf(Offset(nx, ny))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / previewBoxSize.width).coerceIn(0f, 1f)
                                    val ny = (change.position.y / previewBoxSize.height).coerceIn(0f, 1f)
                                    activeStrokePoints = activeStrokePoints + Offset(nx, ny)
                                },
                                onDragEnd = {
                                    if (activeStrokePoints.isNotEmpty()) {
                                        strokes.add(
                                            DrawStroke(
                                                normalizedPoints = activeStrokePoints,
                                                colorArgb = selectedBrushArgb
                                            )
                                        )
                                        activeStrokePoints = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    activeStrokePoints = emptyList()
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = liveBaseBitmap.asImageBitmap(),
                        contentDescription = "Editing photo preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Live Vector Overlay for Brush Strokes
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val minSide = min(size.width, size.height)
                        fun drawNormalizedStroke(stroke: DrawStroke) {
                            val pts = stroke.normalizedPoints
                            if (pts.isEmpty()) return
                            val pxWidth = (stroke.strokeWidthRatio * minSide).coerceAtLeast(3f)
                            if (pts.size == 1) {
                                drawCircle(
                                    color = Color(stroke.colorArgb),
                                    radius = pxWidth * 0.5f,
                                    center = Offset(pts[0].x * size.width, pts[0].y * size.height)
                                )
                            } else {
                                val path = Path()
                                path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                                for (i in 1 until pts.size) {
                                    path.lineTo(pts[i].x * size.width, pts[i].y * size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(stroke.colorArgb),
                                    style = Stroke(width = pxWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        strokes.forEach { drawNormalizedStroke(it) }
                        if (activeStrokePoints.isNotEmpty()) {
                            drawNormalizedStroke(
                                DrawStroke(
                                    normalizedPoints = activeStrokePoints,
                                    colorArgb = selectedBrushArgb
                                )
                            )
                        }
                    }

                    // Draggable Text Overlay Pill (when not actively drawing)
                    if (overlayText.isNotBlank() && previewBoxSize.width > 0 && previewBoxSize.height > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isDrawMode, previewBoxSize) {
                                    if (isDrawMode) return@pointerInput
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / previewBoxSize.width
                                        val dy = dragAmount.y / previewBoxSize.height
                                        overlayPos = Offset(
                                            (overlayPos.x + dx).coerceIn(0.15f, 0.85f),
                                            (overlayPos.y + dy).coerceIn(0.15f, 0.85f)
                                        )
                                    }
                                }
                        ) {
                            Surface(
                                color = Color(0x990A1216),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = ((overlayPos.x * 0.72f) * 240).dp.coerceAtLeast(8.dp),
                                        top = ((overlayPos.y * 0.72f) * 260).dp.coerceAtLeast(8.dp)
                                    )
                                    .clickable { showTextDialog = true }
                            ) {
                                Text(
                                    text = overlayText,
                                    color = Color(overlayColorArgb),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bottom Encrypted Caption Input & Send FAB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.editor_add_caption_hint),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1F2C34),
                        unfocusedContainerColor = Color(0xFF1F2C34),
                        focusedBorderColor = Color(0xFF00A884),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_caption_input")
                )

                Spacer(modifier = Modifier.width(10.dp))

                FloatingActionButton(
                    onClick = {
                        if (isRenderingSend) return@FloatingActionButton
                        isRenderingSend = true
                        scope.launch {
                            val prepared = withContext(Dispatchers.Default) {
                                MediaCompressionUtil.prepareEditedImageForSend(
                                    source = sourceBitmap,
                                    rotationDegrees = rotationDegrees,
                                    cropPreset = cropPreset,
                                    filterPreset = filterPreset,
                                    strokes = strokes.toList(),
                                    overlayText = overlayText,
                                    overlayTextColorArgb = overlayColorArgb,
                                    overlayNormalizedPos = overlayPos,
                                    isHd = isHd
                                )
                            }
                            isRenderingSend = false
                            onSendEditedImage(caption, prepared)
                        }
                    },
                    shape = CircleShape,
                    containerColor = Color(0xFF00A884),
                    contentColor = Color(0xFF04261C),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("editor_send_button")
                ) {
                    if (isRenderingSend) {
                        CircularProgressIndicator(
                            color = Color(0xFF04261C),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Edited Encrypted Photo"
                        )
                    }
                }
            }
        }

        if (showTextDialog) {
            var tempText by remember(overlayText) { mutableStateOf(overlayText) }
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text("Photo Text Overlay") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = tempText,
                            onValueChange = { tempText = it.take(40) },
                            placeholder = { Text("Type sticker or label…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BRUSH_PALETTE.forEach { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (overlayColorArgb == argb) 3.dp else 1.dp,
                                            color = if (overlayColorArgb == argb) SanctuaryGold else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { overlayColorArgb = argb }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            overlayText = tempText.trim()
                            showTextDialog = false
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    if (overlayText.isNotBlank()) {
                        TextButton(
                            onClick = {
                                overlayText = ""
                                showTextDialog = false
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    }
}
