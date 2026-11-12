package no.elhub.auth.v1.common.auth

import io.ktor.server.application.ApplicationCall
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.auth.authorizedParty

// Authentication is shared with v0; v1 uses agent terminology for the same identity.
typealias AuthorizationAgent = AuthorizationParty
typealias AgentType = PartyType

val ApplicationCall.authorizedAgent: AuthorizationAgent
    get() = authorizedParty
