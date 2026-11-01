package com.secreteq.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.secreteq.profile.GameRegistry
import com.secreteq.profile.ProfileManager
import com.secreteq.system.EqService

/**
 * Zuverlässige Vordergrund-App-Erkennung für Profilumschaltung.
 * Kein Content-Auslesen, keine Gesten, keine Audioverarbeitung.
 *
 * Events: WINDOW_STATE_CHANGED, WINDOWS_CHANGED (+ Content nur als Fallback-Trigger).
 * Zusätzlich: rootInActiveWindow / windows-Scan für Vollbildspiele ohne klassische Views.
 */
class AppDetectionService : AccessibilityService() {

    private lateinit var profiles: ProfileManager
    private lateinit var games: GameRegistry
    private var lastPackage: String? = null
    private var lastSwitchUptime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        profiles = ProfileManager(this)
        games = GameRegistry(this)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 50
        }
        Log.i(TAG, "AppDetectionService connected")
        // Initial-Scan
        handler.postDelayed({ pollForeground("onConnect") }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!profiles.autoSwitchEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) scheduleApply(pkg, "WINDOW_STATE")
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Vollbildspiele wechseln oft nur die Window-Liste
                pollForeground("WINDOWS_CHANGED")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // selten nutzen — nur wenn Paket im Event steht
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank() && pkg != lastPackage) {
                    scheduleApply(pkg, "CONTENT")
                }
            }
        }
    }

    /** Debounce: schnelle Event-Fluten zu einem Umschaltvorgang zusammenfassen. */
    private fun scheduleApply(pkg: String, source: String) {
        if (isIgnored(pkg)) return
        pendingPkg = pkg
        handler.removeCallbacksAndMessages(TOKEN_APPLY)
        handler.postAtTime({
            val p = pendingPkg ?: return@postAtTime
            pendingPkg = null
            if (p != lastPackage) {
                Log.i(TAG, "detect [$source] → $p")
                applyProfileFor(p, source)
            }
        }, TOKEN_APPLY, android.os.SystemClock.uptimeMillis() + DEBOUNCE_MS)
    }

    private fun pollForeground(source: String) {
        val pkg = resolveForegroundPackage() ?: return
        scheduleApply(pkg, source)
    }

    /**
     * Ermittelt die Vordergrund-App auch bei OpenGL/Vulkan/Unity-Vollbild:
     * 1) active window package
     * 2) windows() application window mit max Layer
     * 3) rootInActiveWindow.packageName
     */
    private fun resolveForegroundPackage(): String? {
        try {
            val wins = windows
            if (wins != null) {
                var best: AccessibilityWindowInfo? = null
                for (w in wins) {
                    if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    if (!w.isActive && !w.isFocused) continue
                    if (best == null || w.layer > best.layer) best = w
                }
                val root = best?.root
                val pkg = root?.packageName?.toString()
                if (!pkg.isNullOrBlank() && !isIgnored(pkg)) return pkg
            }
        } catch (t: Throwable) {
            Log.w(TAG, "windows scan: ${t.message}")
        }
        try {
            val root = rootInActiveWindow
            val pkg = root?.packageName?.toString()
            if (!pkg.isNullOrBlank() && !isIgnored(pkg)) return pkg
        } catch (t: Throwable) {
            Log.w(TAG, "rootInActiveWindow: ${t.message}")
        }
        return null
    }

    private fun isIgnored(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui") return true
        if (pkg == "com.samsung.android.app.cocktailbarservice") return true
        if (pkg.startsWith("com.android.launcher")) return true
        if (pkg == "com.sec.android.app.launcher") return true
        if (pkg == "com.google.android.permissioncontroller") return true
        if (pkg == "com.android.permissioncontroller") return true
        if (pkg == "com.google.android.packageinstaller") return true
        if (pkg == "com.samsung.android.packageinstaller") return true
        if (pkg == "android") return true
        return false
    }

    private fun applyProfileFor(pkg: String, source: String) {
        val now = android.os.SystemClock.uptimeMillis()
        // harte Anti-Loop-Sperre
        if (pkg == lastPackage && now - lastSwitchUptime < 400) return
        lastPackage = pkg
        lastSwitchUptime = now

        val info = profiles.resolveForPackage(pkg)
        profiles.updateLastActive(info)
        profiles.recordDetection(pkg, source, info.mode.name, games.isGame(pkg))

        Log.i(
            TAG,
            "App → $pkg | game=${games.isGame(pkg)} | mode=${info.mode} | profile=${info.profileName} | via=$source"
        )

        val i = Intent(this, EqService::class.java).apply {
            action = EqService.ACTION_APPLY_PROFILE
            putExtra(EqService.EXTRA_PACKAGE, pkg)
            putExtra(EqService.EXTRA_MODE, info.mode.name)
            putExtra(EqService.EXTRA_SOURCE, source)
        }
        try {
            startForegroundService(i)
        } catch (t: Throwable) {
            Log.w(TAG, "EqService start failed: ${t.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SecretEQ.A11y"
        private const val DEBOUNCE_MS = 120L
        private val TOKEN_APPLY = Any()
    }
}
