package no.elhub.auth.grantmanager.domain.valueobjects

private const val ORGNR_LENGTH = 9

@JvmInline
value class OrganizationNumber(private val orgNrStr: String) {
    init {
        require(orgNrStr.length == ORGNR_LENGTH) {
            "Organization number must be $ORGNR_LENGTH characters long"
        }
    }
}
