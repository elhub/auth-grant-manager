package no.elhub.auth.features.requests.update

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.UpdateRequestAttributes
import no.elhub.auth.features.requests.update.dto.UpdateRequestResponse
import no.elhub.auth.patchJson
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateConflictErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

class RouteTest : FunSpec({

    val authorizedPerson = AuthorizationParty(id = UUID.randomUUID().toString(), type = PartyType.Person)

    val requestData = JsonApiRequestResourceObject(
        id = authorizedPerson.id,
        type = "AuthorizationRequest",
        attributes = UpdateRequestAttributes(status = AuthorizationRequest.Status.Pending)
    )
    val requestBody = JsonApiUpdateRequest(data = requestData)

    val requestedByParty = AuthorizationParty("gln1", PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty("nin1", PartyType.Person)
    val requestedToParty = AuthorizationParty("nin2", PartyType.Person)

    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("PATCH /{id} returns 400 when having an invalid requestID") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            validateMalformedInputResponse(client.patchJson("/random-id", ""))
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("PATCH /{id} returns 400 when input body is wrong") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.patchJson("/${authorizedPerson.id}", """{"test": "badInput"}""")
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].title shouldBe "Missing required field in request body"
            }
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("PATCH /{id} returns 400 when id in path doesn't match request body") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.patchJson("/${UUID.randomUUID()}", requestData)
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].title shouldBe "Missing required field in request body"
            }
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("PATCH /{id} returns Conflict when request type is not AuthorizationRequest") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            validateConflictErrorResponse(
                client.patchJson("/${authorizedPerson.id}", requestBody.copy(data = requestData.copy(type = "InvalidType")))
            )
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("PATCH /{id} returns OK when handler returns") {
        val authorizationRequest = AuthorizationRequest(
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Accepted,
            validTo = currentTimeUtc(),
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            requestedFrom = requestedFromParty,
            id = UUID.fromString(authorizedPerson.id),
            properties = emptyList()
        )
        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.patchJson("/${authorizedPerson.id}", requestBody)
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<UpdateRequestResponse>()
            body.data.id shouldBe authorizationRequest.id.toString()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.status shouldBe "Accepted"
            body.data.attributes.requestType shouldBe "ChangeOfBalanceSupplierForPerson"
            body.data.attributes.validTo.shouldNotBeBlank()
            body.data.attributes.createdAt.shouldNotBeBlank()
            body.data.attributes.updatedAt.shouldNotBeBlank()
            body.data.relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data.relationships.requestedBy.data.type shouldBe requestedByParty.type.name
            body.data.relationships.approvedBy.shouldBeNull()
            body.data.relationships.authorizationGrant.shouldBeNull()
            body.data.meta.values shouldBe emptyMap()
            body.data.links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
        }
    }
})
