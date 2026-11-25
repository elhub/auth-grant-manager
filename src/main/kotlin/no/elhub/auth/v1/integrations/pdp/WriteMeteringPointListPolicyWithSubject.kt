package no.elhub.auth.v1.integrations.pdp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.elhub.auth.plugin.policies.token.core.TokenPolicy
import no.elhub.auth.plugin.policies.token.write.meteringpoints.WriteMeteringPointPolicies
import no.elhub.auth.plugin.policies.token.write.meteringpoints.list.WriteMeteringPointListPolicy

/**
 * PDP write route as [WriteMeteringPointListPolicy], with `subject` in payload for service-token case.
 * `subject.actingGLN` is the organization's Elhub internal ID.
 */
internal object WriteMeteringPointListPolicyWithSubject :
    TokenPolicy<WriteMeteringPointListPolicyWithSubject.Request, WriteMeteringPointListPolicy.Response>(
        key = WriteMeteringPointPolicies.ALL.key,
        route = WriteMeteringPointPolicies.ALL.route,
    ) {
    override val requestSerializer = Request.serializer()
    override val responsePayloadSerializer = WriteMeteringPointListPolicy.Response.serializer()

    @Serializable
    data class Request(
        @SerialName("MeteringPoints")
        val meteringPoints: List<MeteringPoint>,
        val subject: Subject,
    )

    @Serializable
    data class MeteringPoint(
        @SerialName("MeteringPointID")
        val meteringPointId: String,
    )

    @Serializable
    data class Subject(
        @SerialName("actingGLN")
        val actingGln: String,
        @SerialName("authorizedFunctions")
        val authorizedFunctions: List<AuthorizedFunction>,
    )

    @Serializable
    data class AuthorizedFunction(
        @SerialName("functionName")
        val functionName: String,
    )
}
