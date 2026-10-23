package com.belgindo.stok

import android.content.Context

/**
 * Penyimpanan konfigurasi koneksi database cloud Turso.
 * Disimpan di SharedPreferences internal aplikasi (hanya terbaca oleh aplikasi ini).
 * Diisi sekali pada layar konfigurasi saat aplikasi pertama dibuka.
 */
object ConfigStore {

    private const val PREFS = "belgindo_stok_config"
    private const val KEY_URL = "turso_url"
    private const val KEY_TOKEN = "turso_token"

    /** Mengembalikan pasangan (url, token) bila sudah dikonfigurasi, selain itu null. */
    fun load(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, "")?.trim().orEmpty()
        val token = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        return if (url.isNotEmpty() && token.isNotEmpty()) url to token else null
    }

    fun save(context: Context, url: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
