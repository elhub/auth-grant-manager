package no.elhub.auth.v1.api.grants.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantAgentFilters

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

    test("preserves all agent filters and statuses in every pagination link") {
        val person = AuthorizationAgent("abcdef12-3456-7890-abcd-ef1234567890", AgentType.Person)
        val organization = AuthorizationAgent("999888771", AgentType.Organization)
        val entity = AuthorizationAgent("7070575000001", AgentType.OrganizationEntity)
        val agentFilters = GrantAgentFilters(
            grantedBy = listOf(person, organization, entity),
            principal = listOf(organization, person),
            approvedBy = listOf(person, entity),
            grantedTo = listOf(entity, organization),
        )
        val response = Page<AuthorizationGrant>(emptyList(), 5, Pagination(page = 1, size = 2)).toGetCollectionResponse(
            listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired),
            agentFilters,
        )

        listOf(response.links.self, response.links.first, response.links.last, response.links.prev, response.links.next)
            .forEach { link ->
                val parameters = Url(requireNotNull(link)).parameters
                parameters["filter[status]"] shouldBe "Active,Expired"
                parameters["filter[grantedBy]"] shouldBe "${person.id},${organization.id},${entity.id}"
                parameters["filter[principal]"] shouldBe "${organization.id},${person.id}"
                parameters["filter[approvedBy]"] shouldBe "${person.id},${entity.id}"
                parameters["filter[grantedTo]"] shouldBe "${entity.id},${organization.id}"
            }
    }

    test("omits status and agent filters from links when no filters are selected") {
        val response = Page<AuthorizationGrant>(emptyList(), 0, Pagination()).toGetCollectionResponse()

        response.links.self shouldBe "$V1_GRANTS_PATH?page[number]=0&page[size]=100"
        response.links.first shouldBe response.links.self
        response.links.last shouldBe response.links.self
        response.links.prev shouldBe null
        response.links.next shouldBe null
    }
})
