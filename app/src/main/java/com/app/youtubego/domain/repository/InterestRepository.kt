package com.app.youtubego.domain.repository

import com.app.youtubego.domain.model.InterestProfile
import com.app.youtubego.domain.model.VideoSummary
import kotlinx.coroutines.flow.StateFlow

/** Profil minat pengguna, tersimpan lokal di perangkat (tidak dikirim ke server mana pun). */
interface InterestRepository {
    val profile: StateFlow<InterestProfile>
    fun recordSearch(query: String)
    fun recordWatch(video: VideoSummary)
    fun setSeeds(ids: Set<String>)
    fun markOnboarded()
    fun forgetTopic(topic: String)
    fun forgetChannel(channelId: String)
    fun reset()
}
