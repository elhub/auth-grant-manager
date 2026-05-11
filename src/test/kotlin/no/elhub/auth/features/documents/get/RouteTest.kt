package no.elhub.auth.features.documents.get

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.toByteArray
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.currentTimeOslo
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import java.util.UUID
import kotlin.random.Random

class RouteTest : FunSpec({
    val byAuthParty = AuthorizationParty("id1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("id2", PartyType.Person)
    val toAuthParty = AuthorizationParty("id3", PartyType.Person)
    val hardId = UUID.fromString("acc22222-346b-4a26-a01b-e57aeada523b")
    val document = AuthorizationDocument(
        id = hardId,
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = Random.nextBytes(256),
        requestedBy = byAuthParty,
        requestedFrom = fromAuthParty,
        requestedTo = toAuthParty,
        signedBy = null,
        grantId = UUID.fromString("8844261a-5221-455c-a6cd-12a0d60724c2"),
        properties = listOf(
            AuthorizationDocumentProperty("key1", "value1"),
            AuthorizationDocumentProperty("key2", "value2"),
        ),
        validTo = currentTimeOslo().plusDays(30),
        createdAt = currentTimeOslo(),
        updatedAt = currentTimeOslo()
    )
    val authorizedPerson = AuthorizationParty(id = "1d024a64-abb0-47d1-9b81-5d98aaa1a8a9", type = PartyType.Person)
    val authorizedOrg = AuthorizationParty(id = "1", type = PartyType.OrganizationEntity)

    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("GET /{id}[.pdf] returns 200 when authorized as person and handler succeeds") {
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            var response = client.get("/${document.id}")
            response.status shouldBe HttpStatusCode.OK
            validateGetByIdResponse(response, document)
            coVerify(exactly = 1) { handler.invoke(match { it.authorizedParty.id == authorizedPerson.id }) }

            response = client.get("/${document.id}.pdf") { accept(ContentType.Application.Pdf) }
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Pdf
            response.bodyAsChannel().toByteArray() shouldBe document.file
            coVerify(exactly = 2) { handler.invoke(match { it.authorizedParty.id == authorizedPerson.id }) }
        }
    }

    test("GET /{id}[.pdf] returns 200 when authorized as org and handler succeeds") {
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            var response = client.get("/${document.id}")
            response.status shouldBe HttpStatusCode.OK
            validateGetByIdResponse(response, document)
            coVerify(exactly = 1) { handler.invoke(match { it.authorizedParty.id == authorizedOrg.id }) }

            response = client.get("/${document.id}.pdf") { accept(ContentType.Application.Pdf) }
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Pdf
            response.bodyAsChannel().toByteArray() shouldBe document.file
            coVerify(exactly = 2) { handler.invoke(match { it.authorizedParty.id == authorizedOrg.id }) }
        }
    }

    test("GET /{id}.pdf returns 406 on unsupported accept header") {
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/${document.id}.pdf") { accept(ContentType.Application.Json) }
            response.status shouldBe HttpStatusCode.NotAcceptable
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id}[.pdf] returns 400 when UUID is invalid") {
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val id = "not-a-uuid"
            validateMalformedInputResponse(client.get("/$id"))
            validateMalformedInputResponse(client.get("/$id.pdf") { accept(ContentType.Application.Pdf) })
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id}[.pdf] returns 500 when handler fails with IOError") {
        coEvery { handler.invoke(any()) } returns QueryError.IOError.left()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val id = "7b14fcba-c899-4a5c-aecb-6e5abcac2bcf"
            validateInternalServerErrorResponse(client.get("/$id"))
            validateInternalServerErrorResponse(client.get("/$id.pdf") { accept(ContentType.Application.Pdf) })
        }
    }
})

private suspend fun validateGetByIdResponse(response: HttpResponse, handlerDocument: AuthorizationDocument) {
    response.status shouldBe HttpStatusCode.OK
    val documentResponse: GetDocumentSingleResponse = response.body()
    documentResponse.data.apply {
        type shouldBe "AuthorizationDocument"
        id shouldBe handlerDocument.id.toString()
        attributes.apply {
            status shouldBe handlerDocument.status.toString()
            documentType shouldBe handlerDocument.type.toString()
            createdAt shouldBe handlerDocument.createdAt.toTimeZoneOffsetString()
            updatedAt shouldBe handlerDocument.updatedAt.toTimeZoneOffsetString()
            validTo shouldBe handlerDocument.validTo.toTimeZoneOffsetString()
        }
        relationships.apply {
            requestedBy.data.apply {
                id shouldBe handlerDocument.requestedBy.id
                type shouldBe handlerDocument.requestedBy.type.toString()
            }
            requestedFrom.data.apply {
                id shouldBe handlerDocument.requestedFrom.id
                type shouldBe handlerDocument.requestedFrom.type.toString()
            }
            requestedTo.data.apply {
                id shouldBe handlerDocument.requestedTo.id
                type shouldBe handlerDocument.requestedTo.type.toString()
            }
        }
        meta.apply {
            values.map { it.key to it.value } shouldBe handlerDocument.properties.map { it.key to it.value }
        }
    }
}
