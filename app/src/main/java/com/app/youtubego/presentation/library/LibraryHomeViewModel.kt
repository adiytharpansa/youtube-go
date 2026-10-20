package com.app.youtubego.presentation.library

import androidx.lifecycle.ViewModel
import com.app.youtubego.data.local.PlaylistStore
import com.app.youtubego.data.local.UserLibraryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryHomeViewModel @Inject constructor(store: UserLibraryStore, playlistStore: PlaylistStore) : ViewModel() {
    val history = store.history
    val saved = store.saved
    val liked = store.liked
    val subscribed = store.subscribed
    val autoplay = store.autoplay
    val playlists = playlistStore.playlists
}
