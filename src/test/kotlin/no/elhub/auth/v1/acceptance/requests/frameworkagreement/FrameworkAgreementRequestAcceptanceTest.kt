package no.elhub.auth.v1.acceptance.requests.frameworkagreement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.RequestClient
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

class FrameworkAgreementRequestAcceptanceTest : FunSpec({
    lateinit var requestApiClient: RequestClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        requestApiClient = it.requestApiClient
        externalServices = it.externalServices
    }

    context("Requests / EnergySupplierFrameworkAgreement / create") {
        test("Creating a framework agreement request returns Pending with EndUser and EnergySupplier contract changes") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    externalReference = "framework-agreement-2026-001",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveField("data.attributes.status", "Pending")
                .shouldHaveField("data.attributes.requestType", "EnergySupplierFrameworkAgreement")
                .shouldHaveField("data.attributes.externalReference", "framework-agreement-2026-001")
                .shouldHaveField("data.attributes.textReference", "v1")
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

        test("Creating a framework agreement request preserves the requested grant validity period") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val validFrom = grantValidFrom
            val validTo = grantValidTo

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val response = requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal, validFrom = validFrom, validTo = validTo),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)

            response.instant("data.attributes.requestedGrantValidFrom") shouldBe instant(validFrom)
            response.instant("data.attributes.requestedGrantValidTo") shouldBe instant(validTo)
        }

        test("A framework agreement request expires 28 days after creation") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val response = requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)

            val validity = response.instant("data.attributes.validTo") - response.instant("data.attributes.createdAt")
            validity.shouldBeWithin(28.days, 1.minutes)
        }

        test("Creating a framework agreement request rejects supplied scopes") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            requestApiClient.create(
                payload = """
                {
                  "data": {
                    "type": "AuthorizationRequest",
                    "attributes": {
                      "requestType": "EnergySupplierFrameworkAgreement",
                      "requestedScopes": [],
                      "requestedGrantValidFrom": "$grantValidFrom",
                      "requestedGrantValidTo": "$grantValidTo"
                    },
                    "meta": { "requestedPrincipal": "${principal.number}", "requestedOf": "${principal.number}" }
                  }
                }
                """,
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedScopes is not supported")
        }

        test("Creating a framework agreement request rejects requested grant validity starting in the past") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = osloStartOfDay(daysFromNow = -1),
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidFrom must be in the future")
        }

        test("Creating a framework agreement request rejects requested grant validity starting after it ends") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validTo = grantValidFrom,
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "requestedGrantValidFrom must be before requestedGrantValidTo")
        }
    }

    context("Requests / EnergySupplierFrameworkAgreement / approve") {
        test("Without requested grant validity, approval creates a grant starting at approval with no expiry") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val approver = exampleApprover
            val approvalTime = instant(osloStartOfDay(2)) + 1.minutes
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = null,
                    validTo = null,
                ),
                by = energySupplier.caller(),
            )
            atApprovalTime(approvalTime) {
                requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal))
            }.shouldHaveStatus(HttpStatusCode.OK)

            val request = requestApiClient.get(id = requestId, by = energySupplier.caller())
            val grant = atApprovalTime(approvalTime) {
                requestApiClient.getGrant(
                    id = request.singleRelatedId("authorizationGrants"),
                    by = energySupplier.caller(),
                )
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "PersistentGrant")
                .shouldHaveField("data.attributes.status", "Active")
                .shouldHaveField("data.attributes.validTo", null)
            grant.instant("data.attributes.validFrom") shouldBe approvalTime
        }

        test("Approving after requested grant validity starts makes the grant start at approval") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val approver = exampleApprover
            val approvalTime = instant(osloStartOfDay(2)) + 1.minutes
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = osloStartOfDay(1),
                    validTo = null,
                ),
                by = energySupplier.caller(),
            )
            atApprovalTime(approvalTime) {
                requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal))
            }.shouldHaveStatus(HttpStatusCode.OK)

            val request = requestApiClient.get(id = requestId, by = energySupplier.caller())
            val grant = atApprovalTime(approvalTime) {
                requestApiClient.getGrant(
                    id = request.singleRelatedId("authorizationGrants"),
                    by = energySupplier.caller(),
                )
            }.shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.type", "PersistentGrant")
                .shouldHaveField("data.attributes.status", "Active")
                .shouldHaveField("data.attributes.validTo", null)
            grant.instant("data.attributes.validFrom") shouldBe approvalTime
        }

        test("Approval after requested grant validity ends is rejected; the request stays Pending with no grants") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val approver = exampleApprover
            val approvalDay = osloStartOfDay(2)
            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    validFrom = null,
                    validTo = osloStartOfDay(1),
                ),
                by = energySupplier.caller(),
            )
            atApprovalTime(instant(approvalDay) + 1.minutes) {
                requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal))
            }.shouldBeError(HttpStatusCode.UnprocessableEntity, "requested grant validity period")

            requestApiClient.get(id = requestId, by = energySupplier.caller())
                .shouldHaveField("data.attributes.status", "Pending")
                .shouldHaveJson("data.relationships.authorizationGrants", "null")
        }

        test("Approval creates one persistent grant for EndUser and EnergySupplier changes during requested grant validity") {
            val energySupplier = exampleSupplier
            val principal = customerOrganization
            val approver = exampleApprover
            val validFrom = grantValidFrom
            val validTo = grantValidTo

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal, validFrom = validFrom, validTo = validTo),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal)).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.attributes.status", "Approved")

            val grantId = requestApiClient.get(id = requestId, by = energySupplier.caller()).singleRelatedId("authorizationGrants")
            val grant = requestApiClient.getGrant(id = grantId, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK)
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
                      "approvedBy": { "data": { "type": "Person", "id": "${approver.id}" } },
                      "source": { "data": { "type": "AuthorizationRequest", "id": "$requestId" } }
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
