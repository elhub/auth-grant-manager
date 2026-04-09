package no.elhub.auth.features.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Clock

class TimeZoneFormatterTest : FunSpec({

    context("toTimeZoneOffsetString") {
        test("formats using Europe Oslo offset during winter time") {
            val input = OffsetDateTime.parse("2025-01-15T12:34:56Z")

            input.toTimeZoneOffsetString() shouldBe "2025-01-15T13:34:56+01:00"
        }

        test("formats using Europe Oslo offset during summer time") {
            val input = OffsetDateTime.parse("2025-07-15T12:34:56Z")

            input.toTimeZoneOffsetString() shouldBe "2025-07-15T14:34:56+02:00"
        }

        test("truncates fractional seconds from the formatted output") {
            val input = OffsetDateTime.parse("2025-01-15T12:34:56.987654Z")

            input.toTimeZoneOffsetString() shouldBe "2025-01-15T13:34:56+01:00"
        }
    }

    context("currentTimeOslo") {
        test("returns a timestamp in the Europe Oslo offset") {
            val result = currentTimeOslo()

            result.offset shouldBe result.toZonedDateTime().zone.rules.getOffset(result.toInstant())
        }
    }

    context("currentTimeUtc") {
        test("returns a timestamp in UTC") {
            currentTimeUtc().offset shouldBe ZoneOffset.UTC
        }
    }

    context("toTimeZoneOffsetDateTimeAtStartOfDay") {
        test("converts a date to start of day in Europe Oslo during winter time") {
            val input = LocalDate(2025, 1, 15)

            input.toTimeZoneOffsetDateTimeAtStartOfDay() shouldBe OffsetDateTime.parse("2025-01-15T00:00:00+01:00")
        }

        test("converts a date to start of day in Europe Oslo during summer time") {
            val input = LocalDate(2025, 7, 15)

            input.toTimeZoneOffsetDateTimeAtStartOfDay() shouldBe OffsetDateTime.parse("2025-07-15T00:00:00+02:00")
        }
    }

    context("today") {
        test("returns the current UTC date") {
            val expected = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

            todayOslo() shouldBe expected
        }
    }
})
