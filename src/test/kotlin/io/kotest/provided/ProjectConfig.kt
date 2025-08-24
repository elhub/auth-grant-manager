package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import no.elhub.auth.extensions.CloseHttpClient
import no.elhub.auth.extensions.StopPostgresTestContainerExtension
import no.elhub.auth.extensions.StopVaultTransitTestContainer

object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(
        StopPostgresTestContainerExtension,
        CloseHttpClient,
        StopVaultTransitTestContainer
    )
}
