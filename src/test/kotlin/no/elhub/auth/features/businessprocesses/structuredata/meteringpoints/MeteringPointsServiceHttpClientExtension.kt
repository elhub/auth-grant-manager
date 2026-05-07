package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

import io.kotest.core.listeners.AfterProjectListener
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val meteringPointsServiceHttpClient = HttpClient(Apache5) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 40_000
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            },
            contentType = ContentType.Application.Json,
        )
    }
    install(UserAgent) { agent = "test-auth-grant-manager" }
}

object CloseMeteringPointsServiceHttpClient : AfterProjectListener {
    override suspend fun afterProject() {
        meteringPointsServiceHttpClient.close()
    }
}
