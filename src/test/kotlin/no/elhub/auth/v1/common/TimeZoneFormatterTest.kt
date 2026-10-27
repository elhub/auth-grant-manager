package no.elhub.auth.v1.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

class TimeZoneFormatterTest : FunSpec({
    test("converts instants to Oslo local time and date across midnight") {
        val instant = Instant.parse("2026-07-01T22:30:00Z")

        nowOslo(instant) shouldBe LocalDateTime.parse("2026-07-02T00:30:00")
        todayOslo(instant) shouldBe LocalDate.parse("2026-07-02")
        startOfTodayOslo(instant) shouldBe Instant.parse("2026-07-01T22:00:00Z")
    }

    listOf(
        Triple("2026-01-15", "2026-01-14T23:00:00Z", "2026-01-15T22:59:59Z"),
        Triple("2026-07-15", "2026-07-14T22:00:00Z", "2026-07-15T21:59:59Z"),
        Triple("2026-03-29", "2026-03-28T23:00:00Z", "2026-03-29T21:59:59Z"),
        Triple("2026-10-25", "2026-10-24T22:00:00Z", "2026-10-25T22:59:59Z"),
    ).forEach { (date, start, end) ->
        test("Oslo day boundaries for $date account for daylight saving time") {
            LocalDate.parse(date).startOfDayOslo() shouldBe Instant.parse(start)
            LocalDate.parse(date).endOfDayOslo() shouldBe Instant.parse(end)
            startOfTodayOslo(Instant.parse(end)) shouldBe Instant.parse(start)
        }
    }
})
