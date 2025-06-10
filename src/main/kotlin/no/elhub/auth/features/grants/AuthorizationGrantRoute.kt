package no.elhub.auth.features.grants

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            grantHandler.getAllGrantsNew().fold(
                ifLeft = { error ->
                    call.respond(
                        when (error) {
                            AuthorizationGrantError.DataBaseError ->
                                HttpStatusCode.ServiceUnavailable.description(
                                    "Data base error " +
                                        "during fetch all grant: $error",
                                )
                            AuthorizationGrantError.InternalServerError ->
                                HttpStatusCode.InternalServerError.description(
                                    "Internal server error " +
                                        "during fetch all grant: $error",
                                )
                            else -> {} // NotFound and IllegalArgument exceptions are not relevant for this path
                        },
                    )
                },
                ifRight = { grants ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = AuthorizationGrantResponseCollection.from(grants, call.url()),
                    )
                },
            )
        }

        get("/{$ID}") {
            val id = call.parameters[ID]

            grantHandler.getGrantByIdNew(id).fold(
                ifLeft = { error ->
                    call.respond(
                        when (error) {
                            is AuthorizationGrantError.NotFoundError ->
                                HttpStatusCode.NotFound.description("Authorization grant with ID $id grant not found: $error ")
                            is AuthorizationGrantError.DataBaseError ->
                                HttpStatusCode.ServiceUnavailable.description(
                                    "Database error during authorization grant " +
                                        "with ID $id: $error ",
                                )
                            is AuthorizationGrantError.InternalServerError ->
                                HttpStatusCode.InternalServerError.description(
                                    "Internal server error during authorization grant " +
                                        "retrieval with ID $id: $error ",
                                )
                            is AuthorizationGrantError.IllegalArgumentError ->
                                HttpStatusCode.BadRequest.description("Invalid or missing grant ID format: $error")
                        },
                    )
                },
                ifRight = { result ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = AuthorizationGrantResponse.from(result, selfLink = call.url()),
                    )
                },
            )
        }
    }
}
