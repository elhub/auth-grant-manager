package no.elhub.auth.v1.features.requests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.TextLanguage
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RepositoryWriteError
import no.elhub.auth.v1.repositories.RequestRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class UpdateRequestHandlerTest : FunSpec({
    val requestRepository = mockk<RequestRepository>()
    val handler = UpdateRequestHandler(requestRepository)
    val requestedBy = AuthorizationParty("7080005053246", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.Organization)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val request = AuthorizationRequest.create(
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        details = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
        ),
        validTo = Clock.System.now() + 28.days,
        meta = JsonObject(emptyMap()),
        externalReference = null,
        redirectUri = null,
    )
    val id = request.id

    beforeTest { clearMocks(requestRepository) }

    test("accepting creates grants approved by requestedOf and stores the text language") {
        val grants = slot<List<AuthorizationGrant>>()
        val accepted = request.copy(status = AuthorizationRequestStatus.Approved, approvedBy = requestedOf)
        coEvery { requestRepository.find(id) } returns request
        coEvery { requestRepository.accept(id, capture(grants), TextLanguage.NN) } returns accepted

        handler(id, requestedOf, AuthorizationRequestStatus.Approved, TextLanguage.NN) shouldBe accepted

        grants.captured shouldHaveSize 1
        grants.captured.single().approvedBy shouldBe requestedOf
        grants.captured.single().sourceType shouldBe AuthorizationGrantSourceType.Request
        grants.captured.single().sourceId shouldBe id
    }

    test("rejecting stores the text language without creating grants") {
        val rejected = request.copy(status = AuthorizationRequestStatus.Rejected)
        coEvery { requestRepository.find(id) } returns request
        coEvery { requestRepository.reject(id, TextLanguage.EN) } returns rejected

        handler(id, requestedOf, AuthorizationRequestStatus.Rejected, TextLanguage.EN) shouldBe rejected

        coVerify(exactly = 0) { requestRepository.accept(any(), any(), any()) }
    }

    listOf(
        requestedBy,
        requestedPrincipal,
        AuthorizationParty("unrelated", PartyType.Person),
        requestedOf.copy(type = PartyType.Organization),
    ).forEach { party ->
        test("denies ${party.type} ${party.id}") {
            coEvery { requestRepository.find(id) } returns request

            shouldThrow<UpdateRequestError.NotAuthorized> {
                handler(id, party, AuthorizationRequestStatus.Approved, TextLanguage.NB)
            }
            coVerify(exactly = 0) { requestRepository.accept(any(), any(), any()) }
            coVerify(exactly = 0) { requestRepository.reject(any(), any()) }
        }
    }

    listOf(AuthorizationRequestStatus.Approved, AuthorizationRequestStatus.Rejected).forEach { status ->
        test("refuses to $status a request whose requestedOf is an organization") {
            val organization = AuthorizationParty("888777666", PartyType.Organization)
            coEvery { requestRepository.find(id) } returns request.copy(requestedOf = organization)

            shouldThrow<UpdateRequestError.UnsupportedRequestedOf> {
                handler(id, organization, status, TextLanguage.NB)
            }
            coVerify(exactly = 0) { requestRepository.accept(any(), any(), any()) }
            coVerify(exactly = 0) { requestRepository.reject(any(), any()) }
        }
    }

    listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Expired).forEach { status ->
        test("rejects a transition to $status") {
            coEvery { requestRepository.find(id) } returns request

            shouldThrow<UpdateRequestError.InvalidTransition> {
                handler(id, requestedOf, status, TextLanguage.NB)
            }
        }
    }

    listOf(
        AuthorizationRequestStatus.Approved to UpdateRequestError.AlreadyProcessed::class,
        AuthorizationRequestStatus.Rejected to UpdateRequestError.AlreadyProcessed::class,
        AuthorizationRequestStatus.Expired to UpdateRequestError.Expired::class,
    ).forEach { (currentStatus, expectedError) ->
        test("accepting a $currentStatus request fails with ${expectedError.simpleName} before creating grants") {
            coEvery { requestRepository.find(id) } returns request.copy(status = currentStatus)

            shouldThrow<UpdateRequestError> {
                handler(id, requestedOf, AuthorizationRequestStatus.Approved, TextLanguage.NB)
            }::class shouldBe expectedError
            coVerify(exactly = 0) { requestRepository.accept(any(), any(), any()) }
        }
    }

    listOf(
        RepositoryWriteError.ConflictError("conflict") to UpdateRequestError.AlreadyProcessed::class,
        RepositoryWriteError.ExpiredError("expired") to UpdateRequestError.Expired::class,
    ).forEach { (repositoryError, expectedError) ->
        listOf(AuthorizationRequestStatus.Approved, AuthorizationRequestStatus.Rejected).forEach { status ->
            test("maps ${repositoryError::class.simpleName} to ${expectedError.simpleName} when $status") {
                coEvery { requestRepository.find(id) } returns request
                coEvery { requestRepository.accept(id, any(), any()) } throws repositoryError
                coEvery { requestRepository.reject(id, any()) } throws repositoryError

                shouldThrow<UpdateRequestError> {
                    handler(id, requestedOf, status, TextLanguage.NB)
                }::class shouldBe expectedError
            }
        }
    }

    test("propagates not found when the request does not exist") {
        val error = RepositoryReadError.NotFoundError("Missing request")
        coEvery { requestRepository.find(id) } throws error

        shouldThrow<RepositoryReadError.NotFoundError> {
            handler(id, requestedOf, AuthorizationRequestStatus.Approved, TextLanguage.NB)
        } shouldBeSameInstanceAs error
    }
})
