package no.elhub.auth.features.common

import no.elhub.auth.features.common.Constants.CONSENT_MANAGEMENT_OSB_ID
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType

object Constants {
    const val CONSENT_MANAGEMENT_OSB_ID = "consent_management_osb"
}

object AuthorizationParties {
    val ConsentManagementSystem = AuthorizationParty(
        resourceId = CONSENT_MANAGEMENT_OSB_ID,
        type = PartyType.System
    )
}
