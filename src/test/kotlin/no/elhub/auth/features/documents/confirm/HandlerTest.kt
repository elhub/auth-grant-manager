package no.elhub.auth.features.documents.confirm

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.elhub.auth.features.businessprocesses.changeofsupplier.defaultValidTo
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({
    test("returns IllegalTransitionError when document is not pending") {
        val documentId = UUID.randomUUID()
        val requestedByIdentifier = PartyIdentifier(PartyIdentifierType.GlobalLocationNumber, "1234567890123")

        val requestedBy = AuthorizationParty(resourceId = requestedByIdentifier.idValue, type = PartyType.OrganizationEntity)
        val requestedFrom = AuthorizationParty(resourceId = "requested-from", type = PartyType.Person)
        val requestedTo = AuthorizationParty(resourceId = "requested-to", type = PartyType.Person)

        val document =
            AuthorizationDocument.create(
                type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                file = "file".toByteArray(),
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                properties = emptyList(),
                validTo = defaultValidTo()
            ).copy(
                id = documentId,
                status = AuthorizationDocument.Status.Signed
            )

        val documentRepository = mockk<DocumentRepository>()
        val partyService = mockk<PartyService>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)

        every { documentRepository.find(documentId) } returns document.right()
        coEvery { partyService.resolve(requestedByIdentifier) } returns requestedBy.right()

        val handler = Handler(documentRepository, partyService, grantRepository)

        val result = handler(
            Command(
                documentId = documentId,
                requestedByIdentifier = requestedByIdentifier,
                signedFile = "signed".toByteArray()
            )
        )

        result.shouldBeLeft(ConfirmDocumentError.IllegalStateError)
        coVerify(exactly = 1) { partyService.resolve(requestedByIdentifier) }
        verify(exactly = 1) { documentRepository.find(documentId) }
        verify(exactly = 0) { documentRepository.confirm(any(), any(), any(), any()) }
        verify(exactly = 0) { documentRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }
})
