package no.elhub.auth.v1.common

import no.elhub.auth.v1.common.auth.AuthorizationAgent

// Placeholder until the PDP validates the approver against requestedOf and requestedPrincipal.
fun isApproverAllowed(
    approver: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
): Boolean = true
