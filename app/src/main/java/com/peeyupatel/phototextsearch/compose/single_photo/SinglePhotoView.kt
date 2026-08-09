package com.peeyupatel.phototextsearch.compose.single_photo

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.peeyupatel.phototextsearch.ui.theme.Spacing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.peeyupatel.phototextsearch.BuildConfig
import com.peeyupatel.phototextsearch.LocalNavController
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.compose.app_bars.BottomAppBarItem
import com.peeyupatel.phototextsearch.compose.app_bars.FloatingBottomAppBar
import com.peeyupatel.phototextsearch.compose.app_bars.setBarVisibility
import com.peeyupatel.phototextsearch.compose.rememberDeviceOrientation
import com.peeyupatel.phototextsearch.compose.dialogs.ConfirmationDialog
import com.peeyupatel.phototextsearch.compose.dialogs.ExplanationDialog
import com.peeyupatel.phototextsearch.compose.dialogs.LoadingDialog
import com.peeyupatel.phototextsearch.compose.dialogs.SinglePhotoInfoDialog
import com.peeyupatel.phototextsearch.compose.text_selection.TextSelectionState
import com.peeyupatel.phototextsearch.compose.text_selection.rememberTextSelectionState
import com.peeyupatel.phototextsearch.compose.text_selection.TextSelectionViewer
import com.peeyupatel.phototextsearch.compose.text_selection.rememberTextClipboardManager
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarController
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarEvents
import androidx.compose.material3.SnackbarDuration
import com.peeyupatel.phototextsearch.ocr.EnhancedOcrExtractor
import com.peeyupatel.phototextsearch.datastore.Permissions
import com.peeyupatel.phototextsearch.helpers.GetDirectoryPermissionAndRun
import com.peeyupatel.phototextsearch.helpers.GetPermissionAndRun
import com.peeyupatel.phototextsearch.helpers.MultiScreenViewType
import com.peeyupatel.phototextsearch.helpers.Screens
import com.peeyupatel.phototextsearch.helpers.getParentFromPath
import com.peeyupatel.phototextsearch.helpers.moveImageToLockedFolder
import com.peeyupatel.phototextsearch.helpers.rememberVibratorManager
import com.peeyupatel.phototextsearch.helpers.searchWithGoogleLens
import com.peeyupatel.phototextsearch.helpers.setTrashedOnPhotoList
import com.peeyupatel.phototextsearch.helpers.shareImage
import com.peeyupatel.phototextsearch.helpers.toRelativePath
import com.peeyupatel.phototextsearch.helpers.vibrateShort
import com.peeyupatel.phototextsearch.mediastore.MediaStoreData
import com.peeyupatel.phototextsearch.mediastore.MediaType
import com.peeyupatel.phototextsearch.models.favourites_grid.FavouritesViewModel
import com.peeyupatel.phototextsearch.models.favourites_grid.FavouritesViewModelFactory
import com.peeyupatel.phototextsearch.models.multi_album.MultiAlbumViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// private const val TAG = "SINGLE_PHOTO_VIEW"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SinglePhotoView(
    navController: NavHostController,
    window: Window,
    viewModel: MultiAlbumViewModel,
    mediaItemId: Long,
    loadsFromMainViewModel: Boolean,
    searchQuery: String = ""
) {
    val holderGroupedMedia =
        if (!loadsFromMainViewModel) {
            viewModel.mediaFlow.collectAsStateWithLifecycle(context = Dispatchers.IO)
        } else {
            mainViewModel.groupedMedia.collectAsStateWithLifecycle(initialValue = null)
        }

    if (holderGroupedMedia.value == null) return

    val groupedMedia = remember {
        mutableStateOf(
            holderGroupedMedia.value!!.filter { item ->
                item.type != MediaType.Section
            }
        )
    }

    LaunchedEffect(holderGroupedMedia.value) {
        val filtered = holderGroupedMedia.value!!.filter { item ->
            item.type != MediaType.Section
        }

        // Guard against a nested flow elsewhere (e.g. the "Find Similar" dialog, which points
        // the shared mainViewModel.groupedMedia bus at its own results list while open) having
        // overwritten this shared bus with an unrelated list while this screen was paused in
        // the back stack. If the new list doesn't even contain the photo this screen was opened
        // for, it isn't meant for us -- keep this screen's own last-known-good list instead of
        // jumping to whatever happens to sit at the same index in someone else's list (that was
        // the actual bug: backing out of a photo opened from "Find Similar" landed on the first
        // photo of the *search results* instead of returning to this screen's own photo/list).
        if (loadsFromMainViewModel &&
            filtered.none { it.id == mediaItemId } &&
            groupedMedia.value.any { it.id == mediaItemId }
        ) {
            return@LaunchedEffect
        }

        groupedMedia.value = filtered
    }

    var currentMediaItemIndex by rememberSaveable {
        mutableIntStateOf(
            // indexOfFirst (not indexOf+first) -- the shared groupedMedia bus can briefly not
            // yet contain mediaItemId if this screen opens before an async loader (e.g. a
            // Curated Album's photo query) finishes publishing its list; falling back to 0
            // instead of crashing lets the pager open (on the wrong page for a frame) rather
            // than never opening at all.
            groupedMedia.value.indexOfFirst { it.id == mediaItemId }.coerceAtLeast(0)
        )
    }

    val state = rememberPagerState(
        initialPage = currentMediaItemIndex
            .coerceIn(
                0,
                (groupedMedia.value.size - 1)
                    .coerceAtLeast(0)
            )
    ) {
        groupedMedia.value.size
    }

    LaunchedEffect(key1 = state.currentPage) {
        currentMediaItemIndex = state.currentPage
    }

    // Corrects the pager if it opened on the wrong page (or on an empty list) because
    // mediaItemId wasn't in groupedMedia yet at first composition -- once the real list
    // arrives/updates and does contain it, jump straight there instead of silently staying on
    // whatever page 0 happened to be (see the indexOfFirst fallback above).
    LaunchedEffect(groupedMedia.value) {
        val target = groupedMedia.value.indexOfFirst { it.id == mediaItemId }
        if (target >= 0 && groupedMedia.value.getOrNull(state.currentPage)?.id != mediaItemId) {
            state.scrollToPage(target)
        }
    }

    val appBarsVisible = remember { mutableStateOf(true) }
    val currentMediaItem = remember {
        derivedStateOf {
            val index = state.layoutInfo.visiblePagesInfo.firstOrNull()?.index ?: 0
            if (index < groupedMedia.value.size) {
                groupedMedia.value[index]
            } else {
                MediaStoreData(
                    displayName = "Broken Media"
                )
            }
        }
    }

    val showInfoDialog = remember { mutableStateOf(false) }
    val showRenameDialog = remember { mutableStateOf(false) }
    val showRegionSearch = remember { mutableStateOf(false) }
    val textSelectionState = rememberTextSelectionState()
    val context = LocalContext.current

    // Image transformation states are no longer needed for text selection
    // as the dedicated TextSelectionViewer handles coordinate transformation internally

    // Use dedicated TextSelectionImageViewer when in text selection mode
    if (textSelectionState.isTextSelectionMode && currentMediaItem.value.type == MediaType.Image) {
        // Load OCR data when text selection mode is activated
        LaunchedEffect(currentMediaItem.value.uri) {
            try {
                val ocrResult = EnhancedOcrExtractor.extractSelectableTextFromImage(
                    context = context,
                    imageUri = currentMediaItem.value.uri
                )
                textSelectionState.updateOcrResult(ocrResult)
            } catch (e: Exception) {
                // Handle OCR extraction error
                println("OCR extraction failed: ${e.message}")
            }
        }

        com.peeyupatel.phototextsearch.compose.text_selection.TextSelectionImageViewer(
            imageUri = currentMediaItem.value.uri.toString(),
            ocrResult = textSelectionState.ocrResult,
            textSelectionState = textSelectionState,
            onBackPressed = {
                textSelectionState.toggleTextSelectionMode()
            },
            modifier = Modifier.fillMaxSize()
        )
        return // Exit early to avoid showing the normal photo viewer
    }

    // Region-select-to-search: same early-return-to-swap-the-whole-screen pattern as text
    // selection mode above.
    if (showRegionSearch.value && currentMediaItem.value.type == MediaType.Image) {
        RegionSearchOverlay(
            imageUri = currentMediaItem.value.uri,
            onDismiss = { showRegionSearch.value = false },
            onSearch = { extractedText ->
                showRegionSearch.value = false
                mainViewModel.setPendingSearchQuery(extractedText)
                navController.popBackStack(
                    route = com.peeyupatel.phototextsearch.helpers.MultiScreenViewType.MainScreen.name,
                    inclusive = false
                )
            }
        )
        return
    }

    // Search-result highlight: only runs when this photo was opened from a search hit.
    // Reuses the same live OCR extraction the text-selection feature uses, but only to
    // find and highlight the matched region(s) -- no interactive selection UI is shown.
    var searchOcrResult by remember { mutableStateOf<com.peeyupatel.phototextsearch.ocr.SelectableOcrResult?>(null) }
    var searchHighlightContainerSize by remember { mutableStateOf<Size?>(null) }

    LaunchedEffect(currentMediaItem.value.uri, searchQuery) {
        if (searchQuery.isNotBlank() && currentMediaItem.value.type == MediaType.Image) {
            searchOcrResult = try {
                EnhancedOcrExtractor.extractSelectableTextFromImage(
                    context = context,
                    imageUri = currentMediaItem.value.uri
                )
            } catch (e: Exception) {
                null
            }
        } else {
            searchOcrResult = null
        }
    }

    BackHandler(
        enabled = !showInfoDialog.value
    ) {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            // Hide top bar when in text selection mode
            if (!textSelectionState.isTextSelectionMode) {
                val coroutineScope = rememberCoroutineScope()

                TopBar(
                    mediaItem = currentMediaItem.value,
                    visible = appBarsVisible.value,
                    showInfoDialog = showInfoDialog,
                    showRenameDialog = showRenameDialog,
                    removeIfInFavGrid = {
                        if (navController.previousBackStackEntry?.destination?.route == MultiScreenViewType.FavouritesGridView.name) {
                            sortOutMediaMods(
                                currentMediaItem.value,
                                groupedMedia,
                                coroutineScope,
                                state
                            ) {
                                navController.popBackStack()
                            }
                        }
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    textSelectionState = textSelectionState
                )
            }
        },
        bottomBar = {
            // Hide bottom bar when in text selection mode
            if (!textSelectionState.isTextSelectionMode) {
                BottomBar(
                    visible = appBarsVisible.value,
                    currentItem = currentMediaItem.value,
                    groupedMedia = groupedMedia,
                    loadsFromMainViewModel = loadsFromMainViewModel,
                    state = state,
                    onSearchRegion = { showRegionSearch.value = true },
                    showEditingView = {
                        setBarVisibility(
                            visible = true,
                            window = window
                        ) {
                            appBarsVisible.value = it
                        }

                        navController.navigate(
                            Screens.EditingScreen(
                                absolutePath = currentMediaItem.value.absolutePath,
                                uri = currentMediaItem.value.uri.toString(),
                                dateTaken = currentMediaItem.value.dateTaken
                            )
                        )
                    },
                    onZeroItemsLeft = {
                        navController.popBackStack()
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { _ ->
        SinglePhotoInfoDialog(
            showDialog = showInfoDialog,
            currentMediaItem = currentMediaItem.value,
            groupedMedia = groupedMedia,
            loadsFromMainViewModel = loadsFromMainViewModel,
            showMoveCopyOptions = true,
            textSelectionState = textSelectionState
        )

        // Rename dialog triggered by clicking filename
        SinglePhotoInfoDialog(
            showDialog = showRenameDialog,
            currentMediaItem = currentMediaItem.value,
            groupedMedia = groupedMedia,
            loadsFromMainViewModel = loadsFromMainViewModel,
            showMoveCopyOptions = false,
            startInRenameMode = true,
            textSelectionState = textSelectionState
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            // Main image content
            Column(
                modifier = Modifier
                    .padding(0.dp)
                    .fillMaxSize(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalImageList(
                    currentMediaItem = currentMediaItem.value,
                    groupedMedia = groupedMedia.value,
                    state = state,
                    window = window,
                    appBarsVisible = appBarsVisible,
                    onImageSizeChanged = { containerSize, _ ->
                        searchHighlightContainerSize = containerSize
                    }
                )
            }

            if (searchQuery.isNotBlank()) {
                SearchHighlightOverlay(
                    ocrResult = searchOcrResult,
                    containerSize = searchHighlightContainerSize,
                    searchQuery = searchQuery
                )
            }

            // Text selection interface is now handled by the dedicated TextSelectionViewer
            // This code block has been removed and replaced with the TextSelectionViewer above
        }
    }
}

/**
 * Draws light, non-interactive highlight boxes over any OCR text region that matches the
 * active search query, so a photo opened from a search result visibly shows where the match
 * was found -- similar to find-in-page highlighting. Reuses the ContentScale.Fit coordinate
 * math (container size vs. original image size) that HorizontalImageList already reports via
 * onImageSizeChanged, so highlights line up with the actual displayed image regardless of
 * screen size or aspect ratio.
 */
@Composable
private fun SearchHighlightOverlay(
    ocrResult: com.peeyupatel.phototextsearch.ocr.SelectableOcrResult?,
    containerSize: Size?,
    searchQuery: String
) {
    if (ocrResult == null || containerSize == null || containerSize.width <= 0f || containerSize.height <= 0f) return

    val imageSize = ocrResult.imageSize
    if (imageSize.width <= 0f || imageSize.height <= 0f) return

    val query = searchQuery.trim()
    if (query.isEmpty()) return

    val highlightRects = remember(ocrResult, query) {
        val rects = mutableListOf<androidx.compose.ui.geometry.Rect>()
        ocrResult.textBlocks.forEach { block ->
            var matchedAnyElement = false
            block.getAllElements().forEach { element ->
                if (element.text.contains(query, ignoreCase = true)) {
                    rects.add(element.boundingBox)
                    matchedAnyElement = true
                }
            }
            // Fall back to highlighting the whole block if the query only matches across
            // multiple words (e.g. a short phrase) rather than any single element.
            if (!matchedAnyElement && block.text.contains(query, ignoreCase = true)) {
                rects.add(block.boundingBox)
            }
        }
        rects
    }

    if (highlightRects.isEmpty()) return

    val scaleFactor = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
    val displayedWidth = imageSize.width * scaleFactor
    val displayedHeight = imageSize.height * scaleFactor
    val offsetX = (containerSize.width - displayedWidth) / 2f
    val offsetY = (containerSize.height - displayedHeight) / 2f

    // Bold "highlighter pen" yellow (like MS Word / browser find-in-page), not a subtle brand-color tint
    val highlightColor = androidx.compose.ui.graphics.Color(0xFFFFEB3B).copy(alpha = 0.55f)

    highlightRects.forEach { rect ->
        // offsetX/offsetY/scaleFactor were all computed against containerSize, which is already
        // in dp (BoxWithConstraints' maxWidth/maxHeight.value) -- these results are dp magnitudes
        // already, so wrap them directly with `.dp` rather than `.toDp()` (which is for converting
        // raw pixels, and would incorrectly shrink/misplace everything by the screen density).
        val left = offsetX + rect.left * scaleFactor
        val top = offsetY + rect.top * scaleFactor
        val width = (rect.right - rect.left) * scaleFactor
        val height = (rect.bottom - rect.top) * scaleFactor

        if (width <= 0f || height <= 0f) return@forEach

        Box(
            modifier = Modifier
                .offset(x = left.dp, y = top.dp)
                .size(width = width.dp, height = height.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(highlightColor)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    mediaItem: MediaStoreData,
    visible: Boolean,
    showInfoDialog: MutableState<Boolean>,
    showRenameDialog: MutableState<Boolean>,
    removeIfInFavGrid: () -> Unit,
    onBackClick: () -> Unit,
    textSelectionState: TextSelectionState
) {
    val context = LocalContext.current
    val localConfig = LocalConfiguration.current
    var isLandscape by remember { mutableStateOf(localConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) }

    LaunchedEffect(localConfig) {
        isLandscape = localConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    val color = if (isLandscape)
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.surfaceContainer

    val vibratorManager = rememberVibratorManager()

    val favouritesViewModel: FavouritesViewModel = viewModel(
        factory = FavouritesViewModelFactory()
    )

    AnimatedVisibility(
        visible = visible,
        enter =
        slideInVertically(
            animationSpec = tween(
                durationMillis = 350
            )
        ) { width -> -width } + fadeIn(),
        exit =
        slideOutVertically(
            animationSpec = tween(
                durationMillis = 400
            )
        ) { width -> -width } + fadeOut(),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = color
            ),
            navigationIcon = {
                IconButton(
                    onClick = { onBackClick() },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.back_arrow),
                        contentDescription = "Go back to previous page",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(24.dp)
                    )
                }
            },
            title = {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = mediaItem.displayName,
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(if (isLandscape) 300.dp else 160.dp)
                        .clickable {
                            showRenameDialog.value = true
                        }
                )
            },
            actions = {
                val isSelected by favouritesViewModel.isInFavourites(mediaItem.id).collectAsStateWithLifecycle()

                IconButton(
                    onClick = {
                        vibratorManager.vibrateShort()

                        if (!isSelected) {
                            favouritesViewModel.addToFavourites(mediaItem, context)
                        } else {
                            favouritesViewModel.removeFromFavourites(mediaItem.id)
                            removeIfInFavGrid()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(id = if (isSelected) R.drawable.favourite_filled else R.drawable.favourite),
                        contentDescription = "favorite this media item",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(0.dp, 1.dp, 0.dp, 0.dp)
                    )
                }

                // Google Lens button - only show for images, not videos
                if (mediaItem.type == MediaType.Image) {
                    Spacer(modifier = Modifier.width(Spacing.xs))

                    IconButton(
                        onClick = {
                            vibratorManager.vibrateShort()
                            searchWithGoogleLens(mediaItem.uri, context)
                        },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lens),
                            contentDescription = "search with Google Lens",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.xs))

                    // Text Selection button - dedicated backup solution
                    IconButton(
                        onClick = {
                            vibratorManager.vibrateShort()
                            textSelectionState.toggleTextSelectionMode()
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (textSelectionState.isTextSelectionMode) R.drawable.close else R.drawable.ocr
                            ),
                            contentDescription = if (textSelectionState.isTextSelectionMode) "Exit text selection mode" else "Enter text selection mode",
                            tint = if (textSelectionState.isTextSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.xs))

                IconButton(
                    onClick = {
                        showInfoDialog.value = true
                    },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.more_options),
                        contentDescription = "show more options",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(24.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun BottomBar(
    visible: Boolean,
    currentItem: MediaStoreData,
    groupedMedia: MutableState<List<MediaStoreData>>,
    loadsFromMainViewModel: Boolean,
    state: PagerState,
    onSearchRegion: () -> Unit,
    showEditingView: () -> Unit,
    onZeroItemsLeft: () -> Unit
) {
    val isLandscape by rememberDeviceOrientation()

    var showLoadingDialog by remember { mutableStateOf(false) }

    if (showLoadingDialog) {
        LoadingDialog(title = "Encrypting Files", body = "Please wait while the media is processed")
    }

    AnimatedVisibility(
        visible = visible,
        enter =
        slideInVertically(
            animationSpec = tween(
                durationMillis = 250
            )
        ) { width -> width } + fadeIn(),
        exit =
        slideOutVertically(
            animationSpec = tween(
                durationMillis = 300
            )
        ) { width -> width } + fadeOut(),
    ) {
        val context = LocalContext.current
        val copyTextCoroutineScope = rememberCoroutineScope()
        val clipboardManager = rememberTextClipboardManager()

        FloatingBottomAppBar {
            Row(
                modifier = Modifier
                    .fillMaxWidth(1f)
                    .padding(12.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                if (isLandscape)
                    Arrangement.spacedBy(
                        space = 48.dp,
                        alignment = Alignment.CenterHorizontally
                    )
                else Arrangement.SpaceEvenly
            ) {
                    BottomAppBarItem(
                        text = "Share",
                        iconResId = R.drawable.share,
                        cornerRadius = 32.dp,
                        action = {
                            shareImage(currentItem.uri, context)
                        }
                    )

                    if (currentItem.type == MediaType.Image) {
                        BottomAppBarItem(
                            text = "Copy",
                            iconResId = R.drawable.copy,
                            cornerRadius = 32.dp,
                            action = {
                                copyTextCoroutineScope.launch {
                                    val ocrResult = try {
                                        EnhancedOcrExtractor.extractSelectableTextFromImage(
                                            context = context,
                                            imageUri = currentItem.uri
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }

                                    val extractedText = ocrResult?.fullText?.trim()

                                    if (extractedText.isNullOrBlank()) {
                                        LavenderSnackbarController.pushEvent(
                                            LavenderSnackbarEvents.MessageEvent(
                                                message = "No text found in this photo",
                                                iconResId = R.drawable.error_2,
                                                duration = SnackbarDuration.Short
                                            )
                                        )
                                    } else {
                                        clipboardManager.copyTextToClipboard(
                                            text = extractedText,
                                            label = "Extracted Text",
                                            showToast = false,
                                            showSnackbar = false
                                        )

                                        LavenderSnackbarController.pushEvent(
                                            LavenderSnackbarEvents.MessageEvent(
                                                message = "Copied all text from photo",
                                                iconResId = R.drawable.check_item,
                                                duration = SnackbarDuration.Short
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }

                    if (currentItem.type == MediaType.Image) {
                        val navController = LocalNavController.current
                        BottomAppBarItem(
                            text = "Similar",
                            iconResId = R.drawable.search,
                            cornerRadius = 32.dp,
                            action = {
                                navController.navigate(
                                    com.peeyupatel.phototextsearch.helpers.Screens.FindSimilarView(
                                        sourceMediaId = currentItem.id
                                    )
                                )
                            }
                        )
                    }

                    if (currentItem.type == MediaType.Image) {
                        BottomAppBarItem(
                            text = "Select",
                            iconResId = R.drawable.highlighter,
                            cornerRadius = 32.dp,
                            action = onSearchRegion
                        )
                    }

                    val showNotImplementedDialog = remember { mutableStateOf(false) }

                    if (showNotImplementedDialog.value) {
                        ExplanationDialog(
                            title = "Unimplemented",
                            explanation = "Editing videos has not been implemented yet as of version ${BuildConfig.VERSION_NAME} of PhotoLex. This feature will be added as soon as possible, thank you for your patience.",
                            showDialog = showNotImplementedDialog
                        )
                    }

                    BottomAppBarItem(
                        text = "Edit",
                        iconResId = R.drawable.paintbrush,
                        cornerRadius = 32.dp,
                        action = if (currentItem.type == MediaType.Image) {
                            showEditingView
                        } else {
                            { showNotImplementedDialog.value = true }
                        }
                    )

                    val showDeleteDialog = remember { mutableStateOf(false) }
                    val runTrashAction = remember { mutableStateOf(false) }

                    println("CURRENT ITEM URI ${currentItem.uri}")

                    val coroutineScope = rememberCoroutineScope()
                    GetPermissionAndRun(
                        uris = listOf(currentItem.uri),
                        shouldRun = runTrashAction,
                        onGranted = {
                            mainViewModel.launch(Dispatchers.IO) {
                                setTrashedOnPhotoList(
                                    context,
                                    listOf(Pair(currentItem.uri, currentItem.absolutePath)),
                                    true
                                )

                                if (groupedMedia.value.isEmpty()) onZeroItemsLeft()

                                if (loadsFromMainViewModel) {
                                    sortOutMediaMods(
                                        currentItem,
                                        groupedMedia,
                                        coroutineScope,
                                        state
                                    ) {
                                        onZeroItemsLeft()
                                    }
                                }
                            }
                        }
                    )

                    val confirmToDelete by mainViewModel.settings.Permissions.getConfirmToDelete().collectAsStateWithLifecycle(initialValue = true)
                    BottomAppBarItem(
                        text = "Delete",
                        iconResId = R.drawable.trash,
                        cornerRadius = 32.dp,
                        action = {
                            if (confirmToDelete) showDeleteDialog.value = true
                            else runTrashAction.value = true
                        },
                        dialogComposable = {
                            ConfirmationDialog(
                                showDialog = showDeleteDialog,
                                dialogTitle = "Delete this ${currentItem.type}?",
                                confirmButtonLabel = "Delete"
                            ) {
                                runTrashAction.value = true
                            }
                        }
                    )

                    // TODO: maybe restructure this
                    val showMoveToSecureFolderDialog = remember { mutableStateOf(false) }
                    val moveToSecureFolder = remember { mutableStateOf(false) }
                    val tryGetDirPermission = remember { mutableStateOf(false) }

                    GetDirectoryPermissionAndRun(
                        absoluteDirPaths = listOf(groupedMedia.value.firstOrNull()?.absolutePath?.toRelativePath()?.getParentFromPath() ?: ""),
                        shouldRun = tryGetDirPermission,
                        onGranted = {
                        	moveToSecureFolder.value = true
                        	showLoadingDialog = true
                        },
                        onRejected = {}
                    )

                    GetPermissionAndRun(
                        uris = listOf(currentItem.uri),
                        shouldRun = moveToSecureFolder,
                        onGranted = {
                            mainViewModel.launch(Dispatchers.IO) {
                                moveImageToLockedFolder(
                                    listOf(currentItem),
                                    context
                                ) {
                                    if (groupedMedia.value.isEmpty()) onZeroItemsLeft()

                                    if (loadsFromMainViewModel) {
                                        sortOutMediaMods(
                                            currentItem,
                                            groupedMedia,
                                            coroutineScope,
                                            state
                                        ) {
                                            onZeroItemsLeft()
                                        }
                                    }

                                    showLoadingDialog = false
                                }
                            }
                        }
                    )

                    BottomAppBarItem(
                        text = "Secure",
                        iconResId = R.drawable.locked_folder,
                        cornerRadius = 32.dp,
                        action = {
                            showMoveToSecureFolderDialog.value = true
                        },
                        dialogComposable = {
                            ConfirmationDialog(
                                showDialog = showMoveToSecureFolderDialog,
                                dialogTitle = "Move this ${currentItem.type} to Secure Folder?",
                                confirmButtonLabel = "Secure"
                            ) {
                                tryGetDirPermission.value = true

                                if (groupedMedia.value.isEmpty()) onZeroItemsLeft()
                            }
                        }
                    )
            }
        }
    }
}



