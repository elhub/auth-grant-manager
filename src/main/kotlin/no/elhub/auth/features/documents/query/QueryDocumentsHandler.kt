package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class QueryDocumentsHandler(
    private val repo: DocumentRepository
) {
    operator fun invoke(query: QueryDocumentsQuery): Either<QueryDocumentsError, List<AuthorizationDocument>> = either {
        repo.findAll()
            .getOrElse { return QueryDocumentsError.IOError.left() }
    }
}

sealed class QueryDocumentsError {
    data object NotFoundError : QueryDocumentsError()
    data object IOError : QueryDocumentsError()
}

