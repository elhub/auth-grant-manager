package no.elhub.auth.v1.features.documents

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication

class ModuleTest : FunSpec({
    test("does not register v1 routes when disabled") {
        testApplication {
            environment {
                config = MapApplicationConfig("v1.enabled" to "false")
            }
            application {
                module()
            }

            client.get(V1_DOCUMENTS_PATH).status shouldBe HttpStatusCode.NotFound
        }
    }
})
