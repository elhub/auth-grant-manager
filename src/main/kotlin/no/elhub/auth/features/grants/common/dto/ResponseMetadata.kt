package no.elhub.auth.features.grants.common.dto

import no.elhub.auth.features.common.AuthorizationMetadata
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.common.toResponseMetadata
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty

fun List<AuthorizationGrantProperty>.toGrantResponseMetadata(): AuthorizationMetadata =
    toResponseMetadata(
        entries = map { it.key to it.value },
        allowedKeys = setOf(AuthorizationMetadataKeys.MOVE_IN_DATE),
        context = "AuthorizationGrant",
    )
