package no.elhub.auth.v1.acceptance.requests.movein

import no.elhub.auth.v1.acceptance.support.Organization

internal fun moveInRequestPayload(
    requestedPrincipal: Organization,
    meteringPointIds: List<String>,
    moveInDate: String?,
): String {
    val meteringPointIdsJson = meteringPointIds.joinToString(", ") { "\"$it\"" }
    val moveInDateConstraint = moveInDate?.let {
        """, "allowedChanges": [{ "attribute": "validFrom", "value": ["$it"] }]"""
    }.orEmpty()
    return """
        {
          "data": {
            "type": "AuthorizationRequest",
            "attributes": {
              "requestType": "MoveInAndChangeOfEnergySupplierForOrganization",
              "requestedScopes": [{
                "resourceType": "MeteringPointContract",
                "appliesTo": [{ "attribute": "meteringPoint.id", "value": [$meteringPointIdsJson] }]$moveInDateConstraint
              }]
            },
            "meta": { "requestedPrincipal": "${requestedPrincipal.number}", "requestedOf": "${requestedPrincipal.number}" }
          }
        }
    """
}
