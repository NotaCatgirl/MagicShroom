package edu.csumb.magicshroom.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON settings shared by Retrofit and error-body parsing. */
val apiJson = Json {
    // Fields the server adds later don't break older app builds.
    ignoreUnknownKeys = true
    // Null fields are left out of request bodies, so a PATCH sends only what changed.
    explicitNulls = false
}

/** Builds the Retrofit client for the API at [baseUrl], which must end in `/`. */
fun createMagicShroomApi(baseUrl: String, client: OkHttpClient = OkHttpClient()): MagicShroomApi =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MagicShroomApi::class.java)
