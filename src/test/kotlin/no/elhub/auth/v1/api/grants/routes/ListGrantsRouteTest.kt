package no.elhub.auth.v1.api.grants.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
import no.elhub.auth.common.config.jsonApiContentType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.grants.response.JsonApiListAuthorizationGrantsResponse
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.GrantAgentFilters
import no.elhub.auth.v1.features.grants.ListGrantsHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class ListGrantsRouteTest : FunSpec({
    val handler = mockk<ListGrantsHandler>()
    val authorizedAgent = AuthorizationAgent("7070575000001", AgentType.OrganizationEntity)
    val grantedBy = AuthorizationAgent("abcdef12-3456-7890-abcd-ef1234567890", AgentType.Person)
    val organization = AuthorizationAgent("999888771", AgentType.Organization)
    val mixedAgents = listOf(grantedBy, organization, authorizedAgent)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = organization,
        grantedBy = grantedBy,
        grantedTo = authorizedAgent,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )

    beforeTest { clearMocks(handler) }

    test("GET returns a JSON:API collection using default pagination") {
        coEvery { handler(authorizedAgent, Pagination(), emptyList(), GrantAgentFilters()) } returns Page(listOf(grant), 1, Pagination())

        testApplication {
            setupAppWith(authorizedAgent) {
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
        coEvery { handler(authorizedAgent, Pagination(), emptyList(), GrantAgentFilters()) } returns Page(listOf(pending), 1, Pagination())

        testApplication {
            setupAppWith(authorizedAgent) {
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
        coEvery { handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) } returns Page(listOf(grant), 5, pagination)

        testApplication {
            setupAppWith(authorizedAgent) {
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
            coVerify(exactly = 1) { handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) }
        }
    }

    listOf(
        Triple("-1", "101", Pagination(page = 0, size = 100)),
        Triple("invalid", "invalid", Pagination()),
        Triple("2", "0", Pagination(page = 2, size = 1)),
    ).forEach { (page, size, pagination) ->
        test("GET normalizes page '$page' and size '$size' like v0") {
            coEvery { handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) } returns Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedAgent) {
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
                coVerify(exactly = 1) { handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) }
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
            coEvery { handler(authorizedAgent, pagination, statuses, GrantAgentFilters()) } returns Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedAgent) {
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
                coVerify(exactly = 1) { handler(authorizedAgent, pagination, statuses, GrantAgentFilters()) }
                if (statuses.isNotEmpty()) {
                    response.body<JsonApiListAuthorizationGrantsResponse>().links.self
                        .substringAfter("&filter[status]=") shouldBe statuses.joinToString(",")
                }
            }
        }
    }

    listOf(
        GRANTED_BY_FILTER_PARAM to GrantAgentFilters(grantedBy = mixedAgents),
        PRINCIPAL_FILTER_PARAM to GrantAgentFilters(principal = mixedAgents),
        APPROVED_BY_FILTER_PARAM to GrantAgentFilters(approvedBy = mixedAgents),
        GRANTED_TO_FILTER_PARAM to GrantAgentFilters(grantedTo = mixedAgents),
    ).forEach { (parameter, agentFilters) ->
        test("GET infers mixed $parameter agent types, normalizes UUID case and deduplicates in input order") {
            coEvery { handler(authorizedAgent, Pagination(), emptyList(), agentFilters) } returns Page(emptyList(), 0, Pagination())

            testApplication {
                setupAppWith(authorizedAgent) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                val response = client.get(V1_GRANTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append(
                            parameter,
                            "${grantedBy.id.uppercase()},${organization.id},${authorizedAgent.id}," +
                                "${grantedBy.id},${organization.id},${authorizedAgent.id}",
                        )
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { handler(authorizedAgent, Pagination(), emptyList(), agentFilters) }
                Url(requireNotNull(response.body<JsonApiListAuthorizationGrantsResponse>().links.self))
                    .parameters[parameter] shouldBe mixedAgents.joinToString(",") { it.id }
            }
        }

        test("GET rejects malformed $parameter IDs before calling the handler") {
            testApplication {
                setupAppWith(authorizedAgent) {
                    route(V1_GRANTS_PATH) { route(handler) }
                }

                listOf("arbitrary-id", "12345678a", "707057500000a", "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669")
                    .forEach { filter ->
                        val response = client.get(V1_GRANTS_PATH) {
                            accept(jsonApiContentType)
                            url { parameters.append(parameter, filter) }
                        }

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.body<JsonApiErrorCollection>().errors.single().status shouldBe "400"
                    }
                coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
            }
        }
    }

    test("GET forwards combined agent filters, statuses and pagination") {
        val pagination = Pagination(page = 1, size = 2)
        val statuses = listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Pending)
        val agentFilters = GrantAgentFilters(
            grantedBy = listOf(grantedBy, organization),
            principal = listOf(organization),
            approvedBy = listOf(grantedBy),
            grantedTo = listOf(authorizedAgent),
        )
        coEvery { handler(authorizedAgent, pagination, statuses, agentFilters) } returns Page(emptyList(), 0, pagination)

        testApplication {
            setupAppWith(authorizedAgent) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) {
                accept(jsonApiContentType)
                url {
                    parameters.append("page[number]", "1")
                    parameters.append("page[size]", "2")
                    parameters.append(STATUS_FILTER_PARAM, "Active,Pending")
                    parameters.append(GRANTED_BY_FILTER_PARAM, "${grantedBy.id},${organization.id}")
                    parameters.append(PRINCIPAL_FILTER_PARAM, organization.id)
                    parameters.append(APPROVED_BY_FILTER_PARAM, grantedBy.id)
                    parameters.append(GRANTED_TO_FILTER_PARAM, authorizedAgent.id)
                }
            }

            response.status shouldBe HttpStatusCode.OK
            coVerify(exactly = 1) { handler(authorizedAgent, pagination, statuses, agentFilters) }
            val parameters = Url(requireNotNull(response.body<JsonApiListAuthorizationGrantsResponse>().links.self)).parameters
            parameters[STATUS_FILTER_PARAM] shouldBe "Active,Pending"
            parameters[GRANTED_BY_FILTER_PARAM] shouldBe "${grantedBy.id},${organization.id}"
            parameters[PRINCIPAL_FILTER_PARAM] shouldBe organization.id
            parameters[APPROVED_BY_FILTER_PARAM] shouldBe grantedBy.id
            parameters[GRANTED_TO_FILTER_PARAM] shouldBe authorizedAgent.id
        }
    }

    test("GET treats empty and whitespace-only agent filters as omitted") {
        coEvery { handler(authorizedAgent, Pagination(), emptyList(), GrantAgentFilters()) } returns Page(emptyList(), 0, Pagination())

        testApplication {
            setupAppWith(authorizedAgent) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) {
                accept(jsonApiContentType)
                url {
                    parameters.append(GRANTED_BY_FILTER_PARAM, "")
                    parameters.append(PRINCIPAL_FILTER_PARAM, " ")
                    parameters.append(APPROVED_BY_FILTER_PARAM, "\t")
                    parameters.append(GRANTED_TO_FILTER_PARAM, "  ")
                }
            }

            response.status shouldBe HttpStatusCode.OK
            coVerify(exactly = 1) { handler(authorizedAgent, Pagination(), emptyList(), GrantAgentFilters()) }
            response.body<JsonApiListAuthorizationGrantsResponse>().links.self shouldBe
                "$V1_GRANTS_PATH?page[number]=0&page[size]=100"
        }
    }

    test("GET rejects invalid status filter without calling the handler") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) {
                accept(jsonApiContentType)
                url { parameters.append("filter[status]", "invalid-status") }
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "400"
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    test("GET returns a JSON:API error when the handler fails") {
        coEvery { handler(authorizedAgent, Pagination(), emptyList(), GrantAgentFilters()) } throws
            RepositoryReadError.UnexpectedError("Database unavailable")

        testApplication {
            setupAppWith(authorizedAgent) {
                route(V1_GRANTS_PATH) { route(handler) }
            }

            val response = client.get(V1_GRANTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "500"
        }
    }
})
