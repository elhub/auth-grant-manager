package no.elhub.auth.common.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.plugin.dto.AuthInfoV3
import no.elhub.auth.plugin.dto.TokenInfo
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy

class AuthenticatedActorResolverTest : FunSpec({
    test("resolves the person for a self end-user call") {
        resolveAuthenticatedActor(
            AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ENDUSER),
                authInfo = AuthInfoV3(actingType = "person", actingId = "person-1"),
            ),
        ) shouldBe AuthorizationParty("person-1", PartyType.Person)
    }

    test("resolves the original person for a delegated end-user call") {
        resolveAuthenticatedActor(
            AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ENDUSER),
                authInfo = AuthInfoV3(actingType = "organisation", actingId = "org-1", originalId = "person-1"),
            ),
        ) shouldBe AuthorizationParty("person-1", PartyType.Person)
    }

    test("resolves the token-holder organisation for a Maskinporten call") {
        resolveAuthenticatedActor(
            AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.MASKINPORTEN, partyId = "999888777"),
                authInfo = AuthInfoV3(actingGLN = "7080005053246"),
            ),
        ) shouldBe AuthorizationParty("999888777", PartyType.Organization)
    }

    test("resolves the service for an internal service call") {
        resolveAuthenticatedActor(
            AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ELHUB_SERVICE, partyId = "service-1"),
            ),
        ) shouldBe AuthorizationParty("service-1", PartyType.System)
    }

    listOf(null, "", " ").forEach { missingId ->
        test("does not substitute the organisation for a missing original person ID '$missingId'") {
            resolveAuthenticatedActor(
                AuthInfoPolicy.Response(
                    tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ENDUSER, partyId = "token-person"),
                    authInfo = AuthInfoV3(actingType = "organisation", actingId = "org-1", originalId = missingId),
                ),
            ) shouldBe null
        }

        listOf(TokenType.MASKINPORTEN, TokenType.ELHUB_SERVICE).forEach { tokenType ->
            test("rejects missing actor ID '$missingId' for $tokenType") {
                resolveAuthenticatedActor(
                    AuthInfoPolicy.Response(
                        tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = tokenType, partyId = missingId),
                    ),
                ) shouldBe null
            }
        }
    }

    test("rejects missing or unsupported token information") {
        resolveAuthenticatedActor(AuthInfoPolicy.Response(tokenInfo = null)) shouldBe null
        resolveAuthenticatedActor(
            AuthInfoPolicy.Response(tokenInfo = TokenInfo(tokenStatus = "verified", partyId = "unknown")),
        ) shouldBe null
    }
})
