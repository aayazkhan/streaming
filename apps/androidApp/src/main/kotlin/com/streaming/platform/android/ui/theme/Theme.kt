package com.streaming.platform.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFE50914)
val BrandDark = Color(0xFFB0060F)
val Surface = Color(0xFF0B0B0F)
val SurfaceRaised = Color(0xFF181820)

private val StreamingColorScheme = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = BrandDark,
    background = Surface,
    onBackground = Color.White,
    surface = SurfaceRaised,
    onSurface = Color.White,
    error = Color(0xFFCF6679),
)

@Composable
fun StreamingTheme(content: @Composable () -> Unit) {
    // Dark-first, matching apps/webApp/apps/adminApp — isSystemInDarkTheme() is read but this app
    // has no light palette defined yet, same single-look commitment the web clients made.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = StreamingColorScheme, content = content)
}
