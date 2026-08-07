package com.peeyupatel.phototextsearch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Consolidated corner-radius scale. New/touched UI should reuse these instead of
// inventing a new radius, so cards/dialogs/pills read as one consistent shape language.
object AppShapes {
    val card = RoundedCornerShape(16.dp)
    val dialog = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}
