package no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.mapErrorsFromServer
import no.elhub.auth.v0.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.v0.features.common.TraceIdContext
import org.slf4j.LoggerFactory

interface MeteringPointsService {
    suspend fun getMeteringPointByIdAndElhubInternalId(
        meteringPointId: String,
        elhubInternalId: String
    ): Either<ClientError, MeteringPointResponse>

    suspend fun getMeteringPoints(
        meteringPointIds: List<String>,
        elhubInternalId: String
    ): Either<ClientError, List<MeteringPointResponse>>
}

class MeteringPointsApi(
    private val meteringPointsApiConfig: MeteringPointsApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider
) : MeteringPointsService {
    private val logger = LoggerFactory.getLogger(MeteringPointsService::class.java)

    override suspend fun getMeteringPointByIdAndElhubInternalId(meteringPointId: String, elhubInternalId: String): Either<ClientError, MeteringPointResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=metering_point_lookup_missing_trace_id")
        }

        return Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${meteringPointsApiConfig.serviceUrl}/metering-points/$meteringPointId?endUserId=$elhubInternalId") {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
                traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
            }
            if (response.status.isSuccess()) {
                val responseBody: MeteringPointResponse = response.body()
                responseBody
            } else {
                logger.error("Failed to fetch metering point with status ${response.status.value}")
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch metering point: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
    }

    // Uses single-ID lookups until the service exposes a batch endpoint for service-to-service tokens.
    override suspend fun getMeteringPoints(
        meteringPointIds: List<String>,
        elhubInternalId: String
    ): Either<ClientError, List<MeteringPointResponse>> = either {
        meteringPointIds.map { meteringPointId ->
            getMeteringPointByIdAndElhubInternalId(meteringPointId, elhubInternalId).bind()
        }
    }
}

data class MeteringPointsApiConfig(
    val serviceUrl: String,
)
