package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.right
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationGrantProblem
import java.util.UUID

class GetGrantHandler(private val repo: GrantRepository) {
    operator fun invoke(query: GetGrantQuery): Either<AuthorizationGrantProblem, GrantRepository.GrantWithParties> =
        repo.find(query.id)
}
