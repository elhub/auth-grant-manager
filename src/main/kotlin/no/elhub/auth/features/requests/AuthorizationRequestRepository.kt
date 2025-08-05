package no.elhub.auth.features.requests

import no.elhub.auth.model.AuthorizationExceptions
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.AuthorizationRequestProperty
import no.elhub.auth.model.RequestStatus
import no.elhub.auth.model.RequestType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

object AuthorizationRequestRepository {

    data class Request(
        val request: AuthorizationRequest?
    )

    fun findAll(): List<AuthorizationRequest> = transaction {
        val requests = AuthorizationRequest.Entity
            .selectAll()
            .map(::AuthorizationRequest)

        val requestIds = requests.map { UUID.fromString(it.id) }

        val propertiesByRequestId = AuthorizationRequestProperty.Entity
            .selectAll()
            .where { AuthorizationRequestProperty.Entity.authorizationRequestId inList requestIds }
            .map { AuthorizationRequestProperty(it) }
            .groupBy { it.authorizationRequestId }

        requests.forEach { request ->
            request.properties.addAll(propertiesByRequestId.getOrElse(request.id) { emptyList() })
        }
        requests
    }

    fun findById(requestId: UUID): AuthorizationRequest = transaction {
        AuthorizationRequest.Entity
            .selectAll()
            .where { AuthorizationRequest.Entity.id eq requestId }
            .singleOrNull()
            ?.let { row ->
                AuthorizationRequest(row).also { req ->
                    val properties = AuthorizationRequestProperty.Entity
                        .selectAll()
                        .where { AuthorizationRequestProperty.Entity.authorizationRequestId eq requestId }
                        .map { AuthorizationRequestProperty(it) }
                    req.properties.addAll(properties)
                }
            } ?: throw AuthorizationExceptions.NotFoundException(requestId)
    }


    fun create(request: PostAuthorizationRequestPayload): AuthorizationRequest = transaction {
        val authorizationRequestId = AuthorizationRequest.Entity.insertAndGetId {
            it[id] = UUID.randomUUID()
            it[requestType] = RequestType.valueOf(request.data.attributes.requestType)
            it[status] = RequestStatus.Pending
            it[requestedBy] = request.data.relationships.requestedBy.data.id
            it[requestedFrom] = request.data.relationships.requestedFrom.data.id
            it[validTo] = LocalDateTime.now().plusDays(30)
        }
        findById(authorizationRequestId.value)
    }

}
