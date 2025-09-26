package no.elhub.auth.features.requests.confirm

import no.elhub.auth.features.requests.common.RequestRepository

class Handler(private val repo: RequestRepository) {
    operator fun invoke(command: Command): Unit = throw NotImplementedError()
}
