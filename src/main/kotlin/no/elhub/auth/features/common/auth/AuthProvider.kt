package no.elhub.auth.features.common.auth

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.text.removePrefix

enum class RoleType {
    BalanceSupplier
}

data class ResolvedActor(val gln: String, val role: RoleType)

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
}

interface AuthorizationProvider {
    suspend fun authorizeMarketParty(call: ApplicationCall): Either<AuthError, ResolvedActor>
}

class PDPAuthorizationProvider(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val pdpBaseUrl: String,
) : AuthorizationProvider {
    private val log = LoggerFactory.getLogger(PDPAuthorizationProvider::class.java)

    companion object {
        const val POLICY = "v1/data/v2/token/authinfo"

        object Headers {
            const val SENDER_GLN = "SenderGLN"
            const val ON_BEHALF_OF_GLN = "OnBehalfOfGLN"
        }

        fun defaultHttpClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }

    @Serializable
    data class PdpResponse(
        val result: Result
    ) {
        @Serializable
        data class Result(
            val tokenInfo: TokenInfo,
            val authInfo: AuthInfo? = null
        )
    }

    override suspend fun authorizeMarketParty(call: ApplicationCall): Either<AuthError, ResolvedActor> = either {
        val traceId = UUID.randomUUID().toString()
        val authorizationHeader = call.request.headers[HttpHeaders.Authorization] ?: raise(AuthError.MissingAuthorizationHeader)
        val token = authorizationHeader.removePrefix("Bearer ").takeIf { it != authorizationHeader } ?: raise(AuthError.InvalidAuthorizationHeader)

        val senderGLN = call.request.headers[Headers.SENDER_GLN] ?: raise(AuthError.MissingSenderGlnHeader)
        val onBehalfOfGLN = call.request.headers[Headers.ON_BEHALF_OF_GLN]
        log.debug(
            "PDP authorize request: traceId={} senderGLN={} delegated={}",
            traceId,
            senderGLN,
            onBehalfOfGLN ?: false
        )

        val context: MaskinportenContext = when {
            onBehalfOfGLN == null -> MaskinportenContext.Self(senderGLN)
            else -> MaskinportenContext.Delegated(senderGLN, onBehalfOfGLN)
        }

        val request = PdpRequest(
            input = Input(
                token = token,
                elhubTraceId = traceId,
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

        when (tokenInfo.tokenType) {
            "maskinporten" -> {
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
                ResolvedActor(actingGLN, roleType)
            }
            else -> {
                log.warn("Unexpected tokenType for traceId={} tokenType={}", traceId, tokenInfo.tokenType)
                raise(AuthError.InvalidToken)
            }
        }
    }
}
