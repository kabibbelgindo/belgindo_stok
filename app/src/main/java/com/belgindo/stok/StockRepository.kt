package com.belgindo.stok

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satu baris saldo stok per barang + ukuran + lokasi (sama dengan dashboard desktop). */
data class StockRow(
    val productId: Long,
    val code: String,
    val name: String,
    val unit: String,
    val size: String,
    val location: String,
    val qtyIn: Double,
    val qtyOut: Double,
    val stock: Double
)

/** Kunci sortir: nama barang, ukuran, atau lokasi. */
enum class SortKey(val label: String) {
    NAME("Nama"),
    SIZE("Ukuran"),
    LOCATION("Lokasi")
}

/**
 * Pembaca data stok dari database cloud Turso (READ-ONLY).
 * Perhitungan meniru persis halaman "Stok Barang" aplikasi desktop:
 *  1. Agregasi transactions (MASUK - KELUAR) per barang + ukuran ternormalisasi + lokasi.
 *  2. Penyesuaian location_transfers (keluar dari lokasi asal, masuk ke lokasi tujuan).
 */
class StockRepository(private val client: TursoClient) {

    private val stockSql = """
        SELECT p.id AS product_id, p.code, p.name, p.unit,
               COALESCE(REPLACE(REPLACE(TRIM(t.trx_size),'×','x'),' ',''),'') AS norm_size,
               COALESCE(NULLIF(TRIM(t.location),''),'(Tanpa lokasi)') AS location,
               COALESCE(SUM(CASE WHEN t.trx_type='MASUK' THEN t.qty ELSE 0 END),0) AS qty_in,
               COALESCE(SUM(CASE WHEN t.trx_type='KELUAR' THEN t.qty ELSE 0 END),0) AS qty_out,
               COALESCE(SUM(CASE WHEN t.trx_type='MASUK' THEN t.qty ELSE -t.qty END),0) AS stock
        FROM transactions t JOIN products p ON p.id = t.product_id
        GROUP BY p.id, p.code, p.name, p.unit,
                 COALESCE(REPLACE(REPLACE(TRIM(t.trx_size),'×','x'),' ',''),''),
                 COALESCE(NULLIF(TRIM(t.location),''),'(Tanpa lokasi)')
    """.trimIndent()

    private val transfersSql = """
        SELECT product_id,
               COALESCE(REPLACE(REPLACE(TRIM(trx_size),'×','x'),' ',''),'') AS norm_size,
               from_location, to_location, qty
        FROM location_transfers
    """.trimIndent()

    private val productsSql = "SELECT id, code, name, unit FROM products"

    /** Muat saldo stok terkini (dipanggil di Dispatchers.IO). */
    suspend fun loadStock(): List<StockRow> = withContext(Dispatchers.IO) {
        val results = try {
            client.pipeline(listOf(
                stockSql to emptyList<Any?>(),
                transfersSql to emptyList<Any?>(),
                productsSql to emptyList<Any?>()
            ))
        } catch (e: TursoException) {
            throw e
        }
        val stockRes = results[0]
        val transferRes = results[1]
        val productRes = results[2]

        // Data referensi barang untuk baris yang hanya muncul lewat mutasi.
        data class ProductInfo(val code: String, val name: String, val unit: String)
        val products = HashMap<Long, ProductInfo>()
        for (row in productRes.rows) {
            products[row["id"]?.asLong() ?: 0L] = ProductInfo(
                code = row["code"]?.asText().orEmpty(),
                name = row["name"]?.asText().orEmpty(),
                unit = row["unit"]?.asText().orEmpty()
            )
        }

        val data = LinkedHashMap<String, StockRow>()
        for (r in stockRes.rows) {
            val pid = r["product_id"]?.asLong() ?: continue
            val size = r["norm_size"]?.asText().orEmpty()
            val loc = r["location"]?.asText().orEmpty().ifBlank { "(Tanpa lokasi)" }
            val key = "$pid|$size|$loc"
            data[key] = StockRow(
                productId = pid,
                code = r["code"]?.asText().orEmpty(),
                name = r["name"]?.asText().orEmpty(),
                unit = r["unit"]?.asText().orEmpty(),
                size = size,
                location = loc,
                qtyIn = r["qty_in"]?.asDouble() ?: 0.0,
                qtyOut = r["qty_out"]?.asDouble() ?: 0.0,
                stock = r["stock"]?.asDouble() ?: 0.0
            )
        }

        // Mutasi memindahkan saldo antar lokasi tanpa mengubah total perusahaan.
        for (tr in transferRes.rows) {
            val pid = tr["product_id"]?.asLong() ?: continue
            val size = tr["norm_size"]?.asText().orEmpty()
            val qty = tr["qty"]?.asDouble() ?: 0.0
            for ((loc, sign) in listOf(
                (tr["from_location"]?.asText().orEmpty()) to -1.0,
                (tr["to_location"]?.asText().orEmpty()) to 1.0
            )) {
                val display = loc.ifBlank { "(Tanpa lokasi)" }
                val key = "$pid|$size|$display"
                val existing = data[key]
                if (existing == null) {
                    val product = products[pid] ?: continue
                    data[key] = StockRow(
                        productId = pid, code = product.code, name = product.name,
                        unit = product.unit, size = size, location = display,
                        qtyIn = 0.0, qtyOut = 0.0, stock = 0.0
                    )
                }
                val row = data.getValue(key)
                data[key] = row.copy(stock = row.stock + sign * qty)
            }
        }
        data.values.toList()
    }

    companion object {

        /** Filter pencarian: nama / kode / lokasi. */
        fun filter(rows: List<StockRow>, query: String): List<StockRow> {
            val q = query.trim()
            if (q.isEmpty()) return rows
            return rows.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true) ||
                    it.location.contains(q, ignoreCase = true)
            }
        }

        /** Sortir: angka pada ukuran (mis. 2x3x400) dibandingkan secara numerik. */
        fun sort(rows: List<StockRow>, key: SortKey, ascending: Boolean): List<StockRow> {
            val comparator: Comparator<StockRow> = when (key) {
                SortKey.NAME -> compareBy({ it.name.lowercase() }, { it.size.sortKey() }, { it.location.lowercase() })
                SortKey.SIZE -> compareBy({ it.size.sortKey() }, { it.name.lowercase() }, { it.location.lowercase() })
                SortKey.LOCATION -> compareBy({ it.location.lowercase() }, { it.name.lowercase() }, { it.size.sortKey() })
            }
            return if (ascending) rows.sortedWith(comparator) else rows.sortedWith(comparator.reversed())
        }

        /** Kunci sortir ukuran "2x3x400" -> [2.0, 3.0, 400.0]. */
        private fun String.sortKey(): List<Double> =
            split('x', 'X').map { part -> part.toDoubleOrNull() ?: Double.MAX_VALUE }

        /** Format angka Indonesia: titik ribuan, koma desimal. */
        fun formatQty(value: Double): String {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale("in", "ID"))
            val whole = abs(value - value.roundToLong()) < 1e-9
            val pattern = if (whole) "#,##0" else "#,##0.##"
            return java.text.DecimalFormat(pattern, symbols).format(value)
        }
    }
}
