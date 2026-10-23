package com.belgindo.stok.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.belgindo.stok.ConfigStore
import com.belgindo.stok.TursoClient
import com.belgindo.stok.TursoException
import kotlinx.coroutines.launch

/**
 * Layar konfigurasi database cloud Turso — ditampilkan saat aplikasi pertama
 * dibuka (atau lewat tombol Pengaturan). Uji koneksi memverifikasi URL, token,
 * dan keberadaan skema database sebelum disimpan.
 */
@Composable
fun SetupScreen(
    initialUrl: String,
    canCancel: Boolean,
    onSaved: (url: String, token: String) -> Unit,
    onCancel: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun testAndSave() {
        val urlTrim = url.trim()
        val tokenTrim = token.trim()
        if (urlTrim.isEmpty() || tokenTrim.isEmpty()) {
            isError = true
            status = "URL database dan API token wajib diisi."
            return
        }
        busy = true
        status = "Menguji koneksi…"
        isError = false
        scope.launch {
            try {
                val client = TursoClient(urlTrim, tokenTrim)
                client.ping()
                // Pastikan skema sudah tersedia; aplikasi ini read-only.
                val products = client.pipeline(
                    listOf("SELECT COUNT(*) AS n FROM products" to emptyList<Any?>())
                )[0].rows.firstOrNull()?.get("n")?.asLong() ?: 0L
                status = "Koneksi berhasil. Database siap ($products barang terdaftar)."
                isError = false
                onSaved(urlTrim, tokenTrim)
            } catch (e: TursoException) {
                isError = true
                status = if (e.message?.contains("no such table", ignoreCase = true) == true) {
                    "Koneksi berhasil, tetapi skema database belum ada.\n" +
                        "Jalankan aplikasi desktop sekali pada mode \"Database cloud (Turso)\" " +
                        "untuk membuat skema, lalu coba lagi."
                } else {
                    e.message
                }
            } catch (e: Exception) {
                isError = true
                status = "Koneksi gagal: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PT. BELGINDO RAYA",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Konfigurasi Database Cloud (Turso)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Diisi sekali saat aplikasi pertama dibuka. URL dan API token didapat dari dashboard Turso (app.turso.io) atau CLI: turso db show <nama> dan turso db tokens create.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL database") },
                    placeholder = { Text("libsql://nama-db-xxxx.turso.io") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { testAndSave() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Menguji…")
                    } else {
                        Text("Uji Koneksi & Simpan")
                    }
                }
                if (canCancel) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Batal") }
                }
                status?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Aplikasi ini hanya menampilkan stok (read-only) dan tidak dapat mengubah data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Konfigurasi tersimpan di dalam aplikasi pada perangkat ini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
