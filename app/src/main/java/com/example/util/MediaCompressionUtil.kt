package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImageFilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("B&W");

    fun toColorMatrix(): ColorMatrix {
        return when (this) {
            ORIGINAL -> ColorMatrix()
            VIVID -> ColorMatrix().apply { setSaturation(1.48f) }
            WARM -> ColorMatrix(
                floatArrayOf(
                    1.14f, 0f, 0f, 0f, 10f,
                    0f, 1.03f, 0f, 0f, 4f,
                    0f, 0f, 0.88f, 0f, -8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            COOL -> ColorMatrix(
                floatArrayOf(
                    0.90f, 0f, 0f, 0f, -6f,
                    0f, 1.02f, 0f, 0f, 2f,
                    0f, 0f, 1.18f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            MONO -> ColorMatrix().apply { setSaturation(0f) }
        }
    }
}

enum class CropPreset(val label: String, val targetAspect: Float?) {
    ORIGINAL("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

/**
 * Normalized (0f..1f) freehand brush stroke drawn on top of an image in the Image Editor.
 */
data class DrawStroke(
    val normalizedPoints: List<Offset>,
    val colorArgb: Int,
    val strokeWidthRatio: Float = 0.012f
)

data class EditedImagePayload(
    val inlinePreviewBase64: String,
    val hdChunkBytes: ByteArray?,
    val isHd: Boolean,
    val width: Int,
    val height: Int
)

data class VideoMetadataSummary(
    val thumbnailBase64: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String
)

/**
 * On-device Rich Media processor for My Mate:
 * - Standard (864px) & True HD (1600px, 92% quality) image encoding
 * - Full Bitmap Image Editing bake (Crop + Rotate + Color Matrix Filter + Brush Markup + Text Overlay)
 * - Video metadata & encrypted poster thumbnail extraction
 * - Safe binary stream reading for GIFs & HD Videos prior to AES-256-GCM encryption
 */
object MediaCompressionUtil {

    private const val STANDARD_MAX_PX = 864
    private const val HD_MAX_PX = 1600
    private const val INLINE_MAX_BYTES = 260_000
    const val MAX_ENCRYPTED_MEDIA_BYTES = 14 * 1024 * 1024 // ~14 MB safe ceiling inside 32 chunks

    fun uriToCompressedBase64(context: Context, uri: Uri): String? {
        return try {
            val rawBitmap = decodeSampledBitmap(context, uri, STANDARD_MAX_PX) ?: return null
            encodeBitmapToBase64UnderLimit(rawBitmap, STANDARD_MAX_PX, initialQuality = 72, maxBytes = 210_000)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, targetMaxPx: Int = HD_MAX_PX): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            while (max(rawW / sampleSize, rawH / sampleSize) > targetMaxPx * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bakes all user edits (Rotation, Aspect Crop, Color Filter, Freehand Brush Strokes,
     * and Draggable Text Overlay) onto a fresh Bitmap in either Standard (864p) or HD (1600p) resolution.
     */
    fun renderEditedBitmap(
        source: Bitmap,
        rotationDegrees: Int,
        cropPreset: CropPreset,
        filterPreset: ImageFilterPreset,
        strokes: List<DrawStroke>,
        overlayText: String,
        overlayTextColorArgb: Int,
        overlayNormalizedPos: Offset,
        maxDimensionPx: Int
    ): Bitmap {
        // 1. Rotate if needed
        val rotated = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Center-crop to selected aspect ratio if requested
        val cropped = cropPreset.targetAspect?.let { desiredRatio ->
            val srcW = rotated.width.toFloat()
            val srcH = rotated.height.toFloat()
            val currentRatio = srcW / srcH
            if (kotlin.math.abs(currentRatio - desiredRatio) < 0.02f) {
                rotated
            } else if (currentRatio > desiredRatio) {
                val newW = (srcH * desiredRatio).roundToInt().coerceIn(1, rotated.width)
                val xOff = ((rotated.width - newW) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(rotated, xOff, 0, newW, rotated.height)
            } else {
                val newH = (srcW / desiredRatio).roundToInt().coerceIn(1, rotated.height)
                val yOff = ((rotated.height - newH) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(rotated, 0, yOff, rotated.width, newH)
            }
        } ?: rotated

        // 3. Scale to target Standard or HD resolution
        val maxSide = max(cropped.width, cropped.height)
        val outW: Int
        val outH: Int
        if (maxSide > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
            outW = (cropped.width * scale).roundToInt().coerceAtLeast(1)
            outH = (cropped.height * scale).roundToInt().coerceAtLeast(1)
        } else {
            outW = cropped.width.coerceAtLeast(1)
            outH = cropped.height.coerceAtLeast(1)
        }

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 4. Draw base bitmap with selected ColorMatrix filter
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (filterPreset != ImageFilterPreset.ORIGINAL) {
                colorFilter = ColorMatrixColorFilter(filterPreset.toColorMatrix())
            }
        }
        canvas.drawBitmap(
            cropped,
            null,
            RectF(0f, 0f, outW.toFloat(), outH.toFloat()),
            imagePaint
        )

        // 5. Draw normalized freehand brush strokes
        val minCanvasSide = min(outW, outH).toFloat()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        for (stroke in strokes) {
            if (stroke.normalizedPoints.isEmpty()) continue
            strokePaint.color = stroke.colorArgb
            strokePaint.strokeWidth = (stroke.strokeWidthRatio * minCanvasSide).coerceAtLeast(3f)
            if (stroke.normalizedPoints.size == 1) {
                val pt = stroke.normalizedPoints.first()
                canvas.drawPoint(pt.x * outW, pt.y * outH, strokePaint)
            } else {
                val path = Path()
                val first = stroke.normalizedPoints.first()
                path.moveTo(first.x * outW, first.y * outH)
                for (i in 1 until stroke.normalizedPoints.size) {
                    val prev = stroke.normalizedPoints[i - 1]
                    val curr = stroke.normalizedPoints[i]
                    val midX = ((prev.x + curr.x) * 0.5f) * outW
                    val midY = ((prev.y + curr.y) * 0.5f) * outH
                    path.quadTo(prev.x * outW, prev.y * outH, midX, midY)
                }
                val last = stroke.normalizedPoints.last()
                path.lineTo(last.x * outW, last.y * outH)
                canvas.drawPath(path, strokePaint)
            }
        }

        // 6. Draw styled Text Overlay pill if present
        val cleanOverlay = overlayText.trim()
        if (cleanOverlay.isNotEmpty()) {
            val textSizePx = (minCanvasSide * 0.062f).coerceIn(22f, 92f)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlayTextColorArgb
                textSize = textSizePx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, Color.argb(170, 0, 0, 0))
            }
            val centerX = (overlayNormalizedPos.x.coerceIn(0.12f, 0.88f)) * outW
            val centerY = (overlayNormalizedPos.y.coerceIn(0.12f, 0.88f)) * outH
            val textWidth = textPaint.measureText(cleanOverlay)
            val padH = textSizePx * 0.55f
            val padV = textSizePx * 0.36f
            val pillRect = RectF(
                centerX - textWidth / 2f - padH,
                centerY - textSizePx - padV * 0.4f,
                centerX + textWidth / 2f + padH,
                centerY + padV
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(145, 10, 18, 22)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(pillRect, textSizePx * 0.45f, textSizePx * 0.45f, bgPaint)
            canvas.drawText(cleanOverlay, centerX, centerY, textPaint)
        }

        return output
    }

    /**
     * Produces an EditedImagePayload ready for E2EE transmission:
     * - Standard mode: 864px JPEG inline.
     * - HD mode: 1600px 92%-quality crisp JPEG! If > 260 KB, sends an instant inline preview
     *   plus full lossless-clarity HD bytes via chunked E2EE storage so HD is never degraded.
     */
    fun prepareEditedImageForSend(
        source: Bitmap,
        rotationDegrees: Int,
        cropPreset: CropPreset,
        filterPreset: ImageFilterPreset,
        strokes: List<DrawStroke>,
        overlayText: String,
        overlayTextColorArgb: Int,
        overlayNormalizedPos: Offset,
        isHd: Boolean
    ): EditedImagePayload {
        val targetMax = if (isHd) HD_MAX_PX else STANDARD_MAX_PX
        val rendered = renderEditedBitmap(
            source = source,
            rotationDegrees = rotationDegrees,
            cropPreset = cropPreset,
            filterPreset = filterPreset,
            strokes = strokes,
            overlayText = overlayText,
            overlayTextColorArgb = overlayTextColorArgb,
            overlayNormalizedPos = overlayNormalizedPos,
            maxDimensionPx = targetMax
        )

        return if (isHd) {
            val hdOut = ByteArrayOutputStream()
            rendered.compress(Bitmap.CompressFormat.JPEG, 92, hdOut)
            val hdBytes = hdOut.toByteArray()
            if (hdBytes.size <= INLINE_MAX_BYTES) {
                EditedImagePayload(
                    inlinePreviewBase64 = Base64.encodeToString(hdBytes, Base64.NO_WRAP),
                    hdChunkBytes = null,
                    isHd = true,
                    width = rendered.width,
                    height = rendered.height
                )
            } else {
                val previewB64 = encodeBitmapToBase64UnderLimit(
                    bitmap = rendered,
                    maxDimPx = 900,
                    initialQuality = 74,
                    maxBytes = 190_000
                )
                EditedImagePayload(
                    inlinePreviewBase64 = previewB64,
                    hdChunkBytes = hdBytes,
                    isHd = true,
                    width = rendered.width,
                    height = rendered.height
                )
            }
        } else {
            val stdB64 = encodeBitmapToBase64UnderLimit(
                bitmap = rendered,
                maxDimPx = STANDARD_MAX_PX,
                initialQuality = 75,
                maxBytes = 220_000
            )
            EditedImagePayload(
                inlinePreviewBase64 = stdB64,
                hdChunkBytes = null,
                isHd = false,
                width = rendered.width,
                height = rendered.height
            )
        }
    }

    private fun encodeBitmapToBase64UnderLimit(
        bitmap: Bitmap,
        maxDimPx: Int,
        initialQuality: Int,
        maxBytes: Int
    ): String {
        val maxSide = max(bitmap.width, bitmap.height)
        val scaled = if (maxSide > maxDimPx) {
            val ratio = maxDimPx.toFloat() / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        var quality = initialQuality
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > maxBytes && quality >= 28)

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Extracts poster frame thumbnail, duration, dimensions, and byte size for a selected video URI.
     */
    fun extractVideoMetadata(context: Context, uri: Uri): VideoMetadataSummary {
        var durationMs = 0L
        var width = 0
        var height = 0
        var thumbBase64: String? = null
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val sizeBytes = queryUriFileSize(context, uri)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val frame = retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame != null) {
                thumbBase64 = encodeBitmapToBase64UnderLimit(
                    bitmap = frame,
                    maxDimPx = 720,
                    initialQuality = 72,
                    maxBytes = 150_000
                )
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return VideoMetadataSummary(
            thumbnailBase64 = thumbBase64,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            mimeType = mime
        )
    }

    fun queryUriFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Reads raw binary bytes from a content Uri (for `.gif` animations or MP4 HD videos) up to `maxBytes`.
     */
    fun readUriBytesLimited(
        context: Context,
        uri: Uri,
        maxBytes: Int = MAX_ENCRYPTED_MEDIA_BYTES
    ): Result<ByteArray> {
        return try {
            val declaredSize = queryUriFileSize(context, uri)
            if (declaredSize > maxBytes) {
                val mb = String.format("%.1f", declaredSize / (1024f * 1024f))
                return Result.failure(
                    IllegalArgumentException("Selected file is $mb MB. Maximum encrypted HD vault transfer is 14 MB.")
                )
            }
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Could not open selected media file."))
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            input.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        return Result.failure(
                            IllegalArgumentException("Selected video exceeds the 14 MB encrypted HD limit. Choose a shorter clip.")
                        )
                    }
                    out.write(buffer, 0, read)
                }
            }
            Result.success(out.toByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun bytesToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64ToBytes(base64: String?): ByteArray? {
        if (base64.isNullOrBlank()) return null
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun base64ToBitmap(base64: String?): Bitmap? {
        val bytes = base64ToBytes(base64) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun isAnimatedGifBytes(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.size < 6) return false
        val header = String(bytes, 0, 6, Charsets.US_ASCII)
        return header == "GIF87a" || header == "GIF89a"
    }

    fun formatDurationMillis(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return "%d:%02d".format(mins, secs)
    }
}
