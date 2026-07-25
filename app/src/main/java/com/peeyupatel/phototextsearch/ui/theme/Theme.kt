package com.peeyupatel.phototextsearch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = OnAmberPrimaryDark,
    secondary = LightNavySecondaryDark,
    onSecondary = OnLightNavySecondaryDark,
    tertiary = LightAmberTertiaryDark,
    onTertiary = OnLightAmberTertiaryDark,
    background = CharcoalBackgroundDark,
    onBackground = OnCharcoalBackgroundDark,
    surface = CharcoalSurfaceDark,
    onSurface = OnCharcoalSurfaceDark,
    surfaceVariant = CharcoalSurfaceVariantDark,
    onSurfaceVariant = OnCharcoalSurfaceVariantDark,
    outline = WarmOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = OnNavyPrimary,
    secondary = MutedNavySecondary,
    onSecondary = OnMutedNavySecondary,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    background = WarmBackground,
    onBackground = OnWarmBackground,
    surface = WarmSurface,
    onSurface = OnWarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OnWarmSurfaceVariant,
    outline = WarmOutline
)

@Composable
fun GalleryTheme(
    darkTheme: Int = 0,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when(darkTheme) {
        0 -> {
        	val systemInDarkTheme = isSystemInDarkTheme()

			if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				if (systemInDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
			} else {
				if (systemInDarkTheme) DarkColorScheme else LightColorScheme
			}
        }


        1 -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else DarkColorScheme
        2 -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else LightColorScheme

        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


