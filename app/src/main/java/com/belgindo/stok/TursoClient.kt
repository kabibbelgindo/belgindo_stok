package com.belgindo.stok

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Nilai sel hasil query Turso (Hrana v2) dengan konversi tipe yang aman. */
class TursoValue(private val type: String, private val raw: Any?) {

    fun asText(): String = when (type) {
        "null" -> ""
        "blob" -> (raw as? ByteArray)?.let { String(it, Charsets.UTF_8) } ?: ""
        else -> raw?.toString() ?: ""
    }

    fun asDouble(): Double = when (type) {
        "null" -> 0.0
        "integer" -> (raw as? Number)?.toDouble()
            ?: (raw as? String)?.toDoubleOrNull() ?: 0.0
        else -> (raw as? Number)?.toDouble()
            ?: (raw as? String)?.toDoubleOrNull() ?: 0.0
    }

    fun asLong(): Long = when (type) {
        "null" -> 0L
        else -> (raw as? Number)?.toLong()
            ?: (raw as? String)?.toLongOrNull() ?: 0L
    }
}

/** Hasil satu statement pada pipeline. Baris dipetakan nama kolom -> nilai. */
class TursoStatementResult(
    val columns: List<String>,
    private val rawRows: List<List<TursoValue>>,
    val affectedRowCount: Long,
    val lastInsertRowId: Long?
) {
    val rows: List<Map<String, TursoValue>> by lazy {
        rawRows.map { row ->
            val map = LinkedHashMap<String, TursoValue>()
            columns.forEachIndexed { i, name -> if (name.isNotEmpty()) map[name] = row[i] }
            map
        }
    }
}

/** Kesalahan dari sisi server Turso (SQL, auth, atau protokol). */
class TursoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Klien database cloud Turso melalui HTTP API Hrana v2 (endpoint /v2/pipeline).
 * Sama seperti versi desktop: SQL dialek SQLite dengan placeholder '?', dan
 * beberapa statement dapat dikirim sekaligus dalam satu permintaan HTTP.
 */
class TursoClient(baseUrl: String, private val token: String) {

    companion object {
        /** libsql:// / wss:// -> https:// (miror _normalize_turso_url di desktop). */
        fun normalizeUrl(input: String): String {
            var u = input.trim()
            if (u.lowercase().startsWith("libsql+")) u = u.substring(7)
            val low = u.lowercase()
            u = when {
                low.startsWith("libsql://") -> "https://" + u.substring(9)
                low.startsWith("wss://") -> "https://" + u.substring(6)
                low.startsWith("ws://") -> "http://" + u.substring(5)
                !(low.startsWith("http://") || low.startsWith("https://")) -> "https://" + u
                else -> u
            }
            return u.trimEnd('/')
        }
    }

    private val endpoint = normalizeUrl(baseUrl) + "/v2/pipeline"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Jalankan daftar (sql, args) secara berurutan; hasil dikembalikan berurutan. */
    fun pipeline(statements: List<Pair<String, List<Any?>>>): List<TursoStatementResult> {
        if (statements.isEmpty()) return emptyList()

        val requests = JSONArray()
        for ((sql, args) in statements) {
            val stmt = JSONObject().put("sql", sql)
            val arr = JSONArray()
            for (a in args) arr.put(encodeValue(a))
            stmt.put("args", arr)
            requests.put(JSONObject().put("type", "execute").put("stmt", stmt))
        }
        // Tutup stream agar tidak ada session menggantung di server.
        requests.put(JSONObject().put("type", "close"))

        val payload = JSONObject().put("requests", requests).toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw TursoException(
                "Tidak dapat terhubung ke database cloud.\nPeriksa koneksi internet, URL database, dan API token.\nRincian: ${e.message}", e
            )
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint = when (resp.code) {
                    401, 403 -> "Token ditolak (HTTP ${resp.code}). Periksa URL dan API token Turso."
                    else -> "Server merespons HTTP ${resp.code}."
                }
                throw TursoException("$hint\n${text.take(300)}")
            }
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw TursoException("Respons tidak valid dari Turso Cloud.", e)
            }
            val results = root.optJSONArray("results")
                ?: throw TursoException("Respons pipeline tidak memiliki hasil.")
            val out = ArrayList<TursoStatementResult>(results.length())
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val err = item.optJSONObject("error")
                if (err != null || item.optString("type") == "error") {
                    val message = err?.optString("message").takeUnless { it.isNullOrEmpty() }
                        ?: "Kesalahan tidak diketahui dari server."
                    throw TursoException(message)
                }
                val result = item.optJSONObject("response")?.optJSONObject("result")
                    ?: throw TursoException("Hasil pipeline tidak lengkap.")
                out.add(parseResult(result))
            }
            return out
        }
    }

    /** Query ringan untuk uji koneksi. */
    fun ping(): Long = pipeline(listOf("SELECT 1" to emptyList<Any?>()))[0].rows[0].values.first().asLong()

    private fun parseResult(result: JSONObject): TursoStatementResult {
        val cols = ArrayList<String>()
        val colsArr = result.optJSONArray("cols") ?: JSONArray()
        for (i in 0 until colsArr.length()) {
            cols.add(colsArr.optJSONObject(i)?.optString("name").orEmpty())
        }
        val rowsArr = result.optJSONArray("rows") ?: JSONArray()
        val rows = ArrayList<List<TursoValue>>(rowsArr.length())
        for (r in 0 until rowsArr.length()) {
            val rowArr = rowsArr.optJSONArray(r) ?: JSONArray()
            val values = ArrayList<TursoValue>(rowArr.length())
            for (c in 0 until rowArr.length()) {
                values.add(decodeValue(rowArr.optJSONObject(c)))
            }
            rows.add(values)
        }
        val lastId = if (result.has("last_insert_rowid") && !result.isNull("last_insert_rowid")) {
            (result.opt("last_insert_rowid") as? Number)?.toLong()
                ?: result.optString("last_insert_rowid").toLongOrNull()
        } else null
        return TursoStatementResult(cols, rows, result.optLong("affected_row_count"), lastId)
    }

    private fun encodeValue(v: Any?): JSONObject {
        val o = JSONObject()
        when (v) {
            null -> o.put("type", "null")
            is Boolean -> o.put("type", "integer").put("value", if (v) "1" else "0")
            is Int -> o.put("type", "integer").put("value", v.toString())
            is Long -> o.put("type", "integer").put("value", v.toString())
            is Double -> o.put("type", "float").put("value", v.toString())
            is Float -> o.put("type", "float").put("value", v.toString())
            is ByteArray -> o.put("type", "blob")
                .put("value", Base64.encodeToString(v, Base64.NO_WRAP))
            else -> o.put("type", "text").put("value", v.toString())
        }
        return o
    }

    private fun decodeValue(o: JSONObject?): TursoValue {
        if (o == null || o.isNull("type") || o.optString("type") == "null") {
            return TursoValue("null", null)
        }
        val t = o.optString("type")
        val v = o.opt("value")
        return when (t) {
            "integer" -> TursoValue("integer", when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
                else -> null
            })
            "float" -> TursoValue("float", when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            })
            "blob" -> TursoValue("blob", try {
                Base64.decode(v?.toString().orEmpty(), Base64.DEFAULT)
            } catch (e: Exception) {
                ByteArray(0)
            })
            else -> TursoValue(t, v)
        }
    }
}
