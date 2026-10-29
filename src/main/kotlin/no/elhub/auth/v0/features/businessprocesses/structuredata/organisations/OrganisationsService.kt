package no.elhub.auth.v0.features.businessprocesses.structuredata.organisations

import arrow.core.Either
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import no.elhub.auth.common.structuredata.organisations.AltinnOrganisationResponse
import no.elhub.auth.common.structuredata.organisations.PartyResponse
import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.mapErrorsFromServer
import no.elhub.auth.v0.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.v0.features.common.TraceIdContext
import org.slf4j.LoggerFactory

interface OrganisationsService {
    suspend fun getPartyByIdAndPartyType(
        partyId: String,
        partyType: PartyType
    ): Either<ClientError, PartyResponse>
    suspend fun getAltinnOrganisationsByElhubInternalId(
        elhubInternalId: String,
    ): Either<ClientError, AltinnOrganisationResponse>
}

class OrganisationsApi(
    private val organisationsApiConfig: OrganisationsApiConfig,
    private val client: HttpClient,
    private val tokenProvider: JwtTokenProvider
) : OrganisationsService {
    private val logger = LoggerFactory.getLogger(OrganisationsService::class.java)

    override suspend fun getPartyByIdAndPartyType(partyId: String, partyType: PartyType): Either<ClientError, PartyResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=organisation_lookup_missing_trace_id")
        }

        return Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${organisationsApiConfig.serviceUrl}/parties/$partyId?partyType=$partyType") {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
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

    override suspend fun getAltinnOrganisationsByElhubInternalId(elhubInternalId: String): Either<ClientError, AltinnOrganisationResponse> {
        val traceId = TraceIdContext.currentOrNull()
        if (traceId == null) {
            logger.error("event=organisation_lookup_missing_trace_id")
        }
        return Either.catch {
            val jwtToken = tokenProvider.getToken()
            val response = client.get("${organisationsApiConfig.serviceUrl}/altinn?elhubInternalId=$elhubInternalId") {
                header("Authorization", "Bearer $jwtToken")
                header("User-Agent", "auth-grant-manager")
                traceId?.let { header(ELHUB_TRACE_ID_HEADER, it) }
            }
            if (response.status.isSuccess()) {
                val responseBody: AltinnOrganisationResponse = response.body()
                responseBody
            } else {
                logger.error("Failed to fetch Altinn organisations with status: ${response.status.value}")
                return mapErrorsFromServer(response).left()
            }
        }.mapLeft { throwable ->
            logger.error("Failed to fetch Altinn organisations: {}", throwable.message)
            ClientError.UnexpectedError(throwable)
        }
    }
}

data class OrganisationsApiConfig(
    val serviceUrl: String,
)
