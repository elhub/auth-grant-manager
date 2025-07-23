package no.elhub.auth.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.jaxrs2.Reader
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.models.OpenAPI
import no.elhub.auth.features.grants.AuthorizationGrantDocGenerator
import java.io.File

@OpenAPIDefinition(
    info = Info(
        title = "Authorization Grant Manager",
        version = "0.1.0",
        description = "The authorization grant manager handles the authorization grants for Elhub's APIs.",
        contact = Contact(name = "team-devxp"),
        license = License(name = "MIT", url = "https://github.com/elhub/auth-grant-manager?tab=MIT-1-ov-file"),
    )
)
class OpenApiDocConfig

fun writeOpenApiSpecToFile(openApiYamlPath: String) {
    val yaml = generateOpenApiSpec()
    File(openApiYamlPath).writeText(yaml)
}

fun generateOpenApiSpec(): String {
    val openApi: OpenAPI = Reader().read(
        setOf(
            OpenApiDocConfig::class.java,
            AuthorizationGrantDocGenerator::class.java
        )
    )

    openApi.openapi = "3.1.1"
    return Yaml.mapper().writeValueAsString(openApi)
}
