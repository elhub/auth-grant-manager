package no.elhub.auth.features.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.requests.AuthorizationRequest

class AuthorizationTextVersionTest : FunSpec({
    test("request text version is added from the repo-managed constants") {
        emptyMap<String, String>()
            .withRequestTextVersion(AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson) shouldBe mapOf(
            TEXT_VERSION_META_KEY to "v1"
        )
    }
})
