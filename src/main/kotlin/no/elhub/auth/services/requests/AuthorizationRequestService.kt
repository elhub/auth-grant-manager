package no.elhub.auth.services.requests

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.DEFAULT_REQUEST_DEADLINE
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.AuthorizationRequestProperty
import no.elhub.auth.model.RequestStatus
import no.elhub.auth.model.RequestType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single
import java.util.*

@Single
class AuthorizationRequestService {

    fun createRequest(request: AuthorizationRequest.Request): AuthorizationRequest {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date // Must use date to avoid timezone issues
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
        val requestId = requestUuid.toString()
        transaction {
            AuthorizationRequestProperty.Entity.insert {
                it[authorizationRequestId] = UUID.fromString(requestId)
                it[key] = "contract"
                it[value] = request.data.meta.contract
            }
        }
        return getRequest(requestId)
    }

    fun getRequest(id: String): AuthorizationRequest {
        val result = transaction {
            AuthorizationRequest.Entity
                .selectAll()
                .where { AuthorizationRequest.Entity.id eq UUID.fromString(id) }
                .singleOrNull()
        }
        if (result == null) {
            throw Exception("Request not found") // TODO: Replace with custom exception
        }
        val request = AuthorizationRequest(result)
        val properties = transaction {
            AuthorizationRequestProperty.Entity
                .selectAll()
                .where { AuthorizationRequestProperty.Entity.authorizationRequestId eq UUID.fromString(id) }
                .toList().map { AuthorizationRequestProperty(it) }
        }
        request.properties.addAll(properties)
        return request
    }
}
