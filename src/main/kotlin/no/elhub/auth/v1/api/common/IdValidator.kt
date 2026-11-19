package no.elhub.auth.v1.api.common

import no.elhub.auth.v1.api.common.errors.InputError
import kotlin.uuid.Uuid

fun validatePathId(id: String?): Uuid {
    if (id.isNullOrBlank()) throw InputError.MissingIdError("Missing resource ID")
    return Uuid.parseOrNull(id)
        ?: throw InputError.MalformedIdError("The provided payload did not satisfy the expected format")
}

fun validateDataId(dataId: String?, pathId: Uuid): Uuid {
    if (dataId.isNullOrBlank()) throw InputError.MissingIdError("Missing data.id")
    val parsed = Uuid.parseOrNull(dataId)
    if (parsed != pathId) throw InputError.DataIdMismatchError("data.id must be a UUID matching the path ID")
    return parsed
}
