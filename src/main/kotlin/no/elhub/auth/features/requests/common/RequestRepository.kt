package no.elhub.auth.features.requests.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.AuthorizationPartyRecord
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.requests.AuthorizationRequest
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface RequestRepository {
    fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationRequest>>
    fun insert(req: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest>
}

class ExposedRequestRepository(
    private val partyRepo: PartyRepository
) : RequestRepository {

    override fun findAll(): Either<RepositoryReadError, List<AuthorizationRequest>> = either {
        transaction {
            val rows = AuthorizationRequestTable.selectAll()

            rows.map { request ->
                val requestedById = request[AuthorizationRequestTable.requestedBy]
                val requestedFromId = request[AuthorizationRequestTable.requestedFrom]

                val requestedByParty = partyRepo.find(requestedById)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

                val requestedFromParty = partyRepo.find(requestedFromId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

                request.toAuthorizationRequest(
                    requestedByParty,
                    requestedFromParty
                )
            }
        }
    }

    override fun find(requestId: UUID): Either<RepositoryReadError, AuthorizationRequest> = either {
        transaction {
            val request = AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq requestId }
                .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

            val requestedByDbId = request[AuthorizationRequestTable.requestedBy]
            val requestedByParty = partyRepo.find(requestedByDbId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()

            val requestedFromDbId = request[AuthorizationRequestTable.requestedFrom]
            val requestedFromParty = partyRepo.find(requestedFromDbId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()

            request.toAuthorizationRequest(requestedByParty, requestedFromParty)
        }
    }

    override fun insert(req: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest> =
        either {
            transaction {
                val requestedByParty = partyRepo.findOrInsert(req.requestedBy.type, req.requestedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val requestedFromParty = partyRepo.findOrInsert(req.requestedFrom.type, req.requestedFrom.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val request = AuthorizationRequestTable.insertReturning {
                    it[id] = req.id
                    it[requestType] = req.type
                    it[requestStatus] = req.status
                    it[requestedBy] = requestedByParty.id
                    it[requestedFrom] = requestedFromParty.id
                    it[validTo] = req.validTo
                    it[createdAt] = req.createdAt
                }.map {
                    it.toAuthorizationRequest(
                        requestedByParty,
                        requestedFromParty
                    )
                }.single()

                request
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}

object AuthorizationRequestTable : UUIDTable("authorization_request") {
    val requestType = customEnumeration(
        name = "request_type",
        fromDb = { value -> AuthorizationRequest.Type.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_type", it) }
    )
    val requestStatus = customEnumeration(
        name = "request_status",
        fromDb = { value -> AuthorizationRequest.Status.valueOf(value as String) },
        toDb = { PGEnum("authorization_request_status", it) }
    )
    val requestedBy = uuid("requested_by").references(id)
    val requestedFrom = uuid("requested_from").references(id)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to")
}

fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,

) = AuthorizationRequest(
    id = this[AuthorizationRequestTable.id].value,
    type = this[AuthorizationRequestTable.requestType],
    status = this[AuthorizationRequestTable.requestStatus],
    requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
    requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
    createdAt = this[AuthorizationRequestTable.createdAt],
    updatedAt = this[AuthorizationRequestTable.updatedAt],
    validTo = this[AuthorizationRequestTable.validTo],
)
