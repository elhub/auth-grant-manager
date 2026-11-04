package no.elhub.auth.v1.api.documents.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.accept
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.V1_DOCUMENTS_PATH
import no.elhub.auth.v1.features.documents.ConfirmDocumentHandler
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

class ConfirmDocumentRouteTest : FunSpec({
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val documentId = "123e4567-e89b-12d3-a456-426614174000"
    val pdfMaxBytes = 1024 * 1024
    val handler = mockk<ConfirmDocumentHandler>()

    beforeTest { clearMocks(handler) }

    data class UploadCase(
        val description: String,
        val id: String = documentId,
        val contentType: ContentType = ContentType.Application.Pdf,
        val bytes: ByteArray = "%PDF-1.7\nexample document".toByteArray(),
        val expectedStatus: HttpStatusCode,
    )

    listOf(
        UploadCase(
            description = "unsupported content type",
            contentType = ContentType.Application.Json,
            expectedStatus = HttpStatusCode.UnsupportedMediaType,
        ),
        UploadCase(
            description = "invalid document ID",
            id = "not-a-uuid",
            expectedStatus = HttpStatusCode.BadRequest,
        ),
        UploadCase(
            description = "empty upload",
            bytes = byteArrayOf(),
            expectedStatus = HttpStatusCode.BadRequest,
        ),
        UploadCase(
            description = "oversized upload",
            bytes = ByteArray(pdfMaxBytes + 1),
            expectedStatus = HttpStatusCode.PayloadTooLarge,
        ),
        UploadCase(
            description = "valid upload",
            expectedStatus = HttpStatusCode.NoContent,
        ),
        UploadCase(
            description = "upload at the size limit",
            bytes = ByteArray(pdfMaxBytes),
            expectedStatus = HttpStatusCode.NoContent,
        ),
    ).forEach { case ->
        test("PUT /{id}.pdf returns ${case.expectedStatus.value} for ${case.description}") {
            coEvery { handler(any(), any(), any()) } returns mockk()
            testApplication {
                setupAppWith(authorizedParty) {
                    route(V1_DOCUMENTS_PATH) { route(handler) }
                }
                val pdfClient = createClient {}

                val response = pdfClient.put("$V1_DOCUMENTS_PATH/${case.id}.pdf") {
                    accept(ContentType.Application.Pdf)
                    contentType(case.contentType)
                    setBody(case.bytes)
                }

                response.status shouldBe case.expectedStatus
                if (case.expectedStatus == HttpStatusCode.NoContent) {
                    response.bodyAsText() shouldBe ""
                    coVerify(exactly = 1) {
                        handler(UUID.fromString(documentId), authorizedParty, match { it.contentEquals(case.bytes) })
                    }
                } else {
                    response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                    val body = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText())
                    body.errors.single().status shouldBe case.expectedStatus.value.toString()
                    coVerify(exactly = 0) { handler(any(), any(), any()) }
                }
            }
        }
    }
})
