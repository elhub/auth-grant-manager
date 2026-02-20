package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.documents.common.SignatureValidationError

import io.kotest.matchers.string.shouldBeEmpty
import no.elhub.auth.features.documents.module

import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

import io.ktor.serialization.kotlinx.json.json
import arrow.core.Either
import arrow.core.left
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import arrow.core.right
import no.elhub.auth.setupAppWith
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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
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

class RouteTest : FunSpec({

    // TODO coVerify some more stuff
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)
    test("PUT /{id}.pdf returns 204 when authorized as person and handler succeeds") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.put("/02fe286b-4519-4ba8-9c84-dc18bffc9eb3.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody(Random.nextBytes(256))

            }
            response.status shouldBe HttpStatusCode.NoContent
            response.bodyAsText().shouldBeEmpty()
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
    test("PUT /{id}.pdf returns appropriate error when handler fails to validate signature") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns ConfirmError.ValidateSignaturesError(
            SignatureValidationError.MissingBankIdSignature
        ).left()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.put("/8d4ed1dc-2ed2-4744-9a36-c5f9c4d224dc.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody(Random.nextBytes(256))

            }
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    title shouldBe "End user signature validation failed"
                    detail shouldBe "The document is missing the end user signature."
                }
            }
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("PUT /{id}.pdf returns appropriate error when authorization fails") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.InvalidToken.left()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.put("/8d4ed1dc-2ed2-4744-9a36-c5f9c4d224dc.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody(Random.nextBytes(256))

            }
            response.status shouldBe HttpStatusCode.Unauthorized
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    title shouldBe "Invalid token"
                    detail shouldBe "Token could not be verified."
                }
            }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("PUT /{id}.pdf returns appropriate error when id is invalid") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.put("/not-a-uuid.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody(Random.nextBytes(256))

            }
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    title shouldBe "Invalid input"
                    detail shouldBe "The provided payload did not satisfy the expected format"
                }
            }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("PUT /{id}.pdf returns appropriate error when document is empty") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.put("/ac983fc0-8bb5-48be-a343-78d345b3e8f6.pdf") {
                contentType(ContentType.Application.Pdf)
                setBody(Random.nextBytes(0))

            }
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    title shouldBe "Missing input"
                    detail shouldBe "Necessary information was not provided"
                }
            }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
})
