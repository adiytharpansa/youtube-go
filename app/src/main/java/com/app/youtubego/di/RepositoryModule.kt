package com.app.youtubego.di
import com.app.youtubego.data.newpipe.NewPipeVideoRepository
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.data.repository.InterestRepositoryImpl
import com.app.youtubego.domain.repository.InterestRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindVideoRepository(impl: NewPipeVideoRepository): VideoRepository
    @Binds @Singleton abstract fun bindInterestRepository(impl: InterestRepositoryImpl): InterestRepository
}
