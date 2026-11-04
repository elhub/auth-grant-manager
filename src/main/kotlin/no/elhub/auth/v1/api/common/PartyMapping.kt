package no.elhub.auth.v1.api.common

import io.ktor.http.HttpStatusCode
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.party.PartyError
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v1.domain.AgentExternalIdentifier

fun AgentExternalIdentifier.toPartyIdentifier() =
    when (this) {
        is AgentExternalIdentifier.OrganizationIdentifier -> {
            PartyIdentifier(PartyIdentifierType.OrganizationNumber, value)
        }

        is AgentExternalIdentifier.PersonIdentifier -> {
            PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, value)
        }

        is AgentExternalIdentifier.MarketPartyIdentifier -> {
            PartyIdentifier(PartyIdentifierType.GlobalLocationNumber, value)
        }
    }

fun PartyError.toApiErrorResponse() =
    when (this) {
        PartyError.InvalidNin -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid national identity number",
                detail = "Provided national identity number is invalid",
            )
        }

        PartyError.PersonResolutionError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Unable to resolve party",
                detail = "The requested party could not be resolved",
            )
        }
    }
