package no.elhub.auth.features.documents.get

import no.elhub.auth.features.documents.module

import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

import io.ktor.serialization.kotlinx.json.json
import arrow.core.Either
import arrow.core.left
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.auth.module as applicationModule
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.tuple
import io.mockk.every
import io.mockk.coEvery
import kotlin.random.Random
import io.mockk.mockk
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.QueryError
import io.ktor.server.testing.testApplication
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.AuthorizationDocument.Type
import no.elhub.auth.features.documents.AuthorizationDocument.Status
import io.ktor.http.HttpStatusCode
import io.ktor.client.call.body
import io.ktor.http.ContentType
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.toByteArray
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.config.configureErrorHandling
import no.elhub.auth.config.configureSerialization
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponse
import no.elhub.auth.features.documents.route.testBusinessProcessesModule

class RouteTest : FunSpec({

    val byAuthParty = AuthorizationParty("id1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("id2", PartyType.Person)
    val toAuthParty = AuthorizationParty("id3", PartyType.Person)
    val hardId = UUID.fromString("acc22222-346b-4a26-a01b-e57aeada523b")
    val document = AuthorizationDocument(
        id = hardId,
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
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
        validTo = currentTimeWithTimeZone().plusDays(30),
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone()
    )
    val authorizedPerson = AuthorizedParty.Person(id = UUID.fromString("1d024a64-abb0-47d1-9b81-5d98aaa1a8a9"))
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)

    test("GET /{id}[.pdf] returns 200 when authorized as person and handler succeeds") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupApp(handler, authProvider)
            var response = client.get("/$hardId")
            response.status shouldBe HttpStatusCode.OK
            validateGetByIdResponse(response, document)

            coVerify(exactly = 1) {
                handler.invoke(match { it.authorizedParty.id == authorizedPerson.id.toString() })
            }

            response = client.get("/$hardId.pdf")
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Pdf
            response.bodyAsChannel().toByteArray() shouldBe document.file

            coVerify(exactly = 2) {
                handler.invoke(match { it.authorizedParty.id == authorizedPerson.id.toString() })
            }
        }
    }

    test("GET /{id}[.pdf] returns 200 when authorized as org and handler succeeds") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupApp(handler, authProvider)
            var response = client.get("/$hardId")
            response.status shouldBe HttpStatusCode.OK
            validateGetByIdResponse(response, document)

            coVerify(exactly = 1) {
                handler.invoke(match { it.authorizedParty.id == authorizedOrg.gln })
            }
            response = client.get("/$hardId.pdf")
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Pdf
            response.bodyAsChannel().toByteArray() shouldBe document.file
            coVerify(exactly = 2) {
                handler.invoke(match { it.authorizedParty.id == authorizedOrg.gln })
            }
        }
    }

    test("GET /{id}[.pdf] returns 400 when UUID is invalid") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupApp(handler, authProvider)
            val id = "not-a-uuid"
            validateMalformedInputResponse(client.get("/$id"))
            validateMalformedInputResponse(client.get("/$id.pdf"))
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id}[.pdf] returns appropriate error when authorization fails") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns AuthError.NotAuthorized.left()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupApp(handler, authProvider)
            val id = "7b14fcba-c899-4a5c-aecb-6e5abcac2bcf"
            validateNotAuthorizedResponse(client.get("/$id"))
            validateNotAuthorizedResponse(client.get("/$id.pdf"))
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("GET /{id}[.pdf] returns appropriate error when handler fails") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns QueryError.IOError.left()
        testApplication {
            setupApp(handler, authProvider)
            val id = "7b14fcba-c899-4a5c-aecb-6e5abcac2bcf"
            validateInternalServerErrorResponse(client.get("/$id"))
            validateInternalServerErrorResponse(client.get("/$id.pdf"))
        }
    }

})

private suspend fun validateMalformedInputResponse(response: HttpResponse) {
    response.status.value shouldBe 400
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1

        this[0].apply {
            title shouldBe "Invalid input"
            detail shouldBe "The provided payload did not satisfy the expected format"
        }
    }
}

private suspend fun validateInternalServerErrorResponse(response: HttpResponse) {
    response.status.value shouldBe 500
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            title shouldBe "Internal server error"
            detail shouldBe "An internal server error occurred"
        }
    }
}

private suspend fun validateNotAuthorizedResponse(response: HttpResponse) {
    response.status.value shouldBe 401
    val responseJson: JsonApiErrorCollection = response.body()
    responseJson.errors.apply {
        size shouldBe 1
        this[0].apply {
            title shouldBe "Not authorized"
            detail shouldBe "Authentication is required or invalid."
        }
    }
}

// Verifies document response from route matches document returned by handler
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

private fun ApplicationTestBuilder.setupApp(
    handler: Handler, authProvider: AuthorizationProvider
) {
    client = createClient {
        install(ClientContentNegotiation) { json() }
    }
    // TODO review setup! try to set up as similar as possible to real app
    // without configuring too many dependencies
    application {
        configureSerialization()
        configureErrorHandling()
        routing {
            route(handler = handler, authProvider = authProvider)
        }
    }
}
