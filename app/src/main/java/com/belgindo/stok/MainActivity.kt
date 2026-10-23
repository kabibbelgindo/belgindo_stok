package com.belgindo.stok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.belgindo.stok.ui.SetupScreen
import com.belgindo.stok.ui.StockScreen
import com.belgindo.stok.ui.StokBRTheme

/**
 * Stok Barang PT. Belgindo Raya — aplikasi read-only untuk pemantauan stok.
 *
 * Alur:
 * 1. Aplikasi pertama dibuka -> layar konfigurasi database cloud Turso (URL + token).
 * 2. Setelah tersimpan -> daftar stok terkini dengan sortir nama/ukuran/lokasi.
 * 3. Tombol pengaturan pada layar stok membuka kembali konfigurasi.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StokBRTheme {
                var config by remember { mutableStateOf(ConfigStore.load(this)) }
                var showSetup by remember { mutableStateOf(config == null) }

                when {
                    // Layar konfigurasi (pertama kali / dari tombol pengaturan).
                    showSetup -> SetupScreen(
                        initialUrl = config?.first.orEmpty(),
                        canCancel = config != null,
                        onSaved = { url, token ->
                            ConfigStore.save(this, url, token)
                            config = url to token
                            showSetup = false
                        },
                        onCancel = { showSetup = false }
                    )
                    // Layar stok utama.
                    config != null -> StockScreen(
                        url = config!!.first,
                        token = config!!.second,
                        onOpenSetup = { showSetup = true }
                    )
                }
            }
        }
    }
}
