package no.elhub.auth.features.common

import arrow.core.Either
import arrow.core.right

class PartyService(
    private val personService: PersonService,
) {
    suspend fun resolve(partyIdentifier: PartyIdentifier): Either<PartyError, AuthorizationParty> =
        when (partyIdentifier.idType) {
            PartyIdentifierType.NationalIdentityNumber ->
                personService.findOrCreateByNin(partyIdentifier.idValue)
                    .map { AuthorizationParty(resourceId = it.internalId.toString(), type = PartyType.Person) }
                    .mapLeft { PartyError.PersonResolutionError }

            PartyIdentifierType.OrganizationNumber -> AuthorizationParty(
                resourceId = partyIdentifier.idValue,
                type = PartyType.Organization
            ).right()

            PartyIdentifierType.GlobalLocationNumber -> AuthorizationParty(
                resourceId = partyIdentifier.idValue,
                type = PartyType.OrganizationEntity
            ).right()
        }
}

suspend fun PartyIdentifier.toAuthorizationParty(
    service: PartyService
): Either<PartyError, AuthorizationParty> =
    service.resolve(this)

sealed class PartyError {
    data object PersonResolutionError : PartyError()
}
