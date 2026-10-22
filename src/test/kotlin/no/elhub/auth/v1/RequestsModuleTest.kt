package no.elhub.auth.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.elhub.auth.common.configureErrorHandling
import no.elhub.auth.v0.config.configureAuthorization
import no.elhub.auth.v0.config.configureSerialization
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.repositories.ExposedGrantRepository
import no.elhub.auth.v1.repositories.ExposedPartyRepository
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.PartyRepository

class RequestsModuleTest : FunSpec({
    test("does not register v1 request routes when disabled") {
        testApplication {
            environment {
                config = MapApplicationConfig("v1.enabled" to "false")
            }
            application {
                requestsModule()
            }

            client.post(V1_REQUESTS_PATH).status shouldBe HttpStatusCode.NotFound
        }
    }

    test("registers the protected POST $V1_REQUESTS_PATH route when v1 is enabled") {
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "v1.enabled" to "true",
                    "pdp.baseUrl" to "http://localhost/pdp",
                )
            }
            application {
                configureSerialization()
                configureErrorHandling()
                configureAuthorization()
                dependencies {
                    provide<PartyService> { mockk<PartyService>() }
                    provide<PartyRepository> { ExposedPartyRepository() }
                    provide<GrantRepository> { ExposedGrantRepository(resolve()) }
                }
                requestsModule()
            }

            client.post(V1_REQUESTS_PATH).status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
