package com.app.playtubelite.presentation.search

import com.app.playtubelite.domain.model.VideoSummary

/** Jenis hasil: Semua, Video (di atas 1 menit), atau Shorts (maks. 1 menit). */
enum class SearchType(val label: String) { ALL("Semua"), VIDEO("Video"), SHORTS("Shorts") }

enum class UploadDate(val label: String, val maxAgeSec: Long) {
    ANY("Kapan saja", 0), HOUR("Jam terakhir", 3_600), TODAY("Hari ini", 86_400),
    WEEK("Minggu ini", 604_800), MONTH("Bulan ini", 2_592_000), YEAR("Tahun ini", 31_536_000),
}

enum class DurationFilter(val label: String) {
    ANY("Berapa pun"), SHORT("Di bawah 4 menit"), MEDIUM("4 - 20 menit"), LONG("Di atas 20 menit"),
}

enum class SortBy(val label: String) { RELEVANCE("Relevansi"), DATE("Tanggal upload"), VIEWS("Jumlah tontonan") }

/**
 * Filter pencarian ala YouTube. Diterapkan di perangkat pada hasil yang sudah dimuat
 * (ekstraktor tidak menyediakan parameter filter), jadi pergantian filter instan tanpa permintaan jaringan.
 */
data class SearchFilters(
    val type: SearchType = SearchType.ALL,
    val date: UploadDate = UploadDate.ANY,
    val duration: DurationFilter = DurationFilter.ANY,
    val sort: SortBy = SortBy.RELEVANCE,
) {
    /** Jumlah filter lanjutan aktif (tanpa jenis; jenis punya chip sendiri). */
    val advancedCount: Int get() = listOf(date != UploadDate.ANY, duration != DurationFilter.ANY, sort != SortBy.RELEVANCE).count { it }

    fun apply(items: List<VideoSummary>, nowSec: Long): List<VideoSummary> {
        var list = items.filter { v ->
            val isShort = !v.isLive && v.lengthSeconds in 1..SHORTS_MAX_SEC
            val typeOk = when (type) {
                SearchType.ALL -> true
                SearchType.VIDEO -> !isShort
                SearchType.SHORTS -> isShort
            }
            val dateOk = date == UploadDate.ANY || (v.published > 0 && nowSec - v.published <= date.maxAgeSec)
            val durationOk = when (duration) {
                DurationFilter.ANY -> true
                DurationFilter.SHORT -> !v.isLive && v.lengthSeconds in 1 until 240
                DurationFilter.MEDIUM -> !v.isLive && v.lengthSeconds in 240..1_200
                DurationFilter.LONG -> !v.isLive && v.lengthSeconds > 1_200
            }
            typeOk && dateOk && durationOk
        }
        list = when (sort) {
            SortBy.RELEVANCE -> list
            SortBy.DATE -> list.sortedByDescending { it.published }
            SortBy.VIEWS -> list.sortedByDescending { it.viewCount }
        }
        return list
    }

    private companion object { const val SHORTS_MAX_SEC = 61L }
}
