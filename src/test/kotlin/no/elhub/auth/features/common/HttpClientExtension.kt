package no.elhub.auth.features.common

import io.kotest.core.listeners.AfterProjectListener
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val httpTestClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
    }

object CloseHttpClient : AfterProjectListener {
    override suspend fun afterProject() {
        httpTestClient.close()
    }
}
