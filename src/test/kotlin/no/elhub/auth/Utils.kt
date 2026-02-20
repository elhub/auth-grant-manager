package no.elhub.auth

import io.ktor.serialization.kotlinx.json.json

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

import io.ktor.server.routing.routing
import no.elhub.auth.config.configureErrorHandling
import no.elhub.auth.config.configureSerialization

import io.ktor.server.routing.Routing

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.ktor.client.call.*
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.kotest.matchers.should
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import io.kotest.matchers.nulls.shouldNotBeNull

fun String.shouldBeValidUuid() {
    this should beValidUuid()
}

private fun beValidUuid() = Matcher<String> { s ->
    val ok = try {
        UUID.fromString(s)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
    MatcherResult(ok, { "Expected a valid UUID but got '$s'" }, { "Expected not to be a valid UUID" })
}

suspend fun validateMissingTokenResponse(response: HttpResponse) {
    response.status shouldBe HttpStatusCode.Unauthorized
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            status shouldBe "401"
            title shouldBe "Missing authorization"
            detail shouldBe "Bearer token is required in the Authorization header."
        }
    }
    responseJson.meta.apply {
        "createdAt".shouldNotBeNull()
    }
}

suspend fun validateInvalidTokenResponse(response: HttpResponse) {
    response.status shouldBe HttpStatusCode.Unauthorized
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            status shouldBe "401"
            title shouldBe "Invalid token"
            detail shouldBe "Token could not be verified."
        }
    }
    responseJson.meta.apply {
        "createdAt".shouldNotBeNull()
    }

}

suspend fun validateUnsupportedPartyResponse(response: HttpResponse) {
    response.status shouldBe HttpStatusCode.Forbidden
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            status shouldBe "403"
            title shouldBe "Unsupported party type"
            detail shouldBe "The party type you are authorized as is not supported for this endpoint."
        }
    }
    responseJson.meta.apply {
        "createdAt".shouldNotBeNull()
    }
}

fun ApplicationTestBuilder.setupAppWith(
    routingConfig: Routing.() -> Unit
) {
    client = createClient {
        install(ClientContentNegotiation) { json() }
    }
    application {
        configureSerialization()
        configureErrorHandling()
        routing {
            routingConfig()
        }
    }
}
