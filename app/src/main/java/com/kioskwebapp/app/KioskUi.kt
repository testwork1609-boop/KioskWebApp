package com.kioskwebapp.app

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Top-level app switch: setup wizard vs. running kiosk
// ---------------------------------------------------------------------------

private enum class KioskTab { PRIORYTETY, PRZEGLADY }

@Composable
fun KioskApp(
    prefsManager: PrefsManager,
    connectivityManager: ConnectivityManager,
    enterLockTask: () -> Unit,
    exitLockTask: () -> Unit,
    restartApp: () -> Unit
) {
    var configured by remember { mutableStateOf(prefsManager.isConfigured()) }

    if (!configured) {
        SetupWizard(
            onFinished = { urlPriorytety, urlPrzeglady, password, refreshMinutes ->
                prefsManager.saveConfiguration(urlPriorytety, urlPrzeglady, password, refreshMinutes)
                configured = true
            }
        )
    } else {
        KioskWebScreen(
            prefsManager = prefsManager,
            connectivityManager = connectivityManager,
            enterLockTask = enterLockTask,
            exitLockTask = exitLockTask,
            restartApp = restartApp,
            onReset = { configured = false }
        )
    }
}

// ---------------------------------------------------------------------------
// First-run setup wizard (3 steps, per spec)
// ---------------------------------------------------------------------------

/**
 * Text field colors for the setup wizard, which sits on a dark background.
 * Without this, Material3's default text color is dark and becomes
 * unreadable ("nie widać co się wpisuje") on the dark wizard surface.
 */
@Composable
private fun wizardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color(0xFF90A4AE),
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color(0xFFAAB4C0),
    focusedPlaceholderColor = Color(0xFFAAB4C0),
    unfocusedPlaceholderColor = Color(0xFFAAB4C0),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

private fun isValidUrl(url: String): Boolean {
    return (url.startsWith("https://") || url.startsWith("http://")) && url.length > 10 &&
        url.substringAfter("://").contains(".")
}

