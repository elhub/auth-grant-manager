package no.elhub.auth.v1.acceptance.documents.movein

import no.elhub.auth.v1.acceptance.support.Organization

internal fun moveInDocumentPayload(
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
            "type": "AuthorizationDocument",
            "attributes": {
              "documentType": "MoveInAndChangeOfEnergySupplierForOrganization",
              "requestedScopes": [{
                "resourceType": "MeteringPointContract",
                "appliesTo": [{ "attribute": "meteringPoint.id", "value": [$meteringPointIdsJson] }]$moveInDateConstraint
              }]
            },
            "meta": {
              "requestedPrincipal": "${requestedPrincipal.number}",
              "requestedOf": "${requestedPrincipal.number}",
              "language": "nb"
            }
          }
        }
    """
}
