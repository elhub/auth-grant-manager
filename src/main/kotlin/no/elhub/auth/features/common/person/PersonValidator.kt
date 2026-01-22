package no.elhub.auth.features.common.person

const val NIN_LENGTH = 11
const val NIN_CHECKSUM_POS_1 = 9
const val NIN_CHECKSUM_POS_2 = 10
val ninChecks = listOf(
    intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2),
    intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
)

/**
 * isNinValid validates strictly the incoming nin
 * Rules applied:
 * - must be non-null and exactly 11 characters
 * - uses the two-step Mod11 checksum algorithm defined by ninChecks
 * - the computed checksums are compared against the 10th and 11th digits
 *
 * Note:
 * - The function expects a string of digits; non-digit characters will throw on digitToInt()
 * - In the Mod11 algorithm a checksum result of 10 is considered invalid and will not match any digit (0-9)
 */
fun isNinValid(nin: String?): Boolean {
    if (nin.isNullOrBlank()) return false
    if (nin.length != NIN_LENGTH) return false
    if (!nin.all { it.isDigit() }) return false
    return isValidNinChecksum(nin, ninChecks[0]) == nin[NIN_CHECKSUM_POS_1].digitToInt() &&
        isValidNinChecksum(nin, ninChecks[1]) == nin[NIN_CHECKSUM_POS_2].digitToInt()
}

private fun isValidNinChecksum(nin: String, factors: IntArray): Int {
    val sum = factors.indices.sumOf { i -> nin[i].digitToInt() * factors[i] }
    val checksum = 11 - (sum % 11)
    return if (checksum == 11) 0 else checksum
}
