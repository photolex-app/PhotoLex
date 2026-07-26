package com.peeyupatel.phototextsearch.compose.app_bars

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.peeyupatel.phototextsearch.ui.theme.WordmarkFont
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.compose.SelectViewTopBarLeftButtons
import com.peeyupatel.phototextsearch.compose.SelectViewTopBarRightButtons
import com.peeyupatel.phototextsearch.compose.dialogs.AlbumAddChoiceDialog
import com.peeyupatel.phototextsearch.compose.dialogs.ConfirmationDialog
import com.peeyupatel.phototextsearch.compose.grids.MoveCopyAlbumListView
import com.peeyupatel.phototextsearch.datastore.BottomBarTab
import com.peeyupatel.phototextsearch.datastore.DefaultTabs
import com.peeyupatel.phototextsearch.datastore.Permissions
import com.peeyupatel.phototextsearch.helpers.GetPermissionAndRun
import com.peeyupatel.phototextsearch.helpers.setTrashedOnPhotoList
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import android.util.Log

private const val TAG = "BOTTOM_BAR_ANIMATION"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppTopBar(
    alternate: Boolean,
    showDialog: MutableState<Boolean>,
    selectedItemsList: SnapshotStateList<MediaStoreData>,
    currentView: MutableState<BottomBarTab>,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    DualFunctionTopAppBar(
        alternated = alternate,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize()
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Photo",
                    fontFamily = WordmarkFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = TextUnit(28f, TextUnitType.Sp),
                    letterSpacing = TextUnit(0.2f, TextUnitType.Sp),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Lex ",
                    fontFamily = WordmarkFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = TextUnit(28f, TextUnitType.Sp),
                    letterSpacing = TextUnit(0.2f, TextUnitType.Sp),
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.width(6.dp))

                TextExtractionAnimation()
            }
        },
        actions = {
            // Grid view toggle for Gallery and Search tabs
            AnimatedVisibility(
                visible = currentView.value == DefaultTabs.TabTypes.Gallery || currentView.value == DefaultTabs.TabTypes.search,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = scaleOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                val isGridView by mainViewModel.isGridViewMode.collectAsStateWithLifecycle(initialValue = true)

                IconButton(
                    onClick = {
                        mainViewModel.toggleGridViewMode()
                    },
                ) {
                    Icon(
                        painter = painterResource(id = if (isGridView) R.drawable.grid_view else R.drawable.view_day),
                        contentDescription = if (isGridView) "Switch to date view" else "Switch to grid view",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Add album button for Albums tab
            AnimatedVisibility(
                visible = currentView.value == DefaultTabs.TabTypes.albums,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = scaleOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                var showAlbumTypeDialog by remember { mutableStateOf(false) }
                if (showAlbumTypeDialog) {
                    AlbumAddChoiceDialog {
                        showAlbumTypeDialog = false
                    }
                }

                IconButton(
                    onClick = {
                        showAlbumTypeDialog = true
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = "Add album",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    showDialog.value = true
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Settings Button",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        alternateTitle = {
            SelectViewTopBarLeftButtons(selectedItemsList = selectedItemsList)
        },
        alternateActions = {
            SelectViewTopBarRightButtons(
                selectedItemsList = selectedItemsList,
                currentView = currentView
            )
        },
        scrollBehavior = scrollBehavior
    )
}

/**
 * One flying character particle in [TextExtractionAnimation]'s smoke-like burst -- randomized
 * once per particle (fixed seed) so the layout doesn't reshuffle every recomposition.
 */
private data class SmokeCharParticle(
    val char: String,
    val startXFraction: Float,
    val riseDistanceFraction: Float,
    val driftDirectionBias: Float,
    val driftAmplitude: Float,
    val driftFrequency: Float,
    val driftPhaseSeed: Float,
    val rotationDegrees: Float,
    val phaseOffset: Float,
    val sizeFraction: Float
)

/**
 * Small, restrained animated glyph shown beside the "PhotoLex" wordmark: a document shape that
 * periodically bursts a few random characters/letters upward like smoke -- drifting and wobbling
 * in varied directions rather than a straight line, rotating slightly, then fading out (evoking
 * "text extracted from a photo"), plus a faint shimmer sweep timed with the rise, echoing the
 * magnifying-glass motif from the app's own launcher icon. Loops quietly every ~5s -- most of the
 * cycle is a still, idle pause, with only a brief ~900ms motion -- and can be replayed on demand
 * by tapping it. Uses a LaunchedEffect-driven while(true) loop rather than an ever-running
 * infiniteTransition; Compose automatically cancels this coroutine when the composable leaves
 * composition (e.g. this app bar isn't on screen), so it doesn't run/drain battery when not visible.
 */
@Composable
private fun TextExtractionAnimation(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var replayTrigger by remember { mutableIntStateOf(0) }
    val riseProgress = remember { Animatable(0f) }
    val shimmerProgress = remember { Animatable(0f) }

    LaunchedEffect(replayTrigger) {
        while (true) {
            riseProgress.snapTo(0f)
            shimmerProgress.snapTo(0f)
            // Static hold: the characters sit visibly on the photo glyph, unmoving, before the
            // fly-away motion starts -- so the user first registers "this photo has text on it."
            delay(1200)
            launch {
                shimmerProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3600, easing = FastOutSlowInEasing)
                )
            }
            riseProgress.animateTo(
                targetValue = 1f,
                // Much slower -- explicitly requested, since the small size made fast motion
                // unreadable. The characters should be clearly followable, not a blur.
                animationSpec = tween(durationMillis = 3600, easing = FastOutSlowInEasing)
            )
            delay(2500) // brief still pause before the next slow burst
        }
    }

    // Lighter/more muted than the full-strength primary color -- using the same solid brand color
    // as the actual app icon right next to it made this look like a second, redundant app icon.
    val glyphColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    // Use onPrimary (not tertiary) for the flying characters: in dark theme, tertiary is just a
    // lighter shade of the same amber as primary, so they were nearly invisible against their own
    // background. onPrimary is guaranteed to contrast against primary in both themes.
    val charColor = MaterialTheme.colorScheme.onPrimary
    val shimmerColor = MaterialTheme.colorScheme.onPrimary

    // Each particle is a single character that drifts up and out like smoke -- not a straight
    // line, a randomized direction/wobble/rotation per particle, generated once (fixed seed) so
    // the layout doesn't reshuffle on every recomposition, just looks organically varied.
    val particles = remember {
        val charPool = listOf("A", "a", "1", "T", "e", "अ", "२")
        val random = Random(42)
        List(5) {
            SmokeCharParticle(
                char = charPool[random.nextInt(charPool.size)],
                startXFraction = 0.25f + random.nextFloat() * 0.5f,
                riseDistanceFraction = 1.0f + random.nextFloat() * 0.6f,
                driftDirectionBias = (random.nextFloat() - 0.5f) * 0.7f,
                driftAmplitude = 0.10f + random.nextFloat() * 0.10f,
                driftFrequency = 1.2f + random.nextFloat() * 1.3f,
                driftPhaseSeed = random.nextFloat() * 6.28f,
                rotationDegrees = (random.nextFloat() - 0.5f) * 140f,
                phaseOffset = random.nextFloat() * 0.3f,
                sizeFraction = 0.34f + random.nextFloat() * 0.12f
            )
        }
    }

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .size(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    replayTrigger++
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            drawRoundRect(
                color = glyphColor,
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Permanent (non-animated) little "photo" glyph -- a classic sun+mountain image
            // placeholder icon -- so the shape reads as "this is a photo" at a glance, with the
            // text/character burst animating on top of it and vanishing, leaving the clean photo
            // glyph visible again in between cycles.
            drawCircle(
                color = charColor.copy(alpha = 0.85f),
                radius = h * 0.12f,
                center = Offset(w * 0.28f, h * 0.28f)
            )
            val mountainPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.12f, h * 0.78f)
                lineTo(w * 0.40f, h * 0.48f)
                lineTo(w * 0.58f, h * 0.68f)
                lineTo(w * 0.72f, h * 0.52f)
                lineTo(w * 0.90f, h * 0.78f)
                close()
            }
            drawPath(
                path = mountainPath,
                color = charColor.copy(alpha = 0.85f)
            )

            if (shimmerProgress.value > 0f && shimmerProgress.value < 1f) {
                val sweepX = w * shimmerProgress.value * 1.6f - w * 0.3f
                drawLine(
                    color = shimmerColor.copy(alpha = 0.22f * (1f - shimmerProgress.value)),
                    start = Offset(sweepX, 0f),
                    end = Offset(sweepX - h * 0.5f, h),
                    strokeWidth = w * 0.22f
                )
            }

            particles.forEach { particle ->
                // Staggered start (phaseOffset) so particles don't all move in lockstep.
                val p = ((riseProgress.value - particle.phaseOffset) / (1f - particle.phaseOffset))
                    .coerceIn(0f, 1f)
                if (p <= 0f) return@forEach

                // Smoke-like path: rises while wobbling side to side (sine wave) plus an overall
                // directional bias, instead of a straight vertical/horizontal line.
                val riseOffset = p * h * particle.riseDistanceFraction
                val wobble = kotlin.math.sin(p * particle.driftFrequency * 6.28f + particle.driftPhaseSeed) *
                    particle.driftAmplitude * w
                val directionalDrift = particle.driftDirectionBias * p * w * 0.5f
                val xPos = w * particle.startXFraction + wobble + directionalDrift
                val yPos = h * 0.85f - riseOffset

                // Same "hold, then fade in the last 40%" timing already tuned earlier.
                val alpha = (1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f))
                if (alpha <= 0.02f) return@forEach

                rotate(degrees = particle.rotationDegrees * p, pivot = Offset(xPos, yPos)) {
                    drawContext.canvas.nativeCanvas.apply {
                        textPaint.color = charColor.copy(alpha = alpha).toArgb()
                        textPaint.textSize = h * particle.sizeFraction
                        drawText(particle.char, xPos, yPos, textPaint)
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppBottomBar(
    currentView: MutableState<BottomBarTab>,
    tabs: List<BottomBarTab>,
    selectedItemsList: SnapshotStateList<MediaStoreData>
) {
    FloatingBottomAppBar {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 7.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(percent = 35)
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                FloatingBottomBarItem(
                    tab = tab,
                    isSelected = currentView.value == tab,
                    onClick = {
                        if (currentView.value != tab) {
                            selectedItemsList.clear()
                            currentView.value = tab
                        }
                    }
                )
            }
        }
    }
}

/**
 * Animated wrapper for MainAppBottomBar that provides smooth shrinking/expanding animations
 * based on scroll state. Designed for Material Design animation principles.
 *
 * Features:
 * - Responsive scale animation with FastOutLinearInEasing for immediate feedback
 * - Optimized fade effect with high visibility (alpha 0.75) when shrunk
 * - Fast animation timing (200ms scale, 180ms alpha) for modern UX
 * - Performance optimized with hardware acceleration
 * - Maintains navigation functionality during animations
 */
@Composable
fun AnimatedBottomNavigationBar(
    currentView: MutableState<BottomBarTab>,
    tabs: List<BottomBarTab>,
    selectedItemsList: SnapshotStateList<MediaStoreData>,
    isVisible: Boolean = true
) {
    // Animate scale with responsive Material Design curves
    // Uses FastOutLinearInEasing for immediate response and smooth deceleration
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.75f, // Slightly less aggressive shrinking
        animationSpec = tween(
            durationMillis = 200, // Faster for immediate responsiveness
            easing = FastOutLinearInEasing // More responsive curve
        ),
        label = "bottomBarScale"
    )

    // Animate alpha for fade effect with better visibility
    // Higher minimum alpha ensures bottom bar remains clearly visible when shrunk
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.75f, // Much more visible when hidden (0.4 -> 0.75)
        animationSpec = tween(
            durationMillis = 180, // Even faster alpha transition for immediate feedback
            easing = FastOutLinearInEasing // Consistent responsive curve
        ),
        label = "bottomBarAlpha"
    )

    // Apply transformations to the bottom bar
    // Using graphicsLayer for hardware acceleration
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                // Add slight translation for more natural shrinking effect
                translationY = if (!isVisible) 8.dp.toPx() else 0f
            }
    ) {
        MainAppBottomBar(
            currentView = currentView,
            tabs = tabs,
            selectedItemsList = selectedItemsList
        )
    }
}

