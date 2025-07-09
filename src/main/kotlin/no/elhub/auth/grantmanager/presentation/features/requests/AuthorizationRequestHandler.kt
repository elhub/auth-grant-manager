package no.elhub.auth.grantmanager.presentation.features.requests

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.grantmanager.presentation.DEFAULT_REQUEST_DEADLINE
import no.elhub.auth.grantmanager.presentation.features.errors.ApiError
import no.elhub.auth.grantmanager.presentation.model.AuthorizationRequest
import no.elhub.auth.grantmanager.presentation.model.AuthorizationRequestProperty
import no.elhub.auth.grantmanager.presentation.model.RequestStatus
import no.elhub.auth.grantmanager.presentation.model.RequestType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class AuthorizationRequestHandler {

    fun createRequest(request: AuthorizationRequestRequest): Either<ApiError, AuthorizationRequest> = either {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date // Must calculate using date to avoid timezone issues
        val validTimeTo = today.plus(DEFAULT_REQUEST_DEADLINE, DateTimeUnit.DAY)
        val requestUuid = transaction {
            AuthorizationRequest.Entity.insertAndGetId {
                it[requestType] = RequestType.valueOf(request.data.attributes.requestType)
                it[requestStatus] = RequestStatus.Pending
                it[requestedBy] = request.data.relationships.requestedBy.data.id
                it[requestedTo] = request.data.relationships.requestedTo.data.id
                it[validTo] = LocalDateTime(validTimeTo, LocalTime(0, 0)).toJavaLocalDateTime()
            }
        }
        transaction {
            AuthorizationRequestProperty.Entity.insert {
                it[authorizationRequestId] = requestUuid.value
                it[key] = "contract"
                it[value] = request.data.meta.contract
            }
        }
        getRequest(requestUuid.value).bind()
    }

    fun getRequest(id: UUID): Either<ApiError, AuthorizationRequest> = either {
        val result = transaction {
            AuthorizationRequest.Entity
                .selectAll()
                .where { AuthorizationRequest.Entity.id eq id }
                .singleOrNull()
        }
        if (result == null) {
            raise(ApiError.NotFound(detail = "Could not find AuthorizationRequest with id $id."))
        }
        val request = AuthorizationRequest(result)
        val properties = transaction {
            AuthorizationRequestProperty.Entity
                .selectAll()
                .where { AuthorizationRequestProperty.Entity.authorizationRequestId eq id }
                .toList().map { AuthorizationRequestProperty(it) }
        }
        request.properties.addAll(properties)
        request
    }

    fun getRequests(): List<AuthorizationRequest> {
        val results = transaction {
            val requests = AuthorizationRequest.Entity
                .selectAll().associate { it[AuthorizationRequest.Entity.id].toString() to AuthorizationRequest(it) }
            val ids = requests.keys.map { UUID.fromString(it) }
            AuthorizationRequestProperty.Entity
                .selectAll()
                .where { AuthorizationRequestProperty.Entity.authorizationRequestId inList ids }
                .forEach {
                    val id = it[AuthorizationRequestProperty.Entity.authorizationRequestId].toString()
                    requests[id]?.properties?.add(AuthorizationRequestProperty(it))
                }
            requests.map { it.value }.toList()
        }
        return results
    }
}
