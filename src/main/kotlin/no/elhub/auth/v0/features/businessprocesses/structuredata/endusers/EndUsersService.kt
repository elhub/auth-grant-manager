package no.elhub.auth.v0.features.businessprocesses.structuredata.endusers

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.TraceIdContext
import no.elhub.auth.common.structuredata.endusers.EndUserResponse
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.mapErrorsFromServer
import org.slf4j.LoggerFactory

interface EndUsersService {
    suspend fun getEndUserByMeteringPointId(
        meteringPointId: String,
    ): Either<ClientError, EndUserResponse>
}

class EndUsersApi(
    private val endUsersApiConfig: EndUsersApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider,
) : EndUsersService {
    private val logger = LoggerFactory.getLogger(EndUsersService::class.java)

    override suspend fun getEndUserByMeteringPointId(
        meteringPointId: String,
    ): Either<ClientError, EndUserResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=end_user_lookup_missing_trace_id")
        }

        return Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${endUsersApiConfig.serviceUrl}/v1/end-users") {
                parameter("meteringPointId", meteringPointId)
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
                traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
            }
            if (response.status.isSuccess()) {
                response.body<EndUserResponse>()
            } else {
                logger.error("Failed to fetch end user with status ${response.status.value}")
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch end user: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
    }
}

data class EndUsersApiConfig(
    val serviceUrl: String,
)
