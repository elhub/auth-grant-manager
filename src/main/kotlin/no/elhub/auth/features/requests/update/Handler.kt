package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {
    operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        // TODO add authorization check -> only authorized consumers can accept/reject
        // TODO add handle illegal state: Accepted -> Rejected should not be possible
        val updatedRequest = when (command.newStatus) {
            AuthorizationRequest.Status.Accepted -> {
                handleAccepted(command.requestId).bind()
            }

            AuthorizationRequest.Status.Rejected -> {
                handleRejected(command.requestId).bind()
            }

            else -> {
                // consumers can only send Accepted and Rejected statues
                raise(UpdateError.IllegalTransitionError)
            }
        }
        updatedRequest
    }

    private fun handleAccepted(requestId: UUID): Either<UpdateError, AuthorizationRequest> = either {
        val originalRequest = requestRepository.find(requestId)
            .mapLeft { UpdateError.RequestNotFound }
            .bind()

        // TODO this will be provided by value stream via tokens. Temporary setting this as requestedTo
        val approval = originalRequest.requestedTo

        val updatedRequest = requestRepository.updateAccept(
            requestId = originalRequest.id,
            approvedBy = approval
        ).mapLeft {
            UpdateError.PersistenceError
        }.bind()

        val scopeIds = requestRepository
            .findScopeIds(updatedRequest.id)
            .mapLeft {
                when (it) {
                    is RepositoryReadError.NotFoundError -> UpdateError.RequestNotFound
                    is RepositoryReadError.UnexpectedError -> UpdateError.ScopeReadError
                }
            }.bind()

        val grantToCreate = AuthorizationGrant.create(
            grantedFor = updatedRequest.requestedFrom,
            grantedBy = approval,
            grantedTo = updatedRequest.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Document,
            sourceId = updatedRequest.id
        )

        val createdGrant = grantRepository.insert(grantToCreate, scopeIds)
            .mapLeft { UpdateError.GrantCreationError }
            .bind()

        updatedRequest.copy(grantId = createdGrant.id)
    }

    private fun handleRejected(requestId: UUID): Either<UpdateError, AuthorizationRequest> = either {
        val updatedRequest = requestRepository.updateReject(
            requestId = requestId,
        ).mapLeft { UpdateError.PersistenceError }
            .bind()
        updatedRequest
    }
}
