package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest

import no.elhub.auth.grantmanager.domain.models.Consumer
import no.elhub.auth.grantmanager.domain.models.MeteringPoint
import no.elhub.auth.grantmanager.domain.models.Supplier
import no.elhub.auth.grantmanager.domain.valueobjects.OrganizationNumber
import no.elhub.auth.grantmanager.domain.valueobjects.SocialSecurityNumber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import io.kotest.core.spec.style.BehaviorSpec as BehaviourSpec

class CreateRequestTests : BehaviourSpec({

    val meterRepo = SqliteMeterRepository()
    val supplierRepo = SqliteSupplierRepository()
    val requestRepo = SqliteRequestRepository()
    val documentRepo = SqliteDocumentRepository()
    val documentGenerator = SignedDocumentGenerator()

    context("Request consent through Elhub") {

        val supplierId: UUID = UUID.randomUUID()
        val consumerId: UUID = UUID.randomUUID()
        val meteringPointId: UUID = UUID.randomUUID()

        Given("that the potential customer is not already a customer with me") {
            meterRepo.create(
                MeteringPoint(
                    meteringPointId,
                    Consumer(consumerId, SocialSecurityNumber("12345678901"), "Jim")
                )
            )
            supplierRepo.create(Supplier(supplierId, OrganizationNumber("123456789"), "TestKraft"))

            When("I request consent") {
                val command = CreateRequestCommand(
                    "",
                    meteringPointId.toString(),
                    Instant.now().plus(1, ChronoUnit.YEARS)
                )

                CreateRequestUseCase(
                    meterRepo,
                    supplierRepo,
                    requestRepo,
                    documentRepo,
                    documentGenerator
                ).invoke(command)

                Then("a change supplier request should be created") {
                    requestRepo.getRequest(meteringPointId, supplierId) shouldNotBe null
                }
            }
        }
    }
})
