package no.elhub.auth.features.businessprocesses.structuredata

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import no.elhub.auth.features.businessprocesses.structuredata.domain.MeteringPointResponse
import org.slf4j.LoggerFactory

interface MeteringPointsService {
    suspend fun getMeteringPointByIdAndElhubInternalId(
        meteringPointId: String,
        elhubInternalId: String,
    ): Either<ClientError, MeteringPointResponse>
}

class MeteringPointsApi(
    private val meteringPointsApiConfig: MeteringPointsApiConfig,
    private val client: HttpClient,
) : MeteringPointsService {
    private val logger = LoggerFactory.getLogger(MeteringPointsService::class.java)

    override suspend fun getMeteringPointByIdAndElhubInternalId(
        meteringPointId: String,
        elhubInternalId: String,
    ): Either<ClientError, MeteringPointResponse> =
        Either
            .catch {
                val response = client.get("${meteringPointsApiConfig.serviceUrl}/metering-points/$meteringPointId?endUserId=$elhubInternalId")
                if (response.status.isSuccess()) {
                    val responseBody: MeteringPointResponse = response.body()
                    responseBody
                } else {
                    logger.error("Failed to fetch metering point with status: ${response.status.value}")
                    throw ClientRequestException(response, response.bodyAsText())
                }
            }.mapLeft { throwable ->
                logger.error("Failed to fetch metering point: {}", throwable.message)
                ClientError.UnexpectedError(throwable)
            }
}

data class MeteringPointsApiConfig(
    val serviceUrl: String,
    val basicAuthConfig: BasicAuthConfig,
)

data class BasicAuthConfig(
    val username: String,
    val password: String,
)

sealed class ClientError {
    data class UnexpectedError(
        val cause: Throwable,
    ) : ClientError()
}
