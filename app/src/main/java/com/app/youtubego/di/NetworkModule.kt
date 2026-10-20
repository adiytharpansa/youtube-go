package com.app.youtubego.di
import com.app.youtubego.data.remote.ApiConstants
import com.app.youtubego.data.remote.InvidiousApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import android.app.Application
@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    @Provides @Singleton fun okHttp(app: Application): OkHttpClient = OkHttpClient.Builder()
        // Cache HTTP 10 MB: hemat data untuk JSON + thumbnail yang berulang (Coil punya disk cache sendiri).
        .cache(Cache(File(app.cacheDir, "http_cache"), 10L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build()) }
        .apply {
            if (com.app.youtubego.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()
    @Provides @Singleton fun api(okHttp: OkHttpClient, json: Json): InvidiousApi =
        Retrofit.Builder().baseUrl(ApiConstants.BASE_URL).client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(InvidiousApi::class.java)
    @Provides @Named("io") fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
