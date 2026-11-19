package no.elhub.auth.v1.acceptance.documents.frameworkagreement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.elhub.auth.v1.acceptance.support.DocumentClient
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.acceptanceLifecycle
import no.elhub.auth.v1.acceptance.support.atApprovalTime
import no.elhub.auth.v1.acceptance.support.caller
import no.elhub.auth.v1.acceptance.support.customerOrganization
import no.elhub.auth.v1.acceptance.support.exampleApprover
import no.elhub.auth.v1.acceptance.support.exampleSupplier
import no.elhub.auth.v1.acceptance.support.instant
import no.elhub.auth.v1.acceptance.support.osloStartOfDay
import no.elhub.auth.v1.acceptance.support.shouldBeWithin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val grantValidFrom get() = osloStartOfDay(daysFromNow = 30)
private val grantValidTo get() = osloStartOfDay(daysFromNow = 395)

class FrameworkAgreementDocumentAcceptanceTest : FunSpec({
    lateinit var documentApiClient: DocumentClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        documentApiClient = it.documentApiClient
        externalServices = it.externalServices
    }

    context("Documents / EnergySupplierFrameworkAgreement / create") {
        test("Creation rejects a past start") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = osloStartOfDay(-1),
                    validTo = grantValidTo,
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidFrom must be in the future")
        }

        test("Creation rejects a past end") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = null,
                    validTo = osloStartOfDay(-1),
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidTo must be in the future")
        }

        test("Creation rejects equal start and end") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = grantValidFrom,
                    validTo = grantValidFrom,
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidFrom must be before requestedGrantValidTo")
        }

        test("Creation rejects an end before the start") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = grantValidTo,
                    validTo = grantValidFrom,
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidFrom must be before requestedGrantValidTo")
        }

        test("A valid document is created as Pending with the type's fixed scope") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    externalReference = "framework-agreement-2026-001",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveField("data.attributes.status", "Pending")
                .shouldHaveField("data.attributes.documentType", "EnergySupplierFrameworkAgreement")
                .shouldHaveField("data.attributes.externalReference", "framework-agreement-2026-001")
                .shouldHaveJson(
                    "data.attributes.requestedScopes",
                    """
                    [
                      {
                        "resourceType": "MeteringPointContract",
                        "appliesTo": [],
                        "allowedChanges": [{ "attribute": "type", "value": ["EndUser", "EnergySupplier"] }]
                      }
                    ]
                    """,
                )
        }

        test("The document must be signed within 28 days") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val response = documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)

            val validity = response.instant("data.attributes.validTo") - response.instant("data.attributes.createdAt")
            validity.shouldBeWithin(28.days, 1.minutes)
        }
    }

    context("Documents / EnergySupplierFrameworkAgreement / sign") {
        test("Signing without requested dates creates a grant from the signing instant with no expiry") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val signer = exampleApprover.copy(nationalIdentityNumber = "02916297702")
            val approvalTime = instant(osloStartOfDay(2)) + 1.minutes
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.authPersonsService.stubPersonByNationalIdentityNumber(person = signer)
            externalServices.pdpService.stubAgentApproverDecision(approver = signer, requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal, validFrom = null, validTo = null),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )
            atApprovalTime(approvalTime) {
                documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
            }.shouldHaveStatus(HttpStatusCode.NoContent)

            val document = documentApiClient.get(id = documentId, by = energySupplier.caller())
            val grant = atApprovalTime(approvalTime) {
                documentApiClient.getGrant(
                    id = document.singleRelatedId("authorizationGrants"),
                    by = energySupplier.caller(),
                )
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "PersistentGrant")
                .shouldHaveField("data.attributes.status", "Active")
                .shouldHaveField("data.attributes.validTo", null)
            grant.instant("data.attributes.validFrom") shouldBe approvalTime
        }

        test("Signing after the requested start moves the grant start to the signing instant") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val signer = exampleApprover.copy(nationalIdentityNumber = "02916297702")
            val approvalTime = instant(osloStartOfDay(2)) + 1.minutes
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.authPersonsService.stubPersonByNationalIdentityNumber(person = signer)
            externalServices.pdpService.stubAgentApproverDecision(approver = signer, requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = osloStartOfDay(1),
                    validTo = null,
                ),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )
            atApprovalTime(approvalTime) {
                documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
            }.shouldHaveStatus(HttpStatusCode.NoContent)

            val document = documentApiClient.get(id = documentId, by = energySupplier.caller())
            val grant = atApprovalTime(approvalTime) {
                documentApiClient.getGrant(
                    id = document.singleRelatedId("authorizationGrants"),
                    by = energySupplier.caller(),
                )
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "PersistentGrant")
                .shouldHaveField("data.attributes.status", "Active")
                .shouldHaveField("data.attributes.validTo", null)
            grant.instant("data.attributes.validFrom") shouldBe approvalTime
        }

        test("Signing after the requested end is rejected and leaves the document Pending without grants") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val signer = exampleApprover.copy(nationalIdentityNumber = "02916297702")
            val approvalTime = instant(osloStartOfDay(2)) + 1.minutes
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.authPersonsService.stubPersonByNationalIdentityNumber(person = signer)
            externalServices.pdpService.stubAgentApproverDecision(approver = signer, requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = null,
                    validTo = osloStartOfDay(1),
                ),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )
            atApprovalTime(approvalTime) {
                documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
            }.shouldBeError(HttpStatusCode.UnprocessableEntity, "requested grant validity period")

            documentApiClient.get(id = documentId, by = energySupplier.caller())
                .shouldHaveField("data.attributes.status", "Pending")
                .shouldHaveJson("data.relationships.authorizationGrants.data", "[]")
        }

        test("Signing creates one persistent grant with the type's fixed scope for the requested period") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val signer = exampleApprover.copy(nationalIdentityNumber = "02916297702")
            val validFrom = grantValidFrom
            val validTo = grantValidTo

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.authPersonsService.stubPersonByNationalIdentityNumber(person = signer)
            externalServices.pdpService.stubAgentApproverDecision(approver = signer, requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal, validFrom = validFrom, validTo = validTo),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.NoContent)

            val document = documentApiClient.get(id = documentId, by = energySupplier.caller())
                .shouldHaveField("data.attributes.status", "Approved")
                .shouldHaveField("data.relationships.approvedBy.data.type", "Person")
            val grantId = document.singleRelatedId("authorizationGrants")
            val grant = documentApiClient.getGrant(id = grantId, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK)
            val createdAt = requireNotNull(grant.field("data.attributes.createdAt"))
            val updatedAt = requireNotNull(grant.field("data.attributes.updatedAt"))
            val metaCreatedAt = requireNotNull(grant.field("meta.createdAt"))
            grant.shouldHaveJson(
                "",
                """
                {
                  "data": {
                    "id": "$grantId",
                    "type": "PersistentGrant",
                    "attributes": {
                      "status": "Pending",
                      "scopes": [{
                        "capability": "Write",
                        "resourceType": "MeteringPointContract",
                        "appliesTo": [],
                        "allowedChanges": [{ "attribute": "type", "value": ["EndUser", "EnergySupplier"] }]
                      }],
                      "validFrom": "$validFrom",
                      "validTo": "$validTo",
                      "createdAt": "$createdAt",
                      "updatedAt": "$updatedAt"
                    },
                    "relationships": {
                      "principal": { "data": { "type": "Organization", "id": "${principal.number}" } },
                      "grantedBy": { "data": { "type": "Organization", "id": "${principal.number}" } },
                      "grantedTo": { "data": { "type": "MarketEntity", "id": "${energySupplier.gln}" } },
                      "approvedBy": { "data": { "type": "Person", "id": "${signer.id}" } },
                      "source": { "data": { "type": "AuthorizationDocument", "id": "$documentId" } }
                    },
                    "links": { "self": "/access/v1/authorization-grants/$grantId" }
                  },
                  "links": { "self": "/access/v1/authorization-grants/$grantId" },
                  "meta": { "createdAt": "$metaCreatedAt" }
                }
                """,
            )
            grant.instant("data.attributes.validFrom") shouldBe instant(validFrom)
            grant.instant("data.attributes.validTo") shouldBe instant(validTo)
        }
    }
})
