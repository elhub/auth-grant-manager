package no.elhub.auth.features.requests.update

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

class Handler(
    private val businessHandler: RequestBusinessHandler,
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository,
    private val grantPropertiesRepository: GrantPropertiesRepository
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationRequest> = either {
        transaction {
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

            val updatedRequest = when (command.newStatus) {
                AuthorizationRequest.Status.Accepted -> {
                    handleAccepted(request, command.authorizedParty).bind()
                }

                AuthorizationRequest.Status.Rejected -> {
                    handleRejected(request).bind()
                }

                else -> {
                    // consumers can only send Accepted and Rejected statues
                    raise(UpdateError.IllegalTransitionError)
                }
            }
            updatedRequest
        }
    }

    private fun handleAccepted(originalRequest: AuthorizationRequest, acceptedBy: AuthorizationParty): Either<UpdateError, AuthorizationRequest> =
        either {
            val acceptedRequest = requestRepository.acceptRequest(
                requestId = originalRequest.id,
                approvedBy = acceptedBy
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

            val grantProperties = businessHandler.getCreateGrantProperties(acceptedRequest)

            val grantToCreate = AuthorizationGrant.create(
                grantedFor = acceptedRequest.requestedFrom,
                grantedBy = acceptedBy,
                grantedTo = acceptedRequest.requestedBy,
                sourceType = AuthorizationGrant.SourceType.Request,
                sourceId = acceptedRequest.id,
                scopeIds = scopeIds,
                validFrom = grantProperties.validFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
                validTo = grantProperties.validTo.toTimeZoneOffsetDateTimeAtStartOfDay()
            )

            val createdGrant = grantRepository.insert(grantToCreate, scopeIds)
                .mapLeft { UpdateError.GrantCreationError }
                .bind()

            val grantMetaProperties = grantProperties.meta.map { (key, value) ->
                AuthorizationGrantProperty(
                    grantId = createdGrant.id,
                    key = key,
                    value = value
                )
            }

            grantPropertiesRepository.insert(grantMetaProperties)

            acceptedRequest.copy(grantId = createdGrant.id)
        }

    private fun handleRejected(originalRequest: AuthorizationRequest): Either<UpdateError, AuthorizationRequest> =
        either {
            val rejectedRequest = requestRepository.rejectRequest(
                requestId = originalRequest.id,
            ).mapLeft { UpdateError.PersistenceError }
                .bind()
            rejectedRequest
        }
}
