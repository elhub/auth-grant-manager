package no.elhub.auth.features.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta

@Serializable
@JvmInline
value class JsonApiResourceMetaMap(
    val values: Map<String, String>,
) : JsonApiResourceMeta
