package no.elhub.devxp.services.ping

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.elhub.devxp.services.ping.PingService

class PingServiceTest : DescribeSpec({
    describe("PingService") {
        val pingService = PingService()

        it("should return pong when ping is called") {
            pingService.ping() shouldBe "pong"
        }
    }
})
