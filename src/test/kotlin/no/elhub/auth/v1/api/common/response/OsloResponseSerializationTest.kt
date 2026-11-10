package no.elhub.auth.v1.api.common.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.documents.request.CreateAuthorizationDocumentAttributes
import no.elhub.auth.v1.api.documents.response.toGetCollectionResponse
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.api.grants.response.toGetCollectionResponse
import no.elhub.auth.v1.api.grants.response.toGetResponse
import no.elhub.auth.v1.api.requests.request.CreateAuthorizationRequestAttributes
import no.elhub.auth.v1.api.requests.response.toGetCollectionResponse
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import java.util.UUID
import kotlin.time.Instant

class OsloResponseSerializationTest : FunSpec({
    val winter = Instant.parse("2026-01-15T12:34:56.123456789Z")
    val summer = Instant.parse("2026-07-15T12:34:56.987654321Z")
    val winterOutput = "2026-01-15T13:34:56+01:00"
    val summerOutput = "2026-07-15T14:34:56+02:00"
    val agent = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
    val document = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = summer,
        requestedBy = agent,
        requestedPrincipal = agent,
        requestedOf = agent,
        pdfBytes = byteArrayOf(),
    ).copy(validFrom = winter, createdAt = winter, updatedAt = summer)
    val request = AuthorizationRequest.new(
        requestType = AuthorizationRequestType.EnergySupplierFrameworkAgreement,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = summer,
        requestedBy = agent,
        requestedPrincipal = agent,
        requestedOf = agent,
    ).copy(validFrom = winter, createdAt = winter, updatedAt = summer)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Persistent,
        sourceType = AuthorizationGrantSourceType.Request,
        sourceId = UUID.randomUUID(),
        principal = agent,
        grantedBy = agent,
        grantedTo = agent,
        approvedBy = AuthorizationAgent("person-1", AgentType.Person),
        validFrom = winter,
        validTo = summer,
        scopes = emptyList(),
        now = winter,
    ).copy(updatedAt = summer)
    val requiredDates = """
        "validFrom":"$winterOutput","validTo":"$summerOutput",
        "createdAt":"$winterOutput","updatedAt":"$summerOutput"
    """.trimIndent()

    for (withNullableDates in listOf(false, true)) {
        val optionalDates = if (withNullableDates) {
            """, "requestedGrantValidFrom":"$winterOutput","requestedGrantValidTo":"$summerOutput" """
        } else {
            ""
        }

        test("document attributes serialize all dates with nullable dates present=$withNullableDates") {
            val attributes = document.toGetResponse().data.attributes.copy(
                requestedGrantValidFrom = winter.takeIf { withNullableDates },
                requestedGrantValidTo = summer.takeIf { withNullableDates },
            )

            Json.encodeToJsonElement(attributes) shouldBe Json.parseToJsonElement(
                """{
                    "documentType":"EnergySupplierFrameworkAgreement","requestedScopes":[],"status":"Pending",
                    $requiredDates $optionalDates
                }""",
            )
        }

        test("request attributes serialize all dates with nullable dates present=$withNullableDates") {
            val attributes = request.toGetResponse().data.attributes.copy(
                requestedGrantValidFrom = winter.takeIf { withNullableDates },
                requestedGrantValidTo = summer.takeIf { withNullableDates },
                textReference = "text-reference",
            )

            Json.encodeToJsonElement(attributes) shouldBe Json.parseToJsonElement(
                """{
                    "requestType":"EnergySupplierFrameworkAgreement","requestedScopes":[],
                    "status":"Pending","textReference":"text-reference",$requiredDates $optionalDates
                }""",
            )
        }

        test("grant attributes serialize all dates with nullable dates present=$withNullableDates") {
            val attributes = grant.toGetResponse().data.attributes.copy(
                validTo = summer.takeIf { withNullableDates },
            )
            val optionalGrantDates = if (withNullableDates) {
                """, "validTo":"$summerOutput" """
            } else {
                ""
            }

            Json.encodeToJsonElement(attributes) shouldBe Json.parseToJsonElement(
                """{
                    "status":"Active","scopes":[],"validFrom":"$winterOutput",
                    "createdAt":"$winterOutput","updatedAt":"$summerOutput" $optionalGrantDates
                }""",
            )
        }
    }

    test("nullable response timestamps can also be serialized as explicit nulls") {
        val json = Json { encodeDefaults = true }
        val documentJson = json.encodeToJsonElement(document.toGetResponse().data.attributes).jsonObject
        val requestJson = json.encodeToJsonElement(request.toGetResponse().data.attributes).jsonObject
        val grantJson = json.encodeToJsonElement(
            grant.toGetResponse().data.attributes.copy(validTo = null),
        ).jsonObject

        for (attributes in listOf(documentJson, requestJson)) {
            attributes.getValue("requestedGrantValidFrom") shouldBe JsonNull
            attributes.getValue("requestedGrantValidTo") shouldBe JsonNull
        }
        grantJson.getValue("validTo") shouldBe JsonNull
    }

    for (input in listOf("2026-07-15T12:34:56.123Z", "2026-07-15T14:34:56.123+02:00")) {
        test("create request and document timestamps still accept $input and serialize in UTC") {
            val dates = """"requestedGrantValidFrom":"$input","requestedGrantValidTo":"$input" """
            val documentAttributes = Json.decodeFromString<CreateAuthorizationDocumentAttributes>(
                """{"documentType":"EnergySupplierFrameworkAgreement",$dates}""",
            )
            val requestAttributes = Json.decodeFromString<CreateAuthorizationRequestAttributes>(
                """{"requestType":"EnergySupplierFrameworkAgreement",$dates}""",
            )
            val expectedInstant = Instant.parse("2026-07-15T12:34:56.123Z")
            documentAttributes.requestedGrantValidFrom shouldBe expectedInstant
            documentAttributes.requestedGrantValidTo shouldBe expectedInstant
            requestAttributes.requestedGrantValidFrom shouldBe expectedInstant
            requestAttributes.requestedGrantValidTo shouldBe expectedInstant
            val expectedDates = """
                "requestedGrantValidFrom":"2026-07-15T12:34:56.123Z",
                "requestedGrantValidTo":"2026-07-15T12:34:56.123Z"
            """.trimIndent()

            Json.encodeToJsonElement(documentAttributes) shouldBe Json.parseToJsonElement(
                """{"documentType":"EnergySupplierFrameworkAgreement",$expectedDates}""",
            )
            Json.encodeToJsonElement(requestAttributes) shouldBe Json.parseToJsonElement(
                """{"requestType":"EnergySupplierFrameworkAgreement",$expectedDates}""",
            )
        }
    }

    for ((instant, output) in listOf(winter to winterOutput, summer to summerOutput)) {
        test("single and collection metadata use exact Oslo timestamp $output") {
            val expectedSingle = Json.parseToJsonElement("""{"createdAt":"$output"}""")
            listOf(
                document.copy(createdAt = instant).toGetResponse().meta,
                request.copy(createdAt = instant).toGetResponse().meta,
                grant.copy(createdAt = instant).toGetResponse().meta,
            ).forEach { it shouldBe expectedSingle }

            mockkStatic(::now)
            try {
                every { now() } returns instant
                val pagination = Pagination(page = 0, size = 2)
                val expectedCollection = Json.parseToJsonElement(
                    """{"createdAt":"$output","totalItems":1,"totalPages":1,"page":0,"pageSize":2}""",
                )
                listOf(
                    Page(listOf(document), 1, pagination).toGetCollectionResponse().meta,
                    Page(listOf(request), 1, pagination).toGetCollectionResponse().meta,
                    Page(listOf(grant), 1, pagination).toGetCollectionResponse().meta,
                ).forEach { it shouldBe expectedCollection }
            } finally {
                unmockkStatic(::now)
            }
        }

        test("requested and granted scopes format ValidFrom as $output in both constraint kinds") {
            val constraints = AuthorizationScopeConstraintKind.entries.map { kind ->
                AuthorizationScopeConstraint(
                    constraintKind = kind,
                    attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                    value = ResourceConstraint.ValidFrom(instant),
                )
            }
            val requestedScope = RequestedAuthorizationScope(ResourceType.MeteringPointContract, constraints)
            val grantedScope = GrantedAuthorizationScope(
                PermissionCapability.Write,
                ResourceType.MeteringPointContract,
                constraints,
            )
            val expectedScope = """
                "resourceType":"MeteringPointContract",
                "appliesTo":[{"attribute":"validFrom","value":["$output"]}],
                "allowedChanges":[{"attribute":"validFrom","value":["$output"]}]
            """.trimIndent()

            Json.encodeToJsonElement(requestedScope.toResponse()) shouldBe Json.parseToJsonElement(
                """{$expectedScope}""",
            )
            Json.encodeToJsonElement(grantedScope.toResponse()) shouldBe Json.parseToJsonElement(
                """{"capability":"Write",$expectedScope}""",
            )
        }
    }
})
