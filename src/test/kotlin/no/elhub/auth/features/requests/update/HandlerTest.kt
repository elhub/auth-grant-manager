package no.elhub.auth.features.requests.update

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class HandlerTest : FunSpec({
    test("returns IllegalStateError when request is not pending") {
        val requestId = UUID.randomUUID()
        val requestedBy = AuthorizationParty(id = "requested-by", type = PartyType.Person)
        val requestedFrom = AuthorizationParty(id = "requested-from", type = PartyType.Person)
        val requestedTo = AuthorizationParty(id = "requested-to", type = PartyType.Person)

        val existingRequest =
            AuthorizationRequest.create(
                type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                requestedFrom = requestedFrom,
                requestedBy = requestedBy,
                requestedTo = requestedTo,
                validTo = LocalDate(2025, 1, 1).toTimeZoneOffsetDateTimeAtStartOfDay()
            ).copy(
                id = requestId,
                status = AuthorizationRequest.Status.Accepted
            )

        val requestRepository = mockk<RequestRepository>()
        val businessHandler = mockk<GrantBusinessHandler>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)
        val grantPropertiesRepository = mockk<GrantPropertiesRepository>(relaxed = true)

        every { requestRepository.find(requestId) } returns existingRequest.right()

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
            grantRepository = grantRepository,
            grantPropertiesRepository = grantPropertiesRepository)

        val result = handler(
            UpdateCommand(
                requestId = requestId,
                newStatus = AuthorizationRequest.Status.Accepted,
                authorizedParty = requestedTo
            )
        )

        result.shouldBeLeft(UpdateError.AlreadyProcessed)
        verify(exactly = 1) { requestRepository.find(requestId) }
        verify(exactly = 0) { requestRepository.acceptRequest(any(), any()) }
        verify(exactly = 0) { requestRepository.rejectRequest(any()) }
        verify(exactly = 0) { requestRepository.findScopeIds(any()) }
        verify(exactly = 0) { grantRepository.insert(any(), any()) }
    }
})
