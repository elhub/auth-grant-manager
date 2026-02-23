package no.elhub.auth.features.businessprocesses

import io.ktor.server.application.Application
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.ediel.EdielEnvironment
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
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
            ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = get(),
                personService = get(),
                organisationsService = get(),
                stromprisService = get(),
                edielService = get(),
                edielEnvironment = get<EdielEnvironment>(named("edielEnvironment")),
                validateBalanceSupplierContractName = get(named("validateBalanceSupplierContractName"))
            )
        }
        single {
            MoveInAndChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = get(),
                personService = get(),
                organisationsService = get(),
                stromprisService = get(),
                edielService = get(),
                edielEnvironment = get<EdielEnvironment>(named("edielEnvironment")),
                validateBalanceSupplierContractName = get(named("validateBalanceSupplierContractName"))
            )
        }
        singleOf(::ProxyDocumentBusinessHandler) bind DocumentBusinessHandler::class
        singleOf(::ProxyRequestBusinessHandler) bind RequestBusinessHandler::class
    }
}
