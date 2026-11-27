package no.elhub.auth.v1.acceptance.documents.supplierchange

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import no.elhub.auth.v1.acceptance.support.DocumentClient
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.acceptanceLifecycle
import no.elhub.auth.v1.acceptance.support.at
import no.elhub.auth.v1.acceptance.support.atApprovalTime
import no.elhub.auth.v1.acceptance.support.caller
import no.elhub.auth.v1.acceptance.support.customerOrganization
import no.elhub.auth.v1.acceptance.support.exampleApprover
import no.elhub.auth.v1.acceptance.support.exampleSupplier
import no.elhub.auth.v1.acceptance.support.instant
import no.elhub.auth.v1.acceptance.support.text
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.toKotlinInstant

private val meteringPointIds = listOf("707057500000000001", "707057500000000002")
private const val CUSTOMER_INTERNAL_ID = "internal-org-1"

class ChangeOfEnergySupplierDocumentAcceptanceTest : FunSpec({
    lateinit var documentApiClient: DocumentClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        documentApiClient = it.documentApiClient
        externalServices = it.externalServices
    }

    test("Signing creates one EnergySupplier-only consumable grant per point, valid through the Oslo date two years after signing") {
        val oslo = ZoneId.of("Europe/Oslo")
        val signingDate = LocalDate.now(oslo).plusDays(2)
        val signingTime = signingDate.atTime(0, 1).atZone(oslo).toInstant().toKotlinInstant()
        val expectedGrantValidTo = signingDate.plusYears(2).plusDays(1).atStartOfDay(oslo).toInstant().toKotlinInstant()
        val signer = exampleApprover.copy(nationalIdentityNumber = "02916297702")
        val supplier = exampleSupplier.caller()

        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, CUSTOMER_INTERNAL_ID)
        externalServices.pdpService.stubMeteringPointWriteAccess(CUSTOMER_INTERNAL_ID, meteringPointIds, unauthorizedMeteringPointIds = emptyList())
        externalServices.registerMeteringPoint(meteringPointIds[0], postalCode = "0123", meterNumber = "meter-1")
        externalServices.registerMeteringPoint(meteringPointIds[1], postalCode = "0456", meterNumber = "meter-2")
        externalServices.organizationsService.stubPartyByIdAndType(exampleSupplier)
        externalServices.organizationsService.stubBrregOrganizationByNumber(customerOrganization)
        externalServices.authPersonsService.stubPersonByNationalIdentityNumber(signer)
        externalServices.pdpService.stubAgentApproverDecision(signer, customerOrganization, customerOrganization)

        val documentId = documentApiClient.createPending(supplierChangeDocumentPayload(meteringPointIds), supplier)
        val signedPdf = documentApiClient.signWithBankId(documentApiClient.downloadPdf(documentId, supplier), signer)
        atApprovalTime(signingTime) {
            documentApiClient.uploadSignedPdf(documentId, signedPdf, supplier)
        }.shouldHaveStatus(HttpStatusCode.NoContent)

        val document = documentApiClient.get(documentId, supplier).shouldHaveStatus(HttpStatusCode.OK)
            .shouldHaveField("data.attributes.documentType", "ChangeOfEnergySupplierForOrganization")
            .shouldHaveField("data.meta.energySupplierName", exampleSupplier.name)
            .shouldHaveField("data.meta.organizationName", customerOrganization.name)
        document.shouldHaveJson(
            "data.attributes.requestedScopes",
            """
            [{
              "resourceType": "MeteringPointContract",
              "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["${meteringPointIds[0]}", "${meteringPointIds[1]}"] }],
              "allowedChanges": [{ "attribute": "type", "value": ["EnergySupplier"] }]
            }]
            """,
        ).shouldHaveJson(
            "data.meta.requestedMeteringPoints",
            """
            [
              { "id": "${meteringPointIds[0]}", "address": { "postalCode": "0123" }, "meterNumber": "meter-1" },
              { "id": "${meteringPointIds[1]}", "address": { "postalCode": "0456" }, "meterNumber": "meter-2" }
            ]
            """,
        )

        val grantRelationships = document.json.at("data.relationships.authorizationGrants.data")!!.jsonArray
        grantRelationships shouldHaveSize meteringPointIds.size
        val grantedMeteringPointIds = grantRelationships.map { relationship ->
            val grant = atApprovalTime(signingTime) {
                documentApiClient.getGrant(relationship.text("id")!!, supplier)
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "ConsumableGrant")
                .shouldHaveField("data.relationships.source.data.id", documentId)
                .shouldHaveField("data.relationships.grantedTo.data.id", exampleSupplier.gln)
            grant.instant("data.attributes.validFrom") shouldBe signingTime
            grant.instant("data.attributes.validTo") shouldBe expectedGrantValidTo

            val pointId = grant.field("data.attributes.scopes[0].appliesTo[0].value[0]")!!
            grant.shouldHaveJson(
                "data.attributes.scopes",
                """
                [{
                  "capability": "Write",
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["$pointId"] }],
                  "allowedChanges": [{ "attribute": "type", "value": ["EnergySupplier"] }]
                }]
                """,
            )
            pointId
        }
        grantedMeteringPointIds.toSet() shouldBe meteringPointIds.toSet()
    }

    test("Creating a supplier-change document is rejected when the organization cannot write to one requested metering point") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, CUSTOMER_INTERNAL_ID)
        externalServices.pdpService.stubMeteringPointWriteAccess(
            CUSTOMER_INTERNAL_ID,
            meteringPointIds,
            unauthorizedMeteringPointIds = listOf(meteringPointIds.last()),
        )

        documentApiClient.create(supplierChangeDocumentPayload(meteringPointIds), exampleSupplier.caller())
            .shouldBeError(HttpStatusCode.UnprocessableEntity, "does not own all requested metering points")
    }

    test("More than 50 metering points is rejected") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        val tooManyPoints = (1..51).map { "707057500000000${it.toString().padStart(3, '0')}" }

        documentApiClient.create(supplierChangeDocumentPayload(tooManyPoints), exampleSupplier.caller())
            .shouldBeError(HttpStatusCode.UnprocessableEntity, "at most 50 metering points")
    }
})

private fun supplierChangeDocumentPayload(pointIds: List<String>): String {
    val pointIdsJson = pointIds.joinToString(", ") { "\"$it\"" }
    return """
        {
          "data": {
            "type": "AuthorizationDocument",
            "attributes": {
              "documentType": "ChangeOfEnergySupplierForOrganization",
              "requestedScopes": [{
                "resourceType": "MeteringPointContract",
                "appliesTo": [{ "attribute": "meteringPoint.id", "value": [$pointIdsJson] }]
              }]
            },
            "meta": {
              "requestedPrincipal": "${customerOrganization.number}",
              "requestedOf": "${customerOrganization.number}",
              "language": "nb"
            }
          }
        }
    """
}
