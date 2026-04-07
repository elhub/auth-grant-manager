package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.common.mapErrorsFromServer
import org.slf4j.LoggerFactory

interface MeteringPointsService {
    suspend fun getMeteringPointByIdAndElhubInternalId(
        meteringPointId: String,
        elhubInternalId: String
    ): Either<ClientError, MeteringPointResponse>
}

class MeteringPointsApi(
    private val meteringPointsApiConfig: MeteringPointsApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider
) : MeteringPointsService {
    private val logger = LoggerFactory.getLogger(MeteringPointsService::class.java)

    override suspend fun getMeteringPointByIdAndElhubInternalId(meteringPointId: String, elhubInternalId: String): Either<ClientError, MeteringPointResponse> =
        Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${meteringPointsApiConfig.serviceUrl}/metering-points/$meteringPointId?endUserId=$elhubInternalId") {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
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

data class MeteringPointsApiConfig(
    val serviceUrl: String,
)
