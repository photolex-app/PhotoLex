package com.peeyupatel.phototextsearch.ui.theme

import androidx.compose.ui.graphics.Color

// PhotoLex brand palette — matches the launcher icon (amber magnifying glass on navy)
// so the app's visual identity is consistent from the home screen icon through the UI.

// Light theme
val NavyPrimary = Color(0xFF101B33) // exact icon background navy
val OnNavyPrimary = Color(0xFFFFFFFF)
val MutedNavySecondary = Color(0xFF48597A)
val OnMutedNavySecondary = Color(0xFFFFFFFF)
val AmberTertiary = Color(0xFFFFA726) // exact icon accent amber
val OnAmberTertiary = Color(0xFF101B33)
val WarmBackground = Color(0xFFFAF7F2)
val OnWarmBackground = Color(0xFF2A2A2E)
val WarmSurface = Color(0xFFFFFDF9)
val OnWarmSurface = Color(0xFF2A2A2E)
val WarmSurfaceVariant = Color(0xFFEDE7DD)
val OnWarmSurfaceVariant = Color(0xFF5B5B5F)
val WarmOutline = Color(0xFF8C877D)
val WarmOutlineVariant = Color(0xFFD8D2C6)
val WarmScrim = Color(0xFF000000)

// Light theme "container" + surface tonal roles -- these are the ones Material3 quietly fills
// in with its own baseline purple if you only specify primary/secondary/tertiary/surface, so
// they need explicit brand-derived values or purple leaks through in sliders, floating bars,
// bottom sheets, and cards (all of which read one of these roles by default).
val NavyPrimaryContainer = Color(0xFFDCE3F0)
val OnNavyPrimaryContainer = Color(0xFF101B33)
val MutedNavySecondaryContainer = Color(0xFFE4E8F0)
val OnMutedNavySecondaryContainer = Color(0xFF2A3A56)
val AmberTertiaryContainer = Color(0xFFFFE8C2)
val OnAmberTertiaryContainer = Color(0xFF4A2E00)
val WarmSurfaceDim = Color(0xFFE3DED3)
val WarmSurfaceBright = Color(0xFFFFFDF9)
val WarmSurfaceContainerLowest = Color(0xFFFFFFFF)
val WarmSurfaceContainerLow = Color(0xFFFAF5EC)
val WarmSurfaceContainer = Color(0xFFF3EDE0)
val WarmSurfaceContainerHigh = Color(0xFFEDE7DA)
val WarmSurfaceContainerHighest = Color(0xFFE7E0D2)

// Dark theme — amber becomes primary (navy would disappear into a dark background),
// giving the two modes a deliberate swap in emphasis rather than just a dimmed light theme.
val AmberPrimaryDark = Color(0xFFFFA726)
val OnAmberPrimaryDark = Color(0xFF101B33)
val LightNavySecondaryDark = Color(0xFFB8C4D9)
val OnLightNavySecondaryDark = Color(0xFF101B33)
val LightAmberTertiaryDark = Color(0xFFFFC966)
val OnLightAmberTertiaryDark = Color(0xFF101B33)
val CharcoalBackgroundDark = Color(0xFF14161C)
val OnCharcoalBackgroundDark = Color(0xFFF2EFE9)
val CharcoalSurfaceDark = Color(0xFF1C1F27)
val OnCharcoalSurfaceDark = Color(0xFFF2EFE9)
val CharcoalSurfaceVariantDark = Color(0xFF2B2E36)
val OnCharcoalSurfaceVariantDark = Color(0xFFC7C2B8)
val WarmOutlineDark = Color(0xFF6E6A63)
val WarmOutlineVariantDark = Color(0xFF44413B)

// Dark theme container + surface tonal roles -- same reasoning as the light-theme block above.
val AmberPrimaryContainerDark = Color(0xFF5C4200)
val OnAmberPrimaryContainerDark = Color(0xFFFFDDA6)
val LightNavySecondaryContainerDark = Color(0xFF35414F)
val OnLightNavySecondaryContainerDark = Color(0xFFB8C4D9)
val LightAmberTertiaryContainerDark = Color(0xFF4A3712)
val OnLightAmberTertiaryContainerDark = Color(0xFFFFC966)
val CharcoalSurfaceDimDark = Color(0xFF14161C)
val CharcoalSurfaceBrightDark = Color(0xFF383B44)
val CharcoalSurfaceContainerLowestDark = Color(0xFF0F1116)
val CharcoalSurfaceContainerLowDark = Color(0xFF191C23)
val CharcoalSurfaceContainerDark = Color(0xFF1D2028)
val CharcoalSurfaceContainerHighDark = Color(0xFF272A33)
val CharcoalSurfaceContainerHighestDark = Color(0xFF32353E)
