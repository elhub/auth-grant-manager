package no.elhub.auth.common.api.openapi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication

class ModuleTest : FunSpec({
    listOf("/access/openapi", "/access/v0/openapi").forEach { path ->
        test("serves Swagger UI, specification and schemas at $path") {
            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    module()
                }

                val ui = client.get(path)
                ui.status shouldBe HttpStatusCode.OK
                ui.bodyAsText() shouldContain "SwaggerUIBundle"

                val spec = client.get("$path/openapi.yaml")
                spec.status shouldBe HttpStatusCode.OK
                spec.bodyAsText() shouldBe this::class.java.getResource("/static/openapi.yaml")!!.readText()

                val schema = client.get("$path/schemas/json-api-meta.schema.json")
                schema.status shouldBe HttpStatusCode.OK
                schema.bodyAsText() shouldBe this::class.java.getResource("/static/schemas/json-api-meta.schema.json")!!.readText()
            }
        }
    }
})
