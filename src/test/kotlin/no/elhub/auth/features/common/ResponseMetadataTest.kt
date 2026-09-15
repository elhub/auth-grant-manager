package no.elhub.auth.features.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.dto.toDocumentResponseMetadata
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.dto.toGrantResponseMetadata
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.dto.toRequestResponseMetadata
import java.util.UUID

class ResponseMetadataTest : FunSpec({
    test("maps allowed request properties") {
        val requestId = UUID.randomUUID()
        val properties = listOf(
            AuthorizationRequestProperty(requestId, "requestedFromName", JsonPrimitive("Ola Normann")),
            AuthorizationRequestProperty(requestId, "redirectURI", JsonPrimitive("https://example.com")),
        )

        properties.toRequestResponseMetadata(
            AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
        ) shouldContainExactly mapOf(
            "requestedFromName" to JsonPrimitive("Ola Normann"),
            "redirectURI" to JsonPrimitive("https://example.com"),
        )
    }

    test("maps request language stored by the create flow") {
        val properties = listOf(
            AuthorizationRequestProperty(UUID.randomUUID(), "language", JsonPrimitive("nb-NO"))
        )

        properties.toRequestResponseMetadata(
            AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
        ) shouldContainExactly mapOf("language" to JsonPrimitive("nb-NO"))
    }

    test("rejects properties not exposed by the request type") {
        val properties = listOf(
            AuthorizationRequestProperty(
                UUID.randomUUID(),
                "meteringPoints",
                buildJsonArray { add(JsonPrimitive("707057500000000001")) },
            )
        )

        shouldThrow<InvalidStoredMetadataException> {
            properties.toRequestResponseMetadata(
                AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
            )
        }
    }

    test("rejects structured values for existing document properties") {
        val properties = listOf(
            AuthorizationDocumentProperty(
                "requestedFromName",
                buildJsonArray { add(JsonPrimitive("Ola Normann")) },
            )
        )

        shouldThrow<InvalidStoredMetadataException> {
            properties.toDocumentResponseMetadata(
                AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson
            )
        }
    }

    test("only move-in types expose moveInDate") {
        val properties = listOf(
            AuthorizationDocumentProperty("moveInDate", JsonPrimitive("2026-09-15"))
        )

        properties.toDocumentResponseMetadata(
            AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson
        ) shouldContainExactly mapOf("moveInDate" to JsonPrimitive("2026-09-15"))

        shouldThrow<InvalidStoredMetadataException> {
            properties.toDocumentResponseMetadata(
                AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson
            )
        }
    }

    test("grant responses only expose known properties") {
        val grantId = UUID.randomUUID()
        val properties = listOf(
            AuthorizationGrantProperty(grantId, "moveInDate", JsonPrimitive("2026-09-15"))
        )

        properties.toGrantResponseMetadata() shouldContainExactly
            mapOf("moveInDate" to JsonPrimitive("2026-09-15"))

        shouldThrow<InvalidStoredMetadataException> {
            listOf(
                AuthorizationGrantProperty(grantId, "internalValue", JsonPrimitive("secret"))
            ).toGrantResponseMetadata()
        }
    }
})
