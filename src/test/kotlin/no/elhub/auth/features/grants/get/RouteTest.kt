package no.elhub.auth.features.grants.get

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateMalformedInputResponse
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

    test("GET /{id} should return 400 when having invalid token") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.InvalidToken.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            validateInvalidTokenResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id} returns forbund when getting unauthorized from authprovider") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.AccessDenied.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            validateForbiddenResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id} with invalid UUID should give bad request response") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val malformedUuid = "not-a-uuid"
            val response = client.get("/$malformedUuid")
            validateMalformedInputResponse(response)
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("GET /{id} with valid uuid and no errors should give 200 OK") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
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
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.OK
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} returns 500 when handler throws exception") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        coEvery { handler.invoke(any()) } throws RuntimeException("Handler failure")
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.InternalServerError
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} returns 401 when not authorized as any party") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.NotAuthorized.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.Unauthorized
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }
})
