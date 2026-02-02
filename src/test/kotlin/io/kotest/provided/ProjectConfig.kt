package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import no.elhub.auth.features.businessprocesses.structuredata.CloseMeteringPointsServiceHttpClient
import no.elhub.auth.features.businessprocesses.structuredata.StopMeteringPointsServiceTestContainerExtension
import no.elhub.auth.features.common.CloseHttpClient
import no.elhub.auth.features.common.StopAuthPersonsTestContainerExtension
import no.elhub.auth.features.documents.StopVaultTransitTestContainer

object ProjectConfig : AbstractProjectConfig() {
    override val extensions =
        listOf(
            CloseHttpClient,
            StopVaultTransitTestContainer,
            StopAuthPersonsTestContainerExtension,
            StopMeteringPointsServiceTestContainerExtension,
            CloseMeteringPointsServiceHttpClient,
        )
}
