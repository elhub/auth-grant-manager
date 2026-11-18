package no.elhub.auth.v1.acceptance.documents.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import no.elhub.auth.v1.acceptance.documents.frameworkagreement.frameworkAgreementDocumentPayload
import no.elhub.auth.v1.acceptance.support.ApiResponse
import no.elhub.auth.v1.acceptance.support.Caller
import no.elhub.auth.v1.acceptance.support.DocumentClient
import no.elhub.auth.v1.acceptance.support.EnergySupplier
import no.elhub.auth.v1.acceptance.support.ExternalServiceStubs
import no.elhub.auth.v1.acceptance.support.Organization
import no.elhub.auth.v1.acceptance.support.Person
import no.elhub.auth.v1.acceptance.support.acceptanceLifecycle
import no.elhub.auth.v1.acceptance.support.at
import no.elhub.auth.v1.acceptance.support.caller
import no.elhub.auth.v1.acceptance.support.text

/**
 * Rules that hold for every document type. A framework agreement is only the concrete document used to exercise them;
 * type-specific behavior lives in the per-type specs.
 */
class CommonDocumentBehaviorAcceptanceTest : FunSpec({
    lateinit var documentApiClient: DocumentClient
    lateinit var externalServices: ExternalServiceStubs

    acceptanceLifecycle {
        documentApiClient = it.documentApiClient
        externalServices = it.externalServices
    }

    context("Documents / client identity") {
        test("POST without a token is rejected") {
            val principal = Organization(number = "999888771")

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = Caller.Anonymous,
            )
                .shouldBeError(HttpStatusCode.Unauthorized, "Missing or invalid token.")
        }

        test("POST by a market entity without a sender GLN is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")

            externalServices.pdp.authenticate(supplier = energySupplier)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller().copy(senderGln = null),
            ).shouldBeError(HttpStatusCode.Forbidden, "Forbidden")
        }

        test("POST by a market entity claiming another sender GLN is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019")
            val principal = Organization(number = "999888771")

            externalServices.pdp.authenticate(supplier = energySupplier)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller().copy(senderGln = "0128000000599"),
            ).shouldBeError(HttpStatusCode.Forbidden, "Forbidden")
        }
    }

    context("Documents / requestedOf delegation") {
        test("requestedOf allowed to act for requestedPrincipal: created with requestedBy, requestedPrincipal and requestedOf kept distinct") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = principal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldHaveStatus(HttpStatusCode.Created)
                .shouldHaveJson("data.relationships.requestedBy", """{ "data": { "type": "MarketEntity", "id": "${energySupplier.gln}" } }""")
                .shouldHaveJson("data.relationships.requestedPrincipal", """{ "data": { "type": "Organization", "id": "${principal.number}" } }""")
                .shouldHaveJson("data.relationships.requestedOf", """{ "data": { "type": "Organization", "id": "${requestedOf.number}" } }""")
        }

        test("requestedOf without permission to act for requestedPrincipal is rejected, and no document is created") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            // No representative permission given.

            val before = documentApiClient.list(by = energySupplier.caller()).ids()
            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "RequestedOf is not allowed to represent RequestedPrincipal")
            documentApiClient.list(by = energySupplier.caller()).ids() shouldBe before
        }

        test("requestedOf allowed to act for another principal is rejected") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val otherPrincipal = Organization(number = "999888774")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = otherPrincipal)

            documentApiClient.create(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            ).shouldBeError(HttpStatusCode.UnprocessableEntity, "RequestedOf is not allowed to represent RequestedPrincipal")
        }
    }

    context("Documents / read access") {
        test("GET returns the document to the supplier that created it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.id", documentId)
        }

        test("GET returns the document to someone acting for the requestedPrincipal") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val principalRepresentative = Person(id = "d6784082-8344-e733-e053-02058d0a6752")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)
            externalServices.pdp.authenticate(person = principalRepresentative, onBehalfOf = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            documentApiClient.get(id = documentId, by = principalRepresentative.caller(onBehalfOf = principal)).shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.id", documentId)
        }

        test("GET rejects a supplier that is not party to the document") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val otherEnergySupplier = EnergySupplier(organizationNumber = "999888772", gln = "0128000000599")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.pdp.authenticate(supplier = otherEnergySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            documentApiClient.get(id = documentId, by = otherEnergySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The agent is not allowed to access this resource")
        }

        test("LIST includes the document for the supplier that created it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            documentApiClient.list(by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids() shouldContain documentId
        }

        test("LIST excludes the document for a supplier that is not party to it") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val otherEnergySupplier = EnergySupplier(organizationNumber = "999888772", gln = "0128000000599")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.pdp.authenticate(supplier = otherEnergySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )

            documentApiClient.list(by = otherEnergySupplier.caller()).shouldHaveStatus(HttpStatusCode.OK).ids() shouldNotContain documentId
        }
    }

    context("Documents / signing") {
        test("A signer the PDP allows to approve for requestedOf on behalf of requestedPrincipal approves the document") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = principal)
            externalServices.persons.person(person = signer)
            externalServices.pdp.allowApprover(approver = signer, requestedOf = requestedOf, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.NoContent)

            documentApiClient.get(id = documentId, by = energySupplier.caller())
                .shouldHaveField("data.attributes.status", "Approved")
                .shouldHaveField("data.relationships.approvedBy.data.type", "Person")
            documentApiClient.getGrant(
                id = documentApiClient.grantIds(documentId = documentId, by = energySupplier.caller()).single(),
                by = energySupplier.caller(),
            )
                .shouldHaveStatus(HttpStatusCode.OK)
                .shouldHaveField("data.relationships.grantedTo.data.id", energySupplier.gln)
                .shouldHaveField("data.relationships.principal.data.id", principal.number)
                .shouldHaveField("data.relationships.source.data.id", documentId)
        }

        test("A signer without approver permission is rejected; the document stays Pending without grants") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = principal)
            externalServices.persons.person(person = signer)
            // No approver permission given.

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The signer does not match the document's requestedOf agent.")
            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
            documentApiClient.grantIds(documentId = documentId, by = energySupplier.caller()).shouldBeEmpty()
        }

        test("Approver permission for another principal does not allow signing this document") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val otherPrincipal = Organization(number = "999888774")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = principal)
            externalServices.persons.person(person = signer)
            externalServices.pdp.allowApprover(approver = signer, requestedOf = requestedOf, principal = otherPrincipal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The signer does not match the document's requestedOf agent.")
            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("Approver permission for another requestedOf does not allow signing this document") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val requestedOf = Organization(number = "999888773", name = "Example Accounting AS")
            val otherRequestedOf = Organization(number = "999888775")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = requestedOf, principal = principal)
            externalServices.persons.person(person = signer)
            externalServices.pdp.allowApprover(approver = signer, requestedOf = otherRequestedOf, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = requestedOf),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The signer does not match the document's requestedOf agent.")
            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("Only the supplier that created the document can upload the signed PDF") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val otherEnergySupplier = EnergySupplier(organizationNumber = "999888772", gln = "0128000000599")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.pdp.authenticate(supplier = otherEnergySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = otherEnergySupplier.caller())
                .shouldBeError(HttpStatusCode.Forbidden, "The authorized agent is not permitted to confirm this document.")
            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("An unsigned PDF is rejected; the document stays Pending") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )
            val unsignedPdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller())

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = unsignedPdf, by = energySupplier.caller())
                .shouldHaveStatus(HttpStatusCode.UnprocessableEntity)
            documentApiClient.get(id = documentId, by = energySupplier.caller()).shouldHaveField("data.attributes.status", "Pending")
        }

        test("An approved document cannot be signed again") {
            val energySupplier = EnergySupplier(organizationNumber = "999888770", gln = "3004300000019", name = "Example Energy Supplier")
            val principal = Organization(number = "999888771", name = "Example Customer AS")
            val signer = Person(id = "d6784082-8344-e733-e053-02058d0a6752", nationalIdentityNumber = "02916297702")

            externalServices.pdp.authenticate(supplier = energySupplier)
            externalServices.organizations.balanceSupplier(supplier = energySupplier)
            externalServices.organizations.organization(organization = principal)
            externalServices.pdp.allowRepresentative(requestedOf = principal, principal = principal)
            externalServices.persons.person(person = signer)
            externalServices.pdp.allowApprover(approver = signer, requestedOf = principal, principal = principal)

            val documentId = documentApiClient.createPending(
                payload = frameworkAgreementDocumentPayload(requestedPrincipal = principal, requestedOf = principal),
                by = energySupplier.caller(),
            )
            val signedPdf = documentApiClient.signWithBankId(
                pdf = documentApiClient.downloadPdf(id = documentId, by = energySupplier.caller()),
                signer = signer,
            )
            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller()).shouldHaveStatus(HttpStatusCode.NoContent)

            documentApiClient.uploadSignedPdf(id = documentId, signedPdf = signedPdf, by = energySupplier.caller())
                .shouldBeError(HttpStatusCode.Conflict, "Only Pending documents can be confirmed.")
            documentApiClient.grantIds(documentId = documentId, by = energySupplier.caller()) shouldHaveSize 1
        }
    }
})

private fun ApiResponse.ids(): List<String?> = json.at("data")!!.jsonArray.map { it.text("id") }

private suspend fun DocumentClient.grantIds(documentId: String, by: Caller): List<String> =
    (get(documentId, by).shouldHaveStatus(HttpStatusCode.OK).json.at("data.relationships.authorizationGrants.data") as? JsonArray)
        .orEmpty().map { it.text("id")!! }
