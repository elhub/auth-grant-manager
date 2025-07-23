package no.elhub.auth.features.grants

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.openapi.RelationshipData
import no.elhub.auth.openapi.RelationshipInnerData
import no.elhub.auth.openapi.TopLevelLinks
import no.elhub.auth.openapi.TopLevelMeta
import no.elhub.auth.openapi.ApiErrorResponse

class AuthorizationGrantDocGenerator {

    private val authorizationGrantObject = createAuthorizationGrantObject()

    companion object {
        const val AUTHORIZATION_GRANT_GET_ALL_PATH = AUTHORIZATION_GRANT
        const val AUTHORIZATION_GRANT_GET_ID_PATH = "$AUTHORIZATION_GRANT/{id}"
        const val AUTHORIZATION_GRANT_GET_SCOPE_PATH = "$AUTHORIZATION_GRANT/{id}/scopes"
    }

    @Path(AUTHORIZATION_GRANT_GET_ALL_PATH)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Retrieve a list of authorization grants. ",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successful retrieve all. ",
                content = [Content(schema = Schema(implementation = AuthorizationGrantListResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            )
        ]
    )
    fun authorizationGrantDocListResponse(): AuthorizationGrantListResponse =
        AuthorizationGrantListResponse(
            data = listOf(authorizationGrantObject),
            links = TopLevelLinks(self = ""),
            meta = TopLevelMeta(createdAt = "")
        )

    @Path(AUTHORIZATION_GRANT_GET_ID_PATH)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get specific Authorization Grant based by id. ",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successful retrieve Authorization Grant. ",
                content = [Content(schema = Schema(implementation = AuthorizationGrantResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request. Invalid request body. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            )
        ]
    )
    fun authorizationGrantDocResponse(): AuthorizationGrantResponse =
        AuthorizationGrantResponse(
            data = authorizationGrantObject,
            links = TopLevelLinks(self = ""),
            meta = TopLevelMeta(createdAt = "")
        )

    @Path(AUTHORIZATION_GRANT_GET_SCOPE_PATH)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Retrieve all of the scopes for the identified authorization grant. ",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successful retrieve Authorization Grant. ",
                content = [Content(schema = Schema(implementation = AuthorizationGrantResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request. Invalid request body. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error. ",
                content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
            )
        ]
    )
    fun authorizationGrantScopeDocResponse(): AuthorizationGrantResponse =
        AuthorizationGrantResponse(
            data = authorizationGrantObject,
            links = TopLevelLinks(self = ""),
            meta = TopLevelMeta(createdAt = "")
        )

    private fun createAuthorizationGrantObject(): AuthorizationGrantObject {
        return AuthorizationGrantObject(
            id = "",
            type = "",
            attributes = AuthorizationGrantAttributes(
                status = "",
                grantedAt = "",
                validFrom = "",
                validTo = ""
            ),
            relationships = AuthorizationGrantRelationships(
                grantedFor = RelationshipData(data = RelationshipInnerData(type = "", id = "")),
                grantedBy = RelationshipData(data = RelationshipInnerData(type = "", id = "")),
                grantedTo = RelationshipData(data = RelationshipInnerData(type = "", id = ""))
            )
        )
    }

}
