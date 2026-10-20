package com.app.youtubego.di

import com.app.youtubego.data.newpipe.OkHttpNewPipeDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * DI untuk jalur putar di perangkat (NewPipeExtractor).
 * Klien HTTP-nya TERPISAH dari klien API utama: tanpa interceptor Accept/cache aplikasi,
 * karena extractor mengatur header permintaannya sendiri.
 */
@Module @InstallIn(SingletonComponent::class)
object NewPipeModule {
    @Provides @Singleton
    fun newPipeDownloader(): OkHttpNewPipeDownloader = OkHttpNewPipeDownloader(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    )
}
