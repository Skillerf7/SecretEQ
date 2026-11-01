package com.secreteq.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(24)
class SecretEqTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val i = Intent(this, EqService::class.java).setAction(EqService.ACTION_TOGGLE_EQ)
        startForegroundService(i)
        // kurz verzögert aktualisieren
        qsTile?.let {
            updateTile()
        }
        // App optional öffnen bei langem Bedarf — hier Toggle reicht
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = RuntimeStatus.eqEnabled && RuntimeStatus.audioEffectCreated
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Secret EQ"
        tile.subtitle = if (Build.VERSION.SDK_INT >= 29) {
            "${RuntimeStatus.modeLabel()} · ${if (RuntimeStatus.eqEnabled) "AN" else "AUS"}"
        } else null
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    requestListeningState(
                        ctx,
                        android.content.ComponentName(ctx, SecretEqTileService::class.java)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
