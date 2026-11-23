package no.elhub.auth.v1.features.requests

import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.common.AuthorizationRulesService
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.logging.errorName
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.ChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.EnergySupplierFrameworkAgreementRequestHandler
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.RedirectUriValidator
import no.elhub.auth.v1.features.requests.creation.validateRequestCreationAgents
import no.elhub.auth.v1.repositories.RequestRepository
import org.slf4j.LoggerFactory

class CreateRequestHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierRequestHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierRequestHandler,
    private val energySupplierFrameworkAgreementHandler: EnergySupplierFrameworkAgreementRequestHandler,
    private val redirectUriValidator: RedirectUriValidator,
    private val requestRepository: RequestRepository,
    private val authorizationRulesService: AuthorizationRulesService,
) {
    private val logger = LoggerFactory.getLogger(CreateRequestHandler::class.java)

    suspend operator fun invoke(
        specification: AuthorizationRequestSpecification,
        requestedBy: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
        requestedOf: AuthorizationAgent,
    ): AuthorizationRequest {
        val createdRequest = try {
            create(specification, requestedBy, requestedPrincipal, requestedOf)
        } catch (error: Errors) {
            logger.warn(
                "event=authorization_request_creation_failed type={} reason={}",
                specification.requestType,
                error.errorName,
            )
            throw error
        }
        logger.info(
            "event=authorization_request_created requestId={} type={} status={}",
            createdRequest.id,
            createdRequest.requestType,
            createdRequest.status,
        )
        return createdRequest
    }

    private suspend fun create(
        specification: AuthorizationRequestSpecification,
        requestedBy: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
        requestedOf: AuthorizationAgent,
    ): AuthorizationRequest {
        validateRequestCreationAgents(
            requestType = specification.requestType,
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
        )
        if (!authorizationRulesService.canActOnBehalfOf(requestedOf, requestedPrincipal)) {
            throw InputError.RequestedOfRequestedPrincipalMismatch()
        }
        redirectUriValidator.validate(specification.redirectUri, requestedBy)

        val requestTypeSpecificDetails = when (val details = specification.details) {
            is AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization ->
                changeOfEnergySupplierHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )

            is AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization ->
                moveInAndChangeOfEnergySupplierHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )

            is AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement ->
                energySupplierFrameworkAgreementHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )
        }

        val request = AuthorizationRequest.create(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            details = specification.details,
            validTo = requestTypeSpecificDetails.validTo,
            meta = requestTypeSpecificDetails.meta,
            externalReference = specification.externalReference,
            redirectUri = specification.redirectUri,
        )
        return requestRepository.insert(request)
    }
}
