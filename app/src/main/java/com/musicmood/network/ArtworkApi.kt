package com.musicmood.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Endpoint iTunes Search.
 * Doc: https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html
 */
interface ItunesApi {
    @GET("search?media=music&entity=song&limit=1")
    suspend fun searchSong(
        @Query("term") term: String,
        @Query("country") country: String = "it",
    ): ItunesResponse
}

data class ItunesResponse(
    val resultCount: Int = 0,
    val results: List<ItunesResult> = emptyList(),
)

data class ItunesResult(
    val artworkUrl100: String? = null,
    val artworkUrl60: String? = null,
)

/**
 * Endpoint Deezer Search (fallback).
 * Doc: https://developers.deezer.com/api/search
 */
interface DeezerApi {
    @GET("search/track?limit=1")
    suspend fun searchTrack(@Query("q") q: String): DeezerResponse
}

data class DeezerResponse(
    val data: List<DeezerTrack> = emptyList(),
)

data class DeezerTrack(
    val album: DeezerAlbum? = null,
)

data class DeezerAlbum(
    val cover: String? = null,
    val cover_medium: String? = null,
    val cover_big: String? = null,
    val cover_xl: String? = null,
)

object NetworkClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
            )
            .build()
    }

    val itunes: ItunesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesApi::class.java)
    }

    val deezer: DeezerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeezerApi::class.java)
    }
}
