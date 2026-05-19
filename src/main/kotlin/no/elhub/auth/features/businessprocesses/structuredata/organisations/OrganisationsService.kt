package no.elhub.auth.features.businessprocesses.structuredata.organisations

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.common.mapErrorsFromServer
import no.elhub.auth.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.features.common.TraceIdContext
import org.slf4j.LoggerFactory

interface OrganisationsService {
    suspend fun getPartyByIdAndPartyType(
        partyId: String,
        partyType: PartyType
    ): Either<ClientError, PartyResponse>
}

class OrganisationsApi(
    private val organisationsApiConfig: OrganisationsApiConfig,
    private val client: HttpClient
) : OrganisationsService {
    private val logger = LoggerFactory.getLogger(OrganisationsService::class.java)

    override suspend fun getPartyByIdAndPartyType(partyId: String, partyType: PartyType): Either<ClientError, PartyResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=organisation_lookup_missing_trace_id")
        }

        return Either.catch {
            val response = client.get("${organisationsApiConfig.serviceUrl}/parties/$partyId?partyType=$partyType") {
                traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
            }
            if (response.status.isSuccess()) {
                val responseBody: PartyResponse = response.body()
                responseBody
            } else {
                logger.error("Failed to fetch party with status: ${response.status.value}")
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch party: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
    }
}

data class OrganisationsApiConfig(
    val serviceUrl: String,
    val basicAuthConfig: BasicAuthConfig
)

data class BasicAuthConfig(
    val username: String,
    val password: String
)
