package no.elhub.auth.features.grants.consume

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.dto.SingleGrantResponse
import no.elhub.auth.features.grants.consume.dto.ConsumeRequestAttributes
import no.elhub.auth.features.grants.consume.dto.JsonApiConsumeRequest
import no.elhub.auth.patchJson
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateConflictErrorResponse
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import java.time.OffsetDateTime
import java.util.UUID

class RouteTest : FunSpec({
    val authorizedSystem = AuthorizedParty.System(id = "id")
    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"
    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler
    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("PATCH /{id} with invalid uuid returns 400") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val invalidId = "invalid-uuid"
            val response = client.patch("/$invalidId")

            validateMalformedInputResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns 400 Invalid token when authorization fails with Invalid token error") {
        coEvery { authProvider.authorizeElhubService(any()) } returns AuthError.InvalidToken.left()

        testApplication {
            setupAppWith { route(handler, authProvider) }

            val response = client.patch("/$validUuid")
            response.status shouldBe HttpStatusCode.Unauthorized
            validateInvalidTokenResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns 403 Forbidden when auth fails with unauthorized") {
        coEvery { authProvider.authorizeElhubService(any()) } returns AuthError.AccessDenied.left()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.patch("/$validUuid")
            response.status shouldBe HttpStatusCode.Forbidden
            validateForbiddenResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Bad Request when body contains an blank id in request body") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.patchJson<JsonApiConsumeRequest>("/$validUuid", patchGrantBody(id = ""))
            response.status shouldBe HttpStatusCode.BadRequest
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("PATCH /{id} returns Bad Request when body contains an invalid uuid in request body") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.patchJson("/$validUuid", patchGrantBody(id = "not-a-uuid"))
            response.status shouldBe HttpStatusCode.Conflict
            validateConflictErrorResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("PATCH /{id} returns Conflict when type is not AuthorizationGrant") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.patchJson("/$validUuid", patchGrantBody(id = validUuid, type = "WrongType"))
            response.status shouldBe HttpStatusCode.Conflict
            validateConflictErrorResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("PATCH /{id} returns Bad Request when status is missing in attributes") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }

            val response = client.patch("/$validUuid") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{
                  \"data\": {
                    \"id\": \"$validUuid\",
                    \"type\": \"AuthorizationGrant\",
                    \"attributes\": {}
                  }
                }"""
                )
            }
            response.status shouldBe HttpStatusCode.BadRequest
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns bad request when id in path is correct, but not in the requestbody") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val invalidUuid = "invalid-uuid"
            val response = client.patch("/$validUuid") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{
                  \"data\": {
                    \"id\": \"$invalidUuid\",
                    \"type\": \"AuthorizationGrant\",
                    \"attributes\": {}
                  }
                }"""
                )
            }
            response.status shouldBe HttpStatusCode.BadRequest

            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns OK and calls handler on valid request") {
        coEvery { authProvider.authorizeElhubService(any()) } returns authorizedSystem.right()
        val party = no.elhub.auth.features.common.party.AuthorizationParty("test", no.elhub.auth.features.common.party.PartyType.Person)
        val expectedGrant = AuthorizationGrant(
            id = UUID.fromString(validUuid),
            grantStatus = AuthorizationGrant.Status.Exhausted,
            grantedFor = party,
            grantedBy = party,
            grantedTo = party,
            grantedAt = OffsetDateTime.parse("2026-02-27T10:30:25+01:00"),
            validFrom = OffsetDateTime.parse("2026-02-27T09:00:59+01:00"),
            createdAt = OffsetDateTime.parse("2026-02-27T09:00:59+01:00"),
            updatedAt = OffsetDateTime.parse("2026-02-27T09:00:59+01:00"),
            validTo = OffsetDateTime.parse("2026-02-27T09:00:59+01:00"),
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = UUID.randomUUID(),
            scopeIds = emptyList(),
            properties = emptyList()
        )
        coEvery { handler.invoke(any()) } returns expectedGrant.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.patch("/$validUuid") {
                contentType(ContentType.Application.Json)
                setBody(patchGrantBody(id = validUuid))
            }
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<SingleGrantResponse>()
            body.data.attributes!!.grantedAt shouldBe expectedGrant.grantedAt.toString()
            body.data.attributes!!.validFrom shouldBe expectedGrant.validFrom.toString()
            body.data.attributes!!.createdAt shouldBe expectedGrant.createdAt.toString()
            body.data.attributes!!.updatedAt shouldBe expectedGrant.updatedAt.toString()
            body.data.attributes!!.validTo shouldBe expectedGrant.validTo.toString()
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
})

fun patchGrantBody(
    id: String,
    attributes: ConsumeRequestAttributes = ConsumeRequestAttributes(status = AuthorizationGrant.Status.Exhausted),
    type: String = "AuthorizationGrant"
) = JsonApiConsumeRequest(
    data = JsonApiRequestResourceObject(
        id = id,
        type = type,
        attributes = attributes
    )
)
