package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class GetGrantHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = GetGrantHandler(grantRepository)
    val principal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val grantedBy = AuthorizationParty("person-1", PartyType.Person)
    val grantedTo = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val system = AuthorizationParty("system-1", PartyType.System)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = principal,
        grantedBy = grantedBy,
        grantedTo = grantedTo,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val grantId = grant.id

    beforeTest { clearMocks(grantRepository) }

    listOf(grantedTo, principal, system).forEach { party ->
        test("returns the grant for ${party.type} ${party.id}") {
            coEvery { grantRepository.find(grantId) } returns grant

            handler(grantId, party) shouldBe grant
        }
    }

    listOf(
        AuthorizationParty("unrelated-org", PartyType.OrganizationEntity),
        grantedBy,
        grantedTo.copy(type = PartyType.Person),
    ).forEach { party ->
        test("denies access for ${party.type} ${party.id}") {
            coEvery { grantRepository.find(grantId) } returns grant

            shouldThrow<GetGrantError.NotAuthorized> { handler(grantId, party) }
        }
    }

    test("propagates not found when the grant does not exist") {
        val error = RepositoryReadError.NotFoundError("Missing grant")
        coEvery { grantRepository.find(grantId) } throws error

        shouldThrow<RepositoryReadError.NotFoundError> { handler(grantId, grantedTo) } shouldBeSameInstanceAs error
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { grantRepository.find(grantId) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(grantId, grantedTo) } shouldBeSameInstanceAs error
    }
})
