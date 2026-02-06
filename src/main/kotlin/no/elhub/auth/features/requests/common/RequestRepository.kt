package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceId
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.permissionType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestScopeTable.authorizationRequestId
import no.elhub.auth.features.requests.common.AuthorizationRequestScopeTable.authorizationScopeId
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

interface RequestRepository {
    fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    fun findAllBy(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>>
    fun insert(
        request: AuthorizationRequest,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationRequest>
    fun acceptRequest(
        requestId: UUID,
        approvedBy: AuthorizationParty
    ): Either<RepositoryError, AuthorizationRequest>

    fun rejectRequest(requestId: UUID): Either<RepositoryError, AuthorizationRequest>
    fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<UUID>>
}

class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
    private val requestPropertiesRepository: RequestPropertiesRepository
) : RequestRepository {

    override fun findAllBy(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>> =
        either {
            val partyId = partyRepo.findOrInsert(type = party.type, partyId = party.resourceId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
                .id

            val rows = AuthorizationRequestTable
                .selectAll()
                .where {
                    (AuthorizationRequestTable.requestedTo eq partyId) or (AuthorizationRequestTable.requestedBy eq partyId)
                }
                .toList()

            rows.map { row ->
                findInternal(row).bind()
            }
        }

    override fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> =
        either {
            val request =
                AuthorizationRequestTable
                    .selectAll()
                    .where { AuthorizationRequestTable.id eq requestId }
                    .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)
            findInternal(request).bind()
        }

    override fun insert(
        request: AuthorizationRequest,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationRequest> =
        either {
            val requestedByParty =
                partyRepo
                    .findOrInsert(request.requestedBy.type, request.requestedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val requestedFromParty =
                partyRepo
                    .findOrInsert(request.requestedFrom.type, request.requestedFrom.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val requestedToParty =
                partyRepo
                    .findOrInsert(request.requestedTo.type, request.requestedTo.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val insertedRequest =
                AuthorizationRequestTable
                    .insertReturning {
                        it[id] = request.id
                        it[requestType] = request.type
                        it[requestStatus] = request.status.toDataBaseRequestStatus()
                        it[requestedBy] = requestedByParty.id
                        it[requestedFrom] = requestedFromParty.id
                        it[requestedTo] = requestedToParty.id
                        it[validTo] = request.validTo
                        it[createdAt] = request.createdAt
                    }.single()

            handleScopes(scopes, insertedRequest)

            insertedRequest.toAuthorizationRequest(
                requestedBy = requestedByParty,
                requestedFrom = requestedFromParty,
                requestedTo = requestedToParty,
                properties = request.properties,
            )
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun acceptRequest(requestId: UUID, approvedBy: AuthorizationParty) = either {
        val approvedByRecord = partyRepo.findOrInsert(approvedBy.type, approvedBy.resourceId).bind()

        val rowsUpdated =
            AuthorizationRequestTable.update(
                where = { AuthorizationRequestTable.id eq requestId }
            ) {
                it[requestStatus] = DatabaseRequestStatus.Accepted
                it[updatedAt] = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                it[this.approvedBy] = approvedByRecord.id
            }

        updateAndFetch(requestId, rowsUpdated).bind()
    }

    override fun rejectRequest(requestId: UUID): Either<RepositoryError, AuthorizationRequest> = either {
        val rowsUpdated = AuthorizationRequestTable.update(
            where = { AuthorizationRequestTable.id eq requestId }
        ) {
            it[requestStatus] = DatabaseRequestStatus.Rejected
            it[updatedAt] = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
        }

        updateAndFetch(requestId, rowsUpdated).bind()
    }

    override fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<UUID>> =
        either {
            AuthorizationRequestScopeTable
                .selectAll()
                .where { authorizationRequestId eq requestId }
                .map { row ->
                    row[AuthorizationRequestScopeTable.authorizationScopeId]
                }
        }

    private fun handleScopes(scopes: List<CreateScopeData>, insertedRequest: ResultRow) {
        // upsert scopes
        val scopeIds: List<UUID> = AuthorizationScopeTable
            .batchInsert(scopes) { scope ->
                this[authorizedResourceType] = scope.authorizedResourceType
                this[authorizedResourceId] = scope.authorizedResourceId
                this[permissionType] = scope.permissionType
            }
            .map { it[AuthorizationScopeTable.id].value }
            .distinct()

        // insert request-scope links
        if (scopeIds.isNotEmpty()) {
            AuthorizationRequestScopeTable.batchInsert(scopeIds) { id ->
                this[authorizationRequestId] = insertedRequest[AuthorizationRequestTable.id].value
                this[authorizationScopeId] = id
            }
        }
    }

    private fun updateAndFetch(requestId: UUID, rowsUpdated: Int): Either<RepositoryError, AuthorizationRequest> =
        either {
            if (rowsUpdated == 0) {
                raise(RepositoryWriteError.UnexpectedError)
            }

            val request =
                AuthorizationRequestTable
                    .selectAll()
                    .where { AuthorizationRequestTable.id eq requestId }
                    .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

            findInternal(request)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()
        }

    private fun findInternal(request: ResultRow): Either<RepositoryReadError, AuthorizationRequest> =
        either {
            val requestedByDbId = request[AuthorizationRequestTable.requestedBy]
            val requestedFromDbId = request[AuthorizationRequestTable.requestedFrom]
            val requestedToDbId = request[AuthorizationRequestTable.requestedTo]
            val approvedById: UUID? = request[AuthorizationRequestTable.approvedBy]

            val requestedByParty =
                partyRepo
                    .find(requestedByDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            val requestedFromParty =
                partyRepo
                    .find(requestedFromDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            val requestedToParty =
                partyRepo
                    .find(requestedToDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            // fetch approvedBy only if it exists. It should exist in the database after a request has been accepted
            val approvedByParty = approvedById?.let { id ->
                partyRepo.find(id)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()
            }

            val properties =
                requestPropertiesRepository.findBy(requestId = request[AuthorizationRequestTable.id].value)

            request.toAuthorizationRequest(
                requestedByParty,
                requestedFromParty,
                requestedToParty,
                properties,
                approvedByParty
            )
        }
}

object AuthorizationRequestScopeTable : Table("auth.authorization_request_scope") {
    val authorizationRequestId = javaUUID("authorization_request_id")
        .references(AuthorizationRequestTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = javaUUID("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationRequestId, authorizationScopeId)
}

object AuthorizationRequestTable : UUIDTable("auth.authorization_request") {
    val requestType =
        customEnumeration(
            name = "request_type",
            sql = "auth.authorization_request_type",
            fromDb = { value -> AuthorizationRequest.Type.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_type", it) },
        )
    val requestStatus =
        customEnumeration(
            name = "request_status",
            sql = "auth.authorization_request_status",
            fromDb = { value -> DatabaseRequestStatus.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_status", it) },
        )
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = javaUUID("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = javaUUID("requested_to").references(AuthorizationPartyTable.id)
    val approvedBy = javaUUID("approved_by").references(AuthorizationPartyTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val updatedAt = timestampWithTimeZone("updated_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val validTo = timestampWithTimeZone("valid_to")
}

enum class DatabaseRequestStatus {
    Pending,
    Accepted,
    Rejected
}

fun AuthorizationRequest.Status.toDataBaseRequestStatus(): DatabaseRequestStatus = when (this) {
    AuthorizationRequest.Status.Accepted -> DatabaseRequestStatus.Accepted
    AuthorizationRequest.Status.Expired -> DatabaseRequestStatus.Pending
    AuthorizationRequest.Status.Pending -> DatabaseRequestStatus.Pending
    AuthorizationRequest.Status.Rejected -> DatabaseRequestStatus.Rejected
}

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
    properties: List<AuthorizationRequestProperty>,
    approvedBy: AuthorizationPartyRecord? = null
): AuthorizationRequest {
    val dbStatus = this[AuthorizationRequestTable.requestStatus]
    val validTo = this[AuthorizationRequestTable.validTo]

    val status: AuthorizationRequest.Status = when (dbStatus) {
        DatabaseRequestStatus.Accepted -> AuthorizationRequest.Status.Accepted
        DatabaseRequestStatus.Rejected -> AuthorizationRequest.Status.Rejected
        DatabaseRequestStatus.Pending if validTo <= OffsetDateTime.now(ZoneOffset.UTC) -> AuthorizationRequest.Status.Expired
        else -> AuthorizationRequest.Status.Pending
    }
    return AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        type = this[AuthorizationRequestTable.requestType],
        status = status,
        requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
        requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
        requestedTo = AuthorizationParty(resourceId = requestedTo.resourceId, type = requestedTo.type),
        approvedBy = approvedBy?.let { AuthorizationParty(resourceId = it.resourceId, type = it.type) },
        createdAt = this[AuthorizationRequestTable.createdAt],
        updatedAt = this[AuthorizationRequestTable.updatedAt],
        validTo = validTo,
        properties = properties
    )
}
