package no.elhub.auth.v1.api.requests.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.V1_REQUESTS_PATH
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import kotlin.time.Instant

class ListAuthorizationRequestsResponseTest : FunSpec({
    val party = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val request = AuthorizationRequest.new(
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = "contract-123",
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = party,
        requestedPrincipal = party,
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
    )

    test("reuses the single request resource mapping and includes pagination metadata") {
        val response = Page(listOf(request), 5, Pagination(page = 1, size = 2)).toGetCollectionResponse()

        response.data shouldBe listOf(request.toGetResponse().data)
        response.meta["totalItems"]?.jsonPrimitive?.content shouldBe "5"
        response.meta["totalPages"]?.jsonPrimitive?.content shouldBe "3"
        response.meta["page"]?.jsonPrimitive?.content shouldBe "1"
        response.meta["pageSize"]?.jsonPrimitive?.content shouldBe "2"
        Instant.parse(response.meta.getValue("createdAt").jsonPrimitive.content)
        response.links.self shouldBe "$V1_REQUESTS_PATH?page[number]=1&page[size]=2"
        response.links.first shouldBe "$V1_REQUESTS_PATH?page[number]=0&page[size]=2"
        response.links.last shouldBe "$V1_REQUESTS_PATH?page[number]=2&page[size]=2"
        response.links.prev shouldBe response.links.first
        response.links.next shouldBe response.links.last
    }

    test("preserves status filters in all pagination links") {
        val response = Page(listOf(request), 5, Pagination(page = 1, size = 2)).toGetCollectionResponse(
            listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Rejected),
        )

        listOf(response.links.self, response.links.first, response.links.last, response.links.prev, response.links.next)
            .forEach { link ->
                link?.substringAfter("&filter[status]=") shouldBe "Pending,Rejected"
            }
    }

    test("maps an empty result with no previous or next page") {
        val response = Page<AuthorizationRequest>(emptyList(), 0, Pagination()).toGetCollectionResponse()

        response.data shouldBe emptyList()
        response.meta["totalItems"]?.jsonPrimitive?.content shouldBe "0"
        response.meta["totalPages"]?.jsonPrimitive?.content shouldBe "0"
        response.links.self shouldBe "$V1_REQUESTS_PATH?page[number]=0&page[size]=100"
        response.links.first shouldBe response.links.self
        response.links.last shouldBe response.links.self
        response.links.prev shouldBe null
        response.links.next shouldBe null
    }
})
