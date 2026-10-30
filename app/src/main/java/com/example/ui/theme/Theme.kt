package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class MateExtendedColors(
    val headerBackground: Color,
    val headerContent: Color,
    val chatWallBackground: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val bubbleText: Color,
    val timestampText: Color,
    val blueTick: Color,
    val unreadBadge: Color,
    val e2eeBannerBg: Color,
    val e2eeBannerText: Color
)

val LocalMateColors = staticCompositionLocalOf {
    MateExtendedColors(
        headerBackground = WhatsAppTealDark,
        headerContent = Color.White,
        chatWallBackground = LightBackground,
        outgoingBubble = LightOutgoingBubble,
        incomingBubble = LightIncomingBubble,
        bubbleText = LightOnSurface,
        timestampText = LightSecondaryText,
        blueTick = WhatsAppBlueTick,
        unreadBadge = WhatsAppEmeraldAccent,
        e2eeBannerBg = Color(0xFFFFF8E1),
        e2eeBannerText = Color(0xFF5D4037)
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppEmeraldAccent,
    onPrimary = Color(0xFF002116),
    primaryContainer = Color(0xFF005C4B),
    onPrimaryContainer = Color(0xFFD9FDD3),
    secondary = WhatsAppTealPrimary,
    onSecondary = Color.White,
    tertiary = SanctuaryGold,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    error = CallDeclineRed
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppTealDark,
    onPrimary = Color.White,
    primaryContainer = LightOutgoingBubble,
    onPrimaryContainer = Color(0xFF073B33),
    secondary = WhatsAppTealPrimary,
    onSecondary = Color.White,
    tertiary = SanctuaryGold,
    background = Color(0xFFF7F8FA),
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    error = CallDeclineRed
)

@Composable
fun MyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val extendedColors = if (darkTheme) {
        MateExtendedColors(
            headerBackground = DarkSurface,
            headerContent = DarkOnSurface,
            chatWallBackground = DarkBackground,
            outgoingBubble = DarkOutgoingBubble,
            incomingBubble = DarkIncomingBubble,
            bubbleText = DarkOnSurface,
            timestampText = DarkSecondaryText,
            blueTick = WhatsAppBlueTick,
            unreadBadge = WhatsAppEmeraldAccent,
            e2eeBannerBg = Color(0xFF182229),
            e2eeBannerText = Color(0xFFFFD279)
        )
    } else {
        MateExtendedColors(
            headerBackground = WhatsAppTealDark,
            headerContent = Color.White,
            chatWallBackground = LightBackground,
            outgoingBubble = LightOutgoingBubble,
            incomingBubble = LightIncomingBubble,
            bubbleText = LightOnSurface,
            timestampText = LightSecondaryText,
            blueTick = WhatsAppBlueTick,
            unreadBadge = WhatsAppEmeraldAccent,
            e2eeBannerBg = Color(0xFFFFF5C4),
            e2eeBannerText = Color(0xFF54656F)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Header is dark in both Light (WhatsAppTealDark) and Dark (DarkSurface) modes
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMateColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
