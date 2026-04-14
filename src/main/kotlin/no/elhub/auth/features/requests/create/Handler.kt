package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.party.PartyError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel
import no.elhub.auth.features.requests.common.RequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import org.slf4j.LoggerFactory

class Handler(
    private val businessHandler: RequestBusinessHandler,
    private val partyService: PartyService,
    private val requestRepo: RequestRepository,
) {
    private val logger = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
        logger.info("event=authorization_request_creation type=${model.requestType}")
        val requestedByParty =
            partyService
                .resolve(model.coreMeta.requestedBy)
                .mapLeft { error ->
                    when (error) {
                        PartyError.InvalidNin -> CreateError.InvalidNinError
                        is PartyError.PersonResolutionError -> CreateError.RequestedPartyError
                    }
                }
                .bind()

        ensure(model.authorizedParty == requestedByParty) {
            CreateError.AuthorizationError
        }

        val requestedFromParty =
            partyService
                .resolve(model.coreMeta.requestedFrom)
                .mapLeft { error ->
                    when (error) {
                        PartyError.InvalidNin -> CreateError.InvalidNinError
                        is PartyError.PersonResolutionError -> CreateError.RequestedPartyError
                    }
                }
                .bind()

        val requestedToParty =
            partyService
                .resolve(model.coreMeta.requestedTo)
                .mapLeft { error ->
                    when (error) {
                        PartyError.InvalidNin -> CreateError.InvalidNinError
                        is PartyError.PersonResolutionError -> CreateError.RequestedPartyError
                    }
                }
                .bind()

        val businessModel = CreateRequestBusinessModel(
            authorizedParty = model.authorizedParty,
            requestType = model.requestType,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            meta = model.businessMeta
        )

        val businessCommand =
            businessHandler
                .validateAndReturnRequestCommand(businessModel)
                .mapLeft { err ->
                    logger.info("event=authorization_request_business_validation_error kind=${err.kind} detail=${err.detail}")
                    CreateError.BusinessError(err)
                }
                .bind()

        val metaAttributes = businessCommand.meta.toRequestMetaAttributes()

        val requestToCreate =
            AuthorizationRequest.create(
                type = businessCommand.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = businessCommand.validTo,
            )

        val requestProperties: List<AuthorizationRequestProperty> = metaAttributes.map {
            AuthorizationRequestProperty(
                requestId = requestToCreate.id,
                key = it.key,
                value = it.value,
            )
        }

        val savedRequest = requestRepo
            .insert(requestToCreate.copy(properties = requestProperties), businessCommand.scopes)
            .mapLeft { CreateError.PersistenceError }
            .bind()

        logger.info("event=authorization_request_created id=${savedRequest.id} type=${savedRequest.type}")

        savedRequest
    }
}
