package no.elhub.auth.features.common.person

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/*
 * NOTE: All NIN values used in these tests are fictitious and used solely for testing purposes.
 * They do not correspond to real individuals and should not be used for identification.
 */
class PersonValidatorTest : FunSpec({

    test("Should return true when person is born between 1900 and 1999") {
        val bornIn1981 = "21038140997"
        val check = isNinValid(bornIn1981)
        check shouldBe true
    }

    test("Should return true when person is born between 2000 and 2039") {
        val bornIn2001 = "12010180315"
        val check = isNinValid(bornIn2001)
        check shouldBe true
    }

    test("Should return true when person is born in leap year") {
        val bornInLeapYear = "02012916593"
        val check = isNinValid(bornInLeapYear)
        check shouldBe true
    }

    test("Should return true when person is born at the edge of the valid ranges for individual numbers") {
        val list = listOf(
            "20068200130", // individualNumber 000
            "22011649266", // individualNumber 499
            "04050150122", // individual number 500
            "19106799434" // individual number 999
        )

        for (nin in list) {
            val check = isNinValid(nin)
            check shouldBe true
        }
    }

    test("Should return false when nin is null") {
        val check = isNinValid(null)
        check shouldBe false
    }

    test("Should return false when nin is empty string") {
        val check = isNinValid("")
        check shouldBe false
    }

    test("Should return false when nin only has space") {
        val check = isNinValid(" ")
        check shouldBe false
    }

    test("Should return false when nin is too long") {
        val check = isNinValid("200682001302")
        check shouldBe false
    }

    test("Should return false when nin is not numeric") {
        val check = isNinValid("2006A200130")
        check shouldBe false
    }

    test("Should return false when day in date is invalid") {
        val check = isNinValid("30028140997")
        check shouldBe false
    }

    test("Should return false when month in date is invalid") {
        val check = isNinValid("21998140997")
        check shouldBe false
    }

    test("Should return false when year in date is invalid") {
        val check = isNinValid("29029931444")
        check shouldBe false
    }

    test("Should return false person is born before 1900") {
        val check = isNinValid("12098561234")
        check shouldBe false
    }

    test("Should return false person is born after 2032") {
        val check = isNinValid("01024556789")
        check shouldBe false
    }

    test("Should return when K1 is wrong") {
        val check = isNinValid("12099331426")
        check shouldBe false
    }

    test("Should return false K1 is correct but K2 is wrong") {
        val check = isNinValid("12099331445")
        check shouldBe false
    }
})
