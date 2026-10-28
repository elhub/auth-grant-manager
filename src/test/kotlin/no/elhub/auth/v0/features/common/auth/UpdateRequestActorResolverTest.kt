package no.elhub.auth.v0.features.common.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.elhub.auth.plugin.dto.AuthInfoV3
import no.elhub.auth.plugin.dto.TokenInfo
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType

class UpdateRequestActorResolverTest : FunSpec({
    val person = AuthorizationParty("person-1", PartyType.Person)

    fun response(
        actingType: String? = "person",
        actingId: String? = "person-1",
        originalId: String? = "person-1",
        error: String? = null,
        tokenType: TokenType = TokenType.ENDUSER,
    ) = AuthInfoPolicy.Response(
        tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = tokenType),
        authInfo = AuthInfoV3(
            actingType = actingType,
            actingId = actingId,
            originalId = originalId,
            error = error,
        ),
    )

    fun delegated(originalId: String? = "person-1", tokenType: TokenType = TokenType.ENDUSER) =
        response(actingType = "organisation", actingId = "org-internal-id", originalId = originalId, tokenType = tokenType)

    test("returns the person acting for themselves") {
        resolveUpdateRequestActor(response()) shouldBe person
    }

    test("returns the person when acting on behalf of an organisation") {
        resolveUpdateRequestActor(delegated()) shouldBe person
    }

    test("rejects when the PDP reports an error") {
        resolveUpdateRequestActor(
            response(actingType = "", actingId = "", error = "Unable to verify OnBehalfOfOrganisationId for end user token"),
        ).shouldBeNull()
    }

    test("rejects non-enduser tokens") {
        resolveUpdateRequestActor(response(tokenType = TokenType.MASKINPORTEN)).shouldBeNull()
        resolveUpdateRequestActor(delegated(tokenType = TokenType.ELHUB_SERVICE)).shouldBeNull()
    }

    test("rejects unknown acting types") {
        resolveUpdateRequestActor(response(actingType = "service")).shouldBeNull()
    }

    test("rejects when the person identity is missing") {
        resolveUpdateRequestActor(response(actingId = null)).shouldBeNull()
        resolveUpdateRequestActor(delegated(originalId = "")).shouldBeNull()
    }
})
