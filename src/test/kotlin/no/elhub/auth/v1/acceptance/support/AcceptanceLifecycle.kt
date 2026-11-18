package no.elhub.auth.v1.acceptance.support

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty

/** The API and external services available to each acceptance spec. */
class AcceptanceEnvironment(app: ProductionApp) {
    val requestApiClient = RequestClient(Api(app.client))
    val documentApiClient = DocumentClient(Api(app.client))
    val externalServices = AcceptanceTestInfrastructure.externalServices
}

/** Serialize specs sharing the database, WireMock, and JVM configuration. */
fun FunSpec.acceptanceLifecycle(onStart: (AcceptanceEnvironment) -> Unit) {
    var app: ProductionApp? = null
    var locked = false
    beforeSpec {
        AcceptanceTestInfrastructure.specLock.lock()
        locked = true
        try {
            app = ProductionApp.start()
            onStart(AcceptanceEnvironment(requireNotNull(app)))
        } catch (failure: Throwable) {
            try {
                app?.stop()
            } finally {
                AcceptanceTestInfrastructure.specLock.unlock()
                locked = false
            }
            throw failure
        }
    }
    beforeEach {
        AcceptanceTestInfrastructure.clearData()
        AcceptanceTestInfrastructure.externalServices.reset()
    }
    afterEach { AcceptanceTestInfrastructure.externalServices.unmatchedRequests().shouldBeEmpty() }
    afterSpec {
        try {
            app?.stop()
        } finally {
            if (locked) {
                AcceptanceTestInfrastructure.specLock.unlock()
                locked = false
            }
        }
    }
}
