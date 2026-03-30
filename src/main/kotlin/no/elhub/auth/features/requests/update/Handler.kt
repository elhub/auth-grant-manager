package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AcceptWithGrantError
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import org.slf4j.LoggerFactory

class Handler(
    private val businessHandler: RequestBusinessHandler,
    private val requestRepository: RequestRepository,
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        val request = requestRepository.find(command.requestId)
            .mapLeft { UpdateError.RequestNotFound }
            .bind()

        ensure(request.requestedTo == command.authorizedParty) {
            logger.error("Requestee is not authorized to get the request ${command.authorizedParty}")
            UpdateError.NotAuthorizedError
        }

        when (request.status) {
            AuthorizationRequest.Status.Accepted, AuthorizationRequest.Status.Rejected -> raise(UpdateError.AlreadyProcessed)
            AuthorizationRequest.Status.Expired -> raise(UpdateError.Expired)
            AuthorizationRequest.Status.Pending -> Unit
        }

        when (command.newStatus) {
            AuthorizationRequest.Status.Accepted -> handleAccepted(request, command).bind()
            AuthorizationRequest.Status.Rejected -> handleRejected(request).bind()
            else -> raise(UpdateError.IllegalTransitionError) // consumers can only send Accepted and Rejected statuses
        }
    }

    private suspend fun handleAccepted(
        request: AuthorizationRequest,
        command: UpdateCommand,
    ): Either<UpdateError, AuthorizationRequest> = either {
        val scopeIds = requestRepository.findScopeIds(request.id)
            .mapLeft { UpdateError.PersistenceError }
            .bind()

        val grantProperties = businessHandler.getCreateGrantProperties(request)
        val grant = AuthorizationGrant.create(
            grantedFor = request.requestedFrom,
            grantedBy = command.authorizedParty,
            grantedTo = request.requestedBy,
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = request.id,
            scopeIds = scopeIds,
            validFrom = grantProperties.validFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
            validTo = grantProperties.validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
        )
        val properties = grantProperties.meta.map { (key, value) ->
            AuthorizationGrantProperty(grantId = grant.id, key = key, value = value)
        }

        val updatedRequest = requestRepository.acceptWithGrant(
            requestId = command.requestId,
            approvedBy = command.authorizedParty,
            grant = grant,
            grantProperties = properties,
        ).mapLeft { error ->
            when (error) {
                is AcceptWithGrantError.RequestError -> UpdateError.PersistenceError
                AcceptWithGrantError.GrantError -> UpdateError.GrantCreationError
            }
        }.bind()

        logger.info(
            "event=authorization_grant_created sourceType={} sourceId={}",
            AuthorizationGrant.SourceType.Request,
            updatedRequest.id
        )

        updatedRequest
    }

    private suspend fun handleRejected(originalRequest: AuthorizationRequest): Either<UpdateError, AuthorizationRequest> =
        requestRepository.rejectRequest(
            requestId = originalRequest.id,
        ).mapLeft { UpdateError.PersistenceError }
}
