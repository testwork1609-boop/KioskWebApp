package com.kioskwebapp.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
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
 *  - hiding system bars for a fullscreen look,
 *  - blocking the system Back button,
 *  - restarting the whole app process on demand (admin panel action).
 *
 * Note: this app no longer uses Android Lock Task Mode ("app pinning") or
 * FLAG_KEEP_SCREEN_ON - the device's own screen timeout and Home/Recents
 * buttons behave normally. The fullscreen look and the blocked in-app Back
 * button are purely cosmetic/UX, not a hard system-level lock.
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

        hideSystemUi()

        setContent {
            MaterialTheme {
                KioskApp(
                    prefsManager = prefsManager,
                    connectivityManager = connectivityManager,
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
