package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {
    operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        // TODO add state-transition validation in another PR
        // TODO add authorization check in another PR

        val originalRequest = requestRepository.find(command.requestId)
            .mapLeft { UpdateError.RequestNotFound }
            .bind()

        val updatedRequest = when (command.newStatus) {
            AuthorizationRequest.Status.Accepted -> {
                handleAccept(originalRequest, command)
            }

            AuthorizationRequest.Status.Expired,
            AuthorizationRequest.Status.Pending,
            AuthorizationRequest.Status.Rejected -> {
                handleOtherStatus(command)
            }
        }.mapLeft { UpdateError.PersistenceError }
            .bind()

        updatedRequest
    }

    private fun handleAccept(originalRequest: AuthorizationRequest, command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        // TODO this will be provided by value stream via tokens. Temporary setting this as requestedTo
        val approval = originalRequest.requestedTo

        val updatedRequest = requestRepository.update(
            requestId = command.requestId,
            newStatus = AuthorizationRequest.Status.Accepted,
            approvedBy = approval
        ).mapLeft { UpdateError.PersistenceError }
            .bind()

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

    private fun handleOtherStatus(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        val updatedRequest = requestRepository.update(
            requestId = command.requestId,
            newStatus = command.newStatus,
            null
        ).mapLeft { UpdateError.PersistenceError }
            .bind()

        updatedRequest
    }
}
