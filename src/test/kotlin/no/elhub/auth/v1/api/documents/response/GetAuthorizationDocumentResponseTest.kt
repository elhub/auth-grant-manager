package no.elhub.auth.v1.api.documents.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import kotlin.time.Instant

class GetAuthorizationDocumentResponseTest : FunSpec({
    val party = AuthorizationParty("123456789", PartyType.OrganizationEntity)

    test("factory retains requested grant dates and response exposes them") {
        val validFrom = Instant.parse("2100-01-01T00:00:00Z")
        val validTo = Instant.parse("2101-01-01T00:00:00Z")
        val document = AuthorizationDocument.new(
            documentType = AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
            requestedScopes = emptyList(),
            externalReference = null,
            meta = JsonObject(emptyMap()),
            validTo = validTo,
            requestedBy = party,
            requestedPrincipal = party,
            requestedOf = party,
            pdfBytes = byteArrayOf(),
            requestedGrantValidFrom = validFrom,
            requestedGrantValidTo = validTo,
        )

        document.requestedGrantValidFrom shouldBe validFrom
        document.requestedGrantValidTo shouldBe validTo
        val attributes = document.toGetResponse().data.attributes
        attributes.documentType shouldBe AuthorizationDocumentType.EnergySupplierFrameworkAgreement
        attributes.requestedGrantValidFrom shouldBe validFrom
        attributes.requestedGrantValidTo shouldBe validTo
    }

    test("factory and response default requested grant dates to null") {
        val document = AuthorizationDocument.new(
            documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = emptyList(),
            externalReference = null,
            meta = JsonObject(emptyMap()),
            validTo = Instant.parse("2100-01-01T00:00:00Z"),
            requestedBy = party,
            requestedPrincipal = party,
            requestedOf = party,
            pdfBytes = byteArrayOf(),
        )

        document.requestedGrantValidFrom shouldBe null
        document.requestedGrantValidTo shouldBe null
        document.toGetResponse().data.attributes.requestedGrantValidFrom shouldBe null
        document.toGetResponse().data.attributes.requestedGrantValidTo shouldBe null
    }
})
