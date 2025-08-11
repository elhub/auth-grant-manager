package no.elhub.auth.grantmanager.domain.valueobjects

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private const val OLDEST_POSSIBLE_CONSUMER_YEARS: Long = 200
private const val YOUNGEST_POSSIBLE_CONSUMER_YEARS: Long = 18
private const val SSN_BIRTHDATE_FORMAT = "ddMMyy"
private const val SSN_LENGTH = 11

@JvmInline
value class SocialSecurityNumber(private val ssnStr: String) {
    init {
        require(ssnStr.length == SSN_LENGTH) {
            "SSN must be $SSN_LENGTH characters long"
        }

        val birthdateStr = ssnStr.take(SSN_BIRTHDATE_FORMAT.length)

        var birthdate: LocalDate? = null

        try {
            birthdate = LocalDate.parse(birthdateStr, DateTimeFormatter.ofPattern(SSN_BIRTHDATE_FORMAT))
        } catch(dtpe: DateTimeParseException) {
            throw IllegalArgumentException("$birthdateStr is not a valid date", dtpe)
        }

        val now = Instant.now()
        val youngestBirthdate = now.minus(Duration.of(YOUNGEST_POSSIBLE_CONSUMER_YEARS, ChronoUnit.YEARS))
        val oldestBirthdate = now.minus(Duration.of(OLDEST_POSSIBLE_CONSUMER_YEARS, ChronoUnit.YEARS))

        require(Instant.from(birthdate) in oldestBirthdate..youngestBirthdate) {
            "Birthdate $birthdate not within acceptable bounds ($oldestBirthdate - $youngestBirthdate)"
        }
    }
}
