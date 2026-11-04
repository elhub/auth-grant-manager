package no.elhub.auth.v1.domain

@JvmInline
value class MeteringPointId private constructor(
    val value: String,
) {
    companion object {
        fun create(value: String): MeteringPointId {
            if (!value.matches(FORMAT)) {
                throw DomainError.InvalidMeteringPointId(value)
            }

            return MeteringPointId(value)
        }

        private val FORMAT = Regex("^\\d{18}$")
    }
}
