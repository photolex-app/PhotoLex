package com.peeyupatel.phototextsearch.compose.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peeyupatel.phototextsearch.MainActivity.Companion.mainViewModel
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.datastore.Ocr
import com.peeyupatel.phototextsearch.compose.PreferencesSwitchRow
import com.peeyupatel.phototextsearch.compose.components.OcrProgressBar
import com.peeyupatel.phototextsearch.database.entities.OcrProgressEntity
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.helpers.RowPosition
import com.peeyupatel.phototextsearch.ocr.DevanagariOcrManager
import com.peeyupatel.phototextsearch.ocr.OcrManager
import kotlinx.coroutines.launch

private const val TAG = "OcrLanguageModelsPage"

/**
 * Convert DevanagariOcrProgressEntity to OcrProgressEntity for UI compatibility
 */
private fun com.peeyupatel.phototextsearch.database.entities.DevanagariOcrProgressEntity.toOcrProgressEntity(): OcrProgressEntity {
    return OcrProgressEntity(
        id = this.id,
        totalImages = this.totalImages,
        processedImages = this.processedImages,
        failedImages = this.failedImages,
        isProcessing = this.isProcessing,
        isPaused = this.isPaused,
        lastUpdated = this.lastUpdated,
        estimatedCompletionTime = this.estimatedCompletionTime,
        averageProcessingTimeMs = this.averageProcessingTimeMs,
        currentBatchId = this.currentBatchId,
        progressDismissed = this.progressDismissed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguageModelsPage(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // OCR settings state
    val latinOcrEnabled by mainViewModel.settings.Ocr.latinOcrEnabled.collectAsStateWithLifecycle(initialValue = true)
    val devanagariOcrEnabled by mainViewModel.settings.Ocr.devanagariOcrEnabled.collectAsStateWithLifecycle(initialValue = false)
    val indexOnLowBattery by mainViewModel.settings.Ocr.indexOnLowBattery.collectAsStateWithLifecycle(initialValue = true)

    Log.d(TAG, "OcrLanguageModelsPage composed - Latin OCR: $latinOcrEnabled, Devanagari OCR: $devanagariOcrEnabled")
    
    // Database instance
    val database = remember {
        MediaDatabase.getInstance(context)
    }
    
    // OCR managers
    val latinOcrManager = remember { OcrManager(context, database) }
    val devanagariOcrManager = remember { DevanagariOcrManager(context, database) }

    // Progress monitoring state
    val latinOcrProgress by latinOcrManager.getProgressFlow().collectAsStateWithLifecycle(initialValue = null)
    val devanagariOcrProgress by devanagariOcrManager.getProgressFlow().collectAsStateWithLifecycle(initialValue = null)

    Log.d(TAG, "Progress state - Latin: $latinOcrProgress, Devanagari: $devanagariOcrProgress")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Language Models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OCR Language Models",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure which OCR language models to use for text extraction from images. Each language model processes images independently.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress Bars for Active OCR Processing (positioned at top for visibility)

            // Check for active OCR systems
            val isDevanagariEnabledAndActive = devanagariOcrEnabled &&
                (devanagariOcrProgress?.isProcessing == true || devanagariOcrProgress?.isPaused == true)
            val isLatinEnabledAndActive = latinOcrEnabled &&
                (latinOcrProgress?.isProcessing == true || latinOcrProgress?.isPaused == true)

            Log.d(TAG, "Progress bar visibility check:")
            Log.d(TAG, "  Latin enabled: $latinOcrEnabled, active: $isLatinEnabledAndActive")
            Log.d(TAG, "  Devanagari enabled: $devanagariOcrEnabled, active: $isDevanagariEnabledAndActive")

            // Show Devanagari OCR progress bar (non-dismissible)
            if (isDevanagariEnabledAndActive && devanagariOcrProgress != null) {
                Log.d(TAG, "🟦 Showing Devanagari OCR progress: ${devanagariOcrProgress?.processedImages}/${devanagariOcrProgress?.totalImages}")
                val devanagariActiveProgress = devanagariOcrProgress?.toOcrProgressEntity()

                if (devanagariActiveProgress != null) {
                    OcrProgressBar(
                        progress = devanagariActiveProgress,
                        isVisible = true,
                        showDismissButton = false, // Hide dismiss button for Devanagari OCR in settings
                        horizontalPadding = 0.dp, // Use 0dp since page already has 16dp padding
                        title = "Devanagari OCR Progress: ${devanagariActiveProgress.processedImages}/${devanagariActiveProgress.totalImages} images",
                        onDismiss = {
                            // This won't be called since dismiss button is hidden
                            Log.d(TAG, "Devanagari OCR progress bar dismiss ignored in settings page")
                        },
                        onPauseResume = {
                            scope.launch {
                                if (devanagariActiveProgress?.isPaused == true) {
                                    Log.d(TAG, "Resuming Devanagari OCR processing")
                                    devanagariOcrManager.resumeProcessing()
                                } else {
                                    Log.d(TAG, "Pausing Devanagari OCR processing")
                                    devanagariOcrManager.pauseProcessing()
                                }
                            }
                        }
                    )
                }
            }

            // Show Latin OCR progress bar (dismissible at all times)
            if (isLatinEnabledAndActive && latinOcrProgress != null) {
                Log.d(TAG, "🟨 Showing Latin OCR progress: ${latinOcrProgress?.processedImages}/${latinOcrProgress?.totalImages}")

                OcrProgressBar(
                    progress = latinOcrProgress,
                    isVisible = true,
                    showDismissButton = false, // Hide dismiss button for Latin OCR in settings page for consistency
                    horizontalPadding = 0.dp, // Use 0dp since page already has 16dp padding
                    title = "Latin OCR Progress: ${latinOcrProgress?.processedImages}/${latinOcrProgress?.totalImages} images",
                    onDismiss = {
                        // This won't be called since dismiss button is hidden
                        Log.d(TAG, "Latin OCR progress bar dismiss ignored in settings page")
                    },
                    onPauseResume = {
                        scope.launch {
                            if (latinOcrProgress?.isPaused == true) {
                                Log.d(TAG, "Resuming Latin OCR processing")
                                latinOcrManager.resumeProcessing()
                            } else {
                                Log.d(TAG, "Pausing Latin OCR processing")
                                latinOcrManager.pauseProcessing()
                            }
                        }
                    }
                )
            }

            // Latin OCR Settings
            PreferencesSwitchRow(
                title = "Latin OCR",
                summary = "English, Spanish, French, German, Italian, Portuguese, and other Latin-script languages",
                iconResID = R.drawable.ocr,
                checked = latinOcrEnabled,
                position = RowPosition.Top,
                showBackground = false,
                onSwitchClick = { enabled ->
                    Log.d(TAG, "Latin OCR toggle clicked: $enabled")
                    mainViewModel.settings.Ocr.setLatinOcrEnabled(enabled)

                    scope.launch {
                        if (enabled) {
                            Log.d(TAG, "Starting Latin OCR continuous processing")
                            // Start Latin OCR processing
                            latinOcrManager.startContinuousProcessing()
                        } else {
                            Log.d(TAG, "Pausing Latin OCR processing")
                            // Pause Latin OCR processing
                            latinOcrManager.pauseProcessing()
                        }
                    }
                }
            )

            // Devanagari OCR Settings
            PreferencesSwitchRow(
                title = "Devanagari OCR",
                summary = "Hindi, Marathi, Nepali, Sanskrit, and other Devanagari-script languages",
                iconResID = R.drawable.ocr,
                checked = devanagariOcrEnabled,
                position = RowPosition.Middle,
                showBackground = false,
                onSwitchClick = { enabled ->
                    Log.d(TAG, "Devanagari OCR toggle clicked: $enabled")
                    mainViewModel.settings.Ocr.setDevanagariOcrEnabled(enabled)

                    scope.launch {
                        if (enabled) {
                            Log.d(TAG, "Starting Devanagari OCR continuous processing")
                            // Start Devanagari OCR processing
                            val workId = devanagariOcrManager.startContinuousProcessing()
                            Log.d(TAG, "Devanagari OCR work enqueued with ID: $workId")
                        } else {
                            Log.d(TAG, "Pausing Devanagari OCR processing")
                            // Pause Devanagari OCR processing
                            devanagariOcrManager.pauseProcessing()
                        }
                    }
                }
            )

            // Index-on-low-battery setting -- previously hardcoded permissive (always allowed),
            // now a real user-facing choice. Defaults to on, matching prior behavior.
            PreferencesSwitchRow(
                title = "Index even on low battery",
                summary = "When off, background indexing pauses until the battery isn't low. Manually tapping \"index now\" is unaffected.",
                iconResID = R.drawable.ocr,
                checked = indexOnLowBattery,
                position = RowPosition.Bottom,
                showBackground = false,
                onSwitchClick = { enabled ->
                    Log.d(TAG, "Index on low battery toggle clicked: $enabled")
                    mainViewModel.settings.Ocr.setIndexOnLowBattery(enabled)
                }
            )

            // Processing Status Information
            if (latinOcrEnabled || devanagariOcrEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Processing Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        if (latinOcrEnabled) {
                            Text(
                                text = "• Latin OCR: Processing images in background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        if (devanagariOcrEnabled) {
                            Text(
                                text = "• Devanagari OCR: Processing images in background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Text(
                            text = "Progress notifications will appear when processing starts. You can search for text in images once processing is complete.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Performance Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Performance Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Latin OCR is enabled by default for optimal performance\n" +
                              "• Devanagari OCR is disabled by default to conserve resources\n" +
                              "• Both systems can run simultaneously without conflicts\n" +
                              "• Processing happens in the background and won't affect app performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
