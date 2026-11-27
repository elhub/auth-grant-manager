package no.elhub.auth.v0.features.businessprocesses.structuredata.endusers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.TraceIdContext
import no.elhub.auth.common.structuredata.endusers.EndUserResponse
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import org.slf4j.LoggerFactory

interface EndUsersService {
    /**
     * @throws CurrentEndUserNotFoundException when no current end user exists.
     * @throws EndUserLookupException when the upstream service returns another unsuccessful HTTP status.
     */
    suspend fun getEndUserByMeteringPointId(
        meteringPointId: String,
    ): EndUserResponse
}

class CurrentEndUserNotFoundException :
    EndUserLookupException("No current end user found for the metering point")

open class EndUserLookupException(message: String) : RuntimeException(message)

class EndUsersApi(
    private val endUsersApiConfig: EndUsersApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider,
) : EndUsersService {
    private val logger = LoggerFactory.getLogger(EndUsersService::class.java)

    override suspend fun getEndUserByMeteringPointId(
        meteringPointId: String,
    ): EndUserResponse {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=end_user_lookup_missing_trace_id")
        }

        val jwtToken = tokenProvider.getToken()
        val response = client.get("${endUsersApiConfig.serviceUrl}/v1/end-users") {
            parameter("meteringPointId", meteringPointId)
            header("Authorization", "Bearer $jwtToken")
            header("User-Agent", "auth-grant-manager")
            traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
        }
        if (response.status == HttpStatusCode.NotFound) {
            throw CurrentEndUserNotFoundException()
        }
        if (!response.status.isSuccess()) {
            logger.error("Failed to fetch end user with status ${response.status.value}")
            throw EndUserLookupException("EndUsersService returned HTTP ${response.status.value}")
        }
        return response.body<EndUserResponse>()
    }
}

data class EndUsersApiConfig(
    val serviceUrl: String,
)
