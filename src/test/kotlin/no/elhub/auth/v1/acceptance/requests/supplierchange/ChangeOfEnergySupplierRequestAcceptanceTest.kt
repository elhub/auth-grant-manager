package no.elhub.auth.v1.acceptance.requests.supplierchange

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.RequestClient
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

class ChangeOfEnergySupplierRequestAcceptanceTest : FunSpec({
    lateinit var requestApiClient: RequestClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        requestApiClient = it.requestApiClient
        externalServices = it.externalServices
    }

    test("Approval creates one EnergySupplier-only consumable grant per point, valid through the Oslo date two years after approval") {
        val oslo = ZoneId.of("Europe/Oslo")
        val approvalDate = LocalDate.now(oslo).plusDays(2)
        val approvalTime = approvalDate.atTime(0, 1).atZone(oslo).toInstant().toKotlinInstant()
        val expectedGrantValidTo = approvalDate.plusYears(2).plusDays(1).atStartOfDay(oslo).toInstant().toKotlinInstant()
        val supplier = exampleSupplier.caller()
        val approver = exampleApprover.caller(customerOrganization)

        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, CUSTOMER_INTERNAL_ID)
        externalServices.pdpService.stubMeteringPointWriteAccess(CUSTOMER_INTERNAL_ID, meteringPointIds, unauthorizedMeteringPointIds = emptyList())
        externalServices.registerMeteringPoint(meteringPointIds[0], postalCode = "0123", meterNumber = "meter-1")
        externalServices.registerMeteringPoint(meteringPointIds[1], postalCode = "0456", meterNumber = "meter-2")
        externalServices.organizationsService.stubPartyByIdAndType(exampleSupplier)
        externalServices.organizationsService.stubBrregOrganizationByNumber(customerOrganization)
        externalServices.pdpService.stubAuthInfo(exampleApprover, customerOrganization)
        externalServices.pdpService.stubAgentApproverDecision(exampleApprover, customerOrganization, customerOrganization)

        val requestId = requestApiClient.createPending(supplierChangeRequestPayload(meteringPointIds), supplier)
        atApprovalTime(approvalTime) {
            requestApiClient.approve(requestId, approver)
        }.shouldHaveStatus(HttpStatusCode.OK)

        val request = requestApiClient.get(requestId, supplier).shouldHaveStatus(HttpStatusCode.OK)
            .shouldHaveField("data.attributes.requestType", "ChangeOfEnergySupplierForOrganization")
            .shouldHaveField("data.meta.energySupplierName", exampleSupplier.name)
            .shouldHaveField("data.meta.organizationName", customerOrganization.name)
        request.shouldHaveJson(
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

        val grantRelationships = request.json.at("data.relationships.authorizationGrants.data")!!.jsonArray
        grantRelationships shouldHaveSize meteringPointIds.size
        val grantedMeteringPointIds = grantRelationships.map { relationship ->
            val grant = atApprovalTime(approvalTime) {
                requestApiClient.getGrant(relationship.text("id")!!, supplier)
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "ConsumableGrant")
                .shouldHaveField("data.relationships.source.data.id", requestId)
                .shouldHaveField("data.relationships.grantedTo.data.id", exampleSupplier.gln)
            grant.instant("data.attributes.validFrom") shouldBe approvalTime
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

    test("Creating a supplier-change request is rejected when the organization cannot write to one requested metering point") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, CUSTOMER_INTERNAL_ID)
        externalServices.pdpService.stubMeteringPointWriteAccess(
            CUSTOMER_INTERNAL_ID,
            meteringPointIds,
            unauthorizedMeteringPointIds = listOf(meteringPointIds.last()),
        )

        requestApiClient.create(supplierChangeRequestPayload(meteringPointIds), exampleSupplier.caller())
            .shouldBeError(HttpStatusCode.UnprocessableEntity, "does not own all requested metering points")
    }

    test("More than 50 metering points is rejected") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        val tooManyPoints = (1..51).map { "707057500000000${it.toString().padStart(3, '0')}" }

        requestApiClient.create(supplierChangeRequestPayload(tooManyPoints), exampleSupplier.caller())
            .shouldBeError(HttpStatusCode.UnprocessableEntity, "at most 50 metering points")
    }
})

private fun supplierChangeRequestPayload(pointIds: List<String>): String {
    val pointIdsJson = pointIds.joinToString(", ") { "\"$it\"" }
    return """
        {
          "data": {
            "type": "AuthorizationRequest",
            "attributes": {
              "requestType": "ChangeOfEnergySupplierForOrganization",
              "requestedScopes": [{
                "resourceType": "MeteringPointContract",
                "appliesTo": [{ "attribute": "meteringPoint.id", "value": [$pointIdsJson] }]
              }]
            },
            "meta": { "requestedPrincipal": "${customerOrganization.number}", "requestedOf": "${customerOrganization.number}" }
          }
        }
    """
}
