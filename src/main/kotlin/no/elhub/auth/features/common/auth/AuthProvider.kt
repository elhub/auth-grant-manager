package no.elhub.auth.features.common.auth

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
import org.slf4j.LoggerFactory
import java.util.*

enum class RoleType {
    BalanceSupplier
}

sealed interface AuthorizedParty {
    data class AuthorizedPerson(val id: UUID) : AuthorizedParty
    data class AuthorizedOrganizationEntity(val gln: String, val role: RoleType) : AuthorizedParty
}

sealed interface AuthError {
    object MissingAuthorizationHeader : AuthError
    object InvalidAuthorizationHeader : AuthError
    object MissingSenderGlnHeader : AuthError
    object UnexpectedError : AuthError
    object InvalidToken : AuthError
    object ValidationInfoMissing : AuthError
    object ActingGlnMissing : AuthError
    object ActingFunctionMissing : AuthError
    object ActingFunctionNotSupported : AuthError
    object UnknownError : AuthError
    object NotAuthorized : AuthError
}

enum class TokenType(val value: String) {
    MASKINPORTEN("maskinporten"),
    ENDUSER("enduser"),
}

interface AuthorizationProvider {
    suspend fun authorize(call: ApplicationCall): Either<AuthError, AuthorizedParty>
    suspend fun authorizeMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizedParty.AuthorizedOrganizationEntity>
    suspend fun authorizeEndUser(call: ApplicationCall): Either<AuthError, AuthorizedParty.AuthorizedPerson>
}

class PDPAuthorizationProvider(
    private val httpClient: HttpClient,
    private val pdpBaseUrl: String,
) : AuthorizationProvider {
    private val log = LoggerFactory.getLogger(PDPAuthorizationProvider::class.java)

    companion object {
        const val POLICY = "v1/data/v2/token/authinfo"

        object Headers {
            const val AUTHORIZATION = "Authorization"
            const val SENDER_GLN = "SenderGLN"
            const val ON_BEHALF_OF_GLN = "OnBehalfOfGLN"
        }
    }

    override suspend fun authorize(call: ApplicationCall): Either<AuthError, AuthorizedParty> = either {
        val traceId = UUID.randomUUID()
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()

        when (pdpBody.result.tokenInfo.tokenType) {
            TokenType.MASKINPORTEN.value -> {
                authorizeMaskinporten(call, pdpBody, traceId).bind()
            }

            TokenType.ENDUSER.value -> {
                authorizePerson(pdpBody, traceId).bind()
            }

            else -> {
                log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, pdpBody.result.tokenInfo.tokenType)
                raise(AuthError.InvalidToken)
            }
        }
    }

    override suspend fun authorizeMaskinporten(call: ApplicationCall): Either<AuthError, AuthorizedParty.AuthorizedOrganizationEntity> = either {
        val traceId = UUID.randomUUID()
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()
        authorizeMaskinporten(call, pdpBody, traceId).bind()
    }

    override suspend fun authorizeEndUser(call: ApplicationCall): Either<AuthError, AuthorizedParty.AuthorizedPerson> = either {
        val traceId = UUID.randomUUID()
        val pdpBody: PdpResponse = pdpRequestAndValidate(call, traceId).bind()
        authorizePerson(pdpBody, traceId).bind()
    }

    private suspend fun pdpRequestAndValidate(call: ApplicationCall, traceId: UUID): Either<AuthError, PdpResponse> = either {
        val authorizationHeader = call.request.headers[Headers.AUTHORIZATION] ?: raise(AuthError.MissingAuthorizationHeader)
        val token = authorizationHeader.removePrefix("Bearer ").takeIf { it != authorizationHeader } ?: raise(AuthError.InvalidAuthorizationHeader)

        val senderGLN = call.request.headers[Headers.SENDER_GLN]
        val onBehalfOfGLN = call.request.headers[Headers.ON_BEHALF_OF_GLN]
        log.debug(
            "PDP authorize request: traceId={} senderGLN={} delegated={}",
            traceId,
            senderGLN,
            onBehalfOfGLN
        )

        val context: PdpPayload? = senderGLN?.let {
            when {
                onBehalfOfGLN == null -> PdpPayload.Self(senderGLN)
                else -> PdpPayload.Delegated(senderGLN, onBehalfOfGLN)
            }
        }

        val request = PdpRequest(
            input = Input(
                token = token,
                elhubTraceId = traceId.toString(),
                payload = context
            )
        )
        val response = Either.catch {
            httpClient.post("$pdpBaseUrl/$POLICY") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.mapLeft {
            log.error("PDP request failed for traceId={}", traceId, it)
            raise(AuthError.UnexpectedError)
        }.bind()

        val pdpBody: PdpResponse = when {
            response.status.isSuccess() -> response.body()

            else -> {
                val err = response.bodyAsText()
                log.warn("PDP non-2xx for traceId={} status={} body={}", traceId, response.status, err)
                raise(AuthError.UnexpectedError)
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
    ): Either<AuthError, AuthorizedParty.AuthorizedOrganizationEntity> = either {
        val tokenInfo = pdpBody.result.tokenInfo
        if (!TokenType.MASKINPORTEN.value.equals(tokenInfo.tokenType)) {
            raise(AuthError.NotAuthorized)
        }
        call.request.headers[Headers.SENDER_GLN] ?: raise(AuthError.MissingSenderGlnHeader)
        val authInfo = pdpBody.result.authInfo ?: raise(AuthError.ValidationInfoMissing)
        if (authInfo.inputFailed != null) {
            log.error("PDP input validation failed for traceId={} msg={}", traceId, authInfo.inputFailed)
            raise(AuthError.UnknownError)
        }
        val actingGLN = authInfo.actingGLN ?: raise(AuthError.ActingGlnMissing)
        val actingFunction = authInfo.actingFunction ?: raise(AuthError.ActingFunctionMissing)
        val roleType = Either.catch {
            enumValueOf<RoleType>(actingFunction)
        }
            .mapLeft {
                log.warn("Unsupported actingFunction for traceId={} actingFunction={}", traceId, actingFunction)
                AuthError.ActingFunctionNotSupported
            }
            .bind()
        AuthorizedParty.AuthorizedOrganizationEntity(actingGLN, roleType)
    }

    private fun authorizePerson(
        pdpBody: PdpResponse,
        traceId: UUID
    ): Either<AuthError, AuthorizedParty.AuthorizedPerson> = either {
        val tokenInfo = pdpBody.result.tokenInfo
        if (!TokenType.ENDUSER.value.equals(tokenInfo.tokenType)) {
            log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, tokenInfo.tokenType)
            raise(AuthError.NotAuthorized)
        }
        val partyId = tokenInfo.partyId ?: raise(AuthError.UnknownError)
        AuthorizedParty.AuthorizedPerson(UUID.fromString(partyId))
    }
}
