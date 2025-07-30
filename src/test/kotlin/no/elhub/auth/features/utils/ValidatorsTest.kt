package no.elhub.auth.features.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.requests.PostAuthorizationRequestPayload
import no.elhub.auth.features.requests.PostRequestPayloadAttributes
import no.elhub.auth.features.requests.PostRequestPayloadRelationships
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

    context("validateAuthorizationRequest") {
        test("Should return the request when given a valid request") {
            val payload = PostAuthorizationRequestPayload(
                data = JsonApiRequestResourceObjectWithRelationships(
                    type = "AuthorizationRequest",
                    attributes =  PostRequestPayloadAttributes(
                        requestType = "ChangeOfSupplierConfirmation"
                    ),
                    relationships = PostRequestPayloadRelationships(
                        requestedBy = JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(type = "Organization", id = "84797600005")
                        ),
                        requestedFrom = JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(type = "Person", id = "80102512345")
                        )
                    )
                )
            )

            val result = validateAuthorizationRequest(payload)
            result.isRight() shouldBe true
            result.getOrNull() shouldNotBe null
        }

        test("Should return an ApiError.BadRequest when given an invalid requestType") {

            val payload = PostAuthorizationRequestPayload(
                data = JsonApiRequestResourceObjectWithRelationships(
                    type = "AuthorizationRequest",
                    attributes =  PostRequestPayloadAttributes(
                        requestType = "DummySupplier"
                    ),
                    relationships = PostRequestPayloadRelationships(
                        requestedBy = JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(type = "Organization", id = "84797600005")
                        ),
                        requestedFrom = JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(type = "Person", id = "80102512345")
                        )
                    )
                )
            )

            val result = validateAuthorizationRequest(payload)
            result.isLeft() shouldBe true
            result.swap().getOrNull() shouldNotBe null
        }
    }
})
