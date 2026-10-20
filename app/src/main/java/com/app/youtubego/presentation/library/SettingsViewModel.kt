package com.app.youtubego.presentation.library

import androidx.lifecycle.ViewModel
import com.app.youtubego.data.local.UserLibraryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val library: UserLibraryStore) : ViewModel() {
    val autoplay = library.autoplay
    fun setAutoplay(enabled: Boolean) = library.setAutoplay(enabled)
}
