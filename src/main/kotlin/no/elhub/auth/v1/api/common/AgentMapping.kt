package no.elhub.auth.v1.api.common

import io.ktor.http.HttpStatusCode
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v0.features.common.party.PartyError as LegacyPartyError
import no.elhub.auth.v0.features.common.party.PartyIdentifier as LegacyPartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType as LegacyPartyIdentifierType

fun AgentExternalIdentifier.toPartyIdentifier() =
    when (this) {
        is AgentExternalIdentifier.OrganizationIdentifier -> {
            LegacyPartyIdentifier(LegacyPartyIdentifierType.OrganizationNumber, value)
        }

        is AgentExternalIdentifier.PersonIdentifier -> {
            LegacyPartyIdentifier(LegacyPartyIdentifierType.NationalIdentityNumber, value)
        }

        is AgentExternalIdentifier.MarketEntityIdentifier -> {
            LegacyPartyIdentifier(LegacyPartyIdentifierType.GlobalLocationNumber, value)
        }
    }

fun LegacyPartyError.toApiErrorResponse() =
    when (this) {
        LegacyPartyError.InvalidNin -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid national identity number",
                detail = "Provided national identity number is invalid",
            )
        }

        LegacyPartyError.PersonResolutionError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Unable to resolve agent",
                detail = "The requested agent could not be resolved",
            )
        }
    }
