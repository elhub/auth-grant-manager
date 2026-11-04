package no.elhub.auth.v1.common

import no.elhub.auth.common.auth.AuthorizationParty

// Placeholder until the PDP validates the approver against requestedOf and requestedPrincipal.
fun isApproverAllowed(
    approver: AuthorizationParty,
    requestedOf: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
): Boolean = true
