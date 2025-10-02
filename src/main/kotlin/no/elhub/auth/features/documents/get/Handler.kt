package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.FileStorage
import no.elhub.auth.features.documents.common.FileStorageError
import java.net.URI

class Handler(
    private val repo: DocumentRepository,
    private val fileStorage: FileStorage,
) {
    suspend operator fun invoke(query: Query): Either<QueryError, Pair<AuthorizationDocument, URI>> = (
        repo
            .find(query.id)
            .getOrElse { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> return QueryError.ResourceNotFoundError.left()
                    is RepositoryReadError.UnexpectedError -> return QueryError.IOError.left()
                }
            } to fileStorage
            .find(query.id.toString())
            .getOrElse { error ->
                when (error) {
                    is FileStorageError.UnexpectedError -> return QueryError.IOError.left()
                }
            }
        ).right()
}
