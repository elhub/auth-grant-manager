# Authorization scopes

A scope describes what is proposed or permitted. In v1 it is embedded in
requests, documents, and grants, not exposed as a standalone scope resource.

Requested scopes describe the permission sought in a
[request](../resources/authorization-request.md) or
[document](../resources/authorization-document.md). Granted scopes describe
what the resulting [grant](../resources/authorization-grant.md) permits.
Scopes have no standalone identifier or access policies; access follows
the parent resource.

## Relevant Links

* [Scope schema](../../src/main/resources/static/schemas/v1/authorization-scope.schema.json)
* [Constraint schema](../../src/main/resources/static/schemas/v1/authorization-scope-constraint.schema.json)

## Attributes

| Name | Description | Format |
|------|-------------|--------|
| `resourceType` | Kind of business resource covered. | Enum: `MeteringPointContract`. |
| `capability` | Operation permitted by a granted scope. Current approval flows produce `Write`. | Enum: `Read` or `Write`; granted scopes only. |
| `appliesTo` | Restricts which resources are covered. | Array of constraint objects. |
| `allowedChanges` | Restricts what changes are permitted and which values may be used. | Array of constraint objects. |

Each constraint contains the following fields:

| Name | Description | Format |
|------|-------------|--------|
| `attribute` | Field the constraint applies to. | String: `meteringPoint.id`, `type`, or `validFrom`. |
| `value` | Values allowed by the constraint. | Array of strings: metering-point IDs, contract types, or a date-time, depending on the attribute. |

## Constraints

Values must match the attribute: `meteringPoint.id` takes metering-point IDs,
`type` takes contract types (`EnergySupplier` or `EndUser`), and an
`allowedChanges` constraint on `validFrom` limits the contract's effective
date, not the grant's validity.

The [authorization type](authorization-types.md) determines which scope
inputs are accepted and which constraints appear on the resulting grant.
Granted scopes are tied to the grant's principal and recipient and cannot be
edited through a grant update.
