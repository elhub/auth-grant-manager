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
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class ListGrantsHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = ListGrantsHandler(grantRepository)
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val grantedBy = AuthorizationParty("person-1", PartyType.Person)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        grantedBy = grantedBy,
        grantedTo = authorizedParty,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val pagination = Pagination(page = 1, size = 2)

    beforeTest { clearMocks(grantRepository) }

    test("returns the page from the repository for the authorized party") {
        val page = Page(listOf(grant), 3, pagination)
        coEvery { grantRepository.findAll(authorizedParty, pagination) } returns page

        handler(authorizedParty, pagination) shouldBe page
        coVerify(exactly = 1) { grantRepository.findAll(authorizedParty, pagination) }
    }

    test("returns an empty page when the party has no grants") {
        val page = Page(emptyList<AuthorizationGrant>(), 0, pagination)
        coEvery { grantRepository.findAll(authorizedParty, pagination) } returns page

        handler(authorizedParty, pagination) shouldBe page
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { grantRepository.findAll(authorizedParty, pagination) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(authorizedParty, pagination) } shouldBeSameInstanceAs error
    }
})
