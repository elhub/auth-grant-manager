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
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.scope.AuthorizationScopeTable
import no.elhub.auth.features.common.scope.CreateAuthorizationScope
import no.elhub.auth.features.common.scope.ScopeRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

interface RequestRepository {
    fun findRequest(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    fun findAllRequests(): Either<RepositoryReadError, List<AuthorizationRequest>>
    fun insertRequest(req: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest>
    fun confirmRequest(
        requestId: UUID,
        newStatus: AuthorizationRequest.Status,
    ): Either<RepositoryError, AuthorizationRequest>
    fun insertScope(
        requestId: UUID,
        scope: CreateAuthorizationScope
    ): Either<RepositoryWriteError, Long>
}

class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
    private val scopeRepo: ScopeRepository
) : RequestRepository {
    override fun findAllRequests(): Either<RepositoryReadError, List<AuthorizationRequest>> =
        either {
            transaction {
                val rows = AuthorizationRequestTable.selectAll()
                rows.map { request ->
                    findInternalRequest(request).bind()
                }
            }
        }

    override fun findRequest(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> =
        either {
            transaction {
                val request =
                    AuthorizationRequestTable
                        .selectAll()
                        .where { AuthorizationRequestTable.id eq requestId }
                        .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)
                findInternalRequest(request).bind()
            }
        }

    override fun insertRequest(req: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest> =
        either {
            transaction {
                val requestedByParty =
                    partyRepo
                        .findOrInsert(req.requestedBy.type, req.requestedBy.resourceId)
                        .mapLeft { RepositoryWriteError.UnexpectedError }
                        .bind()

                val requestedFromParty =
                    partyRepo
                        .findOrInsert(req.requestedFrom.type, req.requestedFrom.resourceId)
                        .mapLeft { RepositoryWriteError.UnexpectedError }
                        .bind()

                val requestedToParty =
                    partyRepo
                        .findOrInsert(req.requestedTo.type, req.requestedTo.resourceId)
                        .mapLeft { RepositoryWriteError.UnexpectedError }
                        .bind()

                val request =
                    AuthorizationRequestTable
                        .insertReturning {
                            it[id] = req.id
                            it[requestType] = req.type
                            it[requestStatus] = req.status
                            it[requestedBy] = requestedByParty.id
                            it[requestedFrom] = requestedFromParty.id
                            it[requestedTo] = requestedToParty.id
                            it[validTo] = req.validTo.toJavaLocalDate()
                            it[createdAt] = req.createdAt
                        }.map {
                            it.toAuthorizationRequest(
                                requestedByParty,
                                requestedFromParty,
                                requestedToParty,
                            )
                        }.single()

                request
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun insertScope(
        requestId: UUID,
        scope: CreateAuthorizationScope
    ): Either<RepositoryWriteError, Long> =
        either {
            transaction {
                val authScope = scopeRepo
                    .findOrCreateScope(scope)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                AuthorizationRequestScopeTable.insert {
                    it[authorizationRequestId] = requestId
                    it[authorizationScopeId] = authScope
                }

                authScope
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun confirmRequest(
        requestId: UUID,
        newStatus: AuthorizationRequest.Status
    ): Either<RepositoryError, AuthorizationRequest> =
        either {
            transaction {
                // TODO approvedBy should be in the database
                val rowsUpdated =
                    AuthorizationRequestTable.update(
                        where = { AuthorizationRequestTable.id eq requestId }
                    ) {
                        it[requestStatus] = newStatus
                        it[updatedAt] = java.time.LocalDateTime.now()
                    }

                if (rowsUpdated == 0) {
                    raise(RepositoryWriteError.UnexpectedError)
                }

                val request =
                    AuthorizationRequestTable
                        .selectAll()
                        .where { AuthorizationRequestTable.id eq requestId }
                        .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

                findInternalRequest(request)
                    .mapLeft { readError ->
                        when (readError) {
                            is RepositoryReadError.NotFoundError -> RepositoryWriteError.UnexpectedError
                            is RepositoryReadError.UnexpectedError -> RepositoryWriteError.UnexpectedError
                        }
                    }
                    .bind()
            }
        }

    private fun findInternalRequest(request: ResultRow): Either<RepositoryReadError, AuthorizationRequest> =
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

            request.toAuthorizationRequest(
                requestedByParty,
                requestedFromParty,
                requestedToParty,
                approvedByParty
            )
        }
}

object AuthorizationRequestTable : UUIDTable("authorization_request") {
    val requestType =
        customEnumeration(
            name = "request_type",
            fromDb = { value -> AuthorizationRequest.Type.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_type", it) },
        )
    val requestStatus =
        customEnumeration(
            name = "request_status",
            fromDb = { value -> AuthorizationRequest.Status.valueOf(value as String) },
            toDb = { PGEnum("authorization_request_status", it) },
        )
    val requestedBy = uuid("requested_by").references(id)
    val requestedFrom = uuid("requested_from").references(id)
    val requestedTo = uuid("requested_to").references(id)
    val approvedBy = uuid("approved_by").references(id).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val validTo = date("valid_to")
}

object AuthorizationRequestScopeTable : Table("auth.authorization_request_scope") {
    val authorizationRequestId = uuid("authorization_request_id")
        .references(AuthorizationRequestTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationRequestId, authorizationScopeId)
}

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
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
)
