package no.elhub.auth.features.businessprocesses.ediel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RedirectUriDomainValidationTest : FunSpec({
    test("returns matching for same host") {
        validateRedirectUriDomain(
            inputRedirectUri = "https://example.com/callback?state=1",
            edielRedirectUri = "https://example.com/login?lang=no"
        ) shouldBe RedirectUriDomainValidationResult.MatchingDomain
    }

    test("returns matching for subdomain host") {
        validateRedirectUriDomain(
            inputRedirectUri = "https://app.example.com/callback",
            edielRedirectUri = "https://example.com/login"
        ) shouldBe RedirectUriDomainValidationResult.MatchingDomain
    }

    test("returns domain mismatch for different hosts") {
        validateRedirectUriDomain(
            inputRedirectUri = "https://example.com/callback",
            edielRedirectUri = "https://other.example/login"
        ) shouldBe RedirectUriDomainValidationResult.DomainMismatch
    }

    test("returns invalid input uri for malformed input redirect uri") {
        validateRedirectUriDomain(
            inputRedirectUri = "example.com",
            edielRedirectUri = "https://example.com/login"
        ) shouldBe RedirectUriDomainValidationResult.InvalidInputUri
    }

    test("returns invalid ediel uri when ediel uri is null") {
        validateRedirectUriDomain(
            inputRedirectUri = "https://example.com/callback",
            edielRedirectUri = null
        ) shouldBe RedirectUriDomainValidationResult.InvalidEdielUri
    }
})
