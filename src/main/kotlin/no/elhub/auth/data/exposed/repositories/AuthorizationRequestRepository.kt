package no.elhub.auth.data.exposed.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.*
import no.elhub.auth.data.exposed.tables.AuthorizationRequestPropertyTable
import no.elhub.auth.data.exposed.tables.AuthorizationRequestTable
import no.elhub.auth.data.exposed.tables.toAuthorizationProperty
import no.elhub.auth.data.exposed.tables.toAuthorizationRequest
import no.elhub.auth.domain.request.AuthorizationRequest
import no.elhub.auth.domain.request.AuthorizationRequestProblemById
import no.elhub.auth.domain.request.AuthorizationRequestProblemCreate
import no.elhub.auth.domain.request.AuthorizationRequestProblemList
import no.elhub.auth.domain.request.RequestStatus
import no.elhub.auth.domain.request.RequestType
import no.elhub.auth.presentation.model.PostAuthorizationRequestPayload
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object AuthorizationRequestRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationRequestRepository::class.java)

    data class Request(
        val request: AuthorizationRequest?
    )

    fun findAll(): Either<AuthorizationRequestProblemList, List<AuthorizationRequest>> = try {
        transaction {
            val requests = AuthorizationRequestTable
                .selectAll()
                .toList()
                .map { it.toAuthorizationRequest() }

            // collect all request IDs
            val requestIds = requests.map { UUID.fromString(it.id) }

            // fetch all properties for these requests in one query
            val propertiesByRequestId = AuthorizationRequestPropertyTable
                .selectAll()
                .where { AuthorizationRequestPropertyTable.authorizationRequestId inList requestIds }
                .map { it.toAuthorizationProperty() }
                .groupBy { it.authorizationRequestId }

            // attach properties to each request
            requests.forEach { request ->
                request.properties.addAll(propertiesByRequestId[request.id] ?: emptyList())
            }

            requests.right()
        }
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch all requests: ${sqlEx.message}")
        AuthorizationRequestProblemList.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch all requests: ${exp.message}")
        AuthorizationRequestProblemList.UnexpectedError.left()
    }

    fun findById(requestId: UUID): Either<AuthorizationRequestProblemById, AuthorizationRequest> = try {
        val request = transaction {
            AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq requestId }
                .singleOrNull()?.toAuthorizationRequest()
        }

        if (request == null) {
            logger.error("Error occurred during find request for $requestId")
            AuthorizationRequestProblemById.NotFoundError.left()
        } else {
            val properties = transaction {
                AuthorizationRequestPropertyTable
                    .selectAll()
                    .where { AuthorizationRequestPropertyTable.authorizationRequestId eq requestId }
                    .toList().map { it.toAuthorizationProperty() }
            }
            request.properties.addAll(properties)
            request.right()
        }
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch request by id with id $requestId: ${sqlEx.message}")
        AuthorizationRequestProblemById.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch request by id with id $requestId: ${exp.message}")
        AuthorizationRequestProblemById.UnexpectedError.left()
    }

    fun create(request: PostAuthorizationRequestPayload): Either<AuthorizationRequestProblemCreate, AuthorizationRequest> =
        try {
            transaction {
                val authorizationRequestId = AuthorizationRequestTable.insertAndGetId {
                    it[id] = UUID.randomUUID()
                    it[requestType] = RequestType.valueOf(request.data.attributes.requestType)
                    it[status] = RequestStatus.Pending
                    it[requestedBy] = request.data.relationships.requestedBy.data.id
                    it[requestedFrom] = request.data.relationships.requestedFrom.data.id
                    it[validTo] = LocalDateTime.now().plusDays(30)
                }
                findById(authorizationRequestId.value).mapLeft { byIdProblem ->
                    when (byIdProblem) {
                        is AuthorizationRequestProblemById.DataBaseError -> AuthorizationRequestProblemCreate.DataBaseError
                        is AuthorizationRequestProblemById.UnexpectedError, AuthorizationRequestProblemById.NotFoundError
                            -> AuthorizationRequestProblemCreate.UnexpectedError
                    }
                }
            }
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during create request: ${sqlEx.message}")
            AuthorizationRequestProblemCreate.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during create request: ${exp.message}")
            AuthorizationRequestProblemCreate.UnexpectedError.left()
        }
}
