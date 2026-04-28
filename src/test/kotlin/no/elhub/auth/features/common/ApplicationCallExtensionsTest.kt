package no.elhub.auth.features.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import no.elhub.auth.setupAppWith
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

@Serializable
private data class TestDto(val name: String, val value: Int)

class ApplicationCallExtensionsTest : FunSpec({

    fun setupTestRoute(builder: io.ktor.server.routing.Routing.() -> Unit) = builder

    test("receiveEither returns Right with parsed body when JSON is valid") {
        testApplication {
            setupAppWith {
                post("/test") {
                    call.receiveEither<TestDto>().fold(
                        ifLeft = { error ->
                            val (status, body) = error.toApiErrorResponse()
                            call.respond(status, body)
                        },
                        ifRight = { dto -> call.respond(HttpStatusCode.OK, dto) }
                    )
                }
            }
            val response = client.post("/test") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"hello","value":42}""")
            }
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("receiveEither returns Left(InvalidFieldValueError) when field has wrong type") {
        testApplication {
            setupAppWith {
                post("/test") {
                    call.receiveEither<TestDto>().fold(
                        ifLeft = { error ->
                            val (status, body) = error.toApiErrorResponse()
                            call.respond(status, body)
                        },
                        ifRight = { dto -> call.respond(HttpStatusCode.OK, dto) }
                    )
                }
            }
            val response = client.post("/test") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"hello","value":"not-an-int"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            val body: JsonApiErrorCollection = response.body()
            body.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "400"
                    title shouldBe "Invalid field value in request body"
                }
            }
        }
    }

    test("receiveEither returns Left(MissingFieldError) when required field is absent") {
        testApplication {
            setupAppWith {
                post("/test") {
                    call.receiveEither<TestDto>().fold(
                        ifLeft = { error ->
                            val (status, body) = error.toApiErrorResponse()
                            call.respond(status, body)
                        },
                        ifRight = { dto -> call.respond(HttpStatusCode.OK, dto) }
                    )
                }
            }
            val response = client.post("/test") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"hello"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            val body: JsonApiErrorCollection = response.body()
            body.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "400"
                    title shouldBe "Missing required field in request body"
                    detail shouldBe "Field '[value]' is missing or invalid"
                }
            }
        }
    }
})