@Composable
private fun FloatingBottomBarItem(
    tab: BottomBarTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val mutableInteraction = remember { MutableInteractionSource() }
    val selectedColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "selectedColor"
    )
    val selectedIconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedIconColor"
    )
    val selectedTextColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedTextColor"
    )

    Column(
        modifier = Modifier
            .width(70.dp)
            .padding(vertical = 4.dp)
            .clickable(
                indication = null,
                interactionSource = mutableInteraction,
                onClick = { if (!isSelected) onClick() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Selected background indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = selectedColor,
                        shape = RoundedCornerShape(percent = 100)
                    )
                    .clip(RoundedCornerShape(100))
            )

            // Icon
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = if (isSelected) tab.icon.filled else tab.icon.nonFilled),
                contentDescription = "Navigate to ${tab.name} page",
                tint = selectedIconColor
            )
        }

        // Tab name text
        Spacer(modifier = Modifier.height(0.dp))
        Text(
            text = tab.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = selectedTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MainAppSelectingBottomBar(
    selectedItemsList: SnapshotStateList<MediaStoreData>
) {
    FloatingBottomAppBar {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(percent = 35)
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                val selectedItemsWithoutSection by remember {
                    derivedStateOf {
                        selectedItemsList.filter {
                            it.type != MediaType.Section && it != MediaStoreData()
                        }
                    }
                }

                BottomAppBarItem(
                    text = "Share",
                    iconResId = R.drawable.share,
                    action = {
                        coroutineScope.launch {
                            val hasVideos = selectedItemsWithoutSection.any {
                                it.type == MediaType.Video
                            }

                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND_MULTIPLE
                                type = if (hasVideos) "video/*" else "images/*"
                            }

                            val fileUris = ArrayList<Uri>()
                            selectedItemsWithoutSection.forEach {
                                fileUris.add(it.uri)
                            }

                            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)

                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    }
                )

                val show = remember { mutableStateOf(false) }
                var isMoving by remember { mutableStateOf(false) }
                MoveCopyAlbumListView(
                    show = show,
                    selectedItemsList = selectedItemsList,
                    isMoving = isMoving,
                    groupedMedia = null
                )

                BottomAppBarItem(
                    text = "Move",
                    iconResId = R.drawable.cut,
                    action = {
                        isMoving = true
                        show.value = true
                    }
                )

                BottomAppBarItem(
                    text = "Copy",
                    iconResId = R.drawable.copy,
                    action = {
                        isMoving = false
                        show.value = true
                    }
                )

                val showDeleteDialog = remember { mutableStateOf(false) }
                val runDeleteAction = remember { mutableStateOf(false) }

                GetPermissionAndRun(
                    uris = selectedItemsWithoutSection.map { it.uri },
                    shouldRun = runDeleteAction,
                    onGranted = {
                        mainViewModel.launch(Dispatchers.IO) {
                            setTrashedOnPhotoList(
                                context = context,
                                list = selectedItemsWithoutSection.map { Pair(it.uri, it.absolutePath) },
                                trashed = true
                            )

                            selectedItemsList.clear()
                        }
                    }
                )

                val confirmToDelete by mainViewModel.settings.Permissions.getConfirmToDelete()
                    .collectAsStateWithLifecycle(initialValue = true)
                BottomAppBarItem(
                    text = "Delete",
                    iconResId = R.drawable.delete,
                    cornerRadius = 16.dp,
                    action = {
                        if (confirmToDelete) showDeleteDialog.value = true
                        else runDeleteAction.value = true
                    },
                    dialogComposable = {
                        ConfirmationDialog(
                            showDialog = showDeleteDialog,
                            dialogTitle = "Move these items to Trash Bin?",
                            confirmButtonLabel = "Delete"
                        ) {
                            runDeleteAction.value = true
                        }
                    }
                )
        }
    }
}

