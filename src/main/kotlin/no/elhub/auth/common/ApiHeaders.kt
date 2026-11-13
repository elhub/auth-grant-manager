package no.elhub.auth.common

object ApiHeaders {
    // Legacy GLN names remain accepted on v1 temporarily while consumers migrate to Elhub-* headers.
    // v0 continues to use only these original names.
    const val SENDER_GLN = "SenderGln"
    const val ON_BEHALF_OF_GLN = "OnBehalfOfGln"
    const val ELHUB_SENDER_GLN = "Elhub-Sender-Gln"
    const val ELHUB_ON_BEHALF_OF_GLN = "Elhub-On-Behalf-Of-Gln"

    // Internal header used for Minside enduser acting on behalf of an organisation.
    // Do not document this in OpenAPI, since it is not part of the public API contract and would cause confusion for consumers.
    const val ON_BEHALF_OF_ORGANISATION = "ElhubOnBehalfOfOrganisation"
}
