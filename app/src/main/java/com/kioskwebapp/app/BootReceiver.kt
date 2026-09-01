package com.kioskwebapp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Launches the kiosk automatically after the device finishes booting,
 * so the tablet returns straight to the configured website with no
 * manual interaction required.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
