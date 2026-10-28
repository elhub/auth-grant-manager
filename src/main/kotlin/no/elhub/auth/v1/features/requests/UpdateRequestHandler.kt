package no.elhub.auth.v1.features.requests

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.TextLanguage
import no.elhub.auth.v1.repositories.RepositoryWriteError
import no.elhub.auth.v1.repositories.RequestRepository
import java.util.UUID

class UpdateRequestHandler(
    private val requestRepository: RequestRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
        status: AuthorizationRequestStatus,
        textLanguage: TextLanguage,
    ): AuthorizationRequest {
        val request = requestRepository.find(id)

        if (authorizedParty != request.requestedOf) {
            throw UpdateRequestError.NotAuthorized()
        }

        if (request.requestedOf.type != PartyType.Person) {
            throw UpdateRequestError.UnsupportedRequestedOf()
        }

        return try {
            when (status) {
                AuthorizationRequestStatus.Approved -> {
                    ensurePending(request.status)
                    val grants = request.toGrants(approvedBy = authorizedParty)
                    requestRepository.accept(id, grants, textLanguage)
                }

                AuthorizationRequestStatus.Rejected -> requestRepository.reject(id, textLanguage)

                AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Expired ->
                    throw UpdateRequestError.InvalidTransition()
            }
        } catch (error: RepositoryWriteError.ConflictError) {
            throw UpdateRequestError.AlreadyProcessed()
        } catch (error: RepositoryWriteError.ExpiredError) {
            throw UpdateRequestError.Expired()
        }
    }

    private fun ensurePending(status: AuthorizationRequestStatus) {
        when (status) {
            AuthorizationRequestStatus.Pending -> Unit
            AuthorizationRequestStatus.Expired -> throw UpdateRequestError.Expired()
            AuthorizationRequestStatus.Approved, AuthorizationRequestStatus.Rejected -> throw UpdateRequestError.AlreadyProcessed()
        }
    }
}
