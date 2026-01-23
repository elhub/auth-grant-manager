package no.elhub.auth.features.common.person

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PersonValidatorTest : FunSpec({

    test("Should return true when person is born between 1900 and 1999") {
        val bornIn1990 = "01019012480"
        val check = isNinValid(bornIn1990)
        check shouldBe true
    }

    test("Should return true when person is born between 2000 and 2039") {
        val bornIn2005 = "15080550186"
        val check = isNinValid(bornIn2005)
        check shouldBe true
    }

    test("Should return true when person is born in leap year") {
        val bornInLeapYear = "29020450051"
        val check = isNinValid(bornInLeapYear)
        check shouldBe true
    }

    test("Should return true when person is born at the edge of the valid ranges for individual numbers") {
        val list = listOf(
            "01017500011", // individualNumber 000
            "01018849980", // individualNumber 499
            "01010150074", // individual number 500
            "01019999943" // individual number 999
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
        val check = isNinValid("1508055018612")
        check shouldBe false
    }

    test("Should return false when nin is not numeric") {
        val check = isNinValid("15080550B86")
        check shouldBe false
    }

    test("Should return false when day in date is invalid") {
        val check = isNinValid("00099331444")
        check shouldBe false
    }

    test("Should return false when month in date is invalid") {
        val check = isNinValid("12139331444")
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
