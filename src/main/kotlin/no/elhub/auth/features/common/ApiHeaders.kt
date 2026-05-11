package no.elhub.auth.features.common

object ApiHeaders {
    const val SENDER_GLN = "SenderGLN"
    const val ON_BEHALF_OF_GLN = "OnBehalfOfGLN"

    // Internal header used for Minside enduser acting on behalf of an organisation.
    // Do not document this in OpenAPI, since it is not part of the public API contract and would cause confusion for consumers.
    const val ON_BEHALF_OF_ORGANISATION = "ElhubOnBehalfOfOrganisation"
}
