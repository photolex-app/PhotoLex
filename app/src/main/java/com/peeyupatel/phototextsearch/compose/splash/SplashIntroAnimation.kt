package com.peeyupatel.phototextsearch.compose.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.ui.theme.WordmarkFont

/**
 * Total runtime of one play, in ms -- matches the HTML/CSS mockup's timeline (5 cards popping in,
 * flipping to reveal a real extracted word, flying it to the search bar) with the final fade
 * folded in at the end instead of the mockup's separate loop-reset pause.
 */
private const val TOTAL_MS = 5000
private const val FADE_START_MS = 4600f
private const val FADE_END_MS = 5000f

private val NavySplashBg = Color(0xFF101B33)
private val AmberAccent = Color(0xFFFFA726)
private val CreamText = Color(0xFFF2EFE9)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun stepFraction(elapsed: Float, start: Float, end: Float): Float {
    if (end <= start) return if (elapsed >= end) 1f else 0f
    return clamp01((elapsed - start) / (end - start))
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * clamp01(t)

private data class CardTiming(
    val popStart: Float,
    val popOvershoot: Float,
    val popSettle: Float,
    val anticipation: Float,
    val flipped: Float,
    val holdEnd: Float,
    val flyEnd: Float
)

private data class CardSpec(
    val xDp: Float,
    val yDp: Float,
    val wDp: Float,
    val hDp: Float,
    val rotationDeg: Float,
    val bobPeriodMs: Int,
    val timing: CardTiming,
    val word: String,
    val wordTargetDxDp: Float,
    val wordTargetDyDp: Float,
    val front: @Composable BoxScope.() -> Unit
)

/**
 * The launch-animation "movie": five flat, graphic photo cards (receipt, sticky note, sign,
 * ID card, bilingual English/Hindi note -- the last two showing off real differentiators,
 * structured-field OCR and Devanagari support) pop onto navy in turn, each flips face-down like
 * it's being read, reveals the real word it "contains," holds long enough to be legible, then
 * flies that word up toward where the search bar will be revealed. Ends by fading the whole
 * scene away, letting the real app underneath (already composed and waiting) show through --
 * deliberately not drawing a second fake wordmark/search bar on top of the real ones, since an
 * earlier draft of this that did caused a visible double-text mismatch during the crossfade.
 */
@Composable
fun SplashIntroSequence(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(TOTAL_MS, easing = LinearEasing))
        onFinished()
    }
    val elapsed = progress.value * TOTAL_MS
    val sceneAlpha = 1f - stepFraction(elapsed, FADE_START_MS, FADE_END_MS)

    val cards = remember { buildCardSpecs() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = sceneAlpha }
            // The SplashScreenView's own backdrop is transparent outside the icon by this point
            // (the navy windowSplashScreenBackground only paints during the earlier, purely
            // system-drawn cold-start phase) -- without this, the app's own window background
            // shows through instead, which is cream, and the card art/wordmark here is styled
            // for a dark backdrop (cream/amber text) so it'd be nearly invisible on cream.
            .background(NavySplashBg)
    ) {
        val brandAlpha = lerp(0f, 1f, stepFraction(elapsed, 0f, 300f))
        Row(
            modifier = Modifier
                .padding(start = 20.dp, top = 40.dp)
                .graphicsLayer { alpha = brandAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_photolex),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
            Row {
                androidx.compose.material3.Text(
                    text = "Photo",
                    fontFamily = WordmarkFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = CreamText
                )
                androidx.compose.material3.Text(
                    text = "Lex",
                    fontFamily = WordmarkFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = AmberAccent
                )
            }
        }

        cards.forEach { spec -> FlipCard(spec, elapsed) }
    }
}

