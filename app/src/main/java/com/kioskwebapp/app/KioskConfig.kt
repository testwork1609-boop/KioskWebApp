package com.kioskwebapp.app

/**
 * Fixed, build-time configuration of the two kiosk destinations.
 *
 * These used to be entered by the admin during the setup wizard; they are
 * now hardcoded here instead, so EDIT THESE TWO LINES to your real
 * addresses before building the APK, then rebuild via GitHub Actions.
 */
object KioskConfig {
    const val URL_PRIORYTETY = "https://monitor.host171356.xce.pl/monitor.php"
    const val URL_PRZEGLADY = "https://serwis.julita.ovh/URPN/index_tab.php?loc=5"
}