@Composable
fun SetupWizard(onFinished: (urlPriorytety: String, urlPrzeglady: String, password: String, refreshMinutes: Int) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var urlPriorytety by remember { mutableStateOf("https://") }
    var urlPrzeglady by remember { mutableStateOf("https://") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Serwis System - konfiguracja", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))

            when (step) {
                1 -> {
                    Text("Adres strony - Priorytety", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlPriorytety,
                        onValueChange = { urlPriorytety = it; errorMessage = null },
                        placeholder = { Text("https://priorytety.example.com") },
                        singleLine = true,
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Adres strony - Przeglądy", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlPrzeglady,
                        onValueChange = { urlPrzeglady = it; errorMessage = null },
                        placeholder = { Text("https://przeglady.example.com") },
                        singleLine = true,
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFFF6B6B))
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            when {
                                !isValidUrl(urlPriorytety) -> errorMessage = "Podaj poprawny adres URL dla Priorytetów."
                                !isValidUrl(urlPrzeglady) -> errorMessage = "Podaj poprawny adres URL dla Przeglądów."
                                else -> {
                                    errorMessage = null
                                    step = 2
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Dalej") }
                }

                2 -> {
                    Text("Ustaw hasło administratora", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Hasło") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("Powtórz hasło") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFFF6B6B))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { step = 1 }) { Text("Wstecz") }
                        Button(onClick = {
                            when {
                                password.length < 6 -> errorMessage = "Hasło musi mieć minimum 6 znaków."
                                password != confirmPassword -> errorMessage = "Hasła nie są takie same."
                                else -> {
                                    errorMessage = null
                                    step = 3
                                }
                            }
                        }) { Text("Dalej") }
                    }
                }

                3 -> {
                    Text("Podsumowanie", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("Adres strony - Priorytety:", color = Color(0xFFAAB4C0))
                    Text(urlPriorytety, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Adres strony - Przeglądy:", color = Color(0xFFAAB4C0))
                    Text(urlPrzeglady, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Hasło administratora: ustawione", color = Color.White)
                    Spacer(Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { step = 2 }) { Text("Wstecz") }
                        Button(onClick = { onFinished(urlPriorytety, urlPrzeglady, password, 5) }) { Text("Uruchom kiosk") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Running kiosk: fullscreen WebView + hidden admin gesture + admin panel
// ---------------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KioskWebScreen(
    prefsManager: PrefsManager,
    connectivityManager: ConnectivityManager,
    enterLockTask: () -> Unit,
    exitLockTask: () -> Unit,
    restartApp: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isOnline by remember { mutableStateOf(true) }
    var hasLoadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var refreshMinutes by remember { mutableStateOf(prefsManager.getRefreshIntervalMinutes()) }
    var activeTab by remember { mutableStateOf(KioskTab.PRIORYTETY) }

    var cornerTapTimestamps = remember { mutableStateOf(listOf<Long>()) }
    var showAdminLogin by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // Enter Lock Task once the kiosk screen appears.
    LaunchedEffect(Unit) { enterLockTask() }

    // Monitor connectivity continuously.
    DisposableEffect(connectivityManager) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }
            override fun onLost(network: Network) {
                isOnline = false
            }
        }
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    // Auto-reload while offline, roughly every 5 seconds, until back online.
    LaunchedEffect(isOnline) {
        while (!isOnline) {
            delay(5000)
            webViewRef?.reload()
        }
    }

    // Periodic scheduled refresh (configurable in the admin panel). Reloads
    // whichever tab (Priorytety / Przeglądy) is currently active.
    LaunchedEffect(refreshMinutes, reloadKey) {
        val intervalMs = refreshMinutes.coerceAtLeast(1) * 60_000L
        delay(intervalMs)
        if (isOnline) webViewRef?.reload()
        reloadKey++
    }

    // Column layout: the navigation bar is always visible at the very top,
    // full width, and the WebView fills all remaining space below it - in
    // both portrait and landscape, since Row/Column sizes are proportional
    // rather than hard-coded to one orientation.
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF102A43))
        ) {
            KioskTabButton(
                label = "Priorytety",
                isActive = activeTab == KioskTab.PRIORYTETY,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                if (activeTab != KioskTab.PRIORYTETY) {
                    activeTab = KioskTab.PRIORYTETY
                    webViewRef?.loadUrl(prefsManager.getUrlPriorytety())
                }
            }
            KioskTabButton(
                label = "Przeglądy",
                isActive = activeTab == KioskTab.PRZEGLADY,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                if (activeTab != KioskTab.PRZEGLADY) {
                    activeTab = KioskTab.PRZEGLADY
                    webViewRef?.loadUrl(prefsManager.getUrlPrzeglady())
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                hasLoadError = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    hasLoadError = true
                                }
                            }
                        }

                        loadUrl(prefsManager.getUrlPriorytety())
                        webViewRef = this
                    }
                }
            )

            // Offline / load-error overlay.
            if (!isOnline || hasLoadError) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xEE0D1B2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Brak połączenia z Internetem", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("Ponowna próba połączenia...", color = Color(0xFFAAB4C0))
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            // Hidden admin gesture: 5 quick taps in the bottom-right corner
            // (moved here from the top-right corner, which is now occupied
            // by the always-visible Priorytety / Przeglądy navigation bar).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(64.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        val now = System.currentTimeMillis()
                        val recent = (cornerTapTimestamps.value + now).filter { now - it < 3000 }
                        cornerTapTimestamps.value = recent
                        if (recent.size >= 5) {
                            cornerTapTimestamps.value = emptyList()
                            showAdminLogin = true
                        }
                    }
            )
        }
    }

    if (showAdminLogin) {
        AdminLoginDialog(
            errorMessage = loginError,
            onDismiss = { showAdminLogin = false; loginError = null },
            onSubmit = { enteredPassword ->
                if (prefsManager.checkPassword(enteredPassword)) {
                    showAdminLogin = false
                    loginError = null
                    showAdminPanel = true
                } else {
                    loginError = "Nieprawidłowe hasło administratora."
                }
            }
        )
    }

    if (showAdminPanel) {
        AdminPanel(
            prefsManager = prefsManager,
            currentUrlPriorytety = prefsManager.getUrlPriorytety(),
            currentUrlPrzeglady = prefsManager.getUrlPrzeglady(),
            currentRefreshMinutes = refreshMinutes,
            onClose = { showAdminPanel = false },
            onUrlPriorytetyChanged = { newUrl ->
                prefsManager.updateUrlPriorytety(newUrl)
                if (activeTab == KioskTab.PRIORYTETY) webViewRef?.loadUrl(newUrl)
            },
            onUrlPrzegladyChanged = { newUrl ->
                prefsManager.updateUrlPrzeglady(newUrl)
                if (activeTab == KioskTab.PRZEGLADY) webViewRef?.loadUrl(newUrl)
            },
            onPasswordChanged = { newPassword -> prefsManager.updatePassword(newPassword) },
            onRefreshIntervalChanged = { minutes ->
                prefsManager.updateRefreshInterval(minutes)
                refreshMinutes = minutes
            },
            onReloadPage = { webViewRef?.reload() },
            onExitKiosk = {
                exitLockTask()
                showAdminPanel = false
            },
            onRestartApp = { restartApp() },
            onResetConfiguration = {
                exitLockTask()
                prefsManager.resetConfiguration()
                showAdminPanel = false
                onReset()
            }
        )
    }
}

@Composable
private fun KioskTabButton(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF2DA55A) else Color(0xFF102A43),
            contentColor = Color.White
        ),
        modifier = modifier
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AdminLoginDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hasło administratora") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFB00020))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }) { Text("Zatwierdź") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

private enum class AdminScreen { MENU, EDIT_URL, EDIT_PASSWORD, EDIT_INTERVAL, CONFIRM_RESET }

