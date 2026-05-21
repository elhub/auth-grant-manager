package no.elhub.auth.features.common.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.plugin.dto.AuthInfoV3
import no.elhub.auth.plugin.dto.TokenInfo
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy

class AuthorizationPartyResolverTest : FunSpec({

    fun maskinportenResponse(
        actingGLN: String? = "0107000000021",
        functionName: String? = "BalanceSupplier",
        inputFailed: String? = null,
        authorizedFunctions: List<AuthInfoV3.AuthorizedFunction>? = listOf(
            AuthInfoV3.AuthorizedFunction(functionCode = "SELF", functionName = functionName)
        ),
    ) = AuthInfoPolicy.Response(
        tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.MASKINPORTEN),
        authInfo = AuthInfoV3(
            actingGLN = actingGLN,
            authorizedFunctions = authorizedFunctions,
            inputFailed = inputFailed,
        ),
    )

    fun enduserResponse(
        actingType: String? = "person",
        actingId: String? = "some-person-id",
        actingOrganisationNumber: String? = null,
        authInfoError: String? = null,
    ) = AuthInfoPolicy.Response(
        tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ENDUSER),
        authInfo = AuthInfoV3(
            actingType = actingType,
            actingId = actingId,
            actingOrganisationNumber = actingOrganisationNumber,
            error = authInfoError,
        ),
    )

    fun elhubServiceResponse(partyId: String? = "some-service") = AuthInfoPolicy.Response(
        tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ELHUB_SERVICE, partyId = partyId),
    )

    context("Maskinporten token") {
        test("resolves OrganizationEntity for BalanceSupplier") {
            val party = resolveAuthorizedParty(maskinportenResponse())
            party?.id shouldBe "0107000000021"
            party?.type shouldBe PartyType.OrganizationEntity
        }

        test("returns null when inputFailed is set") {
            resolveAuthorizedParty(maskinportenResponse(inputFailed = "invalid input")).shouldBeNull()
        }

        test("returns null when actingGLN is missing") {
            resolveAuthorizedParty(maskinportenResponse(actingGLN = null)).shouldBeNull()
        }

        test("returns null when authorizedFunctions is empty") {
            resolveAuthorizedParty(maskinportenResponse(authorizedFunctions = emptyList())).shouldBeNull()
        }

        test("returns null when authorizedFunctions is null") {
            resolveAuthorizedParty(maskinportenResponse(authorizedFunctions = null)).shouldBeNull()
        }

        test("returns null when functionName is not BalanceSupplier") {
            resolveAuthorizedParty(maskinportenResponse(functionName = "GridOwner")).shouldBeNull()
        }

        test("returns null when authInfo is null") {
            val response = AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.MASKINPORTEN),
                authInfo = null,
            )
            resolveAuthorizedParty(response).shouldBeNull()
        }
    }

    context("End-user token") {
        test("resolves Person for actingType=person") {
            val party = resolveAuthorizedParty(enduserResponse(actingType = "person", actingId = "some-person-id"))
            party?.id shouldBe "some-person-id"
            party?.type shouldBe PartyType.Person
        }

        test("resolves Organization for actingType=organisation") {
            val party = resolveAuthorizedParty(
                enduserResponse(actingType = "organisation", actingOrganisationNumber = "123456789")
            )
            party?.id shouldBe "123456789"
            party?.type shouldBe PartyType.Organization
        }

        test("returns null when authInfo error is set") {
            resolveAuthorizedParty(enduserResponse(authInfoError = "some error")).shouldBeNull()
        }

        test("returns null when actingId is missing for person") {
            resolveAuthorizedParty(enduserResponse(actingType = "person", actingId = null)).shouldBeNull()
        }

        test("returns null when actingOrganisationNumber is missing for organisation") {
            resolveAuthorizedParty(
                enduserResponse(actingType = "organisation", actingOrganisationNumber = null)
            ).shouldBeNull()
        }

        test("returns null for unknown actingType") {
            resolveAuthorizedParty(enduserResponse(actingType = "robot")).shouldBeNull()
        }

        test("returns null when actingType is null") {
            resolveAuthorizedParty(enduserResponse(actingType = null)).shouldBeNull()
        }

        test("returns null when actingType is blank") {
            resolveAuthorizedParty(enduserResponse(actingType = "   ")).shouldBeNull()
        }

        test("returns null when authInfo is null") {
            val response = AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = TokenType.ENDUSER),
                authInfo = null,
            )
            resolveAuthorizedParty(response).shouldBeNull()
        }
    }

    context("Elhub service token") {
        test("resolves System party from partyId") {
            val party = resolveAuthorizedParty(elhubServiceResponse(partyId = "some-service"))
            party?.id shouldBe "some-service"
            party?.type shouldBe PartyType.System
        }

        test("returns null when partyId is missing") {
            resolveAuthorizedParty(elhubServiceResponse(partyId = null)).shouldBeNull()
        }
    }

    context("Unknown or missing tokenType") {
        test("returns null when tokenInfo is null") {
            val response = AuthInfoPolicy.Response(tokenInfo = null)
            resolveAuthorizedParty(response).shouldBeNull()
        }

        test("returns null when tokenType is null") {
            val response = AuthInfoPolicy.Response(
                tokenInfo = TokenInfo(tokenStatus = "verified", tokenType = null)
            )
            resolveAuthorizedParty(response).shouldBeNull()
        }
    }
})
