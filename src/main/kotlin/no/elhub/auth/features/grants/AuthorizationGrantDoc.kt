package no.elhub.auth.features.grants

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.jaxrs2.Reader
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.OpenAPI

@Path("/authorization-grants")
@Tag(name = "Authorization Grants", description = "Manage user authorization grants")
class AuthorizationGrantDoc {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List all authorization grants",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successful list retrieval",
                content = [Content(schema = Schema(implementation = AuthorizationGrant::class))]
            )
        ]
    )
    fun list(): List<AuthorizationGrant> = emptyList()

}

fun generateOpenApiSpec(): String {
    val openApi: OpenAPI = Reader().read(setOf(AuthorizationGrantDoc::class.java))
    return Yaml.mapper().writeValueAsString(openApi)
}
