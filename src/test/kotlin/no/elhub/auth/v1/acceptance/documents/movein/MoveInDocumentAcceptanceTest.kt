package no.elhub.auth.v1.acceptance.documents.movein

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import no.elhub.auth.v1.acceptance.support.DocumentClient
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.Person
import no.elhub.auth.v1.acceptance.support.acceptanceLifecycle
import no.elhub.auth.v1.acceptance.support.at
import no.elhub.auth.v1.acceptance.support.atApprovalTime
import no.elhub.auth.v1.acceptance.support.caller
import no.elhub.auth.v1.acceptance.support.customerOrganization
import no.elhub.auth.v1.acceptance.support.exampleSupplier
import no.elhub.auth.v1.acceptance.support.instant
import no.elhub.auth.v1.acceptance.support.osloStartOfDay
import no.elhub.auth.v1.acceptance.support.shouldBeWithin
import no.elhub.auth.v1.acceptance.support.text
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinInstant

private val customerMeteringPointIds = listOf("707057500000000001", "707057500000000002")
private const val customerOrganizationElhubInternalId = "internal-org-1"
private val documentSigner = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

class MoveInDocumentAcceptanceTest : FunSpec({
    lateinit var documentApiClient: DocumentClient
    lateinit var externalServices: ExternalServiceStubs

    val oslo = ZoneId.of("Europe/Oslo")
    val signingDate = LocalDate.now(oslo).plusDays(2)
    val signingTime = signingDate.atTime(0, 1).atZone(oslo).toInstant().toKotlinInstant()
    val expectedGrantValidTo = signingDate.plusYears(2).plusDays(1).atStartOfDay(oslo).toInstant().toKotlinInstant()

    acceptanceLifecycle {
        documentApiClient = it.documentApiClient
        externalServices = it.externalServices
    }

    suspend fun stubMoveInDocumentSigning(meteringPointIds: List<String>) {
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

        externalServices.authPersonsService.stubPersonByNationalIdentityNumber(documentSigner)
        externalServices.pdpService.stubAgentApproverDecision(documentSigner, customerOrganization, customerOrganization)
    }

    test("Signing with a move-in date creates one consumable grant per point, valid through the Oslo date two years after signing") {
        stubMoveInDocumentSigning(customerMeteringPointIds)
        val moveInDate = LocalDate.now(oslo).toString()

        val createdDocument = documentApiClient.create(
            payload = moveInDocumentPayload(customerOrganization, customerMeteringPointIds, moveInDate),
            by = exampleSupplier.caller(),
        ).shouldHaveStatus(HttpStatusCode.Created)
            .shouldHaveField("data.attributes.status", "Pending")
            .shouldHaveField("data.attributes.documentType", "MoveInAndChangeOfEnergySupplierForOrganization")
            .shouldHaveField("data.meta.energySupplierName", exampleSupplier.name)
            .shouldHaveField("data.meta.organizationName", customerOrganization.name)
            .shouldHaveField("data.meta.validFrom", moveInDate)
        createdDocument.shouldHaveJson(
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
        (createdDocument.instant("data.attributes.validTo") - createdDocument.instant("data.attributes.createdAt"))
            .shouldBeWithin(28.days, 1.minutes)
        val documentId = createdDocument.field("data.id")!!

        val bankIdSignedPdf = documentApiClient.signWithBankId(
            documentApiClient.downloadPdf(documentId, exampleSupplier.caller()),
            documentSigner,
        )
        atApprovalTime(signingTime) {
            documentApiClient.uploadSignedPdf(documentId, bankIdSignedPdf, exampleSupplier.caller())
        }.shouldHaveStatus(HttpStatusCode.NoContent)

        val grantRelationships = documentApiClient.get(documentId, exampleSupplier.caller())
            .json.at("data.relationships.authorizationGrants.data")!!.jsonArray
        grantRelationships shouldHaveSize 2

        val grantedMeteringPointIds = grantRelationships.map { grantRelationship ->
            val grantResponse = atApprovalTime(signingTime) {
                documentApiClient.getGrant(grantRelationship.text("id")!!, exampleSupplier.caller())
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "ConsumableGrant")
                .shouldHaveField("data.relationships.source.data.id", documentId)
            grantResponse.instant("data.attributes.validFrom") shouldBe signingTime
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

    test("Signing without a move-in date creates a grant with no date constraint, valid through the Oslo date two years after signing") {
        stubMoveInDocumentSigning(listOf(customerMeteringPointIds.first()))

        val documentId = documentApiClient.createPending(
            moveInDocumentPayload(customerOrganization, listOf(customerMeteringPointIds.first()), null),
            exampleSupplier.caller(),
        )

        val bankIdSignedPdf = documentApiClient.signWithBankId(
            documentApiClient.downloadPdf(documentId, exampleSupplier.caller()),
            documentSigner,
        )
        atApprovalTime(signingTime) {
            documentApiClient.uploadSignedPdf(documentId, bankIdSignedPdf, exampleSupplier.caller())
        }.shouldHaveStatus(HttpStatusCode.NoContent)

        val grantId = documentApiClient.get(documentId, exampleSupplier.caller()).singleRelatedId("authorizationGrants")
        val grant = atApprovalTime(signingTime) {
            documentApiClient.getGrant(grantId, exampleSupplier.caller())
        }.shouldHaveStatus(HttpStatusCode.OK)
        grant.instant("data.attributes.validFrom") shouldBe signingTime
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

    test("More than 50 metering points is rejected") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        val meteringPointIds = (1..51).map { "707057500000000${it.toString().padStart(3, '0')}" }

        documentApiClient.create(
            payload = moveInDocumentPayload(customerOrganization, meteringPointIds, null),
            by = exampleSupplier.caller(),
        ).shouldBeError(HttpStatusCode.UnprocessableEntity, "at most 50 metering points")
    }

    test("Creating a move-in document is rejected when the organization already has write access to one requested metering point") {
        externalServices.pdpService.stubAuthInfo(supplier = exampleSupplier)
        externalServices.pdpService.stubAgentRepresentativeDecision(customerOrganization, customerOrganization)
        externalServices.registerOrganizationIdentification(customerOrganization.number, customerOrganizationElhubInternalId)
        externalServices.pdpService.stubMeteringPointWriteAccess(
            customerOrganizationElhubInternalId,
            customerMeteringPointIds,
            unauthorizedMeteringPointIds = listOf(customerMeteringPointIds.last()),
        )

        documentApiClient.create(
            payload = moveInDocumentPayload(customerOrganization, customerMeteringPointIds, null),
            by = exampleSupplier.caller(),
        ).shouldBeError(HttpStatusCode.UnprocessableEntity, "already owns at least one of the requested metering points")
    }
})
