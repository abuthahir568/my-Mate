package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.SystemClock
import android.widget.ImageView
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class PresetAnimatedGif(
    val id: String,
    val title: String,
    val emoji: String,
    val subtitle: String,
    val primaryColor: Int,
    val secondaryColor: Int
)

/**
 * Pure Android/Kotlin GIF89a LZW multi-frame encoder & native Animated GIF Composable player.
 *
 * Allows users to:
 * 1. Pick ANY `.gif` file from their phone and play all animation frames natively in chat bubbles.
 * 2. Generate & send real multi-frame `GIF89a` animated stickers/reactions directly in the app!
 */
object AnimatedGifCodec {

    val PRESET_REACTIONS = listOf(
        PresetAnimatedGif("love_pulse", "Love You", "❤️", "Beating heart", 0xFF880E4F.toInt(), 0xFFD81B60.toInt()),
        PresetAnimatedGif("fire_hype", "So Lit!", "🔥", "Blazing spark", 0xFFBF360C.toInt(), 0xFFFF8F00.toInt()),
        PresetAnimatedGif("party_pop", "Celebrate", "🎉", "Confetti burst", 0xFF004D40.toInt(), 0xFF00BFA5.toInt()),
        PresetAnimatedGif("miss_you", "Miss You", "🌙", "Starlight glow", 0xFF1A237E.toInt(), 0xFF3949AB.toInt()),
        PresetAnimatedGif("warm_hug", "Big Hug", "🤗", "Cozy embrace", 0xFF4E342E.toInt(), 0xFFFB8C00.toInt()),
        PresetAnimatedGif("laugh_joy", "Haha!!", "😂", "Tears of joy", 0xFF01579B.toInt(), 0xFF00ACC1.toInt()),
        PresetAnimatedGif("cheers_gold", "Cheers!", "✨", "Golden shimmer", 0xFF1B5E20.toInt(), 0xFFD4AF37.toInt()),
        PresetAnimatedGif("vault_lock", "Just Us 2", "🔒", "Encrypted bond", 0xFF075E54.toInt(), 0xFF128C7E.toInt())
    )

    /**
     * Generates a genuine multi-frame looping `GIF89a` binary animation (`ByteArray`) for a preset or custom caption.
     */
    fun generateAnimatedReactionGifBytes(
        emoji: String,
        caption: String,
        startColorArgb: Int = 0xFF075E54.toInt(),
        endColorArgb: Int = 0xFF128C7E.toInt(),
        width: Int = 220,
        height: Int = 164,
        frameCount: Int = 8,
        delayCentiseconds: Int = 11
    ): ByteArray {
        val frames = ArrayList<Bitmap>(frameCount)
        val cleanText = caption.trim().take(22).ifBlank { "My Mate" }

        for (i in 0 until frameCount) {
            val phase = (i.toFloat() / frameCount.toFloat()) * (2f * PI.toFloat())
            val pulse = 1f + 0.16f * sin(phase)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    startColorArgb,
                    endColorArgb,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Animated orbiting sparkle particles
            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 235, 160)
                style = Paint.Style.FILL
            }
            for (p in 0 until 6) {
                val angle = phase + (p * PI.toFloat() / 3f)
                val rx = width * 0.36f * cos(angle) + width * 0.5f
                val ry = height * 0.32f * sin(angle) + height * 0.44f
                val radius = 3.5f + 2f * sin(phase + p)
                canvas.drawCircle(rx, ry, radius.coerceAtLeast(2f), particlePaint)
            }

