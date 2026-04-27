package no.elhub.auth.features.common.auth

import arrow.core.getOrElse
import arrow.core.Either
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import org.slf4j.LoggerFactory
import java.util.UUID

enum class RoleType {
    BalanceSupplier
}

sealed interface AuthError {
    object MissingAuthorizationHeader : AuthError
    object InvalidAuthorizationHeader : AuthError
    object MissingSenderGlnHeader : AuthError
    object InvalidToken : AuthError
    object InvalidPdpResponseAuthInfoMissing : AuthError
    object InvalidPdpResponseActingGlnMissing : AuthError
    object InvalidPdpResponseAuthorizedFunctionsMissing : AuthError
    object ActingFunctionNotSupported : AuthError
    object EndUserOnBehalfOfOrganisationVerificationFailed : AuthError
    object UnexpectedPdpError : AuthError
    object NotAuthorized : AuthError
    object AccessDenied : AuthError
}

enum class TokenType(val value: String) {
    MASKINPORTEN("maskinporten"),
    ENDUSER("enduser"),
    ELHUB_SERVICE("elhub-service")
}

interface AuthorizationProvider {
    suspend fun authorizeAll(call: ApplicationCall): Either<AuthError, AuthorizationParty>
    suspend fun authorizeEndUserOrMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizationParty>
    suspend fun authorizeMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizationParty>
    suspend fun authorizeEndUser(call: ApplicationCall): Either<AuthError, AuthorizationParty>
    suspend fun authorizeElhubService(call: ApplicationCall): Either<AuthError, AuthorizationParty>
}

