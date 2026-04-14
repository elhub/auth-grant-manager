package no.elhub.auth.features.requests.update

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.common.RepositoryWriteError.UnexpectedError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestRepository
import java.time.OffsetDateTime
import java.util.UUID

class HandlerTest : FunSpec({
    val requestedBy = AuthorizationParty(id = "1234567890123", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(id = "requested-from", type = PartyType.Person)
    val requestedTo = AuthorizationParty(id = "requested-to", type = PartyType.Person)

    fun createRequest(
        requestId: UUID,
        validTo: OffsetDateTime = todayOslo().plus(DatePeriod(days = 30)).toTimeZoneOffsetDateTimeAtStartOfDay(),
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

        coEvery { requestRepository.find(requestId) } returns request.right()

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
        )

        val result = handler(
            UpdateCommand(
                requestId = requestId,
                newStatus = AuthorizationRequest.Status.Accepted,
                authorizedParty = requestedTo
            )
        )

        result.shouldBeLeft(UpdateError.AlreadyProcessed)
        coVerify(exactly = 1) { requestRepository.find(requestId) }
        coVerify(exactly = 0) { requestRepository.acceptWithGrant(any(), any(), any(), any()) }
        coVerify(exactly = 0) { requestRepository.rejectRequest(any()) }
    }

    test("update request and creates grant on success") {
        val requestId = UUID.randomUUID()
        val requestRepository = mockk<RequestRepository>()
        val businessHandler = mockk<RequestBusinessHandler>()
        val validFrom = todayOslo()
        val validTo = todayOslo().plus(DatePeriod(years = 1))
        val request = createRequest(requestId)
        val updatedRequest = request.copy(status = AuthorizationRequest.Status.Accepted)

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
        )
        every { businessHandler.getCreateGrantProperties(any()) } returns CreateGrantProperties(
            validFrom = validFrom,
            validTo = validTo,
            meta = emptyMap()
        )

        coEvery { requestRepository.find(requestId) } returns request.right()
        coEvery { requestRepository.findScopeIds(requestId) } returns emptyList<UUID>().right()
        coEvery {
            requestRepository.acceptWithGrant(eq(requestId), eq(requestedTo), any(), any())
        } returns updatedRequest.right()

        val result = handler(
            UpdateCommand(
                requestId = request.id,
                newStatus = AuthorizationRequest.Status.Accepted,
                authorizedParty = requestedTo
            )
        )

        result.shouldBeRight()
        coVerify(exactly = 1) {
            requestRepository.acceptWithGrant(eq(requestId), eq(requestedTo), any(), any())
        }
        coVerify(exactly = 0) { requestRepository.rejectRequest(any()) }
    }

    test("returns PersistenceError when request reject fails") {
        val requestId = UUID.randomUUID()
        val request = createRequest(requestId)
        val requestRepository = mockk<RequestRepository>()
        val businessHandler = mockk<RequestBusinessHandler>()

        coEvery { requestRepository.find(requestId) } returns request.right()
        coEvery { requestRepository.rejectRequest(requestId) } returns UnexpectedError.left()

        val handler = Handler(
            businessHandler = businessHandler,
            requestRepository = requestRepository,
        )

        val result = handler(
            UpdateCommand(
                requestId = requestId,
                newStatus = AuthorizationRequest.Status.Rejected,
                authorizedParty = requestedTo
            )
        )

        result.shouldBeLeft(UpdateError.PersistenceError)
    }
})
