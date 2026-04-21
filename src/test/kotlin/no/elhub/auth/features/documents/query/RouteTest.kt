package no.elhub.auth.features.documents.query

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
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
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.currentTimeOslo
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.query.dto.GetDocumentCollectionResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInternalServerErrorResponse
import java.util.UUID
import kotlin.random.Random

class RouteTest : FunSpec({

    val byAuthParty = AuthorizationParty("id1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("id2", PartyType.Person)
    val toAuthParty = AuthorizationParty("id3", PartyType.Person)
    val documents = listOf(
        AuthorizationDocument(
            id = UUID.fromString("e4cf3f16-3ca4-4cc1-9130-8792b29d06d3"),
            type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
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
            validTo = currentTimeOslo().plusDays(30),
            createdAt = currentTimeOslo(),
            updatedAt = currentTimeOslo()
        ),
        AuthorizationDocument(
            id = UUID.fromString("f696ce1d-3efa-48aa-b3e5-3019a0bfbabd"),
            type = AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson,
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
            validTo = currentTimeOslo().plusDays(30),
            createdAt = currentTimeOslo(),
            updatedAt = currentTimeOslo()
        ),
    )
    val authorizedPerson = AuthorizationParty(id = "adde4fc4-55b4-40bb-b84b-9f39ec027ce0", type = PartyType.Person)
    val authorizedOrg = AuthorizationParty(id = "🐟", type = PartyType.OrganizationEntity)

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler
    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("GET / returns 200 when authorized as person and handler succeeds") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns Page(documents, documents.size.toLong(), Pagination()).right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            var response = client.get("/")
            validateQueryResponse(response, documents)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("GET / returns 200 when authorized as org and handler succeeds") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Page(documents, documents.size.toLong(), Pagination()).right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            var response = client.get("/")
            validateQueryResponse(response, documents)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
    test("GET / returns 403 'Forbidden' when authorization fails with AccessDenied error") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns AuthError.AccessDenied.left()
        coEvery { handler.invoke(any()) } returns Page(documents, documents.size.toLong(), Pagination()).right()
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

    test("GET with page params passes correct Pagination to handler") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationDocument>(), 0L, Pagination(page = 2, size = 5)).right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            client.get("/?page[number]=2&page[size]=5")
        }
        coVerify(exactly = 1) { handler.invoke(match { it.pagination == Pagination(page = 2, size = 5) }) }
    }

    test("GET response meta and links contain correct pagination fields") {
        val pagination = Pagination(page = 0, size = 10)
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationDocument>(), 4L, pagination).right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<JsonObject>()
            val meta = body["meta"]!!.jsonObject
            meta["totalItems"]!!.jsonPrimitive.content shouldBe "4"
            meta["totalPages"]!!.jsonPrimitive.content shouldBe "1"
            meta["page"]!!.jsonPrimitive.content shouldBe "0"
            meta["pageSize"]!!.jsonPrimitive.content shouldBe "10"
            val links = body["links"]!!.jsonObject
            links["self"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=0&page[size]=10"
            links["first"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=0&page[size]=10"
            links["last"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=0&page[size]=10"
            links["prev"] shouldBe null
            links["next"] shouldBe null
        }
    }

    test("GET links on a middle page contain prev and next") {
        val pagination = Pagination(page = 1, size = 5)
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns Page(emptyList<AuthorizationDocument>(), 15L, pagination).right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/")
            response.status shouldBe HttpStatusCode.OK
            val links = response.body<JsonObject>()["links"]!!.jsonObject
            links["self"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=1&page[size]=5"
            links["first"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=0&page[size]=5"
            links["last"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=2&page[size]=5"
            links["prev"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=0&page[size]=5"
            links["next"]!!.jsonPrimitive.content shouldBe "$DOCUMENTS_PATH?page[number]=2&page[size]=5"
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
