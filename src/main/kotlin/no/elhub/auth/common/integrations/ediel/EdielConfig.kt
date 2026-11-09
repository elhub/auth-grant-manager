package no.elhub.auth.common.integrations.ediel

data class EdielConfig(
    val serviceUrl: String,
    val username: String,
    val password: String,
    val environment: EdielEnvironment,
)

enum class EdielEnvironment {
    TEST,
    PRODUCTION,
}
