package no.elhub.auth.features.requests.update

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.defaultValidTo
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.today
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import java.time.OffsetDateTime
import java.util.UUID

class HandlerTest : FunSpec({
    val requestedBy = AuthorizationParty(id = "1234567890123", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(id = "requested-from", type = PartyType.Person)
    val requestedTo = AuthorizationParty(id = "requested-to", type = PartyType.Person)

    fun createRequest(
        requestId: UUID,
        validTo: OffsetDateTime = defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay(),
        requestedByParty: AuthorizationParty = requestedBy,
        requestedFromParty: AuthorizationParty = requestedFrom,
        requestedToParty: AuthorizationParty = requestedTo
    ): AuthorizationRequest =
        AuthorizationRequest.create(
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            validTo = validTo
        ).copy(
            id = requestId,
        )

    test("returns IllegalStateError when request is not pending") {
        val requestId = UUID.randomUUID()
        val request = createRequest(requestId).copy(
            id = requestId,
            status = AuthorizationRequest.Status.Accepted
        )
        val requestRepository = mockk<RequestRepository>()
        val businessHandler = mockk<RequestBusinessHandler>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)
        val grantPropertiesRepository = mockk<GrantPropertiesRepository>(relaxed = true)

        every { requestRepository.find(requestId) } returns request.right()

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
            grantRepository = grantRepository,
            grantPropertiesRepository = grantPropertiesRepository
        )

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

    test("update request and creates grant on success") {
        val requestId = UUID.randomUUID()
        val requestRepository = mockk<RequestRepository>()
        val businessHandler = mockk<RequestBusinessHandler>()
        val grantRepository = mockk<GrantRepository>(relaxed = true)
        val grantPropertiesRepository = mockk<GrantPropertiesRepository>(relaxed = true)
        val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val partyService = mockk<PartyService>()
        val validFrom = today()
        val validTo = today().plus(DatePeriod(years = 1))
        val request = createRequest(requestId)
        val updatedRequest = request.copy(status = AuthorizationRequest.Status.Accepted)

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
            grantRepository = grantRepository,
            grantPropertiesRepository = grantPropertiesRepository
        )
        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = validFrom,
            validTo = validTo,
            meta = emptyMap()
        )

        val expectedGrant = AuthorizationGrant.create(
            grantedFor = updatedRequest.requestedFrom,
            grantedBy = requestedTo,
            grantedTo = updatedRequest.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = updatedRequest.id,
            scopeIds = scopeIds,
            validFrom = businessHandler.getCreateGrantProperties(updatedRequest).validFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
            validTo = businessHandler.getCreateGrantProperties(updatedRequest).validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
        )

        every { requestRepository.find(requestId) } returns request.right()
        coVerify(exactly = 0) { partyService.resolve(any()) }
        every {
            requestRepository.acceptRequest(requestId, requestedTo)
        } returns updatedRequest.right()
        every { requestRepository.findScopeIds(updatedRequest.id) } returns scopeIds.right()
        every { grantRepository.insert(any(), scopeIds) } returns expectedGrant.right()

        val result = handler(
            UpdateCommand(
                requestId = request.id,
                newStatus = AuthorizationRequest.Status.Accepted,
                authorizedParty = requestedTo
            )
        )

        result.shouldBeRight()
        verify(exactly = 1) { requestRepository.acceptRequest(request.id, requestedTo) }
        verify(exactly = 1) { requestRepository.findScopeIds(updatedRequest.id) }
        coVerify(exactly = 1) {
            grantRepository.insert(
                match { grant ->
                    grant.grantedFor == updatedRequest.requestedFrom &&
                        grant.grantedBy == requestedTo &&
                        grant.grantedTo == updatedRequest.requestedBy &&
                        grant.sourceType == AuthorizationGrant.SourceType.Request &&
                        grant.sourceId == updatedRequest.id &&
                        grant.scopeIds == scopeIds &&
                        grant.validFrom == validFrom.toTimeZoneOffsetDateTimeAtStartOfDay() &&
                        grant.validTo == validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
                },
                scopeIds
            )
        }
    }
})
