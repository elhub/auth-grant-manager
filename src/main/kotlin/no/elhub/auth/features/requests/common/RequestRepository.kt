package no.elhub.auth.features.requests.common

import arrow.core.Either
import java.util.UUID
import no.elhub.auth.features.requests.AuthorizationRequest
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import org.jetbrains.exposed.dao.id.UUIDTable

interface RequestRepository {
    fun find(requestId: UUID): Either<RepositoryError, AuthorizationRequest>
    fun findAll(): Either<RepositoryError, List<AuthorizationRequest>>
    fun insert(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String
    ): Either<RepositoryError, AuthorizationRequest>
}

class ExposedRequestRepository : RequestRepository {

    private val logger = LoggerFactory.getLogger(RequestRepository::class.java)

    data class Request(
        val request: AuthorizationRequest?
    )

    override fun findAll(): Either<RepositoryError, List<AuthorizationRequest>> = either {
        transaction {
            val requests = AuthorizationRequestTable
                .selectAll()
                .map { it.toAuthorizationRequest() }
            val requestIds = requests.map { UUID.fromString(it.id) }

            val propertiesByRequestId = AuthorizationRequestPropertyTable
                .selectAll()
                .where { AuthorizationRequestPropertyTable.authorizationRequestId inList requestIds }
                .map { it.toAuthorizationProperty() }
                .groupBy { it.authorizationRequestId }

            requests.forEach { request ->
                request.properties.addAll(propertiesByRequestId.getOrElse(request.id) { emptyList() })
            }
            requests
        }
    }

    override fun find(requestId: UUID): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
            val request = AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq requestId }
                .singleOrNull()?.toAuthorizationRequest()
                ?: run {
                    logger.error("Authorization request not found for id=$requestId")
                    raise(RepositoryError.AuthorizationNotFound)
                }

            val properties = AuthorizationRequestPropertyTable
                .selectAll()
                .where { AuthorizationRequestPropertyTable.authorizationRequestId eq requestId }
                .map { it.toAuthorizationProperty() }

            request.properties.addAll(properties)
            request
        }
    }

    override fun insert(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String
    ): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
            val authorizationRequestId = AuthorizationRequestTable.insertAndGetId {
                it[id] = UUID.randomUUID()
                it[requestType] = type
                it[status] = AuthorizationRequest.Status.Pending
                it[requestedBy] = requester
                it[requestedFrom] = requestee
                it[validTo] = LocalDateTime.now().plusDays(30)
            }

            find(authorizationRequestId.value)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryError.UnexpectedRepositoryFailure -> AuthorizationRequestProblem.DataBaseError
                        is RepositoryError.AuthorizationNotFound, AuthorizationRequestProblem.NotFoundError
                            -> AuthorizationRequestProblem.UnexpectedError
                    }
                }
        }
    }
}

object AuthorizationRequestTable : UUIDTable("authorization_request") {
    val requestType = customEnumeration(
        name = "request_type",
        fromDb = { value -> AuthorizationRequest.Type.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_type", it) }
    )
    val status = customEnumeration(
        name = "request_status",
        fromDb = { value -> AuthorizationRequest.Status.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_status", it) }
    )
    val requestedBy = varchar("requested_by", 16)
    val requestedFrom = varchar("requested_from", 16)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to")
}

fun ResultRow.toAuthorizationRequest() = AuthorizationRequest(
    id = this[AuthorizationRequestTable.id].toString(),
    requestType = this[AuthorizationRequestTable.requestType],
    status = this[AuthorizationRequestTable.status],
    requestedBy = this[AuthorizationRequestTable.requestedBy],
    requestedFrom = this[AuthorizationRequestTable.requestedFrom],
    createdAt = this[AuthorizationRequestTable.createdAt].toKotlinLocalDateTime(),
    updatedAt = this[AuthorizationRequestTable.updatedAt].toKotlinLocalDateTime(),
    validTo = this[AuthorizationRequestTable.validTo].toKotlinLocalDateTime()
)

data class AuthorizationRequestProperty(
    val authorizationRequestId: String,
    val key: String,
    val value: String,
    val createdAt: KotlinLocalDateTime
)

object AuthorizationRequestPropertyTable : Table("authorization_request_property") {
    val authorizationRequestId = uuid("authorization_request_id")
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

fun ResultRow.toAuthorizationProperty(): AuthorizationRequestProperty = AuthorizationRequestProperty(
    this[AuthorizationRequestPropertyTable.authorizationRequestId].toString(),
    this[AuthorizationRequestPropertyTable.key],
    this[AuthorizationRequestPropertyTable.value],
    this[AuthorizationRequestPropertyTable.createdAt].toKotlinLocalDateTime()
)
