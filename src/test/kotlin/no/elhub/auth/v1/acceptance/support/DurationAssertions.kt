package no.elhub.auth.v1.acceptance.support

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

fun Duration.shouldBeWithin(expected: Duration, tolerance: Duration) {
    withClue("Expected duration $expected ± $tolerance, but got $this") {
        ((this - expected).absoluteValue < tolerance) shouldBe true
    }
}
