package no.elhub.auth.v1.api.grants.routes

import io.kotest.core.spec.style.FunSpec
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.common.response.GrantSourceTypeResponse
import no.elhub.auth.v1.api.common.response.GrantTypeResponse
import no.elhub.auth.v1.api.grants.response.JsonApiGetAuthorizationGrantResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.features.grants.GetGrantError
import no.elhub.auth.v1.features.grants.GetGrantHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class GetGrantRouteTest : FunSpec({
    val handler = mockk<GetGrantHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val principal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val grantedBy = AuthorizationParty("person-1", PartyType.Person)
    val sourceId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = sourceId,
        principal = principal,
        grantedBy = grantedBy,
        grantedTo = authorizedParty,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val grantId = grant.id

    beforeTest { clearMocks(handler) }

    test("GET /{id} returns the grant as JSON:API") {
        coEvery { handler(grantId, authorizedParty) } returns grant

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_GRANTS_PATH/$grantId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val body = response.body<JsonApiGetAuthorizationGrantResponse>()
            body.data.id shouldBe grantId.toString()
            body.data.type shouldBe GrantTypeResponse.ConsumableGrant
            body.data.attributes.status shouldBe AuthorizationGrantStatus.Active
            body.data.relationships.principal.data.id shouldBe principal.id
            body.data.relationships.grantedBy.data.id shouldBe grantedBy.id
            body.data.relationships.grantedTo.data.id shouldBe authorizedParty.id
            body.data.relationships.approvedBy.data.id shouldBe grantedBy.id
            body.data.relationships.source.data.type shouldBe GrantSourceTypeResponse.AuthorizationDocument
            body.data.relationships.source.data.id shouldBe sourceId.toString()
            body.data.links.self shouldBe "$V1_GRANTS_PATH/$grantId"
        }
    }

    test("GET /{id} omits validTo for a grant without an end date") {
        coEvery { handler(grantId, authorizedParty) } returns grant.copy(type = AuthorizationGrantType.Persistent, validTo = null)

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_GRANTS_PATH/$grantId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val attributes = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject.getValue("data").jsonObject.getValue("attributes").jsonObject
            attributes.containsKey("validTo") shouldBe false
            attributes.containsKey("validFrom") shouldBe true
        }
    }

    listOf(
        GetGrantError.NotAuthorized() to HttpStatusCode.Forbidden,
        RepositoryReadError.NotFoundError("Missing grant") to HttpStatusCode.NotFound,
    ).forEach { (error, status) ->
        test("GET /{id} returns ${status.value} for ${error.javaClass.simpleName}") {
            coEvery { handler(grantId, authorizedParty) } throws error

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.get("$V1_GRANTS_PATH/$grantId") { accept(jsonApiContentType) }

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val body = response.body<JsonApiErrorCollection>()
                body.errors.single().status shouldBe status.value.toString()
            }
        }
    }

    test("GET /{id} returns 400 for an invalid grant ID") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_GRANTS_PATH/not-a-uuid") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
            body.errors.single().status shouldBe "400"
            coVerify(exactly = 0) { handler(any(), any()) }
        }
    }
})
