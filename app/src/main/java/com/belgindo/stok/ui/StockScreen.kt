package com.belgindo.stok.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belgindo.stok.StockRepository
import com.belgindo.stok.StockRow
import com.belgindo.stok.SortKey
import com.belgindo.stok.TursoClient
import com.belgindo.stok.TursoException
import kotlinx.coroutines.launch

/**
 * Layar utama untuk HP bos: daftar stok barang terkini (READ-ONLY).
 * Mendukung pencarian dan sortir berdasarkan nama barang, ukuran, dan lokasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    url: String,
    token: String,
    onOpenSetup: () -> Unit
) {
    val repository = remember(url, token) { StockRepository(TursoClient(url, token)) }
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<StockRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf(SortKey.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var loadedOnce by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        errorMessage = null
        scope.launch {
            try {
                rows = repository.loadStock()
                loadedOnce = true
            } catch (e: TursoException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = "Gagal memuat data: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(rows, query) { StockRepository.filter(rows, query) }
    val sorted = remember(filtered, sortKey, ascending) { StockRepository.sort(filtered, sortKey, ascending) }
    val totalStock = remember(sorted) { sorted.sumOf { it.stock } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stok Barang", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "PT. Belgindo Raya · Read-only",
                            fontSize = 12.sp,
                            color = Color(0xFFD9E7F4)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { load() }, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang", tint = Color.White)
                    }
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Pencarian ------------------------------------------------------
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari nama / kode / lokasi…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // Sortir ---------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sortir:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                SortKey.entries.forEach { key ->
                    FilterChip(
                        selected = sortKey == key,
                        onClick = {
                            if (sortKey == key) ascending = !ascending else {
                                sortKey = key
                                ascending = true
                            }
                        },
                        label = { Text(key.label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ascending = !ascending }) {
                    Icon(
                        imageVector = if (ascending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (ascending) "Naik" else "Turun",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Ringkasan ------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${sorted.size} baris stok",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Total: ${StockRepository.formatQty(totalStock)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Daftar stok ----------------------------------------------------
            when {
                loading && !loadedOnce -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Memuat stok terkini…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                sorted.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Belum ada data stok.\nTekan ikon muat ulang untuk memperbarui."
                        else "Tidak ada hasil untuk \"$query\".",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sorted, key = { "${it.productId}|${it.size}|${it.location}" }) { row ->
                        StockCard(row)
                    }
                }
            }
        }
    }

    if (errorMessage != null && !loading && !loadedOnce) {
        // Kegagalan pertama kali: tawarkan pembukaan layar konfigurasi.
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Koneksi bermasalah") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = onOpenSetup) { Text("Buka Pengaturan") }
            },
            dismissButton = {
                TextButton(onClick = { load() }) { Text("Coba Lagi") }
            }
        )
    }
}

@Composable
private fun StockCard(row: StockRow) {
    val negative = row.stock < 0
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.name.ifBlank { "(Tanpa nama)" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (row.code.isNotBlank()) {
                    Text(
                        row.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.size.isNotBlank()) {
                        Box(
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                row.size,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            row.location,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    StockRepository.formatQty(row.stock),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        negative -> BadRed
                        else -> GoodGreen
                    }
                )
                Text(
                    row.unit.ifBlank { "pcs" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "masuk ${StockRepository.formatQty(row.qtyIn)} · keluar ${StockRepository.formatQty(row.qtyOut)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
