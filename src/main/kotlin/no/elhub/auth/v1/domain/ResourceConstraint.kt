package no.elhub.auth.v1.domain

sealed interface ResourceConstraint {
    data class MeteringPoints(
        val ids: Set<MeteringPointId>,
    ) : ResourceConstraint {
        init {
            require(ids.isNotEmpty()) { "At least one metering-point ID is required" }
        }
    }
}
