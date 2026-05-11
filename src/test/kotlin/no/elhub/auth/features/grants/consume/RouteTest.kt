package no.elhub.auth.features.grants.consume

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
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.dto.SingleGrantResponse
import no.elhub.auth.features.grants.consume.dto.ConsumeRequestAttributes
import no.elhub.auth.features.grants.consume.dto.JsonApiConsumeRequest
import no.elhub.auth.patchJson
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateConflictErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import java.time.OffsetDateTime
import java.util.UUID

class RouteTest : FunSpec({
    val authorizedSystem = AuthorizationParty(id = "id", type = PartyType.System)
    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"
    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("PATCH /{id} with invalid uuid returns 400") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patch("/invalid-uuid")
            validateMalformedInputResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Bad Request when body contains a blank id") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patchJson<JsonApiConsumeRequest>("/$validUuid", patchGrantBody(id = ""))
            response.status shouldBe HttpStatusCode.BadRequest
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Conflict when body contains an invalid uuid") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patchJson("/$validUuid", patchGrantBody(id = "not-a-uuid"))
            response.status shouldBe HttpStatusCode.Conflict
            validateConflictErrorResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Conflict when type is not AuthorizationGrant") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patchJson("/$validUuid", patchGrantBody(id = validUuid, type = "WrongType"))
            response.status shouldBe HttpStatusCode.Conflict
            validateConflictErrorResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Bad Request when status is missing in attributes") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patch("/$validUuid") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{
                      "data": {
                        "id": "$validUuid",
                        "type": "AuthorizationGrant",
                        "attributes": {}
                      }
                    }"""
                )
            }
            response.status shouldBe HttpStatusCode.BadRequest
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns Bad Request when id in path does not match request body") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.patch("/$validUuid") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{
                      "data": {
                        "id": "invalid-uuid",
                        "type": "AuthorizationGrant",
                        "attributes": {}
                      }
                    }"""
                )
            }
            response.status shouldBe HttpStatusCode.BadRequest
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PATCH /{id} returns OK and calls handler on valid request") {
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
