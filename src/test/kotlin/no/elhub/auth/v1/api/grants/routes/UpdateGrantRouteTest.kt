package no.elhub.auth.v1.api.grants.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.patchJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.common.GrantResourceType
import no.elhub.auth.v1.api.grants.response.JsonApiGetAuthorizationGrantResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.features.grants.UpdateGrantError
import no.elhub.auth.v1.features.grants.UpdateGrantHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class UpdateGrantRouteTest : FunSpec({
    val handler = mockk<UpdateGrantHandler>()
    val system = AuthorizationParty("system-1", PartyType.System)
    val principal = AuthorizationParty("person-1", PartyType.Person)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Request,
        sourceId = UUID.randomUUID(),
        principal = principal,
        grantedBy = principal,
        grantedTo = AuthorizationParty("7080005053246", PartyType.OrganizationEntity),
        approvedBy = principal,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val id = grant.id
    val path = "$V1_GRANTS_PATH/$id"

    fun body(
        status: String = "Exhausted",
        dataId: String = id.toString(),
        type: String = "ConsumableGrant",
    ) = """{"data":{"id":"$dataId","type":"$type","attributes":{"status":"$status"}}}"""

    beforeTest { clearMocks(handler) }

    test("PATCH consumes a grant and returns it as JSON:API") {
        coEvery { handler(id, system, AuthorizationGrantStatus.Exhausted) } returns
            grant.copy(status = AuthorizationGrantStatus.Exhausted)

        testApplication {
            setupAppWith(system) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.patch(path) {
                accept(jsonApiContentType)
                contentType(jsonApiContentType)
                setBody(body())
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val data = response.body<JsonApiGetAuthorizationGrantResponse>().data
            data.id shouldBe id.toString()
            data.type shouldBe GrantResourceType.ConsumableGrant
            data.attributes.status shouldBe AuthorizationGrantStatus.Exhausted
            data.relationships.principal.data.id shouldBe principal.id
            data.links.self shouldBe path
            coVerify(exactly = 1) { handler(id, system, AuthorizationGrantStatus.Exhausted) }
        }
    }

    test("PATCH revokes a persistent grant as its principal") {
        coEvery { handler(id, principal, AuthorizationGrantStatus.Revoked) } returns
            grant.copy(type = AuthorizationGrantType.Persistent, status = AuthorizationGrantStatus.Revoked)

        testApplication {
            setupAppWith(principal) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.patchJson(path, body(status = "Revoked", type = "PersistentGrant"))

            response.status shouldBe HttpStatusCode.OK
            val data = response.body<JsonApiGetAuthorizationGrantResponse>().data
            data.type shouldBe GrantResourceType.PersistentGrant
            data.attributes.status shouldBe AuthorizationGrantStatus.Revoked
            coVerify(exactly = 1) { handler(id, principal, AuthorizationGrantStatus.Revoked) }
        }
    }

    listOf(
        "invalid path ID" to ("$V1_GRANTS_PATH/not-a-uuid" to body()),
        "blank body ID" to (path to body(dataId = "")),
        "missing body ID" to (path to """{"data":{"type":"ConsumableGrant","attributes":{"status":"Exhausted"}}}"""),
        "missing status" to (path to """{"data":{"id":"$id","type":"ConsumableGrant","attributes":{}}}"""),
        "unknown status" to (path to body(status = "Unknown")),
        "malformed JSON" to (path to "{"),
        "a mismatched body ID" to (path to body(dataId = UUID.randomUUID().toString())),
        "an invalid body ID" to (path to body(dataId = "not-a-uuid")),
    ).forEach { (description, input) ->
        test("PATCH returns 422 for $description without calling the handler") {
            testApplication {
                setupAppWith(system) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.patchJson(input.first, input.second)

                response.status shouldBe HttpStatusCode.UnprocessableEntity
                response.body<JsonApiErrorCollection>().errors.single().status shouldBe "422"
                coVerify(exactly = 0) { handler(any(), any(), any()) }
            }
        }
    }

    test("PATCH returns 422 for a wrong resource type without calling the handler") {
        testApplication {
            setupAppWith(system) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.patchJson(path, body(type = "AuthorizationGrant"))

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "422"
            coVerify(exactly = 0) { handler(any(), any(), any()) }
        }
    }

    listOf(
        UpdateGrantError.NotAuthorized("Not authorized") to HttpStatusCode.Forbidden,
        UpdateGrantError.InvalidTransition() to HttpStatusCode.UnprocessableEntity,
        UpdateGrantError.Expired() to HttpStatusCode.UnprocessableEntity,
        RepositoryReadError.NotFoundError("Missing grant") to HttpStatusCode.NotFound,
        IllegalStateException("boom") to HttpStatusCode.InternalServerError,
    ).forEach { (error, status) ->
        test("PATCH returns ${status.value} for ${error::class.simpleName}") {
            coEvery { handler(id, system, AuthorizationGrantStatus.Exhausted) } throws error

            testApplication {
                setupAppWith(system) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.patchJson(path, body())

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                response.body<JsonApiErrorCollection>().errors.single().status shouldBe status.value.toString()
            }
        }
    }
})
