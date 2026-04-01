package no.elhub.auth.features.requests.common

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.elhub.auth.config.measureDbCall
import no.elhub.auth.config.withTransactionEither
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface RequestPropertiesRepository {
    suspend fun insert(properties: List<AuthorizationRequestProperty>)
    suspend fun findBy(requestId: UUID): List<AuthorizationRequestProperty>
}

class ExposedRequestPropertiesRepository(
    private val metricsProvider: PrometheusMeterRegistry,
) : RequestPropertiesRepository {

    override suspend fun insert(properties: List<AuthorizationRequestProperty>) {
        if (properties.isEmpty()) return
        withTransactionEither<RepositoryWriteError, Unit>({ RepositoryWriteError.UnexpectedError }) {
            metricsProvider.measureDbCall("request_props_repo_insert") {
                AuthorizationRequestPropertyTable.batchInsert(properties) { property ->
                    this[AuthorizationRequestPropertyTable.requestId] = property.requestId
                    this[AuthorizationRequestPropertyTable.key] = property.key
                    this[AuthorizationRequestPropertyTable.value] = property.value
                }
            }
        }
    }

    override suspend fun findBy(requestId: UUID): List<AuthorizationRequestProperty> =
        withTransactionEither<RepositoryReadError, List<AuthorizationRequestProperty>>({ RepositoryReadError.UnexpectedError }) {
            metricsProvider.measureDbCall("request_props_repo_find") {
                AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.requestId eq requestId }
                    .map { it.toAuthorizationRequestProperty() }
            }
        }.fold({ emptyList() }, { it })
}

object AuthorizationRequestPropertyTable : Table("auth.authorization_request_property") {
    val requestId = javaUUID("authorization_request_id").references(AuthorizationRequestTable.id)
    val key = varchar("key", 64)
    val value = text("value")
}

private fun ResultRow.toAuthorizationRequestProperty() = AuthorizationRequestProperty(
    requestId = this[AuthorizationRequestPropertyTable.requestId],
    key = this[AuthorizationRequestPropertyTable.key],
    value = this[AuthorizationRequestPropertyTable.value]
)
