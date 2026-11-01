package com.secreteq.profile

import android.content.Context
import com.secreteq.data.EqPrefs
import com.secreteq.dsp.EqPresets
import com.secreteq.dsp.EqState
import org.json.JSONObject

/**
 * Multi-Profil + Auflösung App → Profil.
 * Spieleerkennung über GameRegistry (Built-in inkl. GRID + manuelle Liste).
 */
class ProfileManager(ctx: Context) {
    private val prefs = EqPrefs(ctx)
    private val p = ctx.applicationContext.getSharedPreferences("secret_eq_profiles", Context.MODE_PRIVATE)
    val games = GameRegistry(ctx)

    enum class Mode {
        DEFAULT,
        GAMING,
        BROWSER_VIDEO,
        CUSTOM_APP
    }

    data class ActiveInfo(
        val mode: Mode,
        val packageName: String?,
        val profileName: String,
        val state: EqState
    )

    data class DetectionLog(
        val packageName: String,
        val source: String,
        val mode: String,
        val isGame: Boolean,
        val timeMs: Long = System.currentTimeMillis()
    )

    /** Browser- und Video-/Streaming-Pakete. */
    private val browserVideoPackages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.sec.android.app.sbrowser",
        "org.mozilla.firefox", "com.opera.browser", "com.brave.browser",
        "com.microsoft.emmx", "com.duckduckgo.mobile.android",
        "com.google.android.youtube", "com.vanced.android.youtube", "com.google.android.apps.youtube.music",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
        "com.hulu.plus", "com.spotify.music", "com.apple.android.music",
        "tv.twitch.android.app", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.instagram.android", "com.facebook.katana", "com.facebook.orca",
        "com.reddit.frontpage", "com.twitter.android",
        "com.crunchyroll.crunchyroid", "com.plexapp.android", "org.videolan.vlc",
        "com.mxtech.videoplayer.ad", "com.google.android.videos"
    )

    fun isGaming(pkg: String): Boolean = games.isGame(pkg)

    fun isBrowserOrVideo(pkg: String): Boolean = browserVideoPackages.contains(pkg) ||
        pkg.contains("browser", ignoreCase = true) ||
        pkg.contains("youtube", ignoreCase = true)

    fun getDefaultState(): EqState = prefs.load()

    fun saveDefaultState(state: EqState) {
        prefs.save(state)
    }

    fun getGamingPreset(): EqState {
        val custom = p.getString("profile_gaming", null)
        if (custom != null) {
            return try {
                EqPrefs.stateFromJson(JSONObject(custom))
            } catch (_: Exception) {
                null
            } ?: gamingFallback()
        }
        return gamingFallback()
    }

    private fun gamingFallback(): EqState = EqPresets.ALL["Hardtekk"]!!.copy(
        presetName = "Gaming",
        enabled = true,
        preDb = -4f
    )

    fun saveGamingPreset(state: EqState) {
        p.edit().putString("profile_gaming", EqPrefs.stateToJson(state.copy(presetName = "Gaming")).toString()).apply()
    }

    fun getBrowserVideoPreset(): EqState {
        val custom = p.getString("profile_browser_video", null)
        if (custom != null) {
            return try {
                EqPrefs.stateFromJson(JSONObject(custom))
            } catch (_: Exception) {
                null
            } ?: browserFallback()
        }
        return browserFallback()
    }

    private fun browserFallback(): EqState = EqPresets.ALL["Vocal"]!!.copy(
        presetName = "Browser/Video",
        enabled = true,
        preDb = -2f
    )

    fun saveBrowserVideoPreset(state: EqState) {
        p.edit().putString("profile_browser_video", EqPrefs.stateToJson(state.copy(presetName = "Browser/Video")).toString()).apply()
    }

    fun getAppProfile(pkg: String): EqState? {
        val json = p.getString("app_$pkg", null) ?: return null
        return try {
            EqPrefs.stateFromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun saveAppProfile(pkg: String, state: EqState) {
        p.edit().putString("app_$pkg", EqPrefs.stateToJson(state).toString()).apply()
    }

    fun clearAppProfile(pkg: String) {
        p.edit().remove("app_$pkg").apply()
    }

    /**
     * Reihenfolge: individuelles App-Profil → Gaming (Registry) → Browser/Video → Default.
     */
    fun resolveForPackage(pkg: String?): ActiveInfo {
        if (pkg.isNullOrBlank() || pkg == "com.secreteq" || pkg == "com.secreteq.debug") {
            val s = getDefaultState()
            return ActiveInfo(Mode.DEFAULT, pkg, s.presetName, s)
        }
        getAppProfile(pkg)?.let {
            return ActiveInfo(Mode.CUSTOM_APP, pkg, it.presetName.ifBlank { pkg }, it)
        }
        if (isGaming(pkg)) {
            val s = getGamingPreset()
            return ActiveInfo(Mode.GAMING, pkg, s.presetName, s)
        }
        if (isBrowserOrVideo(pkg)) {
            val s = getBrowserVideoPreset()
            return ActiveInfo(Mode.BROWSER_VIDEO, pkg, s.presetName, s)
        }
        val s = getDefaultState()
        return ActiveInfo(Mode.DEFAULT, pkg, s.presetName, s)
    }

    var lastActive: ActiveInfo? = null
        private set

    fun updateLastActive(info: ActiveInfo) {
        lastActive = info
    }

    var lastDetection: DetectionLog? = null
        private set

    fun recordDetection(pkg: String, source: String, mode: String, isGame: Boolean) {
        lastDetection = DetectionLog(pkg, source, mode, isGame)
        p.edit()
            .putString("last_detect_pkg", pkg)
            .putString("last_detect_source", source)
            .putString("last_detect_mode", mode)
            .putBoolean("last_detect_game", isGame)
            .putLong("last_detect_time", System.currentTimeMillis())
            .apply()
    }

    fun loadLastDetection(): DetectionLog? {
        val pkg = p.getString("last_detect_pkg", null) ?: return lastDetection
        return DetectionLog(
            packageName = pkg,
            source = p.getString("last_detect_source", "?") ?: "?",
            mode = p.getString("last_detect_mode", "?") ?: "?",
            isGame = p.getBoolean("last_detect_game", false),
            timeMs = p.getLong("last_detect_time", 0L)
        )
    }

    var autoSwitchEnabled: Boolean
        get() = p.getBoolean("auto_switch", true)
        set(v) = p.edit().putBoolean("auto_switch", v).apply()
}
