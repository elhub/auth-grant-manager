package no.elhub.auth.v1.api.documents.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_DOCUMENTS_PATH
import no.elhub.auth.v1.api.common.response.GrantTypeResponse
import no.elhub.auth.v1.api.documents.response.JsonApiGetAuthorizationDocumentResponse
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.features.documents.GetDocumentError
import no.elhub.auth.v1.features.documents.GetDocumentHandler
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class GetDocumentRouteTest : FunSpec({
    val handler = mockk<GetDocumentHandler>()
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val documentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val timestamp = Instant.parse("2026-10-01T08:00:00Z")
    val document = AuthorizationDocument(
        id = documentId.toString(),
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        status = AuthorizationDocumentStatus.Pending,
        requestedScopes = emptyList(),
        externalReference = "contract-123",
        meta = buildJsonObject { put("requestedPrincipalName", "Example AS") },
        validFrom = timestamp,
        validTo = Instant.parse("2026-11-01T08:00:00Z"),
        createdAt = timestamp,
        updatedAt = timestamp,
        requestedBy = authorizedParty,
        requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        approvedBy = null,
        authorizationGrants = null,
        pdfBytes = "%PDF-1.7\nexample document".toByteArray(),
    )

    beforeTest { clearMocks(handler) }

    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = documentId,
        principal = document.requestedPrincipal,
        grantedBy = document.requestedOf,
        grantedTo = document.requestedBy,
        approvedBy = document.requestedOf,
        validFrom = document.validFrom,
        validTo = document.validTo,
        scopes = emptyList(),
    )

    test("GET /{id} serializes the corresponding grant identifiers") {
        val persistentGrant = grant.copy(id = UUID.randomUUID(), type = AuthorizationGrantType.Persistent)
        coEvery { handler(documentId, authorizedParty) } returns document.copy(
            status = AuthorizationDocumentStatus.Approved,
            approvedBy = document.requestedOf,
            authorizationGrants = listOf(grant, persistentGrant),
        )

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_DOCUMENTS_PATH/$documentId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonApiGetAuthorizationDocumentResponse>()
            body.data.attributes.status.name shouldBe "Approved"
            body.data.relationships.approvedBy.shouldNotBeNull().data.id shouldBe document.requestedOf.id
            val grants = body.data.relationships.authorizationGrants.shouldNotBeNull().data
            grants.map { it.id } shouldBe listOf(grant.id.toString(), persistentGrant.id.toString())
            grants.map { it.type } shouldBe listOf(GrantTypeResponse.ConsumableGrant, GrantTypeResponse.PersistentGrant)
        }
    }

    test("GET /{id} serializes an empty grant relationship") {
        coEvery { handler(documentId, authorizedParty) } returns document.copy(authorizationGrants = emptyList())

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_DOCUMENTS_PATH/$documentId") { accept(jsonApiContentType) }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonApiGetAuthorizationDocumentResponse>()
            body.data.relationships.authorizationGrants.shouldNotBeNull().data shouldBe emptyList()
        }
    }

    test("GET /{id} returns the document as JSON:API") {
        coEvery { handler(documentId, authorizedParty) } returns document

        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_DOCUMENTS_PATH/$documentId") {
                accept(jsonApiContentType)
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters().toString() shouldBe "application/vnd.api+json"
            val body = response.body<JsonApiGetAuthorizationDocumentResponse>()
            body.data.id shouldBe documentId.toString()
            body.data.type shouldBe "AuthorizationDocument"
            body.data.attributes.externalReference shouldBe document.externalReference
            body.data.meta shouldBe document.meta
            body.data.relationships.requestedBy.data.id shouldBe authorizedParty.id
            body.data.links.self shouldBe "$V1_DOCUMENTS_PATH/$documentId"
            body.data.links.file shouldBe "$V1_DOCUMENTS_PATH/$documentId.pdf"
        }
    }

    listOf(null, "application/pdf", "*/*", "application/*").forEach { accept ->
        test("GET /{id}.pdf returns PDF bytes with Accept ${accept ?: "absent"}") {
            coEvery { handler(documentId, authorizedParty) } returns document

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }
                val pdfClient = createClient {}

                val response = pdfClient.get("$V1_DOCUMENTS_PATH/$documentId.pdf") {
                    if (accept != null) header(HttpHeaders.Accept, accept)
                }

                response.status shouldBe HttpStatusCode.OK
                response.contentType() shouldBe ContentType.Application.Pdf
                response.body<ByteArray>() shouldBe document.pdfBytes
            }
        }
    }

    test("GET /{id}.pdf returns 406 for an incompatible Accept header") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(V1_DOCUMENTS_PATH) { route(handler) }
            }

            val response = client.get("$V1_DOCUMENTS_PATH/$documentId.pdf") {
                accept(jsonApiContentType)
            }

            response.status shouldBe HttpStatusCode.NotAcceptable
            response.contentType()?.withoutParameters() shouldBe jsonApiContentType
            val body = response.body<JsonApiErrorCollection>()
            body.errors.single().status shouldBe "406"
        }
    }

    listOf("", ".pdf").forEach { suffix ->
        listOf(
            GetDocumentError.NotAuthorized() to HttpStatusCode.Forbidden,
            RepositoryReadError.NotFoundError("Missing document") to HttpStatusCode.NotFound,
            RepositoryReadError.UnexpectedError("Database unavailable") to HttpStatusCode.InternalServerError,
        ).forEach { (error, status) ->
            test("GET /{id}$suffix returns ${status.value} for ${error.javaClass.simpleName}") {
                coEvery { handler(documentId, authorizedParty) } throws error

                testApplication {
                    setupAppWith(authorizedParty) {
                        route(V1_DOCUMENTS_PATH) { route(handler) }
                    }

                    val response = client.get("$V1_DOCUMENTS_PATH/$documentId$suffix") {
                        accept(jsonApiContentType)
                        if (suffix.isNotEmpty()) accept(ContentType.Application.Pdf)
                    }

                    response.status shouldBe status
                    response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                    val body = response.body<JsonApiErrorCollection>()
                    body.errors.single().status shouldBe status.value.toString()
                }
            }
        }

        test("GET /{id}$suffix returns 400 for an invalid document ID") {
            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }

                val response = client.get("$V1_DOCUMENTS_PATH/not-a-uuid$suffix") {
                    accept(if (suffix.isEmpty()) jsonApiContentType else ContentType.Application.Pdf)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
                body.errors.single().status shouldBe "400"
            }
        }
    }

    listOf(
        GetDocumentError.NotAuthorized() to HttpStatusCode.Forbidden,
        RepositoryReadError.NotFoundError("Missing document") to HttpStatusCode.NotFound,
        RepositoryReadError.UnexpectedError("Database unavailable") to HttpStatusCode.InternalServerError,
        IllegalStateException("Unexpected internal details") to HttpStatusCode.InternalServerError,
    ).forEach { (error, status) ->
        test("GET /{id}.pdf returns a JSON:API ${status.value} for ${error.javaClass.simpleName} when only PDF is accepted") {
            coEvery { handler(documentId, authorizedParty) } throws error

            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }
                val pdfClient = createClient {}

                val response = pdfClient.get("$V1_DOCUMENTS_PATH/$documentId.pdf") {
                    accept(ContentType.Application.Pdf)
                }

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
                body.errors.single().status shouldBe status.value.toString()
                if (status == HttpStatusCode.InternalServerError) {
                    body.errors.single().detail shouldBe "An internal server error occurred"
                }
            }
        }
    }

    listOf(
        Triple("not-a-uuid", ContentType.Application.Pdf, HttpStatusCode.BadRequest),
        Triple(documentId.toString(), ContentType.Text.Plain, HttpStatusCode.NotAcceptable),
    ).forEach { (id, acceptedType, status) ->
        test("GET /{id}.pdf returns a JSON:API ${status.value} validation error with Accept $acceptedType") {
            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }
                val pdfClient = createClient {}

                val response = pdfClient.get("$V1_DOCUMENTS_PATH/$id.pdf") { accept(acceptedType) }

                response.status shouldBe status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
                body.errors.single().status shouldBe status.value.toString()
            }
        }
    }
})