class PDPAuthorizationProvider(
    private val httpClient: HttpClient,
    private val pdpBaseUrl: String,
) : AuthorizationProvider {
    private val log = LoggerFactory.getLogger(PDPAuthorizationProvider::class.java)

    companion object {
        const val POLICY = "/v1/data/v3/token/authinfo"

        object Headers {
            const val AUTHORIZATION = "Authorization"
            const val SENDER_GLN = "SenderGLN"
            const val ON_BEHALF_OF_GLN = "OnBehalfOfGLN"

            // Internal header used for Minside enduser acting on behalf of an organisation.
            // Do not document this in OpenAPI, since it is not part of the public API contract and would cause confusion for consumers.
            const val END_USER_ON_BEHALF_OF_ORGANISATION = "ElhubOnBehalfOfOrganisation"
        }
    }

    override suspend fun authorizeAll(call: ApplicationCall): Either<AuthError, AuthorizationParty> = either {
        val traceId = resolveTraceId(call)
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()

        when (pdpBody.result.tokenInfo.tokenType) {
            TokenType.MASKINPORTEN.value -> {
                authorizeMaskinporten(call, pdpBody, traceId).bind()
            }

            TokenType.ENDUSER.value -> {
                authorizeEndUser(pdpBody, traceId).bind()
            }

            TokenType.ELHUB_SERVICE.value -> {
                authorizeSystem(pdpBody, traceId).bind()
            }

            else -> {
                log.warn(
                    "Unexpected tokenType for traceId={} tokenType={}",
                    traceId,
                    pdpBody.result.tokenInfo.tokenType
                )
                raise(AuthError.InvalidToken)
            }
        }
    }

    override suspend fun authorizeEndUserOrMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizationParty> =
        either {
            val traceId = resolveTraceId(call)
            val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()

            when (pdpBody.result.tokenInfo.tokenType) {
                TokenType.MASKINPORTEN.value -> {
                    authorizeMaskinporten(call, pdpBody, traceId).bind()
                }

                TokenType.ENDUSER.value -> {
                    authorizeEndUser(pdpBody, traceId).bind()
                }

                else -> {
                    log.warn(
                        "Unexpected tokenType for traceId={} tokenType={}",
                        traceId,
                        pdpBody.result.tokenInfo.tokenType
                    )
                    raise(AuthError.InvalidToken)
                }
            }
        }

    override suspend fun authorizeMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizationParty> = either {
        val traceId = resolveTraceId(call)
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()
        authorizeMaskinporten(call, pdpBody, traceId).bind()
    }

    override suspend fun authorizeEndUser(call: ApplicationCall): Either<AuthError, AuthorizationParty> = either {
        val traceId = resolveTraceId(call)
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()

        if (pdpBody.result.tokenInfo.tokenType == TokenType.ENDUSER.value) {
            authorizeEndUser(pdpBody, traceId).bind()
        } else {
            log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, pdpBody.result.tokenInfo.tokenType)
            raise(AuthError.ActingFunctionNotSupported)
        }
    }

    override suspend fun authorizeElhubService(call: ApplicationCall): Either<AuthError, AuthorizationParty> = either {
        val traceId = resolveTraceId(call)
        val pdpResponse = pdpRequestAndValidate(call, traceId).bind()
        authorizeSystem(pdpResponse, traceId).bind()
    }

    private suspend fun pdpRequestAndValidate(call: ApplicationCall, traceId: UUID): Either<AuthError, PdpResponse> =
        either {
            val authorizationHeader =
                call.request.headers[Headers.AUTHORIZATION] ?: raise(AuthError.MissingAuthorizationHeader)
            val token = authorizationHeader.removePrefix("Bearer ").takeIf { it != authorizationHeader } ?: raise(
                AuthError.InvalidAuthorizationHeader
            )

            val senderGLN = call.request.headers[Headers.SENDER_GLN]?.ifBlank { null }
            val onBehalfOfGLN = call.request.headers[Headers.ON_BEHALF_OF_GLN]?.ifBlank { null }
            val onBehalfOfOrganisation =
                call.request.headers[Headers.END_USER_ON_BEHALF_OF_ORGANISATION]?.ifBlank { null }

            log.info(
                "PDP authorize request senderGLN={} onBehalfOfGLN={} onBehalfOfOrganisation={}",
                senderGLN,
                onBehalfOfGLN,
                onBehalfOfOrganisation
            )

            val request = PdpRequest(
                input = Input(
                    token = token,
                    elhubTraceId = traceId.toString(),
                    payload = PdpPayload(
                        senderGLN = senderGLN,
                        onBehalfOfGLN = onBehalfOfGLN,
                        onBehalfOfOrganisationId = onBehalfOfOrganisation,
                    )
                )
            )
            val response = Either.catch {
                httpClient.post("$pdpBaseUrl$POLICY") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }.mapLeft {
                log.error("PDP request failed for traceId={}", traceId, it)
                raise(AuthError.UnexpectedPdpError)
            }.bind()

            val pdpBody: PdpResponse = when {
                response.status.isSuccess() -> Either.catch {
                    response.body<PdpResponse>()
                }.getOrElse {
                    log.error(
                        "Failed to deserialize PDP response for traceId={}, response={}",
                        traceId,
                        response.bodyAsText()
                    )
                    raise(AuthError.UnexpectedPdpError)
                }

                else -> {
                    val err = response.bodyAsText()
                    log.warn("PDP non-2xx for traceId={} status={} body={}", traceId, response.status, err)
                    raise(AuthError.UnexpectedPdpError)
                }
            }

            val tokenInfo = pdpBody.result.tokenInfo
            val status = tokenInfo.tokenStatus
            if (status != "verified") {
                log.warn("Invalid token status for traceId={} status={}", traceId, status)
                raise(AuthError.InvalidToken)
            }
            pdpBody
        }

    private fun authorizeMaskinporten(
        call: ApplicationCall,
        pdpBody: PdpResponse,
        traceId: UUID
    ): Either<AuthError, AuthorizationParty> = either {
        val tokenInfo = pdpBody.result.tokenInfo
        if (!TokenType.MASKINPORTEN.value.equals(tokenInfo.tokenType)) {
            raise(AuthError.AccessDenied)
        }
        call.request.headers[Headers.SENDER_GLN] ?: raise(AuthError.MissingSenderGlnHeader)
        val authInfo = pdpBody.result.authInfo ?: raise(AuthError.InvalidPdpResponseAuthInfoMissing)
        if (authInfo.inputFailed != null) {
            log.error("PDP input validation failed for traceId={} msg={}", traceId, authInfo.inputFailed)
            raise(AuthError.AccessDenied)
        }
        val actingGLN = authInfo.actingGLN ?: raise(AuthError.InvalidPdpResponseActingGlnMissing)
        val authorizedFunctions = authInfo.authorizedFunctions
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                log.warn("PDP response missing authorizedFunctions for traceId={}", traceId)
                raise(AuthError.InvalidPdpResponseAuthorizedFunctionsMissing)
            }

        authorizedFunctions
            .firstOrNull { it.functionName == RoleType.BalanceSupplier.name }
            ?: run {
                log.warn(
                    "Unsupported authorizedFunctions for traceId={} authorizedFunctions={}",
                    traceId,
                    authorizedFunctions
                )
                raise(AuthError.ActingFunctionNotSupported)
            }
        val authorizedParty = AuthorizationParty(id = actingGLN, type = PartyType.OrganizationEntity)
        log.info("Authorized party is $authorizedParty")
        authorizedParty
    }

    private fun authorizeEndUser(
        pdpBody: PdpResponse,
        traceId: UUID
    ): Either<AuthError, AuthorizationParty> = either {
        val tokenInfo = pdpBody.result.tokenInfo
        if (!TokenType.ENDUSER.value.equals(tokenInfo.tokenType)) {
            log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, tokenInfo.tokenType)
            raise(AuthError.AccessDenied)
        }
        val authInfo = pdpBody.result.authInfo
        if (authInfo?.error != null) {
            log.warn("PDP authInfo error={}", authInfo.error)
            raise(AuthError.EndUserOnBehalfOfOrganisationVerificationFailed)
        }
        val authorizedParty = when (authInfo?.actingType?.trim()?.lowercase()?.ifBlank { null }) {
            "person" -> {
                val actingId = authInfo.actingId ?: raise(AuthError.UnexpectedPdpError)
                AuthorizationParty(id = actingId, type = PartyType.Person)
            }

            "organisation" -> {
                val orgNumber = authInfo.actingOrganisationNumber ?: raise(AuthError.UnexpectedPdpError)
                AuthorizationParty(id = orgNumber, type = PartyType.Organization)
            }

            else -> {
                raise(AuthError.AccessDenied)
            }
        }
        log.info("Authorized party is $authorizedParty")
        authorizedParty
    }

    private fun authorizeSystem(
        pdpResponse: PdpResponse,
        traceId: UUID
    ): Either<AuthError, AuthorizationParty> = either {
        val tokenInfo = pdpResponse.result.tokenInfo
        if (!TokenType.ELHUB_SERVICE.value.equals(tokenInfo.tokenType)) {
            log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, tokenInfo.tokenType)
            raise(AuthError.AccessDenied)
        }
        val partyId = tokenInfo.partyId ?: raise(AuthError.UnexpectedPdpError)
        val authorizedParty = AuthorizationParty(id = partyId, type = PartyType.System)
        log.info("Authorized party is $authorizedParty")
        authorizedParty
    }

    private fun resolveTraceId(call: ApplicationCall): UUID {
        val callId = call.callId
        if (callId.isNullOrBlank()) {
            log.warn("callId not set, generating new trace id")
            return UUID.randomUUID()
        }
        return UUID.fromString(callId)
    }
}
