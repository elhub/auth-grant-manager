package no.elhub.auth.features.grants

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.openapi.RelationshipData
import no.elhub.auth.openapi.RelationshipInnerData
import no.elhub.auth.openapi.TopLevelLinks
import no.elhub.auth.openapi.TopLevelMeta
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

class AuthorizationGrantDocGenerator {

    private val authorizationGrantObject = createAuthorizationGrantObject()

    companion object {
        const val AUTHORIZATION_GRANT_GET_ALL_PATH = AUTHORIZATION_GRANT
        const val AUTHORIZATION_GRANT_GET_ID_PATH = "$AUTHORIZATION_GRANT/{id}"
    }

    @Path(AUTHORIZATION_GRANT_GET_ALL_PATH)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get all Authorization Grant. ",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successful retrieve all. ",
                content = [Content(schema = Schema(implementation = AuthorizationGrantListResponse::class))]
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
            )
        ]
    )

    fun authorizationGrantDocResponse(): AuthorizationGrantResponse =
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
