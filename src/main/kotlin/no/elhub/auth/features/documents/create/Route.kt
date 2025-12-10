package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.documents.create.toModel
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider,
) {
    post {
        val resolvedActor = authProvider.authorizeMarketParty(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@post
            }

        val requestBody = call.receive<Request>()
        val model = requestBody.toModel()

        if (resolvedActor.role != RoleType.BalanceSupplier) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val document = handler(model)
            .getOrElse { error ->
                log.error("Failed to create authorization document: {}", error)
                when (error) {
                    is
                    CreateDocumentError.FileGenerationError,
                    CreateDocumentError.CertificateRetrievalError,
                    CreateDocumentError.MappingError,
                    CreateDocumentError.SignatureFetchingError,
                    CreateDocumentError.SigningDataGenerationError,
                    CreateDocumentError.SigningError,
                    CreateDocumentError.PersistenceError,
                    CreateDocumentError.RequestedByPartyError,
                    CreateDocumentError.RequestedFromPartyError,
                    CreateDocumentError.RequestedToPartyError,
                    CreateDocumentError.SignedByPartyError,
                    CreateDocumentError.PersonError
                    -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        log.debug("Successfully created document {}", document.id)
        call.respond(
            status = HttpStatusCode.Created,
            message = document.toCreateDocumentResponse()
        )
    }
}
