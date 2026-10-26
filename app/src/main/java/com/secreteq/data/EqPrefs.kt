package com.secreteq.data

import android.content.Context
import com.secreteq.dsp.Band
import com.secreteq.dsp.EqMath
import com.secreteq.dsp.EqState
import org.json.JSONArray
import org.json.JSONObject

class EqPrefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("secret_eq", Context.MODE_PRIVATE)

    fun load(): EqState {
        val json = p.getString("state", null) ?: return EqState()
        return try {
            fromJson(JSONObject(json))
        } catch (_: Exception) {
            EqState()
        }
    }

    fun save(s: EqState) {
        p.edit().putString("state", toJson(s).toString()).apply()
    }

    private fun toJson(s: EqState): JSONObject = JSONObject().apply {
        put("enabled", s.enabled)
        put("preDb", s.preDb.toDouble())
        put("bass", s.bass.toDouble())
        put("treble", s.treble.toDouble())
        put("bassBoost", s.bassBoost)
        put("trebleBoost", s.trebleBoost)
        put("limiter", s.limiter)
        put("autoHeadroom", s.autoHeadroom)
        put("pLowDb", s.pLowDb.toDouble())
        put("pHighDb", s.pHighDb.toDouble())
        put("presetName", s.presetName)
        put("bands", JSONArray().also { arr ->
            s.bands.forEach { b ->
                arr.put(JSONObject().put("f", b.f.toDouble()).put("g", b.g.toDouble()).put("q", b.q.toDouble()))
            }
        })
    }

    private fun fromJson(o: JSONObject): EqState {
        val arr = o.optJSONArray("bands")
        val bands = if (arr != null && arr.length() == EqMath.BAND_COUNT) {
            List(arr.length()) { i ->
                val b = arr.getJSONObject(i)
                Band(b.getDouble("f").toFloat(), b.getDouble("g").toFloat(), b.getDouble("q").toFloat())
            }
        } else EqMath.flatBands()
        return EqState(
            enabled = o.optBoolean("enabled", true),
            preDb = o.optDouble("preDb", 0.0).toFloat(),
            bands = bands,
            bass = o.optDouble("bass", 0.0).toFloat(),
            treble = o.optDouble("treble", 0.0).toFloat(),
            bassBoost = o.optBoolean("bassBoost", false),
            trebleBoost = o.optBoolean("trebleBoost", false),
            limiter = o.optBoolean("limiter", true),
            autoHeadroom = o.optBoolean("autoHeadroom", true),
            pLowDb = o.optDouble("pLowDb", 0.0).toFloat(),
            pHighDb = o.optDouble("pHighDb", 0.0).toFloat(),
            presetName = o.optString("presetName", "Custom")
        )
    }
}
