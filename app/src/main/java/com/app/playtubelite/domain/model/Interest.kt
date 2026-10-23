package com.app.playtubelite.domain.model

/** Skor minat belajar-sendiri: kata kunci (topik) dan channel, dihitung dari pencarian & tontonan. */
data class ChannelAffinity(val name: String, val score: Double)

data class InterestProfile(
    /** id topik yang dipilih pengguna secara eksplisit (lihat [TopicCatalog]). */
    val seeds: Set<String> = emptySet(),
    val topics: Map<String, Double> = emptyMap(),
    val channels: Map<String, ChannelAffinity> = emptyMap(),
    /** id video yang baru ditonton: tidak ditawarkan lagi di beranda. */
    val seen: List<String> = emptyList(),
    val onboarded: Boolean = false,
    /** Naik tiap profil berubah; beranda memakainya untuk tahu kapan feed perlu disegarkan. */
    val rev: Long = 0,
)

data class Topic(val id: String, val label: String, val query: String, val keywords: Set<String>)

object TopicCatalog {
    val all: List<Topic> = listOf(
        Topic("tech", "Teknologi", "review teknologi gadget terbaru", setOf("teknologi", "gadget", "smartphone", "laptop", "review", "tech")),
        Topic("music", "Musik", "musik populer terbaru", setOf("musik", "music", "lagu", "cover", "konser", "akustik")),
        Topic("gaming", "Game", "gameplay game populer", setOf("game", "gameplay", "gaming", "mobile", "legends", "minecraft")),
        Topic("movie", "Film & Series", "ulasan film series", setOf("film", "movie", "series", "trailer", "drama", "ulasan")),
        Topic("anime", "Anime", "anime terbaru rekomendasi", setOf("anime", "manga", "otaku", "jepang")),
        Topic("sport", "Olahraga", "highlight olahraga bola", setOf("bola", "sepak", "liga", "gol", "highlight", "badminton", "basket")),
        Topic("news", "Berita", "berita terkini hari ini", setOf("berita", "news", "terkini", "politik", "ekonomi")),
        Topic("food", "Kuliner", "resep masakan kuliner", setOf("resep", "masak", "kuliner", "makanan", "makan", "jajan")),
        Topic("auto", "Otomotif", "review mobil motor", setOf("mobil", "motor", "otomotif", "modifikasi", "balap")),
        Topic("edu", "Belajar", "tutorial belajar pelajaran", setOf("belajar", "tutorial", "pelajaran", "kuliah", "tips", "materi")),
        Topic("science", "Sains", "sains fakta menarik", setOf("sains", "science", "fakta", "luar", "angkasa", "alam")),
        Topic("comedy", "Komedi", "komedi lucu stand up", setOf("lucu", "komedi", "humor", "prank", "kocak")),
        Topic("travel", "Travel", "wisata jalan-jalan vlog", setOf("wisata", "travel", "liburan", "vlog", "pantai", "gunung")),
        Topic("biz", "Bisnis & Keuangan", "bisnis investasi keuangan", setOf("bisnis", "investasi", "saham", "uang", "keuangan", "usaha")),
    )
    fun byId(id: String): Topic? = all.firstOrNull { it.id == id }
}

object Keywords {
    private val stop = setOf(
        "yang", "dan", "di", "ke", "dari", "untuk", "dengan", "ini", "itu", "pada", "adalah", "atau", "juga", "akan",
        "bisa", "jadi", "saya", "kamu", "aku", "kita", "kami", "nya", "ada", "tidak", "bukan", "para", "oleh", "dalam",
        "the", "and", "for", "with", "this", "that", "you", "your", "from", "are", "was", "how", "what", "why", "who",
        "not", "all", "new", "its", "his", "her", "our", "can", "will", "have", "has", "had", "but", "out", "get",
        "video", "videos", "official", "full", "part", "episode", "live", "terbaru", "hari", "lirik", "lyrics", "clip",
        "audio", "feat", "best", "top", "subscribe", "channel", "shorts", "short", "eps", "season",
    )
    private val splitter = Regex("\\s+")
    private val clean = Regex("[^\\p{L}\\p{N}\\s]")
    fun extract(text: String): List<String> =
        text.lowercase().replace(clean, " ").split(splitter)
            .filter { it.length >= 3 && it !in stop && !it.all(Char::isDigit) }
            .distinct()
}
