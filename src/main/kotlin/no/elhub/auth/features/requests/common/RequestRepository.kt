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
import org.jetbrains.exposed.dao.id.UUIDTable

interface RequestRepository {
    operator fun get(requestId: UUID): Either<AuthorizationRequestProblem, AuthorizationRequest>
    fun all(): Either<AuthorizationRequestProblem, List<AuthorizationRequest>>
    fun create(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String
    ): Either<AuthorizationRequestProblem, AuthorizationRequest>
}

class ExposedRequestRepository : RequestRepository {

    private val logger = LoggerFactory.getLogger(RequestRepository::class.java)

    data class Request(
        val request: AuthorizationRequest?
    )

    override fun all(): Either<AuthorizationRequestProblem, List<AuthorizationRequest>> = try {
        transaction {
            val requests = AuthorizationRequestTable
                .selectAll()
                .toList()
                .map { it.toAuthorizationRequest() }

            // collect all request IDs
            val requestIds = requests.map { UUID.fromString(it.id) }

            // fetch all properties for these requests in one query
            val propertiesByRequestId = AuthorizationRequestPropertyTable
                .selectAll()
                .where { AuthorizationRequestPropertyTable.authorizationRequestId inList requestIds }
                .map { it.toAuthorizationProperty() }
                .groupBy { it.authorizationRequestId }

            // attach properties to each request
            requests.forEach { request ->
                request.properties.addAll(propertiesByRequestId[request.id] ?: emptyList())
            }

            requests.right()
        }
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch all requests: ${sqlEx.message}")
        AuthorizationRequestProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch all requests: ${exp.message}")
        AuthorizationRequestProblem.UnexpectedError.left()
    }

    override fun get(requestId: UUID): Either<AuthorizationRequestProblem, AuthorizationRequest> = try {
        val request = transaction {
            AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq requestId }
                .singleOrNull()?.toAuthorizationRequest()
        }

        if (request == null) {
            logger.error("Error occurred during find request for $requestId")
            AuthorizationRequestProblem.NotFoundError.left()
        } else {
            val properties = transaction {
                AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.authorizationRequestId eq requestId }
                    .toList().map { it.toAuthorizationProperty() }
            }
            request.properties.addAll(properties)
            request.right()
        }
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch request by id with id $requestId: ${sqlEx.message}")
        AuthorizationRequestProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch request by id with id $requestId: ${exp.message}")
        AuthorizationRequestProblem.UnexpectedError.left()
    }

    override fun create(
        type: AuthorizationRequest.Type,
        requester: String,
        requestee: String
    ): Either<AuthorizationRequestProblem, AuthorizationRequest> =
        try {
            transaction {
                val authorizationRequestId = AuthorizationRequestTable.insertAndGetId {
                    it[id] = UUID.randomUUID()
                    it[requestType] = type
                    it[status] = AuthorizationRequest.Status.Pending
                    it[requestedBy] = requester
                    it[requestedFrom] = requestee
                    it[validTo] = LocalDateTime.now().plusDays(30)
                }
                get(authorizationRequestId.value).mapLeft { byIdProblem ->
                    when (byIdProblem) {
                        is AuthorizationRequestProblem.DataBaseError -> AuthorizationRequestProblem.DataBaseError
                        is AuthorizationRequestProblem.UnexpectedError, AuthorizationRequestProblem.NotFoundError
                            -> AuthorizationRequestProblem.UnexpectedError
                    }
                }
            }
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during create request: ${sqlEx.message}")
            AuthorizationRequestProblem.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during create request: ${exp.message}")
            AuthorizationRequestProblem.UnexpectedError.left()
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
