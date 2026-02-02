package no.elhub.auth

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import java.util.UUID

fun String.shouldBeValidUuid() {
    this should beValidUuid()
}

private fun beValidUuid() =
    Matcher<String> { s ->
        val ok =
            try {
                UUID.fromString(s)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        MatcherResult(ok, { "Expected a valid UUID but got '$s'" }, { "Expected not to be a valid UUID" })
    }
