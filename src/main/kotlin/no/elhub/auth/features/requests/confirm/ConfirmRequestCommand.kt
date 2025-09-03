package no.elhub.auth.features.requests.confirm

import java.util.UUID
import no.elhub.auth.features.requests.AuthorizationRequest

data class ConfirmRequestCommand(val id: UUID)
