package no.elhub.auth.features.businessprocesses

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.common.ProxyDocumentBusinessHandler
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestBusinessHandler

fun Application.businessProcessesModule() {
    dependencies {
        provide<ChangeOfBalanceSupplierBusinessHandler> {
            ChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = resolve(),
                organisationsService = resolve(),
                stromprisService = resolve(),
                edielService = resolve(),
                edielEnvironment = resolve("edielEnvironment"),
                validateRedirectUriFeature = resolve("validateRedirectUriFeature"),
                validateBalanceSupplierContractName = resolve("validateBalanceSupplierContractName")
            )
        }
        provide<MoveInAndChangeOfBalanceSupplierBusinessHandler> {
            MoveInAndChangeOfBalanceSupplierBusinessHandler(
                meteringPointsService = resolve(),
                organisationsService = resolve(),
                stromprisService = resolve(),
                edielService = resolve(),
                edielEnvironment = resolve("edielEnvironment"),
                validateRedirectUriFeature = resolve("validateRedirectUriFeature"),
                validateBalanceSupplierContractName = resolve("validateBalanceSupplierContractName")
            )
        }
        provide<DocumentBusinessHandler> { ProxyDocumentBusinessHandler(resolve(), resolve()) }
        provide<RequestBusinessHandler> { ProxyRequestBusinessHandler(resolve(), resolve()) }
    }
}
