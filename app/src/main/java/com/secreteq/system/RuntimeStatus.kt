package com.secreteq.system

import com.secreteq.profile.ProfileManager

/**
 * Geteilter Laufzeitstatus für Notification, UI und Tile.
 * Wird vom AccessibilityService und EqService aktualisiert.
 */
object RuntimeStatus {
    @Volatile var packageName: String? = null
    @Volatile var appLabel: String = "—"
    @Volatile var mode: ProfileManager.Mode = ProfileManager.Mode.DEFAULT
    @Volatile var profileName: String = "Standard"
    @Volatile var isGame: Boolean = false
    @Volatile var detectionSource: String = "—"
    @Volatile var autoSwitch: Boolean = true
    @Volatile var eqEnabled: Boolean = true
    @Volatile var audioSummary: String = "Noch nicht gestartet"
    @Volatile var audioEffectCreated: Boolean = false
    @Volatile var audioEffectEnabled: Boolean = false
    @Volatile var audioBands: Int = 0
    @Volatile var externalAudioProven: Boolean = false
    @Volatile var lastUpdateMs: Long = 0L

    fun modeLabel(): String = when (mode) {
        ProfileManager.Mode.GAMING -> "GAMING"
        ProfileManager.Mode.BROWSER_VIDEO -> "BROWSER/VIDEO"
        ProfileManager.Mode.CUSTOM_APP -> "APP-PROFIL"
        ProfileManager.Mode.DEFAULT -> "STANDARD"
    }

    fun detectionLabel(): String = when {
        packageName.isNullOrBlank() -> "KEINE"
        isGame -> "SPIEL ERKANNT"
        mode == ProfileManager.Mode.BROWSER_VIDEO -> "BROWSER/VIDEO ERKANNT"
        else -> "APP ERKANNT"
    }

    fun audioLine(): String {
        if (!audioEffectCreated) return "Effekt nicht verfügbar (Session 0)"
        val en = if (audioEffectEnabled && eqEnabled) "AKTIV" else "PAUSIERT"
        val prove = if (externalAudioProven) "externe Session angehängt"
        else "fremde Apps: nicht nachgewiesen"
        return "$en · $audioBands Bänder · $prove"
    }
}
