package no.elhub.auth.features.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.requests.AuthorizationRequestRequest
import no.elhub.auth.model.RelationshipLink
import no.elhub.auth.model.RelationshipLink.DataLink

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
            val request = AuthorizationRequestRequest(
                data = AuthorizationRequestRequest.Data(
                    type = "AuthorizationRequest",
                    attributes = AuthorizationRequestRequest.Attributes(
                        requestType = "ChangeOfSupplierConfirmation"
                    ),
                    relationships = AuthorizationRequestRequest.Relations(
                        requestedBy = RelationshipLink(DataLink(id = "d81e5bf2", type = "User")),
                        requestedTo = RelationshipLink(DataLink(id = "d81e5bf3", type = "User"))
                    ),
                    meta = AuthorizationRequestRequest.Meta(contract = "Sample123")
                )
            )
            val result = validateAuthorizationRequest(request)
            result.isRight() shouldBe true
            result.getOrNull() shouldNotBe null
        }

        test("Should return an ApiError.BadRequest when given an invalid requestType") {
            val request = AuthorizationRequestRequest(
                data = AuthorizationRequestRequest.Data(
                    type = "AuthorizationRequest",
                    attributes = AuthorizationRequestRequest.Attributes(
                        requestType = "DummySupplier"
                    ),
                    relationships = AuthorizationRequestRequest.Relations(
                        requestedBy = RelationshipLink(DataLink(id = "d81e5bf2", type = "User")),
                        requestedTo = RelationshipLink(DataLink(id = "d81e5bf3", type = "User"))
                    ),
                    meta = AuthorizationRequestRequest.Meta(contract = "Sample123")
                )
            )
            val result = validateAuthorizationRequest(request)
            result.isLeft() shouldBe true
            result.swap().getOrNull() shouldNotBe null
        }
    }
})
