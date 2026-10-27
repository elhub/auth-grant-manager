package no.elhub.auth.v1.api.requests.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.patchJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_REQUESTS_PATH
import no.elhub.auth.v1.api.requests.response.JsonApiGetAuthorizationRequestResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.TextLanguage
import no.elhub.auth.v1.features.requests.UpdateRequestError
import no.elhub.auth.v1.features.requests.UpdateRequestHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class UpdateRequestRouteTest : FunSpec({
    val handler = mockk<UpdateRequestHandler>()
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val request = AuthorizationRequest.new(
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = AuthorizationParty("7080005053246", PartyType.OrganizationEntity),
        requestedPrincipal = AuthorizationParty("999888777", PartyType.Organization),
        requestedOf = requestedOf,
    )
    val id = request.id
    val path = "$V1_REQUESTS_PATH/$id"

    fun body(
        status: String = "Approved",
        textLanguage: String = "nb",
        dataId: String = id.toString(),
        type: String = "AuthorizationRequest",
    ) = """{"data":{"id":"$dataId","type":"$type","attributes":{"status":"$status","textLanguage":"$textLanguage"}}}"""

    beforeTest { clearMocks(handler) }

    test("PATCH accepts the request and returns it with its grants and text language") {
        val grant = AuthorizationGrant.new(
            type = AuthorizationGrantType.Consumable,
            sourceType = AuthorizationGrantSourceType.Request,
            sourceId = id,
            principal = request.requestedPrincipal,
            grantedBy = requestedOf,
            grantedTo = request.requestedBy,
            approvedBy = requestedOf,
            validFrom = request.validFrom,
            validTo = request.validTo,
            scopes = emptyList(),
        )
        coEvery { handler(id, requestedOf, AuthorizationRequestStatus.Approved, TextLanguage.NN) } returns request.copy(
            status = AuthorizationRequestStatus.Approved,
            approvedBy = requestedOf,
            authorizationGrants = listOf(grant),
            textLanguage = TextLanguage.NN,
        )

        testApplication {
            setupAppWith(requestedOf) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.patch(path) {
                accept(jsonApiContentType)
                contentType(ContentType.Application.Json)
                setBody(body(textLanguage = "nn"))
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val data = response.body<JsonApiGetAuthorizationRequestResponse>().data
            data.id shouldBe id.toString()
            data.attributes.status shouldBe AuthorizationRequestStatus.Approved
            data.attributes.textLanguage shouldBe TextLanguage.NN
            data.relationships.authorizationGrants.shouldNotBeNull().data.single().id shouldBe grant.id.toString()
        }
    }

    test("PATCH rejects the request") {
        coEvery { handler(id, requestedOf, AuthorizationRequestStatus.Rejected, TextLanguage.EN) } returns
            request.copy(status = AuthorizationRequestStatus.Rejected, textLanguage = TextLanguage.EN)

        testApplication {
            setupAppWith(requestedOf) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.patchJson(path, body(status = "Rejected", textLanguage = "en"))

            response.status shouldBe HttpStatusCode.OK
            val data = response.body<JsonApiGetAuthorizationRequestResponse>().data
            data.attributes.status shouldBe AuthorizationRequestStatus.Rejected
            data.attributes.textLanguage shouldBe TextLanguage.EN
        }
    }

    listOf(
        "invalid path ID" to ("$V1_REQUESTS_PATH/not-a-uuid" to body()),
        "unsupported text language" to (path to body(textLanguage = "sv")),
        "missing text language" to (path to """{"data":{"id":"$id","type":"AuthorizationRequest","attributes":{"status":"Approved"}}}"""),
        "unknown status" to (path to body(status = "Accepted")),
        "malformed JSON" to (path to "{"),
    ).forEach { (description, input) ->
        test("PATCH returns 400 for $description without calling the handler") {
            testApplication {
                setupAppWith(requestedOf) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.patchJson(input.first, input.second)

                response.status shouldBe HttpStatusCode.BadRequest
                response.body<JsonApiErrorCollection>().errors.single().status shouldBe "400"
                coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
            }
        }
    }

    listOf(
        "a body ID that does not match the path ID" to UUID.randomUUID().toString(),
        "an invalid body ID" to "not-a-uuid",
    ).forEach { (description, dataId) ->
        test("PATCH returns 409 for $description without calling the handler") {
            testApplication {
                setupAppWith(requestedOf) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.patchJson(path, body(dataId = dataId))

                response.status shouldBe HttpStatusCode.Conflict
                coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
            }
        }
    }

    test("PATCH returns a type mismatch error for a wrong resource type without calling the handler") {
        testApplication {
            setupAppWith(requestedOf) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.patchJson(path, body(type = "AuthorizationDocument"))

            response.status shouldBe HttpStatusCode.Conflict
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    listOf(
        UpdateRequestError.NotAuthorized() to HttpStatusCode.Forbidden,
        UpdateRequestError.InvalidTransition() to HttpStatusCode.UnprocessableEntity,
        UpdateRequestError.AlreadyProcessed() to HttpStatusCode.UnprocessableEntity,
        UpdateRequestError.Expired() to HttpStatusCode.UnprocessableEntity,
        UpdateRequestError.InvalidGrantValidity() to HttpStatusCode.UnprocessableEntity,
        UpdateRequestError.UnsupportedRequestedOf() to HttpStatusCode.UnprocessableEntity,
        RepositoryReadError.NotFoundError("Missing request") to HttpStatusCode.NotFound,
        IllegalStateException("boom") to HttpStatusCode.InternalServerError,
    ).forEach { (error, status) ->
        test("PATCH returns ${status.value} for ${error::class.simpleName}") {
            coEvery { handler(id, requestedOf, any(), any()) } throws error

            testApplication {
                setupAppWith(requestedOf) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.patchJson(path, body())

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                response.body<JsonApiErrorCollection>().errors.single().status shouldBe status.value.toString()
            }
        }
    }
})
