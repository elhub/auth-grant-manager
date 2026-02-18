package no.elhub.auth.features.common.party

import arrow.core.Either
import arrow.core.right
import no.elhub.auth.features.common.person.ClientError
import no.elhub.auth.features.common.person.PersonService

class PartyService(
    private val personService: PersonService,
) {
    suspend fun resolve(partyIdentifier: PartyIdentifier): Either<PartyError, AuthorizationParty> =
        when (partyIdentifier.idType) {
            PartyIdentifierType.NationalIdentityNumber ->
                personService.findOrCreateByNin(partyIdentifier.idValue)
                    .map { AuthorizationParty(id = it.internalId.toString(), type = PartyType.Person) }
                    .mapLeft { error ->
                        when (error) {
                            ClientError.InvalidNin -> PartyError.InvalidNin

                            is ClientError.UnexpectedError, ClientError.RequestRejected, ClientError.MissingHeader
                            -> PartyError.PersonResolutionError
                        }
                    }

            PartyIdentifierType.OrganizationNumber -> AuthorizationParty(
                id = partyIdentifier.idValue,
                type = PartyType.Organization
            ).right()

            PartyIdentifierType.GlobalLocationNumber -> AuthorizationParty(
                id = partyIdentifier.idValue,
                type = PartyType.OrganizationEntity
            ).right()
        }
}

sealed class PartyError {
    data object PersonResolutionError : PartyError()
    data object InvalidNin : PartyError()
}
