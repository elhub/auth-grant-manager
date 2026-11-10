package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.GrantPartyFilters
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class ListGrantsHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = ListGrantsHandler(grantRepository)
    val authorizedParty = AuthorizationParty("7070575000001", PartyType.OrganizationEntity)
    val grantedBy = AuthorizationParty(UUID.randomUUID().toString(), PartyType.Person)
    val principal = AuthorizationParty("999888771", PartyType.Organization)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = principal,
        grantedBy = grantedBy,
        grantedTo = authorizedParty,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val pagination = Pagination(page = 1, size = 2)

    beforeTest { clearMocks(grantRepository) }

    test("forwards the authorized party, pagination, statuses and party filters and returns the repository page") {
        val statuses = listOf(AuthorizationGrantStatus.Pending, AuthorizationGrantStatus.Revoked)
        val partyFilters = GrantPartyFilters(
            grantedBy = listOf(grantedBy, AuthorizationParty(UUID.randomUUID().toString(), PartyType.Person)),
            principal = listOf(principal),
            approvedBy = listOf(grantedBy),
            grantedTo = listOf(authorizedParty),
        )
        val page = Page(listOf(grant), 3, pagination)
        coEvery { grantRepository.findAll(authorizedParty, pagination, statuses, partyFilters) } returns page

        handler(authorizedParty, pagination, statuses, partyFilters) shouldBeSameInstanceAs page
        coVerify(exactly = 1) { grantRepository.findAll(authorizedParty, pagination, statuses, partyFilters) }
    }

    test("returns an empty page when the party has no grants") {
        val page = Page(emptyList<AuthorizationGrant>(), 0, pagination)
        coEvery { grantRepository.findAll(authorizedParty, pagination, emptyList(), GrantPartyFilters()) } returns page

        handler(authorizedParty, pagination, emptyList(), GrantPartyFilters()) shouldBe page
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { grantRepository.findAll(authorizedParty, pagination, emptyList(), GrantPartyFilters()) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> {
            handler(authorizedParty, pagination, emptyList(), GrantPartyFilters())
        } shouldBeSameInstanceAs error
    }
})
