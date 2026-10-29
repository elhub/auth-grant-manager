package no.elhub.auth.v1.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class OsloCalendarTest : FunSpec({
    test("parses ISO dates and maps timestamps to the Oslo calendar date") {
        "2026-07-01".toOsloDateOrNull() shouldBe LocalDate.parse("2026-07-01")
        "2026-07-01T22:30:00Z".toOsloDateOrNull() shouldBe LocalDate.parse("2026-07-02")
        "2026-07-02T00:30:00+02:00".toOsloDateOrNull() shouldBe LocalDate.parse("2026-07-02")
        "2026-02-30".toOsloDateOrNull() shouldBe null
        "not-a-date".toOsloDateOrNull() shouldBe null
    }

    test("converts instants to Oslo local time and date across midnight") {
        val instant = Instant.parse("2026-07-01T22:30:00Z")

        instant.toOsloLocalDateTime() shouldBe LocalDateTime.parse("2026-07-02T00:30:00")
        instant.toOsloDate() shouldBe LocalDate.parse("2026-07-02")
        instant.startOfDayOslo() shouldBe Instant.parse("2026-07-01T22:00:00Z")
    }

    listOf(
        Triple("2026-01-15", "2026-01-14T23:00:00Z", "2026-01-15T23:00:00Z"),
        Triple("2026-07-15", "2026-07-14T22:00:00Z", "2026-07-15T22:00:00Z"),
        Triple("2026-03-29", "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"),
        Triple("2026-10-25", "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"),
    ).forEach { (date, start, end) ->
        test("Oslo day boundaries for $date account for daylight saving time") {
            LocalDate.parse(date).startOfDayOslo() shouldBe Instant.parse(start)
            LocalDate.parse(date).plus(1, DateTimeUnit.DAY).startOfDayOslo() shouldBe Instant.parse(end)
            (Instant.parse(end) - 1.nanoseconds).startOfDayOslo() shouldBe Instant.parse(start)
            Instant.parse(end).startOfDayOslo() shouldBe Instant.parse(end)
        }
    }
})
