package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.party.PartyError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class Handler(
    private val businessHandler: RequestBusinessHandler,
    private val partyService: PartyService,
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository,
) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
        val requestedByParty =
            partyService
                .resolve(model.meta.requestedBy)
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
                .resolve(model.meta.requestedFrom)
                .mapLeft { error ->
                    when (error) {
                        PartyError.InvalidNin -> CreateError.InvalidNinError
                        is PartyError.PersonResolutionError -> CreateError.RequestedPartyError
                    }
                }
                .bind()

        val requestedToParty =
            partyService
                .resolve(model.meta.requestedTo)
                .mapLeft { error ->
                    when (error) {
                        PartyError.InvalidNin -> CreateError.InvalidNinError
                        is PartyError.PersonResolutionError -> CreateError.RequestedPartyError
                    }
                }
                .bind()

        val businessCommand =
            businessHandler
                .validateAndReturnRequestCommand(model)
                .mapLeft { validationError -> CreateError.ValidationError(validationError) }
                .bind()

        val metaAttributes = businessCommand.meta.toMetaAttributes()

        val requestToCreate =
            AuthorizationRequest.create(
                type = businessCommand.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = businessCommand.validTo,
            )

        val result = transaction {
            val savedRequest =
                requestRepo
                    .insert(requestToCreate, businessCommand.scopes)
                    .mapLeft { CreateError.PersistenceError }
                    .bind()

            val requestProperties: List<AuthorizationRequestProperty> = metaAttributes.map {
                AuthorizationRequestProperty(
                    requestId = savedRequest.id,
                    key = it.key,
                    value = it.value,
                )
            }
            requestPropertyRepo
                .insert(requestProperties)
                .mapLeft { CreateError.PersistenceError }
                .bind()

            savedRequest.copy(properties = requestProperties)
        }

        result
    }
}

interface RequestBusinessHandler {
    suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<RequestTypeValidationError, RequestCommand>
    fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties
}
