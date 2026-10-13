package no.elhub.auth.v1.domain

sealed interface MeteringPointIdParseResult {
    data class Valid(val id: MeteringPointId) : MeteringPointIdParseResult
    data object InvalidFormat : MeteringPointIdParseResult
}

@JvmInline
value class MeteringPointId private constructor(
    val value: String,
) {
    companion object {
        fun parse(value: String): MeteringPointIdParseResult =
            if (FORMAT.matches(value)) {
                MeteringPointIdParseResult.Valid(MeteringPointId(value))
            } else {
                MeteringPointIdParseResult.InvalidFormat
            }

        private val FORMAT = Regex("\\d{18}")
    }
}
