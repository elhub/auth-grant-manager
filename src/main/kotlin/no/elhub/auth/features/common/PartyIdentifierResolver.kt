package no.elhub.auth.features.common

import arrow.core.Either
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class PartyIdentifierResolver(
    private val personService: PersonService,
    private val partyRepository: PartyRepository
) {
    suspend fun resolve(partyIdentifier: PartyIdentifier): Either<PartyError, AuthorizationParty> =
        when (partyIdentifier.idType) {
            PartyIdentifierType.NationalIdentityNumber ->
                personService.findOrCreateByNin(partyIdentifier.idValue)
                    .map { AuthorizationParty(resourceId = it.internalId.toString(), type = PartyType.Person) }
                    .mapLeft { PartyError.PersonResolutionError }

            PartyIdentifierType.OrganizationNumber ->
                partyRepository.findOrInsert(PartyType.Organization, partyIdentifier.idValue)
                    .map { AuthorizationParty(resourceId = it.id.toString(), type = PartyType.Organization) }
                    .mapLeft { PartyError.PartyResolutionError }

            PartyIdentifierType.GlobalLocationNumber ->
                partyRepository.findOrInsert(PartyType.OrganizationEntity, partyIdentifier.idValue)
                    .map { AuthorizationParty(resourceId = it.id.toString(), type = PartyType.OrganizationEntity) }
                    .mapLeft { PartyError.PartyResolutionError }
        }
}

object PartyIdentifierResolverCompanion : KoinComponent {
    private val resolver: PartyIdentifierResolver by inject()

    suspend fun resolve(partyIdentifier: PartyIdentifier): Either<PartyError, AuthorizationParty> =
        resolver.resolve(partyIdentifier)
}

suspend fun PartyIdentifier.toAuthorizationParty(): Either<PartyError, AuthorizationParty> =
    PartyIdentifierResolverCompanion.resolve(this)


sealed class PartyError {
    data object PersonResolutionError : PartyError()
    data object PartyResolutionError : PartyError()
}
