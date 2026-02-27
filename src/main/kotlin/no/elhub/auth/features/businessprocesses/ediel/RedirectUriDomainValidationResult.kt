package no.elhub.auth.features.businessprocesses.ediel

import java.net.URI

internal enum class RedirectUriDomainValidationResult {
    MatchingDomain,
    InvalidInputUri,
    InvalidEdielUri,
    DomainMismatch
}

internal fun validateRedirectUriDomain(
    inputRedirectUri: String,
    edielRedirectUri: String?
): RedirectUriDomainValidationResult {
    val inputHost = parseHost(inputRedirectUri) ?: return RedirectUriDomainValidationResult.InvalidInputUri
    val edielHost = parseHost(edielRedirectUri.orEmpty()) ?: return RedirectUriDomainValidationResult.InvalidEdielUri

    val isMatching = isSameOrSubdomain(inputHost, edielHost)
    return if (isMatching) RedirectUriDomainValidationResult.MatchingDomain else RedirectUriDomainValidationResult.DomainMismatch
}

private fun parseHost(uri: String): String? =
    runCatching { URI(uri.trim()) }
        .getOrNull()
        ?.host
        ?.lowercase()
        ?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }

private fun isSameOrSubdomain(host: String, domain: String): Boolean =
    host == domain || host.endsWith(".$domain")
