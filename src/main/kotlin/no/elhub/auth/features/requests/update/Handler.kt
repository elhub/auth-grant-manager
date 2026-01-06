package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import org.slf4j.LoggerFactory

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        // TODO add handle illegal state: Accepted -> Rejected should not be possible

        val originalRequest = requestRepository.find(command.requestId)
            .mapLeft { UpdateError.RequestNotFound }
            .bind()

        ensure(originalRequest.requestedTo == command.authorizedParty) {
            logger.error("Requestee is not authorized to get the request ${command.authorizedParty}")
            UpdateError.NotAuthorizedError
        }

        val updatedRequest = when (command.newStatus) {
            AuthorizationRequest.Status.Accepted -> {
                handleAccepted(originalRequest).bind()
            }

            AuthorizationRequest.Status.Rejected -> {
                handleRejected(originalRequest).bind()
            }

            else -> {
                // consumers can only send Accepted and Rejected statues
                raise(UpdateError.IllegalTransitionError)
            }
        }
        updatedRequest
    }

    private fun handleAccepted(originalRequest: AuthorizationRequest): Either<UpdateError, AuthorizationRequest> = either {
        // TODO this will be provided by value stream via tokens. Temporary setting this as requestedTo
        val approval = originalRequest.requestedTo

        val acceptedRequest = requestRepository.acceptRequest(
            requestId = originalRequest.id,
            approvedBy = approval
        ).mapLeft {
            UpdateError.PersistenceError
        }.bind()

        val scopeIds = requestRepository
            .findScopeIds(acceptedRequest.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> UpdateError.RequestNotFound
                    is RepositoryReadError.UnexpectedError -> UpdateError.ScopeReadError
                }
            }.bind()

        val grantToCreate = AuthorizationGrant.create(
            grantedFor = acceptedRequest.requestedFrom,
            grantedBy = approval,
            grantedTo = acceptedRequest.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = acceptedRequest.id
        )

        val createdGrant = grantRepository.insert(grantToCreate, scopeIds)
            .mapLeft { UpdateError.GrantCreationError }
            .bind()

        acceptedRequest.copy(grantId = createdGrant.id)
    }

    private fun handleRejected(originalRequest: AuthorizationRequest): Either<UpdateError, AuthorizationRequest> = either {
        val rejectedRequest = requestRepository.rejectAccept(
            requestId = originalRequest.id,
        ).mapLeft { UpdateError.PersistenceError }
            .bind()
        rejectedRequest
    }
}
