package no.elhub.auth.v1.api.requests.routes

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.features.requests.CreateRequestHandler
import no.elhub.auth.v1.features.requests.creation.RedirectUriValidationError
import no.elhub.auth.v1.features.requests.creation.RedirectUriValidator
import no.elhub.auth.v1.repositories.RequestRepository
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

class CreateRequestRedirectUriRouteTest : FunSpec({
    val requestedBy = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888771", PartyType.Organization)
    val requestedOf = AuthorizationParty("999888772", PartyType.Organization)
    val redirectUri = "https://app.example.com/callback"
    val requestBody = """
        {
          "data": {
            "type": "AuthorizationRequest",
            "attributes": {
              "requestType": "EnergySupplierFrameworkAgreement",
              "redirectUri": "$redirectUri",
              "requestedGrantValidFrom": "2100-01-01T01:00:00+01:00",
              "requestedGrantValidTo": "2101-01-01T00:00:00Z"
            },
            "meta": {
              "requestedPrincipal": "999888771",
              "requestedOf": "999888772"
            }
          }
        }
    """.trimIndent()

    suspend fun postWith(validator: RedirectUriValidator): Pair<HttpStatusCode, JsonApiErrorCollection> {
        val partyService = mockk<PartyService>()
        coEvery { partyService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())
        val repository = mockk<RequestRepository>(relaxed = true)
        val handler = CreateRequestHandler(
            changeOfEnergySupplierHandler = mockk(),
            moveInAndChangeOfEnergySupplierHandler = mockk(),
            energySupplierFrameworkAgreementHandler = mockk(),
            redirectUriValidator = validator,
            requestRepository = repository,
        )
        var result: Pair<HttpStatusCode, JsonApiErrorCollection>? = null

        testApplication {
            setupAppWith(requestedBy) {
                route(partyService, handler)
            }

            val response = client.postJson("/", requestBody)
            result = response.status to response.body()
        }

        return checkNotNull(result)
    }

    test("POST / returns 422 when the redirect host does not match Ediel") {
        val validator = mockk<RedirectUriValidator>()
        coEvery { validator.validate(redirectUri, requestedBy) } throws
            RedirectUriValidationError.DoesNotMatchEdiel()

        val (status, body) = postWith(validator)

        status shouldBe HttpStatusCode.UnprocessableEntity
        body.errors.single().title shouldBe "Validation error"
        body.errors.single().detail shouldBe "Redirect URI does not match the URI registered in Ediel"
    }

    test("POST / returns 422 when no redirect URL is registered in Ediel") {
        val validator = mockk<RedirectUriValidator>()
        coEvery { validator.validate(redirectUri, requestedBy) } throws
            RedirectUriValidationError.NotRegisteredInEdiel()

        val (status, body) = postWith(validator)

        status shouldBe HttpStatusCode.UnprocessableEntity
        body.errors.single().title shouldBe "Validation error"
        body.errors.single().detail shouldBe "No redirect URL is registered in Ediel for the requesting party"
    }

    test("POST / returns 500 for Ediel lookup failures without exposing the cause") {
        val validator = mockk<RedirectUriValidator>()
        coEvery { validator.validate(redirectUri, requestedBy) } throws
            RedirectUriValidationError.EdielUnavailable(IllegalStateException("private Ediel failure"))

        val (status, body) = postWith(validator)

        status shouldBe HttpStatusCode.InternalServerError
        body.errors.single().title shouldBe "Internal server error"
        body.errors.single().detail shouldBe "An internal server error occurred"
    }
})
