package no.elhub.auth.features.common

import org.postgresql.util.PGobject

class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        type = enumTypeName
        value = enumValue?.name
    }
}
