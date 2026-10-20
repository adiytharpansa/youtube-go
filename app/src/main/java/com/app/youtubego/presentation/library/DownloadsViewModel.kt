package com.app.youtubego.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.OfflineDownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val downloads: OfflineDownloadsRepository) : ViewModel() {
    val records = downloads.records
    init { refresh() }
    fun refresh() { viewModelScope.launch { runCatching { downloads.refresh() } } }
    fun remove(id: Long) { viewModelScope.launch { runCatching { downloads.remove(id) } } }
}
