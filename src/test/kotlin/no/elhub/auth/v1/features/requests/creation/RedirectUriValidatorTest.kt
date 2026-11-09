package no.elhub.auth.v1.features.requests.creation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.integrations.ediel.EdielPartyRedirectNotFoundException
import no.elhub.auth.common.integrations.ediel.EdielRedirectClient
import no.elhub.auth.common.integrations.ediel.EdielRedirectRequestException

class RedirectUriValidatorTest : FunSpec({
    val requestedBy = AuthorizationParty("7080000000000", PartyType.OrganizationEntity)
    lateinit var edielRedirectClient: EdielRedirectClient

    beforeTest {
        edielRedirectClient = mockk()
    }

    test("allows a redirect URI on the registered production host or its subdomain") {
        coEvery { edielRedirectClient.getPartyRedirectUri(requestedBy.id) } returns "https://example.com/login"

        RedirectUriValidator(edielRedirectClient)
            .validate("https://app.example.com/callback", requestedBy)

        coVerify(exactly = 1) { edielRedirectClient.getPartyRedirectUri(requestedBy.id) }
    }

    test("skips the Ediel lookup when redirect URI is absent") {
        RedirectUriValidator(edielRedirectClient).validate(null, requestedBy)

        coVerify(exactly = 0) { edielRedirectClient.getPartyRedirectUri(any()) }
    }

    test("rejects a URI that does not match the Ediel host") {
        coEvery { edielRedirectClient.getPartyRedirectUri(requestedBy.id) } returns "https://example.com/login"

        shouldThrow<RedirectUriValidationError.DoesNotMatchEdiel> {
            RedirectUriValidator(edielRedirectClient).validate("https://other.example/callback", requestedBy)
        }
    }

    test("reports when the party has no Ediel registration") {
        coEvery { edielRedirectClient.getPartyRedirectUri(requestedBy.id) } throws EdielPartyRedirectNotFoundException()

        shouldThrow<RedirectUriValidationError.NotRegisteredInEdiel> {
            RedirectUriValidator(edielRedirectClient).validate("https://example.com/callback", requestedBy)
        }
    }

    test("reports when Ediel has no URL for the configured environment") {
        coEvery { edielRedirectClient.getPartyRedirectUri(requestedBy.id) } returns "  "

        shouldThrow<RedirectUriValidationError.NotRegisteredInEdiel> {
            RedirectUriValidator(edielRedirectClient).validate("https://example.com/callback", requestedBy)
        }
    }

    test("wraps Ediel service failures for server error handling") {
        val serviceFailure = EdielRedirectRequestException("Ediel unavailable")
        coEvery { edielRedirectClient.getPartyRedirectUri(requestedBy.id) } throws serviceFailure

        val error = shouldThrow<RedirectUriValidationError.EdielUnavailable> {
            RedirectUriValidator(edielRedirectClient).validate("https://example.com/callback", requestedBy)
        }

        error.failure shouldBe serviceFailure
    }
})
