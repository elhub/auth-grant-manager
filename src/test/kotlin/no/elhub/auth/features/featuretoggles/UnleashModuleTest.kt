package no.elhub.auth.features.featuretoggles

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class UnleashModuleTest : FunSpec({
    test("provides FakeUnleash through Ktor DI when Unleash is not configured") {
        testApplication {
            environment {
                config = MapApplicationConfig()
            }
            application {
                install(DI)
                unleashModule()
                routing {
                    get("/unleash-type") {
                        val unleash = dependencies.resolve<Unleash>()
                        call.respondText(unleash::class.simpleName.orEmpty())
                    }
                }
            }

            client.get("/unleash-type").bodyAsText() shouldBe FakeUnleash::class.simpleName
        }
    }
})
