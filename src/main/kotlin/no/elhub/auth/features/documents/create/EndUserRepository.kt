package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.elhub.auth.features.common.RepositoryWriteError
import java.util.UUID

interface EndUserRepository {
    suspend fun findOrCreateByNin(nin: String): Either<RepositoryWriteError, EndUser>
}

class ApiEndUserRepository(
    private val cfg: EndUserApiConfig,
    private val client: HttpClient
) : EndUserRepository {

    override suspend fun findOrCreateByNin(nin: String): Either<RepositoryWriteError, EndUser> =
        Either.catch {
            val response = client.post("${cfg.baseUri}${cfg.findOrCreateByNinPath}") {
                contentType(ContentType.Application.Json)
                setBody(EndUserApiPostBody(nin))
            }
            val endUser: EndUserApiResponseBody = response.body()
            return EndUser(endUser.elhubInternalId, nin).right()
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}

data class EndUserApiConfig(
    val baseUri: String,
    val findOrCreateByNinPath: String
)

@JvmInline
value class EndUserApiPostBody(
    val nin: String
)

data class EndUserApiResponseBody(
    val elhubInternalId: UUID
)