@Composable
private fun FlipCard(spec: CardSpec, elapsed: Float) {
    val t = spec.timing

    val popScale = when {
        elapsed < t.popStart -> 0.6f
        elapsed < t.popOvershoot -> lerp(0.6f, 1.12f, stepFraction(elapsed, t.popStart, t.popOvershoot))
        elapsed < t.popSettle -> lerp(1.12f, 1f, stepFraction(elapsed, t.popOvershoot, t.popSettle))
        else -> 1f
    }
    val popAlpha = lerp(0f, 1f, stepFraction(elapsed, t.popStart, t.popOvershoot))

    val density = LocalDensity.current.density
    val infinite = rememberInfiniteTransition(label = "cardBob")
    val bobPx by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -5f * density,
        animationSpec = infiniteRepeatable(
            animation = tween(spec.bobPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobPx"
    )

    val rotationY = when {
        elapsed < t.anticipation - 100f -> 0f
        elapsed < t.anticipation -> lerp(0f, -8f, stepFraction(elapsed, t.anticipation - 100f, t.anticipation))
        elapsed < t.flipped -> lerp(-8f, 180f, stepFraction(elapsed, t.anticipation, t.flipped))
        elapsed < t.holdEnd -> 180f
        elapsed < t.flyEnd -> lerp(180f, 360f, stepFraction(elapsed, t.holdEnd, t.flyEnd))
        else -> 360f
    }
    val showFront = rotationY <= 90f || rotationY >= 270f

    Box(
        modifier = Modifier
            .padding(start = spec.xDp.dp, top = spec.yDp.dp)
            .size(spec.wDp.dp, spec.hDp.dp)
            .graphicsLayer {
                this.translationY = bobPx
                this.alpha = popAlpha
                this.scaleX = popScale
                this.scaleY = popScale
                this.rotationY = rotationY
                this.rotationZ = spec.rotationDeg
                cameraDistance = 16f * density
            }
    ) {
        if (showFront) {
            Box(modifier = Modifier.fillMaxSize(), content = spec.front)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.rotationY = 180f }
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmberAccent)
            )
        }
    }

    // the word that lifts off this card and flies to the search bar -- the only place its text
    // is ever drawn, so there's no duplicate-wordmark ghosting during the handoff.
    val wordAlpha = when {
        elapsed < t.flipped -> 0f
        elapsed < t.flipped + 100f -> lerp(0f, 1f, stepFraction(elapsed, t.flipped, t.flipped + 100f))
        elapsed < t.holdEnd -> 1f
        elapsed < t.flyEnd -> lerp(1f, 0f, stepFraction(elapsed, t.holdEnd, t.flyEnd))
        else -> 0f
    }
    val flyFraction = stepFraction(elapsed, t.holdEnd, t.flyEnd)
    val wordScale = lerp(1f, 0.4f, flyFraction)
    val wordDx = lerp(0f, spec.wordTargetDxDp, flyFraction)
    val wordDy = lerp(0f, spec.wordTargetDyDp, flyFraction)

    if (wordAlpha > 0f) {
        Box(
            modifier = Modifier
                .padding(start = spec.xDp.dp, top = spec.yDp.dp)
                .size(spec.wDp.dp, spec.hDp.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = spec.word,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = CreamText,
                modifier = Modifier.graphicsLayer {
                    this.alpha = wordAlpha
                    this.scaleX = wordScale
                    this.scaleY = wordScale
                    this.translationX = wordDx.dp.toPx()
                    this.translationY = wordDy.dp.toPx()
                }
            )
        }
    }
}

private fun buildCardSpecs(): List<CardSpec> = listOf(
    // Card A: receipt
    CardSpec(
        xDp = 16f, yDp = 190f, wDp = 90f, hDp = 116f, rotationDeg = -8f, bobPeriodMs = 3200,
        timing = CardTiming(0f, 300f, 450f, 800f, 1200f, 1500f, 2000f),
        word = "RECEIPT", wordTargetDxDp = 150f, wordTargetDyDp = -135f,
        front = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFBF9F4))
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(top = 12.dp, start = 10.dp, end = 10.dp)
                ) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .fillMaxWidth(if (i == 1) 0.55f else 0.7f)
                                .padding(top = 8.dp)
                                .height(4.dp)
                                .background(Color(0xFFD8D2C4), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    ),
    // Card B: sticky note
    CardSpec(
        xDp = 128f, yDp = 164f, wDp = 100f, hDp = 90f, rotationDeg = 3f, bobPeriodMs = 3600,
        timing = CardTiming(120f, 420f, 570f, 1300f, 1600f, 1900f, 2400f),
        word = "MEETING", wordTargetDxDp = 42f, wordTargetDyDp = -103f,
        front = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFE9A8))
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(top = 18.dp, start = 14.dp, end = 14.dp)
                ) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .fillMaxWidth(if (i == 2) 0.4f else 0.65f)
                                .padding(top = 6.dp)
                                .height(4.dp)
                                .background(Color(0xFFC9A94F), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    ),
    // Card C: sign / menu board
    CardSpec(
        xDp = 240f, yDp = 210f, wDp = 90f, hDp = 108f, rotationDeg = 8f, bobPeriodMs = 4000,
        timing = CardTiming(240f, 540f, 690f, 1800f, 2000f, 2300f, 2800f),
        word = "MENU", wordTargetDxDp = -50f, wordTargetDyDp = -150f,
        front = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF23264A)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(AmberAccent, androidx.compose.foundation.shape.GenericShape { size, _ ->
                            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height * 0.8f))
                        })
                )
            }
        }
    ),
    // Card D: ID card (landscape, avatar + field lines)
    CardSpec(
        xDp = 36f, yDp = 378f, wDp = 118f, hDp = 80f, rotationDeg = -4f, bobPeriodMs = 3400,
        timing = CardTiming(360f, 660f, 810f, 2300f, 2700f, 3000f, 3500f),
        word = "ID CARD", wordTargetDxDp = 115f, wordTargetDyDp = -310f,
        front = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF2F6FB))
            ) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(AmberAccent))
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9CC2E6))
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    androidx.compose.foundation.layout.Column {
                        repeat(3) { i ->
                            Box(
                                Modifier
                                    .fillMaxWidth(if (i == 0) 0.8f else 0.6f)
                                    .padding(top = 4.dp)
                                    .height(4.dp)
                                    .background(
                                        if (i == 0) Color(0xFF3A3F63) else Color(0xFF9AA7BD),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    ),
    // Card E: bilingual English/Hindi note -- real Devanagari OCR differentiator
    CardSpec(
        xDp = 196f, yDp = 366f, wDp = 108f, hDp = 100f, rotationDeg = 5f, bobPeriodMs = 3800,
        timing = CardTiming(480f, 780f, 930f, 2800f, 3200f, 3500f, 4000f),
        word = "नमस्ते", wordTargetDxDp = -25f, wordTargetDyDp = -300f,
        front = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFBF9F4)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Text(
                        text = "Hello",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF3A3F63)
                    )
                    androidx.compose.material3.Text(
                        text = "नमस्ते",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFB5651D)
                    )
                }
            }
        }
    )
)
