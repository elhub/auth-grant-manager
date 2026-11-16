package no.elhub.auth.common.integrations.ediel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RedirectUriDomainMatcherTest : FunSpec({
    test("accepts the registered host") {
        redirectUriMatchesEdielDomain(
            redirectUri = "https://example.com/callback",
            edielRedirectUri = "https://example.com/login",
        ) shouldBe true
    }

    test("accepts a subdomain of the registered host") {
        redirectUriMatchesEdielDomain(
            redirectUri = "https://app.example.com/callback",
            edielRedirectUri = "https://example.com/login",
        ) shouldBe true
    }

    test("does not accept an unrelated or parent host") {
        redirectUriMatchesEdielDomain(
            redirectUri = "https://example.com/callback",
            edielRedirectUri = "https://app.example.com/login",
        ) shouldBe false
        redirectUriMatchesEdielDomain(
            redirectUri = "https://example.com.attacker.test/callback",
            edielRedirectUri = "https://example.com/login",
        ) shouldBe false
    }

    test("requires HTTPS for both URIs") {
        redirectUriMatchesEdielDomain(
            redirectUri = "http://example.com/callback",
            edielRedirectUri = "https://example.com/login",
        ) shouldBe false
        redirectUriMatchesEdielDomain(
            redirectUri = "https://example.com/callback",
            edielRedirectUri = "http://example.com/login",
        ) shouldBe false
    }
})
