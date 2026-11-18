package no.elhub.auth.v1.acceptance.support

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.V1_REQUESTS_PATH

/** HTTP operations on AGM, without preparing external data or PDP decisions. */
class RequestClient(private val api: Api) {
    suspend fun create(payload: String, by: Caller) =
        api.call(HttpMethod.Post, V1_REQUESTS_PATH, by, body = payload)

    suspend fun createPending(payload: String, by: Caller): String =
        create(payload, by).shouldHaveStatus(HttpStatusCode.Created).field("data.id")!!

    suspend fun get(id: String, by: Caller) =
        api.call(HttpMethod.Get, "$V1_REQUESTS_PATH/$id", by)

    suspend fun list(by: Caller) = api.call(HttpMethod.Get, V1_REQUESTS_PATH, by)

    suspend fun approve(id: String, by: Caller) = update(id, "Approved", by)

    suspend fun reject(id: String, by: Caller) = update(id, "Rejected", by)

    suspend fun getGrant(id: String, by: Caller) =
        api.call(HttpMethod.Get, "$V1_GRANTS_PATH/$id", by)

    private suspend fun update(id: String, status: String, by: Caller) =
        api.call(
            HttpMethod.Patch,
            "$V1_REQUESTS_PATH/$id",
            by,
            body = """
            {
              "data": {
                "id": "$id",
                "type": "AuthorizationRequest",
                "attributes": { "status": "$status", "textLanguage": "nb" }
              }
            }
            """,
        )
}
