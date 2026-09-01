package com.kioskwebapp.app

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Single-activity kiosk host.
 *
 * Responsibilities kept here (as opposed to in Compose UI):
 *  - keeping the screen on and hiding system bars for true fullscreen,
 *  - entering/leaving Android Lock Task Mode,
 *  - blocking the system Back button,
 *  - restarting the whole app process on demand (admin panel action).
 *
 * All configuration, wizard, WebView and admin-panel UI lives in KioskUi.kt.
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PrefsManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Keep the display awake at all times - this is a 24/7 information screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContent {
            MaterialTheme {
                KioskApp(
                    prefsManager = prefsManager,
                    connectivityManager = connectivityManager,
                    enterLockTask = { enterLockTask() },
                    exitLockTask = { exitLockTask() },
                    restartApp = { restartApp() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Enters Lock Task Mode.
     *
     * If the app has been granted Device Owner status (see README.md), it is
     * first allow-listed via DevicePolicyManager, which produces a *silent*,
     * fully locked kiosk with no exit prompt for the end user - Home, Recents
     * and the status bar are unavailable system-wide.
     *
     * If the app is NOT Device Owner, Android still allows "screen pinning"
     * (startLockTask without allow-listing), which blocks Home/Recents for a
     * normal user but shows a one-time system explanation the first time it
     * is used. Full silent kiosk behaviour requires Device Owner.
     */
    fun enterLockTask() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, KioskDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
            }
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        } catch (e: Exception) {
            // Lock Task not permitted in this configuration; the app still
            // runs fullscreen, just without the hard system-level lock.
        }
    }

    fun exitLockTask() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            // Not currently in lock task mode - nothing to do.
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally blocked: a normal kiosk user must never be able to
        // back out of the WebView. Admins use the corner-tap gesture instead.
    }
}
