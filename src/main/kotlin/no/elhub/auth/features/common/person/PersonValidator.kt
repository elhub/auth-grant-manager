package no.elhub.auth.features.common.person

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Control digits K1 and K2 are calculated using the official Norwegian modulus-11 algorithm
 * for national identity numbers
 */
private val W1 = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
private val W2 = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

/**
 * Validates Norwegian national identity number using the current
 * rules in effect before 2032 (https://www.skatteetaten.no/deling/folkeregisteret/pid/)
 *
 * This method is inspired by how Skatteetaten defines nin:
 * https://www.skatteetaten.no/person/folkeregister/identitetsnummer-og-elektronisk-id/fodselsnummer/
 * And also how other systems in Elhub handle validating the nin
 *
 * Note:
 * From 2032, Norway will introduce new nin series and updated control digit rules. Existing identity numbers
 * will remain valid, but new validation logic may be required for numbers issued after that
 */
fun isNinValid(nin: String?): Boolean {
    if (nin.isNullOrBlank() || nin.length != 11) return false
    if (!nin.all { it.isDigit() }) return false

    // 1) validate birthdate (day, month, year inferred from individual number)
    val birthYear = resolveYear(nin) ?: return false
    if (!isValidDate(nin, birthYear)) return false

    // validate control digits (modulus-11)
    val k1 = calculateK1(nin) ?: return false
    if (k1 != nin[9].digitToInt()) return false

    // validate control digits (modulus-11)
    val k2 = calculateK2(nin, k1) ?: return false
    return k2 == nin[10].digitToInt()
}

/**
 * This function resolves the full birth year assuming the system
 * only needs to support persons born between 1900-2039
 *
 * Note:
 * Persons born before 1900 are intentionally not supported
 */
private fun resolveYear(nin: String): Int? {
    val yearPart = nin.substring(4, 6).toInt()

    // extract the individual numbers - these are the numbers that distinguishes
    // people born the same day and indicates birth century
    val individual = nin.substring(6, 9).toInt()

    // 1900-1999
    if (individual in 0..499 || individual in 900..999) {
        return 1900 + yearPart
    }

    // 2000-2039
    if (individual in 500..999 && yearPart in 0..39) {
        return 2000 + yearPart
    }

    return null
}

private fun isValidDate(nin: String, year: Int): Boolean {
    val day = nin.substring(0, 2).toInt()
    val month = nin.substring(2, 4).toInt()

    return try {
        LocalDate.of(year, month, day)
        true
    } catch (_: DateTimeException) {
        false
    }
}

/**
 * Calculates the first control digit (K1).
 *
 * K1 is calculated from the first 9 digits using fixed weights.
 * If the result is 10, the identity number is invalid.
 * If the result is 11, K1 becomes 0 and the number is valid.
 */
private fun calculateK1(nin: String): Int? {
    val sum = W1.indices.sumOf { i -> nin[i].digitToInt() * W1[i] }
    return when (val control = 11 - (sum % 11)) {
        11 -> 0
        10 -> null
        else -> control
    }
}

/**
 * Calculates the second control digit (K2).
 *
 * K2 is calculated the same way as K1,
 * but also includes the previously calculated K1.
 */
private fun calculateK2(nin: String, k1: Int): Int? {
    val sum =
        (0..8).sumOf { i -> nin[i].digitToInt() * W2[i] } +
            (k1 * W2[9])

    return when (val control = 11 - (sum % 11)) {
        11 -> 0
        10 -> null
        else -> control
    }
}
