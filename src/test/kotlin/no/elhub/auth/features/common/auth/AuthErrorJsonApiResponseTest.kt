package no.elhub.auth.features.common.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class AuthErrorJsonApiResponseTest : FunSpec({

    data class Expectation(val status: HttpStatusCode, val code: String, val title: String, val detail: String)

    listOf(
        AuthError.MissingAuthorizationHeader to Expectation(
            status = HttpStatusCode.Unauthorized,
            code = "missing_authorization",
            title = "Missing authorization",
            detail = "Bearer token is required in the Authorization header."
        ),
        AuthError.InvalidAuthorizationHeader to Expectation(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_authorization_header",
            title = "Invalid authorization header",
            detail = "Authorization header must use the Bearer scheme."
        ),
        AuthError.MissingSenderGlnHeader to Expectation(
            status = HttpStatusCode.BadRequest,
            code = "missing_sender_gln",
            title = "Missing SenderGLN header",
            detail = "SenderGLN header is required for authorization."
        ),
        AuthError.InvalidToken to Expectation(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_token",
            title = "Invalid token",
            detail = "Token could not be verified."
        ),
        AuthError.ActingFunctionNotSupported to Expectation(
            status = HttpStatusCode.Forbidden,
            code = "unsupported_party_type",
            title = "Unsupported party type",
            detail = "The party type you are authorized as is not supported for this endpoint."
        )
    ).forEach { (error, expected) ->
        test("toApiErrorResponse maps ${error::class.simpleName} to ${expected.status}") {
            val (status, response) = error.toAuthErrorResponse()

            status shouldBe expected.status
            response.errors.size shouldBe 1
            response.errors.first().apply {
                this.status shouldBe expected.status.value.toString()
                title shouldBe expected.title
                detail shouldBe expected.detail
                code shouldBe expected.code
            }
        }
    }

    listOf(
        AuthError.ValidationInfoMissing,
        AuthError.ActingGlnMissing,
        AuthError.ActingFunctionMissing,
        AuthError.UnexpectedError,
        AuthError.UnknownError
    ).forEach { error ->
        test("toApiErrorResponse maps ${error::class.simpleName} to InternalServerError") {
            val (status, response) = error.toAuthErrorResponse()

            status shouldBe HttpStatusCode.InternalServerError
            response.errors.size shouldBe 1
            response.errors.first().apply {
                this.status shouldBe HttpStatusCode.InternalServerError.value.toString()
                title shouldBe "Internal authorization error"
                detail shouldBe "An internal error occurred."
                code shouldBe "internal_authorization_error"
            }
        }
    }
})
