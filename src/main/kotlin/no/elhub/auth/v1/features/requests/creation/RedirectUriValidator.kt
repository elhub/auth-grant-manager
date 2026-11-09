package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.integrations.ediel.EdielPartyRedirectNotFoundException
import no.elhub.auth.common.integrations.ediel.EdielRedirectClient
import no.elhub.auth.common.integrations.ediel.EdielRedirectRequestException
import no.elhub.auth.common.integrations.ediel.redirectUriMatchesEdielDomain
import no.elhub.auth.v1.Errors

class RedirectUriValidator(
    private val edielRedirectClient: EdielRedirectClient,
) {
    suspend fun validate(redirectUri: String?, requestedBy: AuthorizationParty) {
        if (redirectUri == null) return

        val edielRedirectUri = try {
            edielRedirectClient.getPartyRedirectUri(requestedBy.id)
        } catch (_: EdielPartyRedirectNotFoundException) {
            throw RedirectUriValidationError.NotRegisteredInEdiel()
        } catch (cause: EdielRedirectRequestException) {
            throw RedirectUriValidationError.EdielUnavailable(cause)
        }

        if (edielRedirectUri.isNullOrBlank()) {
            throw RedirectUriValidationError.NotRegisteredInEdiel()
        }

        if (!redirectUriMatchesEdielDomain(redirectUri, edielRedirectUri)) {
            throw RedirectUriValidationError.DoesNotMatchEdiel()
        }
    }
}

sealed class RedirectUriValidationError(override val detail: String) : Errors(detail) {
    class NotRegisteredInEdiel : RedirectUriValidationError(
        "No redirect URL is registered in Ediel for the requesting party",
    )

    class DoesNotMatchEdiel : RedirectUriValidationError(
        "Redirect URI does not match the URI registered in Ediel",
    )

    class EdielUnavailable(val failure: Throwable) : RedirectUriValidationError(
        "Ediel redirect URI validation is unavailable",
    )
}
