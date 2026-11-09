package no.elhub.auth.v1.api.grants.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.grants.response.JsonApiListAuthorizationGrantsResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.features.grants.ListGrantsHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class ListGrantsRouteTest : FunSpec({
    val handler = mockk<ListGrantsHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val grantedBy = AuthorizationParty("person-1", PartyType.Person)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = AuthorizationParty("999888771", PartyType.OrganizationEntity),
        grantedBy = grantedBy,
        grantedTo = authorizedParty,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )

    beforeTest { clearMocks(handler) }

    test("GET returns a JSON:API collection using default pagination") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } returns Page(listOf(grant), 1, Pagination())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val body = response.body<JsonApiListAuthorizationGrantsResponse>()
            body.data.single().id shouldBe grant.id.toString()
            body.data.single().links.self shouldBe "$V1_GRANTS_PATH/${grant.id}"
            body.meta.getValue("totalItems").jsonPrimitive.long shouldBe 1L
            body.meta.getValue("totalPages").jsonPrimitive.int shouldBe 1
        }
    }

    test("GET serializes pending grants in the collection") {
        val pending = grant.copy(
            status = AuthorizationGrantStatus.Pending,
            validFrom = Instant.parse("2099-01-01T00:00:00Z"),
        )
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } returns Page(listOf(pending), 1, Pagination())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val attributes = response.body<JsonApiListAuthorizationGrantsResponse>().data.single().attributes
            attributes.status shouldBe AuthorizationGrantStatus.Pending
            attributes.validFrom shouldBe pending.validFrom
        }
    }

    test("GET passes page parameters to the handler") {
        val pagination = Pagination(page = 1, size = 2)
        coEvery { handler(authorizedParty, pagination, emptyList()) } returns Page(listOf(grant), 5, pagination)

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) {
                accept(jsonApiContentType)
                url {
                    parameters.append("page[number]", "1")
                    parameters.append("page[size]", "2")
                }
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonApiListAuthorizationGrantsResponse>()
            body.meta.getValue("page").jsonPrimitive.int shouldBe 1
            body.meta.getValue("pageSize").jsonPrimitive.int shouldBe 2
            body.meta.getValue("totalPages").jsonPrimitive.int shouldBe 3
            coVerify(exactly = 1) { handler(authorizedParty, pagination, emptyList()) }
        }
    }

    listOf(
        Triple("-1", "101", Pagination(page = 0, size = 100)),
        Triple("invalid", "invalid", Pagination()),
        Triple("2", "0", Pagination(page = 2, size = 1)),
    ).forEach { (page, size, pagination) ->
        test("GET normalizes page '$page' and size '$size' like v0") {
            coEvery { handler(authorizedParty, pagination, emptyList()) } returns Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.get(V1_GRANTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", page)
                        parameters.append("page[size]", size)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                response.body<JsonApiListAuthorizationGrantsResponse>().data shouldBe emptyList()
                coVerify(exactly = 1) { handler(authorizedParty, pagination, emptyList()) }
            }
        }
    }

    (
        AuthorizationGrantStatus.entries.map { it.name to listOf(it) } + listOf(
            "Active,Expired,Active" to listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired),
            "" to emptyList(),
            " " to emptyList(),
        )
        ).forEach { (filter, statuses) ->
        test("GET parses status filter '$filter' and forwards it to the handler") {
            val pagination = Pagination(page = 1, size = 2)
            coEvery { handler(authorizedParty, pagination, statuses) } returns Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.get(V1_GRANTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", "1")
                        parameters.append("page[size]", "2")
                        parameters.append("filter[status]", filter)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { handler(authorizedParty, pagination, statuses) }
                if (statuses.isNotEmpty()) {
                    response.body<JsonApiListAuthorizationGrantsResponse>().links.self
                        ?.substringAfter("&filter[status]=") shouldBe statuses.joinToString(",")
                }
            }
        }
    }

    test("GET rejects invalid status filter without calling the handler") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) {
                accept(jsonApiContentType)
                url { parameters.append("filter[status]", "invalid-status") }
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "400"
            coVerify(exactly = 0) { handler(any(), any(), any()) }
        }
    }

    test("GET returns a JSON:API error when the handler fails") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } throws
            RepositoryReadError.UnexpectedError("Database unavailable")

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "500"
        }
    }
})
