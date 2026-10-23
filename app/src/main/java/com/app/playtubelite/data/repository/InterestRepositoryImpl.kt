package com.app.playtubelite.data.repository

import android.content.Context
import com.app.playtubelite.domain.model.ChannelAffinity
import com.app.playtubelite.domain.model.InterestProfile
import com.app.playtubelite.domain.model.Keywords
import com.app.playtubelite.domain.model.VideoSummary
import com.app.playtubelite.domain.repository.InterestRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Serializable
private data class StoredChannel(val name: String, val score: Double)

@Serializable
private data class StoredProfile(
    val seeds: Set<String> = emptySet(),
    val topics: Map<String, Double> = emptyMap(),
    val channels: Map<String, StoredChannel> = emptyMap(),
    val seen: List<String> = emptyList(),
    val onboarded: Boolean = false,
    val rev: Long = 0,
    val decayedAt: Long = 0,
)

@Singleton
class InterestRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : InterestRepository {
    private val prefs = context.getSharedPreferences("interest_profile", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var decayedAt = 0L
    private val _profile = MutableStateFlow(load())
    override val profile: StateFlow<InterestProfile> = _profile.asStateFlow()

    override fun recordSearch(query: String) {
        val tokens = Keywords.extract(query).take(6)
        if (tokens.isEmpty()) return
        mutate { it.copy(topics = bump(it.topics, tokens, 3.0)) }
    }

    override fun recordWatch(video: VideoSummary) {
        val tokens = Keywords.extract(video.title).take(8)
        mutate { p ->
            val channels = if (video.authorId.isBlank()) p.channels else p.channels + (video.authorId to
                ChannelAffinity(video.author, (p.channels[video.authorId]?.score ?: 0.0) + 4.0))
            p.copy(
                topics = bump(p.topics, tokens, 2.0),
                channels = channels.entries.sortedByDescending { it.value.score }.take(30).associate { it.key to it.value },
                seen = (listOf(video.id) + p.seen.filter { it != video.id }).take(80),
            )
        }
    }

    override fun setSeeds(ids: Set<String>) = mutate { it.copy(seeds = ids, onboarded = true) }
    override fun markOnboarded() = mutate { it.copy(onboarded = true) }
    override fun forgetTopic(topic: String) = mutate { it.copy(topics = it.topics - topic) }
    override fun forgetChannel(channelId: String) = mutate { it.copy(channels = it.channels - channelId) }
    override fun reset() = mutate { InterestProfile(onboarded = true) }

    private fun bump(map: Map<String, Double>, keys: List<String>, by: Double): Map<String, Double> {
        val m = map.toMutableMap()
        keys.forEach { m[it] = (m[it] ?: 0.0) + by }
        return m.entries.sortedByDescending { it.value }.take(80).associate { it.key to it.value }
    }

    /** Minat lama memudar ~7% per hari supaya beranda mengikuti selera terkini. */
    private fun decay(p: InterestProfile): InterestProfile {
        val now = System.currentTimeMillis()
        if (decayedAt == 0L) { decayedAt = now; return p }
        val days = (now - decayedAt) / 86_400_000.0
        if (days < 1.0) return p
        decayedAt = now
        val f = 0.93.pow(days)
        return p.copy(
            topics = p.topics.mapValues { it.value * f }.filterValues { it >= 0.5 },
            channels = p.channels.mapValues { it.value.copy(score = it.value.score * f) }.filterValues { it.score >= 0.5 },
        )
    }

    @Synchronized
    private fun mutate(block: (InterestProfile) -> InterestProfile) {
        val current = _profile.value
        val next = block(decay(current)).copy(rev = current.rev + 1)
        _profile.value = next
        persist(next)
    }

    private fun persist(p: InterestProfile) {
        val s = StoredProfile(p.seeds, p.topics, p.channels.mapValues { StoredChannel(it.value.name, it.value.score) }, p.seen, p.onboarded, p.rev, decayedAt)
        prefs.edit().putString("profile", json.encodeToString(StoredProfile.serializer(), s)).apply()
    }

    private fun load(): InterestProfile {
        val raw = prefs.getString("profile", null) ?: return InterestProfile()
        return runCatching { json.decodeFromString(StoredProfile.serializer(), raw) }.getOrNull()?.let {
            decayedAt = it.decayedAt
            InterestProfile(it.seeds, it.topics, it.channels.mapValues { c -> ChannelAffinity(c.value.name, c.value.score) }, it.seen, it.onboarded, it.rev)
        } ?: InterestProfile()
    }
}
