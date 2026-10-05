package no.elhub.auth.v1.domain

import no.elhub.auth.v1.InputError

@JvmInline
value class MeteringPointId private constructor(
    val value: String,
) {
    companion object {
        fun create(value: String): MeteringPointId {
            if (!value.matches(FORMAT)) {
                throw InputError.InvalidMeteringPointId(value)
            }

            return MeteringPointId(value)
        }

        private val FORMAT = Regex("^\\d{18}$")
    }
}
