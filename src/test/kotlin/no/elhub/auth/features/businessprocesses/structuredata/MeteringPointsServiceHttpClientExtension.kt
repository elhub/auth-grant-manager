package no.elhub.auth.features.businessprocesses.structuredata

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun meteringPointsServiceHttpClient(basicAuthUsername: String, basicAuthPassword: String) = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 40_000
    }
    install(Auth) {
        basic {
            credentials {
                BasicAuthCredentials(basicAuthUsername, basicAuthPassword)
            }
            sendWithoutRequest { true }
            realm = "Access to the '/' path"
        }
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
