package com.app.playtubelite.di
import com.app.playtubelite.data.newpipe.NewPipeVideoRepository
import com.app.playtubelite.domain.repository.VideoRepository
import com.app.playtubelite.data.repository.InterestRepositoryImpl
import com.app.playtubelite.domain.repository.InterestRepository
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
