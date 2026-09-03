package com.kioskwebapp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a PDF (reusing the kiosk WebView's session cookies, so
 * authenticated documents work too) and renders every page to a Bitmap
 * using Android's built-in PdfRenderer - no external viewer, no Chrome,
 * fully native and works without any extra library.
 */
object PdfDownloader {

    sealed class Result {
        data class Success(val pages: List<Bitmap>) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun downloadAndRender(context: Context, pdfUrl: String): Result =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val file = downloadToCache(context, pdfUrl)
                tempFile = file
                val pages = renderPages(file)
                Result.Success(pages)
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Nie udało się otworzyć pliku PDF.")
            } finally {
                tempFile?.delete()
            }
        }

    private fun downloadToCache(context: Context, pdfUrl: String): File {
        val connection = URL(pdfUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 20000

        val cookie = CookieManager.getInstance().getCookie(pdfUrl)
        if (!cookie.isNullOrEmpty()) {
            connection.setRequestProperty("Cookie", cookie)
        }
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw Exception("Serwer zwrócił błąd ${connection.responseCode} podczas pobierania dokumentu.")
        }

        val outFile = File(context.cacheDir, "kiosk_pdf_${System.currentTimeMillis()}.pdf")
        connection.inputStream.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun renderPages(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val targetWidth = 1080
                        val scale = targetWidth.toFloat() / page.width
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        }
        return bitmaps
    }
}

/**
 * Returns true when a URL points to a PDF file, based on its path
 * (ignoring query string / fragment). Used to intercept PDF links before
 * the WebView tries (and fails) to navigate to them directly.
 */
fun looksLikePdfUrl(url: String): Boolean =
    url.substringBefore("?").substringBefore("#").lowercase().endsWith(".pdf")

/**
 * Fullscreen modal that downloads and displays a PDF natively, with a
 * "Zamknij" button. Covers the whole screen (including the Priorytety /
 * Przeglądy bar) while open.
 */
@Composable
fun PdfViewerOverlay(pdfUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var result by remember(pdfUrl) { mutableStateOf<PdfDownloader.Result?>(null) }

    LaunchedEffect(pdfUrl) {
        result = PdfDownloader.downloadAndRender(context, pdfUrl)
    }

    // Free the rendered bitmaps' memory as soon as the viewer is closed or
    // a different PDF is requested - important for a device that runs 24/7.
    DisposableEffect(pdfUrl) {
        onDispose {
            (result as? PdfDownloader.Result.Success)?.pages?.forEach { it.recycle() }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar: title + close button, always visible while the PDF is open.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF102A43))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Podgląd dokumentu PDF", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DA55A))
                ) { Text("Zamknij") }
            }

            when (val r = result) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Wczytywanie dokumentu...", color = Color.White)
                        }
                    }
                }

                is PdfDownloader.Result.Failure -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nie udało się otworzyć pliku PDF.", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(r.message, color = Color(0xFFAAB4C0))
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = onClose) { Text("Wróć") }
                        }
                    }
                }

                is PdfDownloader.Result.Success -> {
                    if (r.pages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Dokument nie zawiera żadnych stron.", color = Color.White)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(r.pages) { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
