package no.elhub.auth.v1.acceptance.requests.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import no.elhub.auth.v1.acceptance.requests.frameworkagreement.frameworkAgreementRequestPayload
import no.elhub.auth.v1.acceptance.support.ApiResponse
import no.elhub.auth.v1.acceptance.support.Caller
import no.elhub.auth.v1.acceptance.support.EnergySupplier
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.Organization
import no.elhub.auth.v1.acceptance.support.Person
import no.elhub.auth.v1.acceptance.support.RequestClient
import no.elhub.auth.v1.acceptance.support.acceptanceLifecycle
import no.elhub.auth.v1.acceptance.support.at
import no.elhub.auth.v1.acceptance.support.caller
import no.elhub.auth.v1.acceptance.support.text

/**
 * Rules that hold for every request type. A framework agreement is only the concrete request used to exercise them;
 * type-specific behavior lives in the per-type specs.
 */
class CommonRequestBehaviorAcceptanceTest : FunSpec({
    lateinit var requestApiClient: RequestClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        requestApiClient = it.requestApiClient
        externalServices = it.externalServices
    }

    context("Requests / client identity") {
        test("POST without a token is rejected") {
            val principal = Organization(number = "999888771")

            requestApiClient.create(payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal), by = Caller.Anonymous)
                .shouldBeError(HttpStatusCode.Unauthorized, "Missing or invalid token.")
        }

        test("POST by a market entity without a sender GLN is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller().copy(senderGln = null),
            ).shouldBeError(HttpStatusCode.Forbidden, "Forbidden")
        }

        test("POST by a market entity claiming another sender GLN is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller().copy(senderGln = "0128000000599"),
            ).shouldBeError(HttpStatusCode.Forbidden, "Forbidden")
        }

        test("PATCH by an end user claiming another organization is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val otherOrganization = Organization(number = "999888772")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = otherOrganization))
                .shouldBeError(HttpStatusCode.Forbidden, "Forbidden")
            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("LIST by an end user acting as a person, without an organization header, is allowed") {
            val person = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(person = person)

            requestApiClient.list(by = person.caller()).shouldHaveStatus(HttpStatusCode.OK)
        }
    }

    context("Requests / requestedOf delegation") {
        test("requestedOf allowed to act for requestedPrincipal: created with requestedBy, requestedPrincipal and requestedOf kept distinct") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = principal)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveJson("data.relationships.requestedBy", """{ "data": { "type": "MarketEntity", "id": "${energySupplier.gln}" } }""")
                .shouldHaveJson("data.relationships.requestedPrincipal", """{ "data": { "type": "Organization", "id": "${principal.number}" } }""")
                .shouldHaveJson("data.relationships.requestedOf", """{ "data": { "type": "Organization", "id": "${requestedOf.number}" } }""")
        }

        test("requestedOf without permission to act for requestedPrincipal is rejected, and no request is created") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")
            val requestedOf = Organization(number = "999888773")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            // No representative permission given.

            val before = requestApiClient.list(by = energySupplier.caller()).ids()
            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "RequestedOf is not allowed to represent RequestedPrincipal")
            requestApiClient.list(by = energySupplier.caller()).ids() shouldBe before
        }

        test("requestedOf allowed to act for another principal is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")
            val otherPrincipal = Organization(number = "999888774")
            val requestedOf = Organization(number = "999888773")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = otherPrincipal)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "RequestedOf is not allowed to represent RequestedPrincipal")
        }
    }

    context("Requests / redirectUri") {
        test("Without a redirectUri the request is created without asking Ediel") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            // Ediel is not stubbed: an unexpected call fails the test.

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveField("data.attributes.redirectUri", null)
        }

        test("A redirectUri on the domain registered in Ediel is accepted and stored") {
            val energySupplier = EnergySupplier(
                organizationNumber = "999888770",
                gln = "3004300000019",
                name = "Example Energy Supplier",
                redirectUrl = "https://supplier.example",
            )
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.edielService.stubPartyRedirectUrl(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "https://supplier.example/authorization/return",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveField("data.attributes.redirectUri", "https://supplier.example/authorization/return")
        }

        test("A redirectUri on a subdomain of the domain registered in Ediel is accepted") {
            val energySupplier = EnergySupplier(
                organizationNumber = "999888770",
                gln = "3004300000019",
                name = "Example Energy Supplier",
                redirectUrl = "https://supplier.example",
            )
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.edielService.stubPartyRedirectUrl(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "https://login.supplier.example/return",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
        }

        test("A redirectUri on another domain is rejected") {
            val energySupplier = EnergySupplier(
                organizationNumber = "999888770",
                gln = "3004300000019",
                name = "Example Energy Supplier",
                redirectUrl = "https://supplier.example",
            )
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.edielService.stubPartyRedirectUrl(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "https://phishing.example/return",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.UnprocessableEntity)
                .shouldHaveField("errors[0].title", "Validation error")
                .shouldHaveField("errors[0].detail", "Redirect URI does not match the URI registered in Ediel")
        }

        test("A redirectUri that is not https is rejected before asking Ediel") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            // Rejected by input validation, before Ediel or any other lookup.

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "http://supplier.example/return",
                ),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "redirectUri must use the https scheme")
        }

        test("A redirectUri from a supplier with no redirect URL in Ediel is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.edielService.stubPartyRedirectUrlNotFound(supplier = energySupplier)

            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "https://supplier.example/return",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.UnprocessableEntity)
                .shouldHaveField("errors[0].title", "Validation error")
                .shouldHaveField("errors[0].detail", "No redirect URL is registered in Ediel for the requesting party")
        }

        test("When Ediel is unavailable the request is not created") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.edielService.stubPartyRedirectUrlUnavailable(supplier = energySupplier)

            val before = requestApiClient.list(by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids()
            requestApiClient.create(
                payload = frameworkAgreementRequestPayload(
                    requestedPrincipal = principal,
                    requestedOf = principal,
                    redirectUri = "https://supplier.example/return",
                ),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.InternalServerError)
                .shouldHaveField("errors[0].title", "Internal server error")
                .shouldHaveField("errors[0].detail", "An internal server error occurred")
            requestApiClient.list(by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids() shouldBe before
        }
    }

    context("Requests / read access") {
        test("GET returns the request to the supplier that created it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.id", requestId)
        }

        test("GET returns the request to someone acting for the requestedPrincipal") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val principalRepresentative = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = principalRepresentative, onBehalfOf = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.get(id = requestId, by = principalRepresentative.caller(onBehalfOf = principal)).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.id", requestId)
        }

        test("GET rejects a supplier that is not party to the request") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val otherEnergySupplier = EnergySupplier(organizationNumber = "999888772", gln = "0128000000599")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.pdpService.stubAuthInfo(supplier = otherEnergySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.get(id = requestId, by = otherEnergySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The agent is not allowed to access this resource")
        }

        test("LIST includes the request for the supplier that created it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.list(by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids() shouldContain requestId
        }

        test("LIST excludes the request for a supplier that is not party to it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val otherEnergySupplier = EnergySupplier(organizationNumber = "999888772", gln = "0128000000599")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.pdpService.stubAuthInfo(supplier = otherEnergySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.list(by = otherEnergySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids() shouldNotContain requestId
        }
    }

    context("Requests / approval") {
        test("A person the PDP allows to approve for requestedOf on behalf of requestedPrincipal can approve") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = requestedOf)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = requestedOf, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = requestedOf)).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.attributes.status", "Approved")
                .shouldHaveField("data.relationships.decidedBy.data.id", approver.id)
            val grant = requestApiClient.getGrant(
                id = requestApiClient.grantIds(requestId = requestId, by = energySupplier.caller()).single(),
                by = energySupplier.caller(),
            )
                .shouldHaveStatus(HttpStatusCode.OK)
            grant.shouldHaveField("data.relationships.grantedTo.data.id", energySupplier.gln)
                .shouldHaveField("data.relationships.principal.data.id", principal.number)
                .shouldHaveField("data.relationships.approvedBy.data.type", "Person")
                .shouldHaveField("data.relationships.source.data.id", requestId)
        }

        test("A person without approver permission cannot approve; the request stays Pending without grants") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val person = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = person, onBehalfOf = requestedOf)
            // No approver permission given.

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = person.caller(onBehalfOf = requestedOf))
                .shouldBeError(HttpStatusCode.Forbidden, "Only the requestedOf agent may approve or reject this AuthorizationRequest.")
            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
            requestApiClient.grantIds(requestId = requestId, by = energySupplier.caller()).shouldBeEmpty()
        }

        test("Approver permission for another principal does not allow approving this request") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val otherPrincipal = Organization(number = "999888774")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = requestedOf)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = requestedOf, principal = otherPrincipal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = requestedOf))
                .shouldBeError(HttpStatusCode.Forbidden, "Only the requestedOf agent may approve or reject this AuthorizationRequest.")
            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("Approver permission for another requestedOf does not allow approving this request") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val otherRequestedOf = Organization(number = "999888775")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = requestedOf, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = requestedOf)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = otherRequestedOf, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = requestedOf))
                .shouldBeError(HttpStatusCode.Forbidden, "Only the requestedOf agent may approve or reject this AuthorizationRequest.")
            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("An approved request cannot be approved again") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )
            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal)).shouldHaveStatus(HttpStatusCode.OK)

            requestApiClient.approve(id = requestId, by = approver.caller(onBehalfOf = principal))
                .shouldBeError(HttpStatusCode.UnprocessableEntity, "AuthorizationRequest must be in 'Pending' status to update.")
            requestApiClient.grantIds(requestId = requestId, by = energySupplier.caller()) shouldHaveSize 1
        }
    }

    context("Requests / rejection") {
        test("A permitted person can reject; the request gets no grant") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val approver = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = approver, onBehalfOf = principal)
            externalServices.pdpService.stubAgentApproverDecision(approver = approver, requestedOf = principal, principal = principal)

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.reject(id = requestId, by = approver.caller(onBehalfOf = principal)).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.attributes.status", "Rejected")
            requestApiClient.grantIds(requestId = requestId, by = energySupplier.caller()).shouldBeEmpty()
        }

        test("A person without approver permission cannot reject; the request stays Pending") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val person = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdpService.stubAuthInfo(supplier = energySupplier)
            externalServices.organizationsService.stubPartyByIdAndType(supplier = energySupplier)
            externalServices.organizationsService.stubBrregOrganizationByNumber(organization = principal)
            externalServices.pdpService.stubAgentRepresentativeDecision(requestedOf = principal, principal = principal)
            externalServices.pdpService.stubAuthInfo(person = person, onBehalfOf = principal)
            // No approver permission given.

            val requestId = requestApiClient.createPending(
                payload = frameworkAgreementRequestPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            requestApiClient.reject(id = requestId, by = person.caller(onBehalfOf = principal))
                .shouldBeError(HttpStatusCode.Forbidden, "Only the requestedOf agent may approve or reject this AuthorizationRequest.")
            requestApiClient.get(id = requestId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }
    }
})

private fun ApiResponse.ids(): List<String?> = json.at("data")!!.jsonArray.map { it.text("id") }

private suspend fun RequestClient.grantIds(requestId: String, by: Caller): List<String> =
    (get(requestId, by).shouldHaveStatus(HttpStatusCode.OK).json.at("data.relationships.authorizationGrants.data") as? JsonArray)
        .orEmpty().map { it.text("id")!! }
