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
            onFinished = { urlPriorytety, urlPrzeglady, password, przegladyLogin, przegladyPassword, refreshMinutes ->
                prefsManager.saveConfiguration(
                    urlPriorytety,
                    urlPrzeglady,
                    password,
                    przegladyLogin,
                    przegladyPassword,
                    refreshMinutes
                )
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
fun SetupWizard(
    onFinished: (
        urlPriorytety: String,
        urlPrzeglady: String,
        password: String,
        przegladyLogin: String,
        przegladyPassword: String,
        refreshMinutes: Int
    ) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var urlPriorytety by remember { mutableStateOf("https://") }
    var urlPrzeglady by remember { mutableStateOf("https://") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var przegladyLogin by remember { mutableStateOf("") }
    var przegladyPassword by remember { mutableStateOf("") }
    var confirmPrzegladyPassword by remember { mutableStateOf("") }
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
                    Text("Dane logowania do sekcji Przeglądy", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Użytkownik będzie musiał podać ten login i hasło, aby otworzyć zakładkę \"Przeglądy\".",
                        color = Color(0xFFAAB4C0),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = przegladyLogin,
                        onValueChange = { przegladyLogin = it; errorMessage = null },
                        label = { Text("Login") },
                        singleLine = true,
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = przegladyPassword,
                        onValueChange = { przegladyPassword = it; errorMessage = null },
                        label = { Text("Hasło") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = wizardFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPrzegladyPassword,
                        onValueChange = { confirmPrzegladyPassword = it; errorMessage = null },
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
                        OutlinedButton(onClick = { step = 2 }) { Text("Wstecz") }
                        Button(onClick = {
                            when {
                                przegladyLogin.isBlank() -> errorMessage = "Podaj login do sekcji Przeglądy."
                                przegladyPassword.length < 6 -> errorMessage = "Hasło musi mieć minimum 6 znaków."
                                przegladyPassword != confirmPrzegladyPassword -> errorMessage = "Hasła nie są takie same."
                                else -> {
                                    errorMessage = null
                                    step = 4
                                }
                            }
                        }) { Text("Dalej") }
                    }
                }

                4 -> {
                    Text("Podsumowanie", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("Adres strony - Priorytety:", color = Color(0xFFAAB4C0))
                    Text(urlPriorytety, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Adres strony - Przeglądy:", color = Color(0xFFAAB4C0))
                    Text(urlPrzeglady, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Hasło administratora: ustawione", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Dane logowania do Przeglądów: ustawione (login: $przegladyLogin)", color = Color.White)
                    Spacer(Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { step = 3 }) { Text("Wstecz") }
                        Button(onClick = {
                            onFinished(urlPriorytety, urlPrzeglady, password, przegladyLogin, przegladyPassword, 5)
                        }) { Text("Uruchom kiosk") }
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
    var pdfUrlToShow by remember { mutableStateOf<String?>(null) }
    var przegladyAuthenticated by remember { mutableStateOf(false) }
    var showPrzegladyLogin by remember { mutableStateOf(false) }
    var przegladyLoginError by remember { mutableStateOf<String?>(null) }

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
                    if (przegladyAuthenticated) {
                        activeTab = KioskTab.PRZEGLADY
                        webViewRef?.loadUrl(prefsManager.getUrlPrzeglady())
                    } else {
                        przegladyLoginError = null
                        showPrzegladyLogin = true
                    }
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
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
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

                            // A PDF link tapped directly (not opened in a new
                            // window) - open it in the native viewer instead
                            // of letting WebView try (and silently fail) to
                            // render it inline.
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                return if (looksLikePdfUrl(requestUrl)) {
                                    pdfUrlToShow = requestUrl
                                    true
                                } else {
                                    false
                                }
                            }
                        }

                        // Many "view PDF" buttons open the document via
                        // window.open() / target="_blank", which a plain
                        // WebView otherwise ignores completely. This catches
                        // that case and routes PDFs to the native viewer,
                        // and any other link back into the main WebView.
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val popup = WebView(ctx)
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        popupView: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val requestUrl = request?.url?.toString() ?: return true
                                        if (looksLikePdfUrl(requestUrl)) {
                                            pdfUrlToShow = requestUrl
                                        } else {
                                            view?.loadUrl(requestUrl)
                                        }
                                        return true
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = popup
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        // Fallback: some servers trigger a genuine file
                        // download (Content-Disposition: attachment) rather
                        // than a normal navigation - catch that too.
                        setDownloadListener { downloadUrl, _, _, mimetype, _ ->
                            if (mimetype == "application/pdf" || looksLikePdfUrl(downloadUrl)) {
                                pdfUrlToShow = downloadUrl
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

    if (showPrzegladyLogin) {
        PrzegladyLoginDialog(
            errorMessage = przegladyLoginError,
            onDismiss = { showPrzegladyLogin = false; przegladyLoginError = null },
            onSubmit = { login, enteredPassword ->
                if (prefsManager.checkPrzegladyCredentials(login, enteredPassword)) {
                    przegladyAuthenticated = true
                    showPrzegladyLogin = false
                    przegladyLoginError = null
                    activeTab = KioskTab.PRZEGLADY
                    webViewRef?.loadUrl(prefsManager.getUrlPrzeglady())
                } else {
                    przegladyLoginError = "Nieprawidłowy login lub hasło."
                }
            }
        )
    }

    if (showAdminPanel) {
        AdminPanel(
            prefsManager = prefsManager,
            currentUrlPriorytety = prefsManager.getUrlPriorytety(),
            currentUrlPrzeglady = prefsManager.getUrlPrzeglady(),
            currentPrzegladyLogin = prefsManager.getPrzegladyLogin(),
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
            onPrzegladyCredentialsChanged = { login, newPassword ->
                prefsManager.updatePrzegladyCredentials(login, newPassword)
                // Changing the credentials revokes the current session, so
                // the new login/password must be entered again next time.
                przegladyAuthenticated = false
            },
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

    // Fullscreen PDF viewer - covers everything (including the nav bar)
    // while a document is open. Tapping "Zamknij" clears the state and
    // returns to whichever tab (Priorytety / Przeglądy) was active.
    pdfUrlToShow?.let { url ->
        PdfViewerOverlay(
            pdfUrl = url,
            onClose = { pdfUrlToShow = null }
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

/**
 * Authentication gate shown when the user taps the "Przeglądy" tab.
 * Requires a separate login + password (distinct from the admin password),
 * configured during setup and editable from the admin panel.
 */
@Composable
fun PrzegladyLoginDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (login: String, password: String) -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logowanie - Przeglądy") },
        text = {
            Column {
                Text(
                    "Podaj login i hasło, aby uzyskać dostęp do sekcji Przeglądy.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Login") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło") },
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
            Button(onClick = { onSubmit(login, password) }) { Text("Zaloguj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

private enum class AdminScreen { MENU, EDIT_URL, EDIT_PASSWORD, EDIT_PRZEGLADY_LOGIN, EDIT_INTERVAL, CONFIRM_RESET }

@Composable
fun AdminPanel(
    prefsManager: PrefsManager,
    currentUrlPriorytety: String,
    currentUrlPrzeglady: String,
    currentPrzegladyLogin: String,
    currentRefreshMinutes: Int,
    onClose: () -> Unit,
    onUrlPriorytetyChanged: (String) -> Unit,
    onUrlPrzegladyChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPrzegladyCredentialsChanged: (login: String, password: String) -> Unit,
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

    var newPrzegladyLogin by remember { mutableStateOf(currentPrzegladyLogin) }
    var newPrzegladyPassword by remember { mutableStateOf("") }
    var confirmNewPrzegladyPassword by remember { mutableStateOf("") }
    var przegladyCredentialsError by remember { mutableStateOf<String?>(null) }

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
                        AdminMenuButton("Zmień dane logowania - Przeglądy") { screen = AdminScreen.EDIT_PRZEGLADY_LOGIN }
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

                    AdminScreen.EDIT_PRZEGLADY_LOGIN -> {
                        Text("Dane logowania do sekcji Przeglądy")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPrzegladyLogin,
                            onValueChange = { newPrzegladyLogin = it; przegladyCredentialsError = null },
                            label = { Text("Login") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPrzegladyPassword,
                            onValueChange = { newPrzegladyPassword = it; przegladyCredentialsError = null },
                            label = { Text("Nowe hasło") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmNewPrzegladyPassword,
                            onValueChange = { confirmNewPrzegladyPassword = it; przegladyCredentialsError = null },
                            label = { Text("Powtórz hasło") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        przegladyCredentialsError?.let { Text(it, color = Color(0xFFB00020)) }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { screen = AdminScreen.MENU }) { Text("Wstecz") }
                            Button(onClick = {
                                when {
                                    newPrzegladyLogin.isBlank() -> przegladyCredentialsError = "Podaj login."
                                    newPrzegladyPassword.length < 6 -> przegladyCredentialsError = "Hasło musi mieć minimum 6 znaków."
                                    newPrzegladyPassword != confirmNewPrzegladyPassword -> przegladyCredentialsError = "Hasła nie są takie same."
                                    else -> {
                                        onPrzegladyCredentialsChanged(newPrzegladyLogin, newPrzegladyPassword)
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
