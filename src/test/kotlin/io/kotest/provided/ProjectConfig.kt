package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import no.elhub.auth.features.common.CloseHttpClient
import no.elhub.auth.features.common.StopPostgresTestContainerExtension
import no.elhub.auth.features.documents.StopAuthPersonTestContainer
import no.elhub.auth.features.documents.StopVaultTransitTestContainer

object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(
        StopPostgresTestContainerExtension,
        CloseHttpClient,
        StopVaultTransitTestContainer,
        StopAuthPersonTestContainer
    )
}
