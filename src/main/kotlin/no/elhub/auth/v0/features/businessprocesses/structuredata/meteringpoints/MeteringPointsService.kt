package no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.common.structuredata.meteringpoints.MeteringPointResponse
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.mapErrorsFromServer
import no.elhub.auth.v0.features.common.ApiHeaders
import no.elhub.auth.v0.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.v0.features.common.TraceIdContext
import org.slf4j.LoggerFactory

interface MeteringPointsService {
    suspend fun getMeteringPointByIdAndElhubInternalId(
        meteringPointId: String,
        elhubInternalId: String
    ): Either<ClientError, MeteringPointResponse>

    suspend fun getMeteringPointsForOrganisation(
        meteringPointIds: List<String>,
        elhubInternalId: String
    ): List<MeteringPointResponse>
}

class MeteringPointsLookupException(val clientError: ClientError) :
    RuntimeException("Failed to fetch metering points: $clientError")

class MeteringPointsApi(
    private val meteringPointsApiConfig: MeteringPointsApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider
) : MeteringPointsService {
    private val logger = LoggerFactory.getLogger(MeteringPointsService::class.java)

    override suspend fun getMeteringPointByIdAndElhubInternalId(meteringPointId: String, elhubInternalId: String): Either<ClientError, MeteringPointResponse> =
        getMeteringPoint(url = "${meteringPointsApiConfig.serviceUrl}/metering-points/$meteringPointId?endUserId=$elhubInternalId")

    override suspend fun getMeteringPointsForOrganisation(
        meteringPointIds: List<String>,
        elhubInternalId: String
    ): List<MeteringPointResponse> = meteringPointIds.map { meteringPointId ->
        getMeteringPoint(
            url = "${meteringPointsApiConfig.serviceUrl}/metering-points/$meteringPointId?endUserId=$elhubInternalId",
            onBehalfOfOrganisation = elhubInternalId,
        ).fold(
            ifLeft = { throw MeteringPointsLookupException(it) },
            ifRight = { it },
        )
    }

    private suspend fun getMeteringPoint(url: String, onBehalfOfOrganisation: String? = null): Either<ClientError, MeteringPointResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=metering_point_lookup_missing_trace_id")
        }

        return Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get(url) {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
                traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
                onBehalfOfOrganisation?.let { header(ApiHeaders.ON_BEHALF_OF_ORGANISATION, it) }
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
}

data class MeteringPointsApiConfig(
    val serviceUrl: String,
)
