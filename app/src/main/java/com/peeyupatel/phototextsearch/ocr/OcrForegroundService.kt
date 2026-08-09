package com.peeyupatel.phototextsearch.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.peeyupatel.phototextsearch.MainActivity
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.datastore.Settings
import com.peeyupatel.phototextsearch.datastore.Ocr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that ensures OCR processing continues even when the app is force-closed
 */
class OcrForegroundService : Service() {

    companion object {
        private const val TAG = "OcrForegroundService"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "ocr_foreground_service"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        
        // Service actions
        const val ACTION_START_LATIN_OCR = "start_latin_ocr"
        const val ACTION_START_DEVANAGARI_OCR = "start_devanagari_ocr"
        const val ACTION_STOP_LATIN_OCR = "stop_latin_ocr"
        const val ACTION_STOP_DEVANAGARI_OCR = "stop_devanagari_ocr"
        const val ACTION_STOP_SERVICE = "stop_service"
        
        // Service state
        private var isServiceRunning = false
        
        fun isRunning(): Boolean = isServiceRunning
        
        /**
         * Start the foreground service for Latin OCR
         */
        fun startLatinOcr(context: Context) {
            val intent = Intent(context, OcrForegroundService::class.java).apply {
                action = ACTION_START_LATIN_OCR
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Start the foreground service for Devanagari OCR
         */
        fun startDevanagariOcr(context: Context) {
            val intent = Intent(context, OcrForegroundService::class.java).apply {
                action = ACTION_START_DEVANAGARI_OCR
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Stop Latin OCR processing
         */
        fun stopLatinOcr(context: Context) {
            val intent = Intent(context, OcrForegroundService::class.java).apply {
                action = ACTION_STOP_LATIN_OCR
            }
            context.startService(intent)
        }
        
        /**
         * Stop Devanagari OCR processing
         */
        fun stopDevanagariOcr(context: Context) {
            val intent = Intent(context, OcrForegroundService::class.java).apply {
                action = ACTION_STOP_DEVANAGARI_OCR
            }
            context.startService(intent)
        }
        
        /**
         * Stop the entire service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, OcrForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var database: MediaDatabase
    private lateinit var settings: Settings
    private lateinit var workManager: WorkManager
    
    private var latinOcrManager: OcrManager? = null
    private var devanagariOcrManager: DevanagariOcrManager? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var latinOcrMonitorJob: Job? = null
    private var devanagariOcrMonitorJob: Job? = null
    
    private var isLatinOcrActive = false
    private var isDevanagariOcrActive = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 OCR Foreground Service created")
        
        isServiceRunning = true
        
        // Initialize components
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        workManager = WorkManager.getInstance(this)
        
        // Initialize database
        database = MediaDatabase.getInstance(applicationContext)
        
        // Initialize settings
        settings = Settings(applicationContext, serviceScope)
        
        // Create notification channel
        createNotificationChannel()
        
        // Acquire wake lock to prevent device from sleeping during OCR processing
        acquireWakeLock()
        
        Log.d(TAG, "✅ OCR Foreground Service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📨 Service command received: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_LATIN_OCR -> startLatinOcrProcessing()
            ACTION_START_DEVANAGARI_OCR -> startDevanagariOcrProcessing()
            ACTION_STOP_LATIN_OCR -> stopLatinOcrProcessing()
            ACTION_STOP_DEVANAGARI_OCR -> stopDevanagariOcrProcessing()
            ACTION_STOP_SERVICE -> stopSelf()
            else -> {
                // Default: start with a basic notification
                startForeground(NOTIFICATION_ID, createServiceNotification())
            }
        }
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "🛑 OCR Foreground Service destroyed")
        
        isServiceRunning = false
        
        // Cancel all monitoring jobs
        latinOcrMonitorJob?.cancel()
        devanagariOcrMonitorJob?.cancel()
        
        // Cancel service scope
        serviceScope.cancel()
        
        // Release wake lock
        releaseWakeLock()
        
        super.onDestroy()
    }

    /**
     * Start Latin OCR processing
     */
    private fun startLatinOcrProcessing() {
        Log.d(TAG, "🔤 Starting Latin OCR processing in foreground service")
        
        if (isLatinOcrActive) {
            Log.d(TAG, "Latin OCR already active")
            return
        }
        
        isLatinOcrActive = true
        
        serviceScope.launch {
            try {
                // Check if Latin OCR is enabled
                val isEnabled = settings.Ocr.latinOcrEnabled.first()
                if (!isEnabled) {
                    Log.d(TAG, "Latin OCR is disabled in settings")
                    isLatinOcrActive = false
                    checkIfServiceShouldStop()
                    return@launch
                }
                
                // Initialize Latin OCR manager
                latinOcrManager = OcrManager(applicationContext, database)
                
                // Start foreground with notification
                startForeground(NOTIFICATION_ID, createServiceNotification())
                
                // Start continuous processing
                latinOcrManager?.startContinuousProcessing()
                
                // Start monitoring Latin OCR progress
                startLatinOcrMonitoring()
                
                Log.d(TAG, "✅ Latin OCR processing started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start Latin OCR processing", e)
                isLatinOcrActive = false
                checkIfServiceShouldStop()
            }
        }
    }

    /**
     * Start Devanagari OCR processing
     */
    private fun startDevanagariOcrProcessing() {
        Log.d(TAG, "🔤 Starting Devanagari OCR processing in foreground service")
        
        if (isDevanagariOcrActive) {
            Log.d(TAG, "Devanagari OCR already active")
            return
        }
        
        isDevanagariOcrActive = true
        
        serviceScope.launch {
            try {
                // Check if Devanagari OCR is enabled
                val isEnabled = settings.Ocr.devanagariOcrEnabled.first()
                if (!isEnabled) {
                    Log.d(TAG, "Devanagari OCR is disabled in settings")
                    isDevanagariOcrActive = false
                    checkIfServiceShouldStop()
                    return@launch
                }
                
                // Initialize Devanagari OCR manager
                devanagariOcrManager = DevanagariOcrManager(applicationContext, database)
                
                // Start foreground with notification if not already started
                if (!isLatinOcrActive) {
                    startForeground(NOTIFICATION_ID, createServiceNotification())
                }
                
                // Start continuous processing
                devanagariOcrManager?.startContinuousProcessing()
                
                // Start monitoring Devanagari OCR progress
                startDevanagariOcrMonitoring()
                
                Log.d(TAG, "✅ Devanagari OCR processing started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start Devanagari OCR processing", e)
                isDevanagariOcrActive = false
                checkIfServiceShouldStop()
            }
        }
    }

    /**
     * Stop Latin OCR processing
     */
    private fun stopLatinOcrProcessing() {
        Log.d(TAG, "🛑 Stopping Latin OCR processing")

        isLatinOcrActive = false
        latinOcrMonitorJob?.cancel()

        serviceScope.launch {
            try {
                latinOcrManager?.cancelAllOcr()
                latinOcrManager = null
                Log.d(TAG, "✅ Latin OCR processing stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping Latin OCR", e)
            }

            checkIfServiceShouldStop()
        }
    }

    /**
     * Stop Devanagari OCR processing
     */
    private fun stopDevanagariOcrProcessing() {
        Log.d(TAG, "🛑 Stopping Devanagari OCR processing")

        isDevanagariOcrActive = false
        devanagariOcrMonitorJob?.cancel()

        serviceScope.launch {
            try {
                devanagariOcrManager?.cancelAllOcr()
                devanagariOcrManager = null
                Log.d(TAG, "✅ Devanagari OCR processing stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping Devanagari OCR", e)
            }

            checkIfServiceShouldStop()
        }
    }

    /**
     * Check if service should stop (when no OCR processing is active)
     */
    private fun checkIfServiceShouldStop() {
        if (!isLatinOcrActive && !isDevanagariOcrActive) {
            Log.d(TAG, "🛑 No OCR processing active, stopping service")
            stopSelf()
        } else {
            // Update notification to reflect current state
            updateServiceNotification()
        }
    }

    /**
     * Observe Latin OCR progress. Reacts to DB writes made by OcrManager's own progress
     * monitor instead of running an independent 2-second poll loop of its own - the two
     * loops previously duplicated the same DB reads and notification updates in parallel.
     */
    private fun startLatinOcrMonitoring() {
        latinOcrMonitorJob?.cancel()
        latinOcrMonitorJob = serviceScope.launch {
            try {
                database.ocrProgressDao().getProgressFlow().collect { progress ->
                    if (progress != null && progress.isProcessing) {
                        renewWakeLock()
                        updateServiceNotification()
                    } else if (progress != null && !progress.isPaused) {
                        // isComplete only means "every image succeeded" -- a run that ends with
                        // one or more images permanently failing (never going to succeed on
                        // retry) never satisfies it, so this branch never fired and the
                        // foreground service (and its "Processing images in background"
                        // notification) ran forever even after the worker had genuinely stopped.
                        // !isProcessing && !isPaused is the actual "nothing left running" signal.
                        Log.d(TAG, "Latin OCR processing finished (isComplete=${progress.isComplete})")
                        isLatinOcrActive = false
                        checkIfServiceShouldStop()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Latin OCR monitoring", e)
                checkIfServiceShouldStop()
            }
        }
    }

    /**
     * Observe Devanagari OCR progress via Flow, same rationale as startLatinOcrMonitoring().
     */
    private fun startDevanagariOcrMonitoring() {
        devanagariOcrMonitorJob?.cancel()
        devanagariOcrMonitorJob = serviceScope.launch {
            try {
                database.devanagariOcrProgressDao().getProgressFlow().collect { progress ->
                    if (progress != null && progress.isProcessing) {
                        renewWakeLock()
                        updateServiceNotification()
                    } else if (progress != null && !progress.isPaused) {
                        // See the identical fix/comment in startLatinOcrMonitoring() -- isComplete
                        // alone missed the "finished with permanent failures" case.
                        Log.d(TAG, "Devanagari OCR processing finished (isComplete=${progress.isComplete})")
                        isDevanagariOcrActive = false
                        checkIfServiceShouldStop()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Devanagari OCR monitoring", e)
                checkIfServiceShouldStop()
            }
        }
    }

    /**
     * Create notification channel for the foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OCR Background Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OCR processing running in background"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the service notification
     */
    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_ocr_settings", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            isLatinOcrActive && isDevanagariOcrActive -> "OCR Processing (Latin + Devanagari)"
            isLatinOcrActive -> "OCR Processing (Latin)"
            isDevanagariOcrActive -> "OCR Processing (Devanagari)"
            else -> "OCR Service Running"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Processing images in background")
            .setSmallIcon(R.drawable.ocr)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    /**
     * Update the service notification with current progress
     */
    private fun updateServiceNotification() {
        serviceScope.launch {
            try {
                val notification = createServiceNotification()
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update service notification", e)
            }
        }
    }

    /**
     * Acquire wake lock to prevent device from sleeping during processing. Non-reference-
     * counted so renewWakeLock() can call acquire() again later purely to push the timeout
     * forward, without needing a matching extra release().
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PhotoLex::OcrForegroundService"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "🔋 Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Push the wake lock's auto-release timeout forward. Calling acquire(timeout) again on an
     * already-held, non-reference-counted lock just resets its timer rather than stacking --
     * called on every progress tick while actively processing so a scan longer than the flat
     * timeout used to be doesn't silently lose the wake lock partway through.
     */
    private fun renewWakeLock() {
        try {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to renew wake lock", e)
        }
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "🔋 Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
}
