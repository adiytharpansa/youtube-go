package com.app.youtubego.di
import com.app.youtubego.data.repository.VideoRepositoryImpl
import com.app.youtubego.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
