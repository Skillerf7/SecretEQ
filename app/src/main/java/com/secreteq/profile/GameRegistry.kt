package com.secreteq.profile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONArray

/**
 * Erkennt Spiele möglichst zuverlässig und speichert manuelle Zuordnungen.
 *
 * Signale (OR — eines reicht):
 * 1) Nutzer-Allowlist
 * 2) Built-in-Allowlist (GRID usw.)
 * 3) ApplicationInfo.FLAG_IS_GAME (vom System/Play gesetzt)
 * 4) ApplicationInfo.category == CATEGORY_GAME (API 26+)
 * 5) Paket-/Label-Heuristik (game, unity, unreal, feral, …)
 *
 * Nutzer kann Apps explizit hinzufügen oder entfernen (Entfernen gilt nur für User-Einträge;
 * System-FLAG_IS_GAME bleibt erkannt, außer Nutzer setzt eine Blacklist).
 */
class GameRegistry(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val p = appCtx.getSharedPreferences("secret_eq_games", Context.MODE_PRIVATE)
    private val pm = appCtx.packageManager

    val builtinGames: Map<String, String> = linkedMapOf(
        "com.feralinteractive.gridas" to "GRID Autosport",
        "com.feralinteractive.gridautosport_edition_android" to "GRID Autosport Custom Edition",
        "com.feralinteractive.gridlegends_android" to "GRID Legends",
        "com.tencent.ig" to "PUBG Mobile",
        "com.pubg.krmobile" to "PUBG KR",
        "com.activision.callofduty.shooter" to "Call of Duty Mobile",
        "com.garena.game.codm" to "COD Mobile (Garena)",
        "com.miHoYo.GenshinImpact" to "Genshin Impact",
        "com.miHoYo.Yuanshen" to "Genshin Impact (CN)",
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.supercell.clashroyale" to "Clash Royale",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.epicgames.fortnite" to "Fortnite",
        "com.roblox.client" to "Roblox",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.riotgames.league.wildrift" to "Wild Rift",
        "com.ea.gp.fifamobile" to "FIFA Mobile",
        "com.nintendo.zara" to "Mario Kart Tour",
        "com.kiloo.subwaysurf" to "Subway Surfers",
        "com.king.candycrushsaga" to "Candy Crush",
        "com.dts.freefireth" to "Free Fire",
        "com.mobile.legends" to "Mobile Legends",
        "com.ngame.allstar.eu" to "Arena of Valor"
    )

    /** Explizit KEINE Spiele (verhindert False Positives). */
    private val blacklist = setOf(
        "com.android.chrome", "com.sec.android.app.sbrowser", "com.brave.browser",
        "com.google.android.youtube", "com.spotify.music", "com.netflix.mediaclient",
        "com.instagram.android", "com.facebook.katana", "com.whatsapp",
        "com.android.settings", "com.sec.android.app.launcher"
    )

    fun userGamePackages(): Set<String> {
        val raw = p.getString("user_games", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun userBlacklist(): Set<String> {
        val raw = p.getString("user_blacklist", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun addUserGame(pkg: String, label: String? = null) {
        val set = userGamePackages().toMutableSet()
        set.add(pkg)
        saveJsonSet("user_games", set)
        // von Blacklist nehmen
        val bl = userBlacklist().toMutableSet()
        if (bl.remove(pkg)) saveJsonSet("user_blacklist", bl)
        if (!label.isNullOrBlank()) p.edit().putString("label_$pkg", label).apply()
    }

    fun removeUserGame(pkg: String) {
        val set = userGamePackages().toMutableSet()
        set.remove(pkg)
        saveJsonSet("user_games", set)
        // Wenn System es trotzdem als Spiel sieht: auf Blacklist setzen
        if (systemMarksAsGame(pkg)) {
            val bl = userBlacklist().toMutableSet()
            bl.add(pkg)
            saveJsonSet("user_blacklist", bl)
        }
        p.edit().remove("label_$pkg").apply()
    }

    private fun saveJsonSet(key: String, set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        p.edit().putString(key, arr.toString()).apply()
    }

    fun labelFor(pkg: String): String {
        p.getString("label_$pkg", null)?.let { return it }
        builtinGames[pkg]?.let { return it }
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    /** System-Markierung: FLAG_IS_GAME oder category GAME. */
    fun systemMarksAsGame(pkg: String): Boolean {
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            if (ai.flags and ApplicationInfo.FLAG_IS_GAME != 0) return true
            if (Build.VERSION.SDK_INT >= 26) {
                if (ai.category == ApplicationInfo.CATEGORY_GAME) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun heuristicGame(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (blacklist.contains(pkg)) return false
        // Publisher / Engines
        if (lower.contains("feralinteractive")) return true
        if (lower.contains("unity3d") || lower.contains("unity.")) return true
        if (lower.contains("unreal") || lower.contains("epicgames")) return true
        if (lower.contains("gameloft") || lower.contains("supercell")) return true
        if (lower.contains("mihoyo") || lower.contains("hoyoverse")) return true
        if (lower.contains("codemasters") || lower.contains("ea.gp")) return true
        // generische Segmente
        if (lower.contains(".game.") || lower.contains(".games.") || lower.contains(".gaming.")) return true
        if (lower.endsWith(".game") || lower.endsWith(".games")) return true
        if (lower.contains("game") && (
                lower.contains("racing") || lower.contains("shoot") ||
                    lower.contains("rpg") || lower.contains("sport") ||
                    lower.contains("battle") || lower.contains("legend") ||
                    lower.contains("craft") || lower.contains("clash")
                )
        ) return true
        // Label-Heuristik
        return try {
            val label = labelFor(pkg).lowercase()
            label.contains("game") || label.contains("spiel") ||
                label.contains("racing") || label.contains("rpg")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Zentrale Entscheidung: Ist dieses Paket ein Spiel?
     * Reihenfolge: Blacklist → User → Built-in → System-Flag → Heuristik
     */
    fun isGame(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (userBlacklist().contains(pkg)) return false
        if (blacklist.contains(pkg)) return false
        if (userGamePackages().contains(pkg)) return true
        if (builtinGames.containsKey(pkg)) return true
        if (systemMarksAsGame(pkg)) return true
        if (heuristicGame(pkg)) return true
        return false
    }

    /** Warum erkannt (für UI/Debug). */
    fun detectionReason(pkg: String): String {
        if (userBlacklist().contains(pkg)) return "Blacklist (Nutzer)"
        if (blacklist.contains(pkg)) return "System-Blacklist"
        if (userGamePackages().contains(pkg)) return "Manuelle Liste"
        if (builtinGames.containsKey(pkg)) return "Built-in"
        if (systemMarksAsGame(pkg)) return "System FLAG_IS_GAME / CATEGORY_GAME"
        if (heuristicGame(pkg)) return "Heuristik"
        return "Kein Spiel"
    }

    data class InstalledApp(
        val packageName: String,
        val label: String,
        val isGameFlag: Boolean,
        val reason: String
    )

    fun listInstalledLaunchable(): List<InstalledApp> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == appCtx.packageName) return@mapNotNull null
            val label = try {
                ri.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) {
                pkg
            }
            val game = isGame(pkg)
            InstalledApp(pkg, label, game, detectionReason(pkg))
        }.distinctBy { it.packageName }.sortedWith(
            compareByDescending<InstalledApp> { it.isGameFlag }.thenBy { it.label.lowercase() }
        )
    }

    /**
     * Scannt alle Launchable-Apps und trägt System-Spiele + Heuristik-Treffer
     * in die User-Liste ein (einmalig / auf Knopfdruck).
     * @return Anzahl neu hinzugefügter Pakete
     */
    fun autoRegisterInstalledGames(): Int {
        var added = 0
        listInstalledLaunchable().forEach { app ->
            if (app.isGameFlag && !userGamePackages().contains(app.packageName) &&
                !builtinGames.containsKey(app.packageName)
            ) {
                // nur System/Heuristik — speichern, damit Profil stabil bleibt
                if (systemMarksAsGame(app.packageName) || heuristicGame(app.packageName)) {
                    addUserGame(app.packageName, app.label)
                    added++
                }
            }
        }
        Log.i(TAG, "autoRegisterInstalledGames: +$added")
        return added
    }

    fun allRegisteredGames(): List<Pair<String, String>> {
        val out = linkedMapOf<String, String>()
        builtinGames.forEach { (k, v) -> out[k] = v }
        userGamePackages().forEach { pkg -> out[pkg] = labelFor(pkg) }
        // zusätzlich: aktuell per System-Flag erkannte, ohne sie zwingend zu speichern
        try {
            listInstalledLaunchable().filter { it.isGameFlag }.forEach { app ->
                if (!out.containsKey(app.packageName)) {
                    out[app.packageName] = app.label
                }
            }
        } catch (_: Exception) {}
        return out.toList()
    }

    companion object {
        private const val TAG = "SecretEQ.Games"
    }
}
