package no.elhub.auth.features.requests.query

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.query.dto.GetRequestCollectionResponse
import no.elhub.auth.setupAppWith
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

class RouteTest : FunSpec({

    val authorizedPerson = AuthorizationParty(id = UUID.randomUUID().toString(), type = PartyType.Person)

    val requestedByParty = AuthorizationParty("gln1", PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty("nin1", PartyType.Person)
    val requestedToParty = AuthorizationParty("nin2", PartyType.Person)

    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("GET returns 403 when handler returns NotAuthorizedError") {
        coEvery { handler.invoke(any()) } returns QueryError.NotAuthorizedError.left()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.Forbidden
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET returns 500 when handler throws exception") {
        coEvery { handler.invoke(any()) } throws RuntimeException("Unexpected error")
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.InternalServerError
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET returns OK with empty list when handler returns no requests") {
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationRequest>(), 0L, Pagination()).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            response.body<GetRequestCollectionResponse>().data.shouldBeEmpty()
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET returns OK with correct body when handler returns requests") {
        val authorizationRequest = AuthorizationRequest(
            id = UUID.randomUUID(),
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Pending,
            validTo = currentTimeUtc(),
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            requestedFrom = requestedFromParty,
            properties = emptyList()
        )
        coEvery { handler.invoke(any()) } returns Page(listOf(authorizationRequest), 1L, Pagination()).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<GetRequestCollectionResponse>()
            body.data.shouldHaveSize(1)
            body.data[0].id shouldBe authorizationRequest.id.toString()
            body.data[0].type shouldBe "AuthorizationRequest"
            body.data[0].attributes.status shouldBe "Pending"
            body.data[0].attributes.requestType shouldBe "ChangeOfBalanceSupplierForPerson"
            body.data[0].attributes.validTo.shouldNotBeBlank()
            body.data[0].relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data[0].relationships.approvedBy.shouldBeNull()
            body.data[0].relationships.authorizationGrant.shouldBeNull()
            body.data[0].meta.values shouldBe emptyMap()
            body.data[0].links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET returns OK with multiple items when handler returns multiple requests") {
        val request1 = AuthorizationRequest(
            id = UUID.randomUUID(),
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Pending,
            validTo = currentTimeUtc(), createdAt = currentTimeUtc(), updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty, requestedTo = requestedToParty, requestedFrom = requestedFromParty,
            properties = emptyList()
        )
        val request2 = AuthorizationRequest(
            id = UUID.randomUUID(),
            type = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Accepted,
            validTo = currentTimeUtc(), createdAt = currentTimeUtc(), updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty, requestedTo = requestedToParty, requestedFrom = requestedFromParty,
            properties = emptyList()
        )
        coEvery { handler.invoke(any()) } returns Page(listOf(request1, request2), 2L, Pagination()).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<GetRequestCollectionResponse>()
            body.data.shouldHaveSize(2)
            body.data[0].attributes.status shouldBe "Pending"
            body.data[1].attributes.status shouldBe "Accepted"
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET with page params passes correct Pagination to handler") {
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationRequest>(), 0L, Pagination(page = 1, size = 5)).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            client.get("/?page[number]=1&page[size]=5")
        }
        coVerify(exactly = 1) { handler.invoke(match { it.pagination == Pagination(page = 1, size = 5) }) }
    }

    test("GET with status param passes correct status to handler") {
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationRequest>(), 0L, Pagination()).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            client.get("/?filter[status]=Pending,Rejected")
        }
        coVerify(exactly = 1) {
            handler.invoke(match { it.statuses == listOf(AuthorizationRequest.Status.Pending, AuthorizationRequest.Status.Rejected) })
        }
    }

    test("GET with status param returns BadRequest when supplying invalid status") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/?filter[status]=Foo")
            response.status shouldBe HttpStatusCode.BadRequest
            val resultJson: JsonApiErrorCollection = response.body()
            resultJson.errors[0].detail shouldBe "Invalid filter[status] value 'Foo'. Valid values: Accepted, Expired, Pending, Rejected"
        }
    }

    test("GET response meta and links contain correct pagination fields") {
        val pagination = Pagination(page = 1, size = 5)
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationRequest>(), 15L, pagination).right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonObject>()
            val meta = body["meta"]!!.jsonObject
            meta["totalItems"]!!.jsonPrimitive.content shouldBe "15"
            meta["totalPages"]!!.jsonPrimitive.content shouldBe "3"
            meta["page"]!!.jsonPrimitive.content shouldBe "1"
            meta["pageSize"]!!.jsonPrimitive.content shouldBe "5"
            val links = body["links"]!!.jsonObject
            links["self"]!!.jsonPrimitive.content shouldBe "$REQUESTS_PATH?page[number]=1&page[size]=5"
            links["first"]!!.jsonPrimitive.content shouldBe "$REQUESTS_PATH?page[number]=0&page[size]=5"
            links["last"]!!.jsonPrimitive.content shouldBe "$REQUESTS_PATH?page[number]=2&page[size]=5"
            links["prev"]!!.jsonPrimitive.content shouldBe "$REQUESTS_PATH?page[number]=0&page[size]=5"
            links["next"]!!.jsonPrimitive.content shouldBe "$REQUESTS_PATH?page[number]=2&page[size]=5"
        }
    }
})
