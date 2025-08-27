package no.elhub.auth.features.requests.confirm

import arrow.core.Either
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class ConfirmRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(command: ConfirmRequestCommand) {
        throw NotImplementedError()
    }
}
