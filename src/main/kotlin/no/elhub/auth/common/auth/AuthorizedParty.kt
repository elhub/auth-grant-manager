package no.elhub.auth.common.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey

val AuthorizedPartyKey = AttributeKey<AuthorizationParty>("AuthorizedParty")

val ApplicationCall.authorizedParty: AuthorizationParty
    get() = attributes[AuthorizedPartyKey]
