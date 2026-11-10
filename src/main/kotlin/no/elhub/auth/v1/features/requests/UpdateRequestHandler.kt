package no.elhub.auth.v1.features.requests

import no.elhub.auth.v1.common.AuthorizationRulesService
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.TextLanguage
import no.elhub.auth.v1.repositories.RepositoryWriteError
import no.elhub.auth.v1.repositories.RequestRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class UpdateRequestHandler(
    private val requestRepository: RequestRepository,
    private val authorizationRulesService: AuthorizationRulesService,
) {
    private val logger = LoggerFactory.getLogger(UpdateRequestHandler::class.java)

    suspend operator fun invoke(
        id: UUID,
        authorizedAgent: AuthorizationAgent,
        status: AuthorizationRequestStatus,
        textLanguage: TextLanguage,
    ): AuthorizationRequest {
        val request = requestRepository.find(id)

        if (!authorizationRulesService.isApproverAllowed(authorizedAgent, request.requestedOf, request.requestedPrincipal)) {
            logger.warn("Approver not allowed for AuthorizationRequest {} (approver type: {})", id, authorizedAgent.type)
            throw UpdateRequestError.NotAuthorized()
        }

        return try {
            when (status) {
                AuthorizationRequestStatus.Approved -> {
                    ensurePending(request.status)
                    val grants = request.toGrants(approvedBy = authorizedAgent)
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
