package no.elhub.auth.v1.acceptance.requests.movein

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
import no.elhub.auth.v1.acceptance.support.osloStartOfDay
import no.elhub.auth.v1.acceptance.support.text
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.toKotlinInstant

private val customerMeteringPointIds = listOf("707057500000000001", "707057500000000002")
private const val customerOrganizationElhubInternalId = "internal-org-1"
private val oslo = ZoneId.of("Europe/Oslo")
private val todayInOslo get() = LocalDate.now(oslo)

class MoveInRequestAcceptanceTest : FunSpec({
    lateinit var requestApiClient: RequestClient
    lateinit var externalServices: ExternalServiceStubs

    val approvalDate = todayInOslo.plusDays(2)
    val approvalTime = approvalDate.atTime(0, 1).atZone(oslo).toInstant().toKotlinInstant()
    val expectedGrantValidTo = approvalDate.plusYears(2).plusDays(1).atStartOfDay(oslo).toInstant().toKotlinInstant()

    acceptanceLifecycle {
        requestApiClient = it.requestApiClient
        externalServices = it.externalServices
    }

    suspend fun stubMoveInRequestApproval(meteringPointIds: List<String>) {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)

        externalServices.organizationsService.stubPartyByIdAndType(exampleSupplier)
        externalServices.organizationsService.stubBrregOrganizationByNumber(customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, customerOrganizationElhubInternalId)
        externalServices.pdpService.stubMeteringPointWriteAccess(
            customerOrganizationElhubInternalId,
            meteringPointIds,
            unauthorizedMeteringPointIds = meteringPointIds,
        )
        meteringPointIds.forEach {
            externalServices.registerMeteringPoint(it, postalCode = "0123", meterNumber = "meter-1")
        }

        externalServices.pdpService.stubAuthInfo(exampleApprover, customerOrganization)
        externalServices.pdpService.stubAgentApproverDecision(exampleApprover, customerOrganization, customerOrganization)
    }

    test("Approval with a move-in date creates one consumable grant per point, valid through the Oslo date two years after approval") {
        stubMoveInRequestApproval(customerMeteringPointIds)
        val moveInDate = todayInOslo.toString()

        val createdRequest = requestApiClient.create(
            moveInRequestPayload(customerOrganization, customerMeteringPointIds, moveInDate),
            exampleSupplier.caller(),
        ).shouldHaveStatus(HttpStatusCode.Created)

        createdRequest.shouldHaveField("data.attributes.requestType", "MoveInAndChangeOfEnergySupplierForOrganization")
            .shouldHaveField("data.meta.energySupplierName", exampleSupplier.name)
            .shouldHaveField("data.meta.organizationName", customerOrganization.name)
            .shouldHaveField("data.meta.moveInDate", moveInDate)
        createdRequest.shouldHaveJson(
            "data.attributes.requestedScopes",
            """
            [{
              "resourceType": "MeteringPointContract",
              "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["${customerMeteringPointIds[0]}", "${customerMeteringPointIds[1]}"] }],
              "allowedChanges": [
                { "attribute": "type", "value": ["EndUser", "EnergySupplier"] },
                { "attribute": "validFrom", "value": ["${osloStartOfDay(0)}"] }
              ]
            }]
            """,
        )

        val requestId = createdRequest.field("data.id")!!
        atApprovalTime(approvalTime) {
            requestApiClient.approve(requestId, exampleApprover.caller(customerOrganization))
        }.shouldHaveStatus(HttpStatusCode.OK)

        val grantRelationships = requestApiClient.get(requestId, exampleSupplier.caller())
            .json.at("data.relationships.authorizationGrants.data")!!.jsonArray
        grantRelationships shouldHaveSize 2

        val grantedMeteringPointIds = grantRelationships.map { grantRelationship ->
            val grantResponse = atApprovalTime(approvalTime) {
                requestApiClient.getGrant(grantRelationship.text("id")!!, exampleSupplier.caller())
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "ConsumableGrant")
                .shouldHaveField("data.relationships.source.data.id", requestId)
                .shouldHaveField("data.relationships.grantedTo.data.id", exampleSupplier.gln)
            grantResponse.instant("data.attributes.validFrom") shouldBe approvalTime
            grantResponse.instant("data.attributes.validTo") shouldBe expectedGrantValidTo

            val grantedMeteringPointId = grantResponse.field("data.attributes.scopes[0].appliesTo[0].value[0]")!!
            grantResponse.shouldHaveJson(
                "data.attributes.scopes",
                """
                [{
                  "capability": "Write",
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["$grantedMeteringPointId"] }],
                  "allowedChanges": [
                    { "attribute": "type", "value": ["EndUser", "EnergySupplier"] },
                    { "attribute": "validFrom", "value": ["${osloStartOfDay(0)}"] }
                  ]
                }]
                """,
            )

            grantedMeteringPointId
        }

        grantedMeteringPointIds.toSet() shouldBe customerMeteringPointIds.toSet()
    }

    test("Approval without a move-in date creates a grant with no date constraint, valid through the Oslo date two years after approval") {
        stubMoveInRequestApproval(listOf(customerMeteringPointIds.first()))

        val requestId = requestApiClient.createPending(
            moveInRequestPayload(customerOrganization, listOf(customerMeteringPointIds.first()), null),
            exampleSupplier.caller(),
        )
        atApprovalTime(approvalTime) {
            requestApiClient.approve(requestId, exampleApprover.caller(customerOrganization))
        }.shouldHaveStatus(HttpStatusCode.OK)

        val grantId = requestApiClient.get(requestId, exampleSupplier.caller()).singleRelatedId("authorizationGrants")
        val grant = atApprovalTime(approvalTime) {
            requestApiClient.getGrant(grantId, exampleSupplier.caller())
        }.shouldHaveStatus(HttpStatusCode.OK)
        grant.instant("data.attributes.validFrom") shouldBe approvalTime
        grant.instant("data.attributes.validTo") shouldBe expectedGrantValidTo
        grant.shouldHaveJson(
            "data.attributes.scopes",
            """
            [{
              "capability": "Write",
              "resourceType": "MeteringPointContract",
              "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["${customerMeteringPointIds.first()}"] }],
              "allowedChanges": [{ "attribute": "type", "value": ["EndUser", "EnergySupplier"] }]
            }]
            """,
        )
    }

    test("A future move-in date is rejected") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)

        requestApiClient.create(
            moveInRequestPayload(customerOrganization, listOf(customerMeteringPointIds.first()), todayInOslo.plusDays(1).toString()),
            exampleSupplier.caller(),
        ).shouldBeError(HttpStatusCode.UnprocessableEntity, "today or in the past")
    }

    test("More than 50 metering points is rejected") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        val meteringPointIds = (1..51).map { "707057500000000${it.toString().padStart(3, '0')}" }

        requestApiClient.create(
            moveInRequestPayload(customerOrganization, meteringPointIds, null),
            exampleSupplier.caller(),
        ).shouldBeError(HttpStatusCode.UnprocessableEntity, "at most 50 metering points")
    }

    test("Creating a move-in request is rejected when the organization already has write access to one requested metering point") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, customerOrganizationElhubInternalId)
        externalServices.pdpService.stubMeteringPointWriteAccess(
            customerOrganizationElhubInternalId,
            customerMeteringPointIds,
            unauthorizedMeteringPointIds = listOf(customerMeteringPointIds.last()),
        )

        requestApiClient.create(
            moveInRequestPayload(customerOrganization, customerMeteringPointIds, null),
            exampleSupplier.caller(),
        ).shouldBeError(HttpStatusCode.UnprocessableEntity, "already owns at least one of the requested metering points")
    }
})
