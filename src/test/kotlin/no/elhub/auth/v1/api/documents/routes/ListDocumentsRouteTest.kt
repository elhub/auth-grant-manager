package no.elhub.auth.v1.api.documents.routes

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
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_DOCUMENTS_PATH
import no.elhub.auth.v1.api.documents.response.JsonApiListAuthorizationDocumentsResponse
import no.elhub.auth.v1.api.documents.response.ResponseAuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.features.documents.ListDocumentsHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import kotlin.time.Instant

class ListDocumentsRouteTest : FunSpec({
    val handler = mockk<ListDocumentsHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val document = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = authorizedParty,
        requestedPrincipal = authorizedParty,
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        pdfBytes = byteArrayOf(),
    )

    beforeTest { clearMocks(handler) }

    test("GET returns a JSON:API collection using default pagination and no status filter") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } returns
            Page(listOf(document), 1, Pagination())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get(V1_DOCUMENTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val body = response.body<JsonApiListAuthorizationDocumentsResponse>()
            body.data.single().id shouldBe document.id
            body.data.single().links.self shouldBe "$V1_DOCUMENTS_PATH/${document.id}"
        }
    }

    listOf(
        "Signed" to listOf(AuthorizationDocumentStatus.Accepted),
        "Expired" to listOf(AuthorizationDocumentStatus.Expired),
        "Pending" to listOf(AuthorizationDocumentStatus.Pending),
        "Signed,Expired,Signed" to listOf(AuthorizationDocumentStatus.Accepted, AuthorizationDocumentStatus.Expired),
        "" to emptyList(),
    ).forEach { (filter, statuses) ->
        test("GET maps public status filter '$filter' to domain statuses") {
            val pagination = Pagination(page = 1, size = 2)
            coEvery { handler(authorizedParty, pagination, statuses) } returns
                Page(listOf(document.copy(status = AuthorizationDocumentStatus.Accepted)), 5, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }

                val response = client.get(V1_DOCUMENTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", "1")
                        parameters.append("page[size]", "2")
                        parameters.append("filter[status]", filter)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<JsonApiListAuthorizationDocumentsResponse>()
                body.data.single().attributes.status shouldBe ResponseAuthorizationDocumentStatus.Signed
                coVerify(exactly = 1) { handler(authorizedParty, pagination, statuses) }
            }
        }
    }

    listOf("Accepted", "Rejected", "signed", "Pending,Unknown", "Signed, Expired").forEach { filter ->
        test("GET rejects invalid status filter '$filter' without calling the handler") {
            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }

                val response = client.get(V1_DOCUMENTS_PATH) {
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
        test("GET normalizes page '$page' and size '$size' like v0") {
            coEvery { handler(authorizedParty, pagination, emptyList()) } returns
                Page(emptyList(), 0, pagination)

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }

                val response = client.get(V1_DOCUMENTS_PATH) {
                    accept(jsonApiContentType)
                    url {
                        parameters.append("page[number]", page)
                        parameters.append("page[size]", size)
                    }
                }

                response.status shouldBe HttpStatusCode.OK
                response.body<JsonApiListAuthorizationDocumentsResponse>().data shouldBe emptyList()
                coVerify(exactly = 1) { handler(authorizedParty, pagination, emptyList()) }
            }
        }
    }

    test("GET returns a JSON:API error when the handler fails") {
        coEvery { handler(authorizedParty, Pagination(), emptyList()) } throws
            RepositoryReadError.UnexpectedError("Database unavailable")

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get(V1_DOCUMENTS_PATH) { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.InternalServerError
            response.body<JsonApiErrorCollection>().errors.single().status shouldBe "500"
        }
    }
})
