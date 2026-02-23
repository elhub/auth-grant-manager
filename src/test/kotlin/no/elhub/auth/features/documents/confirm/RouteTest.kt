package no.elhub.auth.features.documents.confirm

import arrow.core.Either
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
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.toByteArray
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
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponse
import no.elhub.auth.features.documents.query.dto.GetDocumentCollectionResponse
import no.elhub.auth.putPdf
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.auth.validateNotAuthorizedResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.random.Random
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.elhub.auth.module as applicationModule

class RouteTest : FunSpec({

    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler
    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("PUT /{id}.pdf returns 204 when authorized as org and handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.putPdf("/02fe286b-4519-4ba8-9c84-dc18bffc9eb3.pdf", Random.nextBytes(256))
            response.status shouldBe HttpStatusCode.NoContent
            response.bodyAsText().shouldBeEmpty()
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
    test("PUT /{id}.pdf returns 400 with missing signature message when handler fails to validate signature") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns ConfirmError.ValidateSignaturesError(
            SignatureValidationError.MissingBankIdSignature
        ).left()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.putPdf("/8d4ed1dc-2ed2-4744-9a36-c5f9c4d224dc.pdf", Random.nextBytes(256))
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    title shouldBe "End user signature validation failed"
                    detail shouldBe "The AuthorizationDocument is missing the end user signature."
                }
            }
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("PUT /{id}.pdf returns 400 Invalid token when authorization fails with InvalidToken error") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.InvalidToken.left()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.putPdf("/7507f641-4734-41c5-9798-a507747f326e.pdf", Random.nextBytes(256))
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

    test("PUT /{id}.pdf returns 400 'Invalid input' when id is invalid") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.putPdf("/not-a-uuid.pdf", Random.nextBytes(256))
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
    test("PUT /{id}.pdf returns 400 'Missing input' when document is empty") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns Unit.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.putPdf("/4803264d-11a4-4d6a-81f4-996986cb7b35.pdf", Random.nextBytes(0))
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
