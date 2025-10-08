package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.elhub.auth.features.common.RepositoryWriteError
import java.util.UUID

interface EndUserRepository {
    suspend fun findInternalIdByNin(nin: String): Either<RepositoryWriteError, UUID>
}

class ApiEndUserRepository(
    private val endUser: EndUserApiConfig,
    private val client: HttpClient
) : EndUserRepository {

    override suspend fun findInternalIdByNin(nin: String): Either<RepositoryWriteError, UUID> =
        Either.catch {
            val response = client.get("${endUser.baseUri}/persons/$nin")
            val body: EndUserApiResponseBody = response.body()
            val uuid = body.data.id
            return UUID.fromString(uuid).right()
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}

data class EndUserApiConfig(
    val baseUri: String,
)
