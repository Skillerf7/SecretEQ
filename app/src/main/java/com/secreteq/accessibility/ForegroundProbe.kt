package com.secreteq.accessibility

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Optionale Zusatz-Erkennung über UsageStatsManager.
 * Erfordert PACKAGE_USAGE_STATS (Nutzer muss in den Einstellungen freigeben).
 * Dient als Fallback / Test-Hilfe, nicht als alleinige Quelle.
 */
object ForegroundProbe {
    private const val TAG = "SecretEQ.Probe"

    fun hasUsageAccess(ctx: Context): Boolean {
        return try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            Log.w(TAG, "hasUsageAccess: ${t.message}")
            false
        }
    }

    fun openUsageAccessSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "open usage settings: ${t.message}")
        }
    }

    /**
     * Liefert das zuletzt im Vordergrund genutzte Paket (letzte Minuten).
     * null wenn keine Berechtigung oder keine Daten.
     */
    fun queryRecentForeground(ctx: Context): String? {
        if (!hasUsageAccess(ctx)) return null
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 60_000L
            val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            if (list.isNullOrEmpty()) return null
            val top = list.maxByOrNull { it.lastTimeUsed } ?: return null
            if (end - top.lastTimeUsed > 30_000L) return null
            top.packageName
        } catch (t: Throwable) {
            Log.w(TAG, "queryRecentForeground: ${t.message}")
            null
        }
    }
}
