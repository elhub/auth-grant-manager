package no.elhub.auth.common.integrations.ediel

import java.net.URI

fun redirectUriMatchesEdielDomain(
    redirectUri: String,
    edielRedirectUri: String?,
): Boolean {
    val redirectHost = httpsHost(redirectUri) ?: return false
    val edielHost = edielRedirectUri?.let(::httpsHost) ?: return false
    return redirectHost == edielHost || redirectHost.endsWith(".$edielHost")
}

private fun httpsHost(value: String): String? =
    runCatching { URI(value.trim()) }
        .getOrNull()
        ?.takeIf { it.scheme.equals("https", ignoreCase = true) }
        ?.host
        ?.lowercase()
        ?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }
