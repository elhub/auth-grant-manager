package no.elhub.auth.v1.api.grants.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantPartyFilters

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

    test("preserves all party filters and statuses in every pagination link") {
        val partyFilters = GrantPartyFilters(
            grantedBy = listOf("grantor-1", "grantor-2"),
            principal = listOf("principal-1", "principal-2"),
            approvedBy = listOf("approver-1", "approver-2"),
            grantedTo = listOf("recipient-1", "recipient-2"),
        )
        val response = Page<AuthorizationGrant>(emptyList(), 5, Pagination(page = 1, size = 2)).toGetCollectionResponse(
            listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired),
            partyFilters,
        )

        listOf(response.links.self, response.links.first, response.links.last, response.links.prev, response.links.next)
            .forEach { link ->
                val parameters = Url(requireNotNull(link)).parameters
                parameters["filter[status]"] shouldBe "Active,Expired"
                parameters["filter[grantedBy]"] shouldBe "grantor-1,grantor-2"
                parameters["filter[principal]"] shouldBe "principal-1,principal-2"
                parameters["filter[approvedBy]"] shouldBe "approver-1,approver-2"
                parameters["filter[grantedTo]"] shouldBe "recipient-1,recipient-2"
            }
    }

    test("URL-encodes party IDs without changing their values") {
        val ids = listOf("party&other=value", "party+%#?")
        val response = Page<AuthorizationGrant>(emptyList(), 0, Pagination()).toGetCollectionResponse(
            partyFilters = GrantPartyFilters(grantedBy = ids),
        )

        Url(response.links.self).parameters["filter[grantedBy]"] shouldBe ids.joinToString(",")
        Url(response.links.self).parameters.names() shouldBe setOf("page[number]", "page[size]", "filter[grantedBy]")
    }

    test("omits status and party filters from links when no filters are selected") {
        val response = Page<AuthorizationGrant>(emptyList(), 0, Pagination()).toGetCollectionResponse()

        response.links.self shouldBe "$V1_GRANTS_PATH?page[number]=0&page[size]=100"
        response.links.first shouldBe response.links.self
        response.links.last shouldBe response.links.self
        response.links.prev shouldBe null
        response.links.next shouldBe null
    }
})
