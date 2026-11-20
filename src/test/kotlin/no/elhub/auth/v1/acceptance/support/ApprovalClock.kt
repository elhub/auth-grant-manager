package no.elhub.auth.v1.acceptance.support

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import no.elhub.auth.v1.common.now
import kotlin.time.Instant

/** Advance only the application's calendar clock; token and signature verification use real time. */
suspend fun <T> atApprovalTime(time: Instant, block: suspend () -> T): T {
    mockkStatic(::now)
    return try {
        every { now() } returns time
        block()
    } finally {
        unmockkStatic(::now)
    }
}
