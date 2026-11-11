package no.elhub.auth.v1.api.common

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import no.elhub.auth.common.config.jsonApiContentType
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

suspend fun assertUnsupportedQueryParameters(
    client: HttpClient,
    path: String,
    acceptType: ContentType = jsonApiContentType,
    method: HttpMethod = HttpMethod.Get,
) {
    listOf("unsupportedParameter", "filter[unsupportedProperty]", "page[unsupportedProperty]").forEach { parameter ->
        val response = client.request(path) {
            this.method = method
            accept(acceptType)
            url { this.parameters.append(parameter, "") }
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.contentType()?.withoutParameters() shouldBe jsonApiContentType
        val error = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText()).errors.single()
        error.status shouldBe "400"
        error.detail shouldBe "Unsupported query parameter '$parameter'."
    }
}