@Composable
fun AdminPanel(
    prefsManager: PrefsManager,
    currentUrlPriorytety: String,
    currentUrlPrzeglady: String,
    currentRefreshMinutes: Int,
    onClose: () -> Unit,
    onUrlPriorytetyChanged: (String) -> Unit,
    onUrlPrzegladyChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
    onReloadPage: () -> Unit,
    onExitKiosk: () -> Unit,
    onRestartApp: () -> Unit,
    onResetConfiguration: () -> Unit
) {
    var screen by remember { mutableStateOf(AdminScreen.MENU) }

    var newUrlPriorytety by remember { mutableStateOf(currentUrlPriorytety) }
    var newUrlPrzeglady by remember { mutableStateOf(currentUrlPrzeglady) }
    var urlError by remember { mutableStateOf<String?>(null) }

    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var intervalText by remember { mutableStateOf(currentRefreshMinutes.toString()) }
    var intervalError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Panel administracyjny") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (screen) {
                    AdminScreen.MENU -> {
                        AdminMenuButton("Zmień adresy stron") { screen = AdminScreen.EDIT_URL }
                        AdminMenuButton("Zmień hasło") { screen = AdminScreen.EDIT_PASSWORD }
                        AdminMenuButton("Ustaw interwał odświeżania") { screen = AdminScreen.EDIT_INTERVAL }
                        AdminMenuButton("Przeładuj stronę") { onReloadPage(); onClose() }
                        AdminMenuButton("Wyjdź z Kiosk Mode") { onExitKiosk() }
                        AdminMenuButton("Uruchom ponownie aplikację") { onRestartApp() }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        AdminMenuButton("Resetuj konfigurację", danger = true) { screen = AdminScreen.CONFIRM_RESET }
                    }

                    AdminScreen.EDIT_URL -> {
                        Text("Adres strony - Priorytety")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newUrlPriorytety,
                            onValueChange = { newUrlPriorytety = it; urlError = null },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Adres strony - Przeglądy")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newUrlPrzeglady,
                            onValueChange = { newUrlPrzeglady = it; urlError = null },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        urlError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color(0xFFB00020))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { screen = AdminScreen.MENU }) { Text("Wstecz") }
                            Button(onClick = {
                                when {
                                    !isValidUrl(newUrlPriorytety) -> urlError = "Podaj poprawny adres URL dla Priorytetów."
                                    !isValidUrl(newUrlPrzeglady) -> urlError = "Podaj poprawny adres URL dla Przeglądów."
                                    else -> {
                                        onUrlPriorytetyChanged(newUrlPriorytety)
                                        onUrlPrzegladyChanged(newUrlPrzeglady)
                                        onClose()
                                    }
                                }
                            }) { Text("Zapisz") }
                        }
                    }

                    AdminScreen.EDIT_PASSWORD -> {
                        Text("Nowe hasło administratora")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; passwordError = null },
                            label = { Text("Hasło") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it; passwordError = null },
                            label = { Text("Powtórz hasło") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        passwordError?.let { Text(it, color = Color(0xFFB00020)) }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { screen = AdminScreen.MENU }) { Text("Wstecz") }
                            Button(onClick = {
                                when {
                                    newPassword.length < 6 -> passwordError = "Hasło musi mieć minimum 6 znaków."
                                    newPassword != confirmNewPassword -> passwordError = "Hasła nie są takie same."
                                    else -> {
                                        onPasswordChanged(newPassword)
                                        onClose()
                                    }
                                }
                            }) { Text("Zapisz") }
                        }
                    }

                    AdminScreen.EDIT_INTERVAL -> {
                        Text("Interwał automatycznego odświeżania (minuty)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it; intervalError = null },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        intervalError?.let { Text(it, color = Color(0xFFB00020)) }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { screen = AdminScreen.MENU }) { Text("Wstecz") }
                            Button(onClick = {
                                val minutes = intervalText.toIntOrNull()
                                if (minutes == null || minutes < 1) {
                                    intervalError = "Podaj liczbę minut większą od 0."
                                } else {
                                    onRefreshIntervalChanged(minutes)
                                    onClose()
                                }
                            }) { Text("Zapisz") }
                        }
                    }

                    AdminScreen.CONFIRM_RESET -> {
                        Text("Czy na pewno chcesz zresetować konfigurację? Adres strony i hasło zostaną usunięte, a kreator pierwszego uruchomienia pojawi się ponownie.")
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { screen = AdminScreen.MENU }) { Text("Anuluj") }
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020)),
                                onClick = { onResetConfiguration() }
                            ) { Text("Resetuj") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (screen == AdminScreen.MENU) {
                TextButton(onClick = onClose) { Text("Zamknij") }
            }
        }
    )
}

@Composable
private fun AdminMenuButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020)) else ButtonDefaults.buttonColors()
    ) { Text(label) }
}
