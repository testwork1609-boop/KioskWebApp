package com.kioskwebapp.app

import android.app.admin.DeviceAdminReceiver

/**
 * Required receiver for enabling this app as Device Owner, which unlocks
 * full Lock Task (kiosk) capability without the "screen pinning" prompt.
 * See README.md for the one-time `adb` command needed on a factory-reset
 * tablet to grant Device Owner status.
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver()
