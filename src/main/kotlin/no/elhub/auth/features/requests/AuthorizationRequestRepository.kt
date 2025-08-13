package no.elhub.auth.features.requests

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.errors.RepositoryError
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

    fun findAll(): Either<RepositoryError, List<AuthorizationRequest>> = either {
        transaction {
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
    }

    fun findById(requestId: UUID): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
            val request = AuthorizationRequest.Entity
                .selectAll()
                .where { AuthorizationRequest.Entity.id eq requestId }
                .singleOrNull()
                ?.let { AuthorizationRequest(it) }
                ?: run {
                    logger.error("Authorization request not found for id=$requestId")
                    raise(RepositoryError.AuthorizationNotFound)
                }

            val properties = AuthorizationRequestProperty.Entity
                .selectAll()
                .where { AuthorizationRequestProperty.Entity.authorizationRequestId eq requestId }
                .map { AuthorizationRequestProperty(it) }

            request.properties.addAll(properties)
            request
        }
    }

    fun create(request: PostAuthorizationRequestPayload): Either<RepositoryError, AuthorizationRequest> = either {
        transaction {
            val authorizationRequestId = AuthorizationRequest.Entity.insertAndGetId {
                it[id] = UUID.randomUUID()
                it[requestType] = RequestType.valueOf(request.data.attributes.requestType)
                it[status] = RequestStatus.Pending
                it[requestedBy] = request.data.relationships.requestedBy.data.id
                it[requestedFrom] = request.data.relationships.requestedFrom.data.id
                it[validTo] = LocalDateTime.now().plusDays(30)
            }

            findById(authorizationRequestId.value)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryError.AuthorizationNotFound -> {
                            logger.error("Authorization request was not successfully created: $error")
                            raise(RepositoryError.AuthorizationNotCreated)
                        }
                        else -> {
                            logger.error("Unknown error occurred: $error")
                            raise(error)
                        }
                    }
                }
                .bind()
        }
    }
}
