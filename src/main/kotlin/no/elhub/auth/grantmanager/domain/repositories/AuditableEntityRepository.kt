package no.elhub.auth.grantmanager.domain.repositories

import arrow.core.Either
import no.elhub.auth.grantmanager.domain.errors.RepoRetrievalError
import no.elhub.auth.grantmanager.domain.models.AuditableEntity
import java.util.UUID

interface AuditableEntityRepository<T> where T : AuditableEntity {
    suspend fun create(item: T)
    suspend fun get(id: UUID): Either<RepoRetrievalError, T>
    suspend fun update(item: T)
}
