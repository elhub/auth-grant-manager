package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestScopeTable.authorizationRequestId
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

interface RequestRepository {
    fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>>
    fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest>
    fun acceptRequest(requestId: UUID, approvedBy: AuthorizationParty): Either<RepositoryError, AuthorizationRequest>
    fun rejectAccept(requestId: UUID): Either<RepositoryError, AuthorizationRequest>
    fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<Long>>
}

class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
    private val requestPropertiesRepository: RequestPropertiesRepository
) : RequestRepository {

    override fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationRequest>> =
        either {
            transaction {
                val partyId = partyRepo.findOrInsert(type = party.type, resourceId = party.resourceId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()
                    .id

                val rows = AuthorizationRequestTable
                    .selectAll()
                    .where {
                        (AuthorizationRequestTable.requestedTo eq partyId)
                    }
                    .toList()

                rows.map { row ->
                    findInternal(row).bind()
                }
            }
        }

    override fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> =
        either {
            transaction {
                val request =
                    AuthorizationRequestTable
                        .selectAll()
                        .where { AuthorizationRequestTable.id eq requestId }
                        .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)
                findInternal(request).bind()
            }
        }

    override fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest> =
        either {
            transaction {
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
                            it[requestStatus] = request.status
                            it[requestedBy] = requestedByParty.id
                            it[requestedFrom] = requestedFromParty.id
                            it[requestedTo] = requestedToParty.id
                            it[validTo] = request.validTo.toJavaLocalDate()
                            it[createdAt] = request.createdAt
                        }
                        .map {
                            it.toAuthorizationRequest(
                                requestedByParty,
                                requestedFromParty,
                                requestedToParty,
                                request.properties
                            )
                        }.single()

                insertedRequest
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun acceptRequest(requestId: UUID, approvedBy: AuthorizationParty) = either {
        transaction {
            val approvedByRecord = partyRepo.findOrInsert(approvedBy.type, approvedBy.resourceId).bind()

            val rowsUpdated =
                AuthorizationRequestTable.update(
                    where = { AuthorizationRequestTable.id eq requestId }
                ) {
                    it[requestStatus] = AuthorizationRequest.Status.Accepted
                    it[updatedAt] = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                    it[this.approvedBy] = approvedByRecord.id
                }

            updateAndFetch(requestId, rowsUpdated).bind()
        }
    }

    override fun rejectAccept(requestId: UUID): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
            val rowsUpdated = AuthorizationRequestTable.update(
                where = { AuthorizationRequestTable.id eq requestId }
            ) {
                it[requestStatus] = AuthorizationRequest.Status.Rejected
                it[updatedAt] = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
            }

            updateAndFetch(requestId, rowsUpdated).bind()
        }
    }

    override fun findScopeIds(requestId: UUID): Either<RepositoryReadError, List<Long>> =
        either {
            transaction {
                AuthorizationRequestScopeTable
                    .selectAll()
                    .where { authorizationRequestId eq requestId }
                    .map { row ->
                        row[AuthorizationRequestScopeTable.authorizationScopeId]
                    }
            }
        }

    private fun updateAndFetch(requestId: UUID, rowsUpdated: Int): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
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

            val properties = requestPropertiesRepository.findBy(requestId = request[AuthorizationRequestTable.id].value)

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
    val authorizationRequestId = uuid("authorization_request_id")
        .references(AuthorizationRequestTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationRequestId, authorizationScopeId)
}

object AuthorizationRequestTable : UUIDTable("authorization_request") {
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
            fromDb = { value -> AuthorizationRequest.Status.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_status", it) },
        )
    val requestedBy = uuid("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = uuid("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = uuid("requested_to").references(AuthorizationPartyTable.id)
    val approvedBy = uuid("approved_by").references(AuthorizationPartyTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val updatedAt = timestampWithTimeZone("updated_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val validTo = date("valid_to")
}

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
    properties: List<AuthorizationRequestProperty>,
    approvedBy: AuthorizationPartyRecord? = null
) = AuthorizationRequest(
    id = this[AuthorizationRequestTable.id].value,
    type = this[AuthorizationRequestTable.requestType],
    status = this[AuthorizationRequestTable.requestStatus],
    requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
    requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
    requestedTo = AuthorizationParty(resourceId = requestedTo.resourceId, type = requestedTo.type),
    approvedBy = approvedBy?.let { AuthorizationParty(resourceId = it.resourceId, type = it.type) },
    createdAt = this[AuthorizationRequestTable.createdAt],
    updatedAt = this[AuthorizationRequestTable.updatedAt],
    validTo = this[AuthorizationRequestTable.validTo].toKotlinLocalDate(),
    properties = properties
)
