package no.elhub.auth.v1.api.common

import kotlinx.serialization.Serializable

@Serializable
enum class GrantResourceType {
    ConsumableGrant,
    PersistentGrant,
}
