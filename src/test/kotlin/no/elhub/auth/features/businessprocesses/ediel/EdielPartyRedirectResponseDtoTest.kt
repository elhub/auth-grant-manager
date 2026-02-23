package no.elhub.auth.features.businessprocesses.ediel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EdielPartyRedirectResponseDtoTest : FunSpec({
    test("redirectUriFor returns production URL in production environment") {
        val response = EdielPartyRedirectResponseDto(
            redirectUrls = EdielRedirectUrlsDto(
                production = "https://production.example/callback",
                test = "https://test.example/callback"
            )
        )

        response.redirectUriFor(EdielEnvironment.PRODUCTION) shouldBe "https://production.example/callback"
    }

    test("redirectUriFor returns test URL in test environment") {
        val response = EdielPartyRedirectResponseDto(
            redirectUrls = EdielRedirectUrlsDto(
                production = "https://production.example/callback",
                test = "https://test.example/callback"
            )
        )

        response.redirectUriFor(EdielEnvironment.TEST) shouldBe "https://test.example/callback"
    }
})
