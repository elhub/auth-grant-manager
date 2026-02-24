package no.elhub.auth

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.elhub.auth.config.configureErrorHandling
import no.elhub.auth.config.configureSerialization
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

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

suspend fun validateMalformedInputResponse(response: HttpResponse) {
    response.status.value shouldBe 400
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1

        this[0].apply {
            title shouldBe "Invalid input"
            detail shouldBe "The provided payload did not satisfy the expected format"
        }
    }
}

suspend fun validateInternalServerErrorResponse(response: HttpResponse) {
    response.status.value shouldBe 500
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            title shouldBe "Internal server error"
            detail shouldBe "An internal server error occurred"
        }
    }
}

suspend fun validateNotAuthorizedResponse(response: HttpResponse) {
    response.status.value shouldBe 401
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            title shouldBe "Not authorized"
            detail shouldBe "Authentication is required or invalid."
        }
    }
}

suspend fun validateForbiddenResponse(response: HttpResponse) {
    response.status.value shouldBe 403
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            title shouldBe "Forbidden"
            detail shouldBe "Access is denied for this endpoint."
        }
    }
}

suspend fun validatePartyNotAuthorizedResponse(response: HttpResponse) {
    response.status shouldBe HttpStatusCode.Forbidden
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            status shouldBe "403"
            title shouldBe "Party not authorized"
            detail shouldBe "The party is not allowed to access this resource"
        }
    }
    responseJson.meta.apply {
        "createdAt".shouldNotBeNull()
    }
}

suspend inline fun <reified T> HttpClient.postJson(
    path: String,
    body: T
) = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend inline fun HttpClient.putPdf(
    path: String,
    body: ByteArray
) = put(path) {
    contentType(ContentType.Application.Pdf)
    setBody(body)
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
