package no.elhub.auth.features.requests.common

import arrow.core.Either
import java.util.UUID
import no.elhub.auth.features.requests.AuthorizationRequest
import arrow.core.left
import arrow.core.raise.either
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
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.JoinType

interface RequestRepository {
    fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationRequest>>
    fun insert(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String,
    ): Either<RepositoryWriteError, UUID>
}

class ExposedRequestRepository : RequestRepository {

    private val logger = LoggerFactory.getLogger(RequestRepository::class.java)

    override fun findAll(): Either<RepositoryReadError, List<AuthorizationRequest>> = either {
        transaction {
            AuthorizationRequestTable
                .join(
                    AuthorizationRequestPropertyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationRequestTable.id,
                    otherColumn = AuthorizationRequestPropertyTable.authorizationRequestId
                )
                .selectAll()
                .map { it.toAuthorizationRequest() }
        }
    }

    override fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> = either {
        transaction {
            AuthorizationRequestTable
                .join(
                    AuthorizationRequestPropertyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationRequestTable.id,
                    otherColumn = AuthorizationRequestPropertyTable.authorizationRequestId
                )
                .selectAll()
                .singleOrNull { AuthorizationRequestTable.id == requestId }
                ?.toAuthorizationRequest()
                ?: run {
                    logger.error("Authorization request not found for id=$requestId")
                    raise(RepositoryReadError.NotFoundError)
                }
        }
    }

    override fun insert(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String
    ): Either<RepositoryWriteError, UUID> = Either.catch {
        transaction {
            AuthorizationRequestTable.insertAndGetId {
                it[id] = UUID.randomUUID()
                it[requestType] = type
                it[status] = AuthorizationRequest.Status.Pending
                it[requestedBy] = requester
                it[requestedFrom] = requestee
                it[validTo] = LocalDateTime.now().plusDays(30)
            }.value
        }
    }.mapLeft { RepositoryWriteError.UnexpectedError }
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
    validTo = this[AuthorizationRequestTable.validTo].toKotlinLocalDateTime(),
    // TODO: Add properties"
    // properties = AuthorizationRequest.Property(
    //    this[AuthorizationRequestPropertyTable.authorizationRequestId].toString(),
    //    this[AuthorizationRequestPropertyTable.key],
    //    this[AuthorizationRequestPropertyTable.value],
    //    this[AuthorizationRequestPropertyTable.createdAt].toKotlinLocalDateTime()
    //),
)

object AuthorizationRequestPropertyTable : Table("authorization_request_property") {
    val authorizationRequestId = uuid("authorization_request_id")
    val key = varchar("key", 64)
    val value = text("value")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
