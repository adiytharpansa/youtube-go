package com.app.youtubego.presentation.common
/** State UI generik untuk SEMUA layar. Sealed interface: when() wajib exhaustive, tanpa else. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
