package no.elhub.auth.v0.features.common.party.dto

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne

fun AuthorizationParty.toJsonApiRelationship() =
    JsonApiRelationshipToOne(
        data = JsonApiRelationshipData(
            type = this.type.name,
            id = this.id
        )
    )