            // Pulsing central emoji
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 48f * pulse
                color = Color.WHITE
            }
            canvas.drawText(emoji, width * 0.5f, height * 0.52f, emojiPaint)

            // Animated caption banner at bottom
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 8, 16, 20)
            }
            canvas.drawRoundRect(
                16f,
                height - 42f,
                width - 16f,
                height - 10f,
                16f,
                16f,
                bannerPaint
            )

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cleanText, width * 0.5f, height - 20f, textPaint)
            frames.add(bmp)
        }

        return encodeBitmapsToGif89a(frames, delayCentiseconds)
    }

    /**
     * Encodes a sequence of Bitmaps into a valid looping GIF89a binary file using a 3-3-2 256-color
     * global palette and standard variable-width GIF LZW compression.
     */
    fun encodeBitmapsToGif89a(frames: List<Bitmap>, delayCentiseconds: Int = 12): ByteArray {
        require(frames.isNotEmpty()) { "At least 1 frame required for GIF89a" }
        val w = frames.first().width
        val h = frames.first().height
        val out = ByteArrayOutputStream(32 * 1024)

        // 1. GIF89a Header
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 2. Logical Screen Descriptor
        writeShortLE(out, w)
        writeShortLE(out, h)
        out.write(0xF7) // Global Color Table Flag (1), Color Res (7), Sort (0), GCT Size 256 (7)
        out.write(0x00) // Background color index
        out.write(0x00) // Pixel aspect ratio

        // 3. Global Color Table (256 entries: 3 bits R, 3 bits G, 2 bits B)
        for (idx in 0 until 256) {
            val r = (((idx shr 5) and 0x07) * 255) / 7
            val g = (((idx shr 2) and 0x07) * 255) / 7
            val b = ((idx and 0x03) * 255) / 3
            out.write(r)
            out.write(g)
            out.write(b)
        }

        // 4. Netscape 2.0 Infinite Loop Extension
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))

        // 5. Frames
        val pixels = IntArray(w * h)
        val indexed = ByteArray(w * h)
        for (bmp in frames) {
            val frameBmp = if (bmp.width == w && bmp.height == h) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
            frameBmp.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r3 = (Color.red(c) * 7 + 127) / 255
                val g3 = (Color.green(c) * 7 + 127) / 255
                val b2 = (Color.blue(c) * 3 + 127) / 255
                indexed[i] = ((r3 shl 5) or (g3 shl 2) or b2).toByte()
            }

            // Graphic Control Extension
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
            writeShortLE(out, delayCentiseconds.coerceIn(2, 1000))
            out.write(0x00) // Transparent color index (unused)
            out.write(0x00) // Block terminator

            // Image Descriptor
            out.write(0x2C)
            writeShortLE(out, 0)
            writeShortLE(out, 0)
            writeShortLE(out, w)
            writeShortLE(out, h)
            out.write(0x00) // No local color table

            // LZW Image Data
            lzwEncodeFrame(indexed, out)
        }

        // 6. GIF Trailer
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun lzwEncodeFrame(indexedPixels: ByteArray, out: ByteArrayOutputStream) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val endCode = clearCode + 1 // 257
        var codeSize = minCodeSize + 1 // 9
        var nextCode = endCode + 1 // 258

        // Open-addressing hash table for (prefix shl 8) | suffix -> code
        val tableKeys = IntArray(8192) { -1 }
        val tableValues = IntArray(8192)

        fun resetDict() {
            tableKeys.fill(-1)
            codeSize = minCodeSize + 1
            nextCode = endCode + 1
        }

        val subBlock = ByteArray(255)
        var subBlockLen = 0
        var bitBuffer = 0
        var bitCount = 0

        fun flushByte(b: Int) {
            subBlock[subBlockLen++] = b.toByte()
            if (subBlockLen == 255) {
                out.write(255)
                out.write(subBlock, 0, 255)
                subBlockLen = 0
            }
        }

        fun writeCode(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                flushByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        writeCode(clearCode)
        var prefix = indexedPixels[0].toInt() and 0xFF

        for (i in 1 until indexedPixels.size) {
            val suffix = indexedPixels[i].toInt() and 0xFF
            val composite = (prefix shl 8) or suffix
            var slot = ((composite * 2654435761L).toInt() ushr 19) and 8191
            var foundCode = -1
            while (true) {
                val existingKey = tableKeys[slot]
                if (existingKey == -1) break
                if (existingKey == composite) {
                    foundCode = tableValues[slot]
                    break
                }
                slot = (slot + 1) and 8191
            }

            if (foundCode != -1) {
                prefix = foundCode
            } else {
                writeCode(prefix)
                if (nextCode < 4096) {
                    tableKeys[slot] = composite
                    tableValues[slot] = nextCode
                    if (nextCode == (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                    nextCode++
                } else {
                    writeCode(clearCode)
                    resetDict()
                }
                prefix = suffix
            }
        }

        writeCode(prefix)
        writeCode(endCode)
        if (bitCount > 0) {
            flushByte(bitBuffer and 0xFF)
        }
        if (subBlockLen > 0) {
            out.write(subBlockLen)
            out.write(subBlock, 0, subBlockLen)
        }
        out.write(0x00) // Sub-block terminator
    }
}

/**
 * Renders animated `.gif` bytes smoothly inside Jetpack Compose across all Android API levels:
 * - API 28+ (`Build.VERSION_CODES.P`): Hardware-accelerated `ImageDecoder` + `AnimatedImageDrawable`
 * - API 24–27 fallback: Frame-synced `android.graphics.Movie` Canvas renderer
 */
@Composable
fun AnimatedGifView(
    gifBytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cropCenter: Boolean = true
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = if (cropCenter) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    this.contentDescription = contentDescription
                }
            },
            update = { imageView ->
                imageView.scaleType = if (cropCenter) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                if (imageView.tag != gifBytes.size.xor(gifBytes.firstOrNull()?.toInt() ?: 0)) {
                    imageView.tag = gifBytes.size.xor(gifBytes.firstOrNull()?.toInt() ?: 0)
                    try {
                        val source = ImageDecoder.createSource(ByteBuffer.wrap(gifBytes))
                        val drawable = ImageDecoder.decodeDrawable(source)
                        imageView.setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        )
    } else {
        @Suppress("DEPRECATION")
        val movie = remember(gifBytes) { Movie.decodeByteArray(gifBytes, 0, gifBytes.size) }
        var frameTick by remember { mutableLongStateOf(0L) }
        val startRealtime = remember(gifBytes) { SystemClock.uptimeMillis() }

        LaunchedEffect(movie) {
            if (movie != null && movie.duration() > 0) {
                while (true) {
                    withFrameMillis {
                        frameTick = SystemClock.uptimeMillis() - startRealtime
                    }
                }
            }
        }

        ComposeCanvas(modifier = modifier) {
            if (movie != null && movie.width() > 0 && movie.height() > 0) {
                val dur = movie.duration().coerceAtLeast(1000)
                movie.setTime((frameTick % dur).toInt())
                val scaleX = size.width / movie.width().toFloat()
                val scaleY = size.height / movie.height().toFloat()
                val scale = if (cropCenter) kotlin.math.max(scaleX, scaleY) else min(scaleX, scaleY)
                val dx = (size.width - movie.width() * scale) * 0.5f
                val dy = (size.height - movie.height() * scale) * 0.5f
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(dx, dy)
                    canvas.nativeCanvas.scale(scale, scale)
                    movie.draw(canvas.nativeCanvas, 0f, 0f)
                    canvas.nativeCanvas.restore()
                }
            }
        }
    }
}
