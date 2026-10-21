package com.app.youtubego.domain.usecase
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.presentation.common.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
/** Halaman lanjutan beranda (scroll tak terbatas). FOR_YOU memakai profil minat, lainnya memakai kueri per kategori. */
class LoadMoreHomeUseCase @Inject constructor(private val repo: VideoRepository, private val forYou: GetForYouFeedUseCase) {
    suspend operator fun invoke(category: HomeCategory, page: Int): Result<List<VideoSummary>> =
        if (category == HomeCategory.FOR_YOU) forYou.more(page) else repo.homeFeed(category, page)
}
/** Pola baku semua use case: Flow<UiState<T>> = Loading -> Success | Error(pesan ramah, tanpa istilah teknis mentah). */
class GetTrendingUseCase @Inject constructor(private val repo: VideoRepository) {
    operator fun invoke(category: HomeCategory): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        emit(repo.homeFeed(category, 1).fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) }))
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
    is java.net.SocketTimeoutException -> "Server video terlalu lambat merespons. Coba lagi sebentar."
    is java.io.IOException -> "Tidak ada koneksi ke server. Periksa internet lalu coba lagi."
    is retrofit2.HttpException -> when {
        code() >= 500 -> "Server video sedang bermasalah (galat ${code()}). Coba lagi sebentar."
        code() == 404 -> "Video tidak tersedia atau sudah dihapus."
        else -> "Permintaan ditolak server (galat ${code()})."
    }
    else -> "Gagal memuat data. Coba lagi."
}

class GetCommentsUseCase @Inject constructor(private val repo: VideoRepository) {
    operator fun invoke(id: String): Flow<UiState<List<com.app.youtubego.domain.model.CommentItem>>> = flow {
        emit(UiState.Loading)
        emit(repo.comments(id).fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) }))
    }
}
