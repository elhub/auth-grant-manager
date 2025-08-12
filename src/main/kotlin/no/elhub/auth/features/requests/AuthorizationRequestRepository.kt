package no.elhub.auth.features.requests

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.AuthorizationRequestProperty
import no.elhub.auth.model.RequestStatus
import no.elhub.auth.model.RequestType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

object AuthorizationRequestRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationRequestRepository::class.java)

    data class Request(
        val request: AuthorizationRequest?
    )

    fun findAll(): Either<DomainError, List<AuthorizationRequest>> = try {
        transaction {
            val requests = AuthorizationRequest.Entity
                .selectAll()
                .map(::AuthorizationRequest)

            // collect all request IDs
            val requestIds = requests.map { UUID.fromString(it.id) }

            // fetch all properties for these requests in one query
            val propertiesByRequestId = AuthorizationRequestProperty.Entity
                .selectAll()
                .where { AuthorizationRequestProperty.Entity.authorizationRequestId inList requestIds }
                .map { AuthorizationRequestProperty(it) }
                .groupBy { it.authorizationRequestId }

            // attach properties to each request
            requests.forEach { request ->
                request.properties.addAll(propertiesByRequestId[request.id] ?: emptyList())
            }

            requests.right()
        }
    } catch (e: Exception) {
        logger.error("Unknown error occurred during fetch all requests: ${e.message}")
        DomainError.RepositoryError.Unexpected(e).left()
    }

    fun findById(requestId: UUID): Either<DomainError, AuthorizationRequest> = try {
        val request = transaction {
            AuthorizationRequest.Entity
                .selectAll()
                .where { AuthorizationRequest.Entity.id eq requestId }
                .singleOrNull()
                ?.let { AuthorizationRequest(it) }
        }

        if (request == null) {
            logger.error("Error occurred during find request for $requestId")
            DomainError.RepositoryError.AuthorizationNotFound.left()
        } else {
            val properties = transaction {
                AuthorizationRequestProperty.Entity
                    .selectAll()
                    .where { AuthorizationRequestProperty.Entity.authorizationRequestId eq requestId }
                    .toList().map { AuthorizationRequestProperty(it) }
            }
            request.properties.addAll(properties)
            request.right()
        }
    } catch (e: Exception) {
        logger.error("Unknown error occurred during fetch request by id with id $requestId: ${e.message}")
        DomainError.RepositoryError.Unexpected(e).left()
    }

    fun create(request: PostAuthorizationRequestPayload): Either<DomainError, AuthorizationRequest> = try {
        transaction {
            val authorizationRequestId = AuthorizationRequest.Entity.insertAndGetId {
                it[id] = UUID.randomUUID()
                it[requestType] = RequestType.valueOf(request.data.attributes.requestType)
                it[status] = RequestStatus.Pending
                it[requestedBy] = request.data.relationships.requestedBy.data.id
                it[requestedFrom] = request.data.relationships.requestedFrom.data.id
                it[validTo] = LocalDateTime.now().plusDays(30)
            }
            findById(authorizationRequestId.value).mapLeft { byIdProblem ->
                when (byIdProblem) {
                    is DomainError.RepositoryError.AuthorizationNotFound ->
                        DomainError.RepositoryError.AuthorizationNotCreated
                    else -> byIdProblem
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Unknown error occurred during create request: ${e.message}")
        DomainError.RepositoryError.Unexpected(e).left()
    }
}
