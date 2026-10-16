package no.elhub.auth.v1.features.common.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType

@Serializable
data class RequestedScopeResponse(
    val resourceType: ResourceType,
    val appliesTo: List<ScopeConstraintResponse>,
    val allowedChanges: List<ScopeConstraintResponse>,
)

@Serializable
data class GrantedScopeResponse(
    val capability: PermissionCapability,
    val resourceType: ResourceType,
    val appliesTo: List<ScopeConstraintResponse>,
    val allowedChanges: List<ScopeConstraintResponse>,
)

@Serializable
data class ScopeConstraintResponse(
    val attribute: String,
    val value: List<String>,
)

fun RequestedAuthorizationScope.toResponse() = RequestedScopeResponse(
    resourceType = resourceType,
    appliesTo = constraints.filter { it.constraintKind == AuthorizationScopeConstraintKind.AppliesTo }
        .map(AuthorizationScopeConstraint::toResponse),
    allowedChanges = constraints.filter { it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges }
        .map(AuthorizationScopeConstraint::toResponse),
)

fun GrantedAuthorizationScope.toResponse() = GrantedScopeResponse(
    capability = capability,
    resourceType = resourceType,
    appliesTo = constraints.filter { it.constraintKind == AuthorizationScopeConstraintKind.AppliesTo }
        .map(AuthorizationScopeConstraint::toResponse),
    allowedChanges = constraints.filter { it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges }
        .map(AuthorizationScopeConstraint::toResponse),
)

private fun AuthorizationScopeConstraint.toResponse() = ScopeConstraintResponse(
    attribute = attribute.apiName,
    value = when (val constraint = value) {
        is ResourceConstraint.MeteringPoints -> constraint.ids.map { it.value }.sorted()
        is ResourceConstraint.ContractTypes -> constraint.types.map { it.name }.sorted()
        is ResourceConstraint.ValidFrom -> listOf(constraint.value.toString())
    },
)
