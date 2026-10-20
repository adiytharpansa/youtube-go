package com.app.youtubego.domain.usecase
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.presentation.common.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
/** Pola baku semua use case: Flow<UiState<T>> = Loading -> Success | Error(pesan ramah, tanpa istilah teknis mentah). */
class GetTrendingUseCase @Inject constructor(private val repo: VideoRepository) {
    operator fun invoke(category: HomeCategory): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        emit(repo.trending(category).fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) }))
    }
}
class SearchVideosUseCase @Inject constructor(private val repo: VideoRepository) {
    operator fun invoke(query: String, shortsOnly: Boolean = false): Flow<UiState<List<VideoSummary>>> = flow {
        if (query.isBlank()) { emit(UiState.Success(emptyList())); return@flow }
        emit(UiState.Loading)
        emit(repo.search(query, shortsOnly = shortsOnly).fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) }))
    }
}
class GetVideoDetailUseCase @Inject constructor(private val repo: VideoRepository) {
    operator fun invoke(id: String): Flow<UiState<VideoDetail>> = flow {
        emit(UiState.Loading)
        emit(repo.videoDetail(id).fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) }))
    }
}
fun Throwable.toFriendlyMessage(): String = when (this) {
    is java.io.IOException -> "Tidak ada koneksi. Periksa internet lalu coba lagi."
    is retrofit2.HttpException -> if (code() >= 500) "Server sedang bermasalah. Coba lagi sebentar." else "Video tidak tersedia."
    else -> "Gagal memuat data. Coba lagi."
}
