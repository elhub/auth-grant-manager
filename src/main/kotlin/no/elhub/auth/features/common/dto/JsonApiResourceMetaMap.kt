package no.elhub.auth.features.common.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta

@Serializable
@JvmInline
value class JsonApiResourceMetaMap(
    val values: JsonObject,
) : JsonApiResourceMeta
