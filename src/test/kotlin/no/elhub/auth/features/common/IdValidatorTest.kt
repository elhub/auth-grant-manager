package no.elhub.auth.features.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.validateId
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithRelationships

class ValidatorsTest : FunSpec({

    context("validateId") {
        test("Should return a valid UUID when given a valid UUID string") {
            val id = "d81e5bf2-8a0c-4348-a788-2a3fab4e77d6"
            val result = validateId(id)
            result.isRight() shouldBe true
            result.getOrNull() shouldNotBe null
        }

        test("Should return an ApiError.BadRequest when given an invalid UUID string") {
            val id = "invalid-uuid"
            val result = validateId(id)
            result.isLeft() shouldBe true
            result.swap().getOrNull() shouldNotBe null
        }

        test("Should return an ApiError.BadRequest when given a null or blank string") {
            val id = null
            val result = validateId(id)
            result.isLeft() shouldBe true
            result.swap().getOrNull() shouldNotBe null
        }
    }
})
