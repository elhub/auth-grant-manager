package no.elhub.auth.features.requests.confirm

import no.elhub.auth.features.requests.common.RequestRepository

class ConfirmRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(command: ConfirmRequestCommand) {
        throw NotImplementedError()
    }
}
