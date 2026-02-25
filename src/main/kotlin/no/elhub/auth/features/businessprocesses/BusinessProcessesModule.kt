package no.elhub.auth.features.businessprocesses

import io.ktor.server.application.Application
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.MoveInAndChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.ktor.plugin.koinModule

fun Application.businessProcessesModule() {
    koinModule {
        single {
            ChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = get(),
                personService = get(),
                organisationsService = get(),
                stromprisService = get(),
                validateBalanceSupplierContractName = get(named("validateBalanceSupplierContractName"))
            )
        }
        single {
            MoveInAndChangeOfEnergySupplierBusinessHandler(
                meteringPointsService = get(),
                personService = get(),
                organisationsService = get(),
                stromprisService = get(),
                validateBalanceSupplierContractName = get(named("validateBalanceSupplierContractName"))
            )
        }
        singleOf(::ProxyDocumentBusinessHandler) bind DocumentBusinessHandler::class
        singleOf(::ProxyRequestBusinessHandler) bind RequestBusinessHandler::class
    }
}
