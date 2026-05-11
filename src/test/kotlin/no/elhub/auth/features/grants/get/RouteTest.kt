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
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateMalformedInputResponse
import java.time.OffsetDateTime
import java.util.UUID

class RouteTest : FunSpec({
    val authorizedSystem = AuthorizationParty(id = "id", type = PartyType.System)
    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"
    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("GET /{id} with invalid UUID returns 400") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            validateMalformedInputResponse(client.get("/not-a-uuid"))
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("GET /{id} with valid uuid returns 200 OK") {
        val party = AuthorizationParty("test", PartyType.Person)
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
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.OK
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} returns 500 when handler throws exception") {
        coEvery { handler.invoke(any()) } throws RuntimeException("Handler failure")
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.InternalServerError
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} returns 404 when handler returns ResourceNotFoundError") {
        coEvery { handler.invoke(any()) } returns no.elhub.auth.features.common.QueryError.ResourceNotFoundError.left()
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.NotFound
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }
})
