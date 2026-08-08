package com.peeyupatel.phototextsearch.helpers

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

private const val TAG = "OEM_BACKGROUND_SETTINGS"

/**
 * Deep-links straight into the OEM-specific "auto-start"/"allow background activity" settings
 * screen for known aggressive Android skins (MIUI, ColorOS, FuntouchOS/OriginOS, EMUI/MagicUI,
 * OneUI), instead of just describing where to find it. The standard
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog (already requested separately) only
 * covers stock Android's doze/battery-optimization system -- these OEMs layer a second, fully
 * proprietary background-app killer on top that stock APIs cannot query or grant, and that
 * silently resets whenever the app is reinstalled.
 *
 * Component names vary across ROM versions and are not part of any public API, so every
 * candidate is tried in order (newest/most common first) and wrapped so a missing/renamed
 * activity on a given ROM just falls through to the next candidate instead of crashing.
 *
 * @return true if an OEM-specific settings screen was actually opened, false if none of the
 * known candidates exist on this device (caller should fall back to the generic app-info page).
 */
fun tryOpenOemAutoStartSettings(context: Context, manufacturer: String = Build.MANUFACTURER): Boolean {
    val lower = manufacturer.lowercase()

    val candidates: List<ComponentName> = when {
        lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        )

        lower.contains("oppo") || lower.contains("realme") || lower.contains("oneplus") -> listOf(
            // Newer ColorOS/RealmeUI builds moved this from the "coloros"-prefixed safecenter
            // package to "oplus"-prefixed battery package -- try the modern one first.
            ComponentName("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.color.safecenter", "com.color.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )

        lower.contains("vivo") || lower.contains("iqoo") -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")
        )

        lower.contains("huawei") || lower.contains("honor") -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )

        lower.contains("samsung") -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )

        else -> emptyList()
    }

    for (component in candidates) {
        try {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "OEM settings screen not found: $component, trying next candidate")
        } catch (e: Exception) {
            Log.d(TAG, "Failed to open OEM settings screen $component: ${e.message}")
        }
    }

    return false
}

/** True if [tryOpenOemAutoStartSettings] has a known candidate for this manufacturer at all --
 * used to decide whether to show an OEM-specific action in the UI in the first place. */
fun hasKnownOemAutoStartSettings(manufacturer: String = Build.MANUFACTURER): Boolean {
    val lower = manufacturer.lowercase()
    return lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") ||
        lower.contains("oppo") || lower.contains("realme") || lower.contains("oneplus") ||
        lower.contains("vivo") || lower.contains("iqoo") ||
        lower.contains("huawei") || lower.contains("honor") ||
        lower.contains("samsung")
}
