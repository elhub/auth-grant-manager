package no.elhub.auth.v1.api.grants.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus

class ListAuthorizationGrantsResponseTest : FunSpec({
    test("preserves status filters in all pagination links") {
        val response = Page<AuthorizationGrant>(emptyList(), 5, Pagination(page = 1, size = 2)).toGetCollectionResponse(
            listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired),
        )

        listOf(response.links.self, response.links.first, response.links.last, response.links.prev, response.links.next)
            .forEach { link ->
                link?.substringAfter("&filter[status]=") shouldBe "Active,Expired"
            }
    }

    test("omits the status filter from links when no statuses are selected") {
        val response = Page<AuthorizationGrant>(emptyList(), 0, Pagination()).toGetCollectionResponse()

        response.links.self shouldBe "$V1_GRANTS_PATH?page[number]=0&page[size]=100"
        response.links.first shouldBe response.links.self
        response.links.last shouldBe response.links.self
        response.links.prev shouldBe null
        response.links.next shouldBe null
    }
})
