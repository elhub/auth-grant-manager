package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.RepositoryWriteError

interface EndUserRepository {
    suspend fun findOrCreateByNin(nin: String): Either<RepositoryWriteError, String>
}

class ApiEndUserRepository(
    private val cfg: EndUserApiConfig,
    private val client: HttpClient
) : EndUserRepository {

    override suspend fun findOrCreateByNin(nin: String): Either<RepositoryWriteError, String> =
        Either.catch {
            val response = client.get("${cfg.baseUri}${cfg.findOrCreateByNinPath}/$nin")
            val body: EndUserApiResponseBody = response.body()
            return body.data.id.right()
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}

data class EndUserApiConfig(
    val baseUri: String,
    val findOrCreateByNinPath: String
)

@Serializable
data class EndUserApiResponseBody(
    val data: EndUserData
)

@Serializable
data class EndUserData(
    val type: String,
    val id: String
)
