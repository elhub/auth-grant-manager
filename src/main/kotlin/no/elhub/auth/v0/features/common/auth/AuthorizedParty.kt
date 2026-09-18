package no.elhub.auth.v0.features.common.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import no.elhub.auth.v0.features.common.party.AuthorizationParty

val AuthorizedPartyKey = AttributeKey<AuthorizationParty>("AuthorizedParty")

val ApplicationCall.authorizedParty: AuthorizationParty
    get() = attributes[AuthorizedPartyKey]
