package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AcceptWithGrantError
import no.elhub.auth.features.requests.common.RequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestRepository
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
            UpdateError.AuthorizedPartyNotAllowedToUpdateAuthorizationRequest
        }

        when (command.newStatus) {
            AuthorizationRequest.Status.Accepted -> handleAccepted(request, command).bind()
            AuthorizationRequest.Status.Rejected -> handleRejected(request).bind()
            else -> raise(UpdateError.IllegalTransitionError)
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
                is AcceptWithGrantError.GrantError -> UpdateError.GrantCreationError
                is AcceptWithGrantError.RequestError.AlreadyProcessed -> UpdateError.AlreadyProcessed
                is AcceptWithGrantError.RequestError.Expired -> UpdateError.Expired
                is AcceptWithGrantError.RequestError -> UpdateError.PersistenceError
            }
        }.bind()

        logger.info(
            "event=authorization_grant_created grantId={} sourceType={} sourceId={}",
            grant.id,
            AuthorizationGrant.SourceType.Request,
            updatedRequest.id
        )

        updatedRequest
    }

    private suspend fun handleRejected(originalRequest: AuthorizationRequest): Either<UpdateError, AuthorizationRequest> =
        requestRepository.rejectRequest(
            requestId = originalRequest.id,
        ).mapLeft { error ->
            when (error) {
                is RepositoryWriteError.ConflictError -> UpdateError.AlreadyProcessed
                is RepositoryWriteError.ExpiredError -> UpdateError.Expired
                else -> UpdateError.PersistenceError
            }
        }
}
