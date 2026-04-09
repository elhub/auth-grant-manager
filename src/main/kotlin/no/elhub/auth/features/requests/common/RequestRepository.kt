package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceId
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.permissionType
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestScopeTable.authorizationRequestId
import no.elhub.auth.features.requests.common.AuthorizationRequestScopeTable.authorizationScopeId
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

sealed interface AcceptWithGrantError {
    sealed interface RequestError : AcceptWithGrantError {
        data object NotFound : RequestError
        data object NotAuthorized : RequestError
        data object AlreadyProcessed : RequestError
        data object Expired : RequestError
        data object Unexpected : RequestError
    }

    data object GrantError : AcceptWithGrantError
}

interface RequestRepository {
    suspend fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    suspend fun findAllAndSortByCreatedAt(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>>
    suspend fun insert(
        request: AuthorizationRequest,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationRequest>

    suspend fun rejectRequest(requestId: UUID): Either<RepositoryError, AuthorizationRequest>
    suspend fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<UUID>>

    suspend fun acceptWithGrant(
        requestId: UUID,
        approvedBy: AuthorizationParty,
        grant: AuthorizationGrant,
        grantProperties: List<AuthorizationGrantProperty>,
    ): Either<AcceptWithGrantError, AuthorizationRequest>
}

class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
    private val requestPropertiesRepository: RequestPropertiesRepository,
    private val grantRepository: GrantRepository,
    private val grantPropertiesRepository: GrantPropertiesRepository,
    private val transactionContext: TransactionContext,
) : RequestRepository {

    override suspend fun findAllAndSortByCreatedAt(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>> =
        transactionContext("db_operations", "RequestRepository", "findAllAndSortByCreatedAt", { RepositoryReadError.UnexpectedError }) {
            val partyId = partyRepo.findOrInsert(type = party.type, partyId = party.id)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
                .id

            AuthorizationRequestTable
                .selectAll()
                .where {
                    (AuthorizationRequestTable.requestedTo eq partyId) or (AuthorizationRequestTable.requestedBy eq partyId)
                }
                .orderBy(AuthorizationRequestTable.createdAt to SortOrder.DESC)
                .toList()
                .map { row -> findInternal(row).mapLeft { RepositoryReadError.UnexpectedError }.bind() }
        }

    override suspend fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> =
        transactionContext("db_operations", "RequestRepository", "find", { RepositoryReadError.UnexpectedError }) {
            val request = AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq requestId }
                .singleOrNull() ?: raise(RepositoryReadError.UnexpectedError)
            findInternal(request).mapLeft { RepositoryReadError.UnexpectedError }.bind()
        }

    override suspend fun insert(
        request: AuthorizationRequest,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationRequest> =
        transactionContext("db_operations", "RequestRepository", "insert", { RepositoryWriteError.UnexpectedError }) {
            val requestedByParty = partyRepo
                .findOrInsert(request.requestedBy.type, request.requestedBy.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val requestedFromParty = partyRepo
                .findOrInsert(request.requestedFrom.type, request.requestedFrom.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val requestedToParty = partyRepo
                .findOrInsert(request.requestedTo.type, request.requestedTo.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val insertedRequest = AuthorizationRequestTable
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
            requestPropertiesRepository.insert(request.properties)

            insertedRequest.toAuthorizationRequest(
                requestedBy = requestedByParty,
                requestedFrom = requestedFromParty,
                requestedTo = requestedToParty,
                properties = request.properties,
            )
        }

    override suspend fun rejectRequest(requestId: UUID): Either<RepositoryError, AuthorizationRequest> =
        transactionContext("db_operations", "RequestRepository", "rejectRequest", { RepositoryWriteError.UnexpectedError }) {
            val rowsUpdated = AuthorizationRequestTable.update(
                where = { AuthorizationRequestTable.id eq requestId }
            ) {
                it[requestStatus] = DatabaseRequestStatus.Rejected
                it[updatedAt] = currentTimeUtc()
            }
            updateAndFetch(requestId, rowsUpdated).mapLeft { RepositoryWriteError.UnexpectedError }.bind()
        }

    override suspend fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<UUID>> =
        transactionContext("db_operations", "RequestRepository", "findScopeIds", { RepositoryReadError.UnexpectedError }) {
            AuthorizationRequestScopeTable
                .selectAll()
                .where { authorizationRequestId eq requestId }
                .map { row -> row[authorizationScopeId] }
        }

    override suspend fun acceptWithGrant(
        requestId: UUID,
        approvedBy: AuthorizationParty,
        grant: AuthorizationGrant,
        grantProperties: List<AuthorizationGrantProperty>,
    ): Either<AcceptWithGrantError, AuthorizationRequest> =
        transactionContext<AcceptWithGrantError, AuthorizationRequest>(
            "db_operations",
            "RequestRepository",
            "acceptWithGrant",
            { AcceptWithGrantError.RequestError.Unexpected }
        ) {
            val approvedByRecord = partyRepo.findOrInsert(approvedBy.type, approvedBy.id)
                .mapLeft { AcceptWithGrantError.RequestError.Unexpected as AcceptWithGrantError }
                .bind()

            val rowsUpdated = AuthorizationRequestTable.update(
                where = { AuthorizationRequestTable.id eq requestId }
            ) {
                it[requestStatus] = DatabaseRequestStatus.Accepted
                it[updatedAt] = currentTimeUtc()
                it[this.approvedBy] = approvedByRecord.id
            }

            val acceptedRequest = updateAndFetch(requestId, rowsUpdated)
                .mapLeft { AcceptWithGrantError.RequestError.Unexpected as AcceptWithGrantError }
                .bind()

            grantRepository.insert(grant)
                .mapLeft { AcceptWithGrantError.GrantError as AcceptWithGrantError }
                .bind()

            grantPropertiesRepository.insert(grantProperties)
                .mapLeft { AcceptWithGrantError.GrantError as AcceptWithGrantError }
                .bind()

            acceptedRequest.copy(grantId = grant.id)
        }

    private fun handleScopes(scopes: List<CreateScopeData>, insertedRequest: ResultRow) {
        val scopeIds: List<UUID> = AuthorizationScopeTable
            .batchInsert(scopes) { scope ->
                this[authorizedResourceType] = scope.authorizedResourceType
                this[authorizedResourceId] = scope.authorizedResourceId
                this[permissionType] = scope.permissionType
            }
            .map { it[AuthorizationScopeTable.id].value }
            .distinct()

        if (scopeIds.isNotEmpty()) {
            AuthorizationRequestScopeTable.batchInsert(scopeIds) { id ->
                this[authorizationRequestId] = insertedRequest[AuthorizationRequestTable.id].value
                this[authorizationScopeId] = id
            }
        }
    }

    private suspend fun updateAndFetch(requestId: UUID, rowsUpdated: Int): Either<RepositoryError, AuthorizationRequest> =
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

    private suspend fun findInternal(request: ResultRow): Either<RepositoryReadError, AuthorizationRequest> =
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
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { currentTimeUtc() }
    val validTo = timestampWithTimeZone("valid_to").clientDefault { currentTimeUtc() }
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
        DatabaseRequestStatus.Pending if validTo <= currentTimeUtc() -> AuthorizationRequest.Status.Expired
        else -> AuthorizationRequest.Status.Pending
    }
    return AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        type = this[AuthorizationRequestTable.requestType],
        status = status,
        requestedBy = AuthorizationParty(id = requestedBy.resourceId, type = requestedBy.type),
        requestedFrom = AuthorizationParty(id = requestedFrom.resourceId, type = requestedFrom.type),
        requestedTo = AuthorizationParty(id = requestedTo.resourceId, type = requestedTo.type),
        approvedBy = approvedBy?.let { AuthorizationParty(id = it.resourceId, type = it.type) },
        createdAt = this[AuthorizationRequestTable.createdAt],
        updatedAt = this[AuthorizationRequestTable.updatedAt],
        validTo = validTo,
        properties = properties
    )
}
