package no.elhub.auth.v1.api.requests.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_REQUESTS_PATH
import no.elhub.auth.v1.api.common.GrantResourceType
import no.elhub.auth.v1.api.requests.response.JsonApiGetAuthorizationRequestResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.features.requests.GetRequestError
import no.elhub.auth.v1.features.requests.GetRequestHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class GetRequestRouteTest : FunSpec({
    val handler = mockk<GetRequestHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val timestamp = Instant.parse("2026-10-01T08:00:00Z")
    val request = AuthorizationRequest(
        id = requestId,
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        status = AuthorizationRequestStatus.Pending,
        requestedScopes = emptyList(),
        externalReference = "contract-123",
        redirectUri = null,
        meta = buildJsonObject { put("requestedPrincipalName", "Example AS") },
        validFrom = timestamp,
        validTo = Instant.parse("2026-11-01T08:00:00Z"),
        createdAt = timestamp,
        updatedAt = timestamp,
        requestedBy = authorizedParty,
        requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        approvedBy = null,
        authorizationGrants = null,
    )

    beforeTest { clearMocks(handler) }

    test("GET /{id} returns the request as JSON:API") {
        coEvery { handler(requestId, authorizedParty) } returns request

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_REQUESTS_PATH/$requestId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters().toString() shouldBe "application/vnd.api+json"
            val body = response.body<JsonApiGetAuthorizationRequestResponse>()
            body.data.id shouldBe requestId.toString()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.externalReference shouldBe request.externalReference
            body.data.meta shouldBe request.meta
            body.data.relationships.requestedBy.data.id shouldBe authorizedParty.id
            body.data.links.self shouldBe "$V1_REQUESTS_PATH/$requestId"
        }
    }

    test("GET /{id} serializes the corresponding grant identifiers") {
        val grant = AuthorizationGrant.new(
            type = AuthorizationGrantType.Consumable,
            sourceType = AuthorizationGrantSourceType.Request,
            sourceId = requestId,
            principal = request.requestedPrincipal,
            grantedBy = request.requestedOf,
            grantedTo = request.requestedBy,
            approvedBy = request.requestedOf,
            validFrom = request.validFrom,
            validTo = request.validTo,
            scopes = emptyList(),
        )
        coEvery { handler(requestId, authorizedParty) } returns request.copy(
            status = AuthorizationRequestStatus.Approved,
            approvedBy = request.requestedOf,
            authorizationGrants = listOf(grant),
        )

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_REQUESTS_PATH/$requestId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonApiGetAuthorizationRequestResponse>()
            body.data.relationships.approvedBy.shouldNotBeNull().data.id shouldBe request.requestedOf.id
            val grants = body.data.relationships.authorizationGrants.shouldNotBeNull().data
            grants.map { it.id } shouldBe listOf(grant.id.toString())
            grants.map { it.type } shouldBe listOf(GrantResourceType.ConsumableGrant)
        }
    }

    listOf(
        GetRequestError.NotAuthorized() to HttpStatusCode.Forbidden,
        RepositoryReadError.NotFoundError("Missing request") to HttpStatusCode.NotFound,
        RepositoryReadError.UnexpectedError("Database unavailable") to HttpStatusCode.InternalServerError,
    ).forEach { (error, status) ->
        test("GET /{id} returns ${status.value} for ${error.javaClass.simpleName}") {
            coEvery { handler(requestId, authorizedParty) } throws error

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.get("$V1_REQUESTS_PATH/$requestId") { accept(jsonApiContentType) }

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val body = response.body<JsonApiErrorCollection>()
                body.errors.single().status shouldBe status.value.toString()
            }
        }
    }

    test("GET /{id} returns 400 for an invalid request ID") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_REQUESTS_PATH/not-a-uuid") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
            body.errors.single().status shouldBe "400"
        }
    }
})
