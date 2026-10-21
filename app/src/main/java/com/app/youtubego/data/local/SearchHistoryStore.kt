package com.app.youtubego.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Riwayat pencarian lokal (terbaru di depan, tanpa duplikat, maks. [MAX] entri).
 * Tersimpan hanya di perangkat; kueri tidak pernah memuat baris baru karena kolom cari satu baris,
 * jadi "\n" aman dipakai sebagai pemisah tanpa perlu serialisasi.
 */
@Singleton
class SearchHistoryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save()
    }

    fun remove(query: String) {
        _items.value = _items.value.filterNot { it == query }
        save()
    }

    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun read(): List<String> =
        (prefs.getString(KEY, "") ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)

    private fun save() { prefs.edit().putString(KEY, _items.value.joinToString("\n")).apply() }

    private companion object {
        const val KEY = "items"
        const val MAX = 20
    }
}
