package no.elhub.auth.v1.acceptance.support

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** Signed test JWTs from the local token service; the PDP remains a fake. */
class TestTokens(private val baseUrl: String) {
    private val client = HttpClient(Apache5)
    private val cache = ConcurrentHashMap<String, String>()

    /** Cached per identity, so clients and PDP stubs for the same actor share one token. */
    suspend fun marketEntity(organizationNumber: String): String = cached("maskinporten:$organizationNumber") {
        fetch(
            "maskinporten",
            buildJsonObject {
                put("orgNumber", organizationNumber)
                put("scope", "elhub:balancesupplier")
            }.toString(),
        )
    }

    suspend fun endUser(personId: String): String = cached("enduser:$personId") {
        fetch("enduser", buildJsonObject { put("elhubInternalId", personId) }.toString())
    }

    private suspend fun cached(key: String, fetch: suspend () -> String): String =
        cache[key] ?: fetch().also { cache.putIfAbsent(key, it) }.let { cache.getValue(key) }

    private suspend fun fetch(type: String, body: String): String {
        val response = client.post("$baseUrl/token/$type") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        check(response.status.isSuccess()) { "Could not fetch $type test token: ${response.status}" }
        return response.bodyAsText().trim().removeSurrounding("\"").also {
            check(it.count { char -> char == '.' } == 2) { "Token service returned an invalid $type JWT" }
        }
    }
}
