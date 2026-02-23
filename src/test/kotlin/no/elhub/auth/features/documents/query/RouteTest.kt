package no.elhub.auth.features.documents.query

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.AuthorizationDocument.Status
import no.elhub.auth.features.documents.AuthorizationDocument.Type
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.query.dto.GetDocumentCollectionResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateNotAuthorizedResponse
import java.util.UUID
import kotlin.random.Random
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.elhub.auth.module as applicationModule

class RouteTest : FunSpec({

    val byAuthParty = AuthorizationParty("id1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("id2", PartyType.Person)
    val toAuthParty = AuthorizationParty("id3", PartyType.Person)
    val documents = listOf(
        AuthorizationDocument(
            id = UUID.fromString("e4cf3f16-3ca4-4cc1-9130-8792b29d06d3"),
            type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
            status = AuthorizationDocument.Status.Pending,
            file = Random.nextBytes(256),
            requestedBy = byAuthParty,
            requestedFrom = fromAuthParty,
            requestedTo = toAuthParty,
            signedBy = null,
            grantId = UUID.fromString("e6c038c6-4cba-41dc-af3b-ed027058504b"),
            properties = listOf(
                AuthorizationDocumentProperty("key1", "value1"),
                AuthorizationDocumentProperty("key2", "value2"),
            ),
            validTo = currentTimeWithTimeZone().plusDays(30),
            createdAt = currentTimeWithTimeZone(),
            updatedAt = currentTimeWithTimeZone()
        ),
        AuthorizationDocument(
            id = UUID.fromString("f696ce1d-3efa-48aa-b3e5-3019a0bfbabd"),
            type = AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson,
            status = AuthorizationDocument.Status.Rejected,
            file = Random.nextBytes(256),
            requestedBy = byAuthParty,
            requestedFrom = fromAuthParty,
            requestedTo = toAuthParty,
            signedBy = null,
            grantId = UUID.fromString("14b87e56-3070-4f8c-a1de-920b6b3b5cd7"),
            properties = listOf(
                AuthorizationDocumentProperty("key1", "value1"),
                AuthorizationDocumentProperty("key2", "value2"),
                AuthorizationDocumentProperty("key3", "value3"),
            ),
            validTo = currentTimeWithTimeZone().plusDays(30),
            createdAt = currentTimeWithTimeZone(),
            updatedAt = currentTimeWithTimeZone()
        ),
    )
    val authorizedPerson = AuthorizedParty.Person(id = UUID.fromString("adde4fc4-55b4-40bb-b84b-9f39ec027ce0"))
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "🐟", role = RoleType.BalanceSupplier)

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler
    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("GET / returns 200 when authorized as person and handler succeeds") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns documents.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            var response = client.get("/")
            validateQueryResponse(response, documents)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("GET / returns 200 when authorized as org and handler succeeds") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns documents.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            var response = client.get("/")
            validateQueryResponse(response, documents)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
    test("GET / returns 403 'Forbidden' when authorization fails with AccessDenied error") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns AuthError.AccessDenied.left()
        coEvery { handler.invoke(any()) } returns documents.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            validateForbiddenResponse(client.get("/"))
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("GET / returns 500 Internal server error when authorized as org and handler fails with IOError") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns QueryError.IOError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/")
            validateInternalServerErrorResponse(response)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
})

// Verifies documents from route matches documents
private suspend fun validateQueryResponse(response: HttpResponse, handlerDocuments: List<AuthorizationDocument>) {
    response.status shouldBe HttpStatusCode.OK
    val documentResponse: GetDocumentCollectionResponse = response.body()
    documentResponse.data.forEachIndexed { i, doc ->
        doc.apply {
            type shouldBe "AuthorizationDocument"
            id shouldBe handlerDocuments[i].id.toString()
            attributes.apply {
                status shouldBe handlerDocuments[i].status.toString()
                documentType shouldBe handlerDocuments[i].type.toString()
                createdAt shouldBe handlerDocuments[i].createdAt.toTimeZoneOffsetString()
                updatedAt shouldBe handlerDocuments[i].updatedAt.toTimeZoneOffsetString()
                validTo shouldBe handlerDocuments[i].validTo.toTimeZoneOffsetString()
            }
            relationships.apply {
                requestedBy.data.apply {
                    id shouldBe handlerDocuments[i].requestedBy.id
                    type shouldBe handlerDocuments[i].requestedBy.type.toString()
                }
                requestedFrom.data.apply {
                    id shouldBe handlerDocuments[i].requestedFrom.id
                    type shouldBe handlerDocuments[i].requestedFrom.type.toString()
                }
                requestedTo.data.apply {
                    id shouldBe handlerDocuments[i].requestedTo.id
                    type shouldBe handlerDocuments[i].requestedTo.type.toString()
                }
            }
            meta.apply {
                values.map { it.key to it.value } shouldBe handlerDocuments[i].properties.map { it.key to it.value }
            }
        }
    }
}
