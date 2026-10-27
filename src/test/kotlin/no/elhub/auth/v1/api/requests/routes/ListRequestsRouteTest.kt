package no.elhub.auth.v1.api.requests.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
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
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_REQUESTS_PATH
import no.elhub.auth.v1.api.requests.response.JsonApiListAuthorizationRequestsResponse
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.features.requests.ListRequestsHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import kotlin.time.Instant

class ListRequestsRouteTest : FunSpec({
    val handler = mockk<ListRequestsHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val request = AuthorizationRequest.new(
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = authorizedParty,
        requestedPrincipal = authorizedParty,
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
    )

    beforeTest { clearMocks(handler) }

    test("GET returns a JSON:API collection using default pagination and no status filter") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } returns
            Page(listOf(request), 1, Pagination())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get(V1_REQUESTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val body = response.body<JsonApiListAuthorizationRequestsResponse>()
            body.data.single().id shouldBe request.id.toString()
            body.data.single().links.self shouldBe "$V1_REQUESTS_PATH/${request.id}"
        }
    }

    test("GET serializes grants for requests that have them") {
        val grant = AuthorizationGrant.new(
            type = AuthorizationGrantType.Consumable,
            sourceType = AuthorizationGrantSourceType.Request,
            sourceId = request.id,
            principal = request.requestedPrincipal,
            grantedBy = request.requestedOf,
            grantedTo = request.requestedBy,
            approvedBy = request.requestedOf,
            validFrom = request.validFrom,
            validTo = request.validTo,
            scopes = emptyList(),
        )
        val accepted = request.copy(
            status = AuthorizationRequestStatus.Approved,
            approvedBy = request.requestedOf,
            authorizationGrants = listOf(grant),
        )
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } returns
            Page(listOf(accepted), 1, Pagination())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get(V1_REQUESTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonApiListAuthorizationRequestsResponse>()
            val grants = body.data.single().relationships.authorizationGrants.shouldNotBeNull().data
            grants.map { it.id } shouldBe listOf(grant.id.toString())
        }
    }

    listOf(
        "Pending" to listOf(AuthorizationRequestStatus.Pending),
        "Approved" to listOf(AuthorizationRequestStatus.Approved),
        "Rejected" to listOf(AuthorizationRequestStatus.Rejected),
        "Expired" to listOf(AuthorizationRequestStatus.Expired),
        "Pending,Approved,Pending" to listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Approved),
        "" to emptyList(),
    ).forEach { (filter, statuses) ->
        test("GET passes status filter '$filter' to the handler") {
            val pagination = Pagination(page = 1, size = 2)
            coEvery { handler(authorizedParty, pagination, statuses) } returns
                Page(listOf(request), 5, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.get(V1_REQUESTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", "1")
                        parameters.append("page[size]", "2")
                        parameters.append("filter[status]", filter)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                response.body<JsonApiListAuthorizationRequestsResponse>().data.single().id shouldBe request.id.toString()
                coVerify(exactly = 1) { handler(authorizedParty, pagination, statuses) }
            }
        }
    }

    listOf("Accepted", "pending", "Pending,Unknown", "Pending, Approved").forEach { filter ->
        test("GET rejects invalid status filter '$filter' without calling the handler") {
            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.get(V1_REQUESTS_PATH) {
                    accept(jsonApiContentType)
                    url { parameters.append("filter[status]", filter) }
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.body<JsonApiErrorCollection>().errors.single().status shouldBe "400"
                coVerify(exactly = 0) { handler(any(), any(), any()) }
            }
        }
    }

    listOf(
        Triple("-1", "101", Pagination(page = 0, size = 100)),
        Triple("invalid", "invalid", Pagination()),
        Triple("2", "0", Pagination(page = 2, size = 1)),
    ).forEach { (page, size, pagination) ->
        test("GET normalizes page '$page' and size '$size'") {
            coEvery { handler(authorizedParty, pagination, emptyList()) } returns
                Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_REQUESTS_PATH) { route(handler) }
                }

                val response = client.get(V1_REQUESTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", page)
                        parameters.append("page[size]", size)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                response.body<JsonApiListAuthorizationRequestsResponse>().data shouldBe emptyList()
                coVerify(exactly = 1) { handler(authorizedParty, pagination, emptyList()) }
            }
        }
    }

    test("GET returns a JSON:API error when the handler fails") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } throws
            RepositoryReadError.UnexpectedError("Database unavailable")

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_REQUESTS_PATH) { route(handler) }
            }

            val response = client.get(V1_REQUESTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "500"
        }
    }
})
