package no.elhub.auth.features.businessprocesses.datasharing

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.common.mapErrorsFromServer
import org.slf4j.LoggerFactory

interface StromprisService {
    suspend fun getProductsByOrganizationNumber(
        organizationNumber: String
    ): Either<ClientError, ProductsResponse>
}

class StromprisApi(
    private val stromprisApiConfig: StromprisApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider
) : StromprisService {
    private val logger = LoggerFactory.getLogger(StromprisService::class.java)

    override suspend fun getProductsByOrganizationNumber(organizationNumber: String): Either<ClientError, ProductsResponse> =
        Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${stromprisApiConfig.serviceUrl}/products?organizationNumber=$organizationNumber") {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
            }
            if (response.status.isSuccess()) {
                response.body<ProductsResponse>()
            } else {
                logger.error("Failed to fetch products for organization $organizationNumber with status ${response.status.value}")
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch products for organization $organizationNumber: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
}

data class StromprisApiConfig(
    val serviceUrl: String,
)
