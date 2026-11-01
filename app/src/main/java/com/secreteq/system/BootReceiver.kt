package com.secreteq.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Startet den EQ-Service nach Reboot (sofern Nutzer die App zuvor genutzt hat).
 * Accessibility muss der Nutzer weiter manuell aktiviert lassen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        try {
            val i = Intent(context, EqService::class.java).setAction(EqService.ACTION_APPLY)
            context.startForegroundService(i)
            Log.i(TAG, "EqService started after boot")
        } catch (t: Throwable) {
            Log.w(TAG, "boot start failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "SecretEQ.Boot"
    }
}
