package no.elhub.auth.features.documents.route

import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest

import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.documents.module
import no.elhub.auth.module as applicationModule
import io.ktor.server.config.MapApplicationConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder

fun ApplicationTestBuilder.setUpAuthorizationDocumentsTestApplication() {
    client = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    application {
        applicationModule()
        testBusinessProcessesModule()
        commonModule()
        module()
    }

    environment {
        config = MapApplicationConfig(
            "ktor.database.username" to "app",
            "ktor.database.password" to "app",
            "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
            "ktor.database.driverClass" to "org.postgresql.Driver",
            "pdfGenerator.mustacheResourcePath" to "templates",
            "pdfGenerator.useTestPdfNotice" to "true",
            "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
            "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
            "pdfSigner.vault.key" to "test-key",
            "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            "pdfSigner.certificate.bankIdIdRoot" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION,
            "featureToggle.enableEndpoints" to "true",
            "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
            "pdp.baseUrl" to "http://localhost:8085"
        )
    }
}


val examplePostBody = JsonApiCreateDocumentRequest(
    data = JsonApiRequestResourceObjectWithMeta(
        type = "AuthorizationDocument",
        attributes = CreateDocumentRequestAttributes(
            documentType = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson
        ),
        meta = CreateDocumentMeta(
            requestedBy = PartyIdentifier(
                idType = PartyIdentifierType.GlobalLocationNumber,
                idValue = "0107000000021"
            ),
            requestedFrom = PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = REQUESTED_FROM_NIN
            ),
            requestedTo = PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = REQUESTED_TO_NIN
            ),
            requestedFromName = "Hillary Orr",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "quaerendum",
            balanceSupplierName = "Jami Wade",
            balanceSupplierContractName = "Selena Chandler"
        )
    )
)
const val REQUESTED_FROM_NIN = "02916297702"
const val REQUESTED_TO_NIN = "14810797496"
