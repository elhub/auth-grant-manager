package no.elhub.auth.v1.acceptance.support

/** Authentication and acting context sent with an API request; the PDP decides business roles. */
sealed interface Caller {
    val token: String

    data class MarketEntity(
        val organizationNumber: String,
        val senderGln: String?,
        override val token: String,
        val onBehalfOfGln: String? = null,
    ) : Caller

    data class EndUser(
        val personId: String,
        override val token: String,
        val onBehalfOfOrganization: String? = null,
    ) : Caller

    data object Anonymous : Caller {
        override val token = ""
    }
}
