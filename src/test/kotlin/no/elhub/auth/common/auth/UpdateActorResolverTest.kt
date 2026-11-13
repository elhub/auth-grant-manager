package no.elhub.auth.common.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.elhub.auth.plugin.dto.AuthInfoV3
import no.elhub.auth.plugin.dto.TokenInfo
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy

class UpdateActorResolverTest : FunSpec({
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
        resolveUpdateActor(response()) shouldBe person
    }

    test("returns the person when acting on behalf of an organisation") {
        resolveUpdateActor(delegated()) shouldBe person
    }

    test("rejects when the PDP reports an error") {
        resolveUpdateActor(
            response(actingType = "", actingId = "", error = "Unable to verify OnBehalfOfOrganisationId for end user token"),
        ).shouldBeNull()
    }

    test("rejects non-enduser tokens") {
        resolveUpdateActor(response(tokenType = TokenType.MASKINPORTEN)).shouldBeNull()
        resolveUpdateActor(delegated(tokenType = TokenType.ELHUB_SERVICE)).shouldBeNull()
    }

    test("rejects unknown acting types") {
        resolveUpdateActor(response(actingType = "service")).shouldBeNull()
    }

    test("rejects when the person identity is missing") {
        resolveUpdateActor(response(actingId = null)).shouldBeNull()
        resolveUpdateActor(delegated(originalId = "")).shouldBeNull()
    }

    test("grant updates allow systems when explicitly enabled") {
        val response = AuthInfoPolicy.Response(
            tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ELHUB_SERVICE, partyId = "system-1"),
        )

        resolveUpdateActor(response, allowSystem = true) shouldBe AuthorizationParty("system-1", PartyType.System)
        resolveUpdateActor(response).shouldBeNull()
    }

    test("grant updates reject systems without a party ID") {
        resolveUpdateActor(response(tokenType = TokenType.ELHUB_SERVICE), allowSystem = true).shouldBeNull()
    }

    test("allowing systems preserves person resolution and rejects Maskinporten tokens") {
        resolveUpdateActor(response(), allowSystem = true) shouldBe person
        resolveUpdateActor(delegated(), allowSystem = true) shouldBe person
        resolveUpdateActor(response(tokenType = TokenType.MASKINPORTEN), allowSystem = true).shouldBeNull()
    }
})
