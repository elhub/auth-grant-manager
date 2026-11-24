# Authorization scope

A scope describes what is proposed or permitted. In v1 it is embedded in
requests, documents, and grants, not exposed as a standalone scope resource.

## Components

| Component | Meaning |
|-----------|---------|
| `resourceType` | Kind of business resource: currently `MeteringPointContract`. |
| `capability` | Granted operation; current approval flows produce `Write`. Requested scopes do not carry a granted capability. |
| `appliesTo` | Which resources are covered, such as selected `meteringPoint.id` values. |
| `allowedChanges` | Which contract attributes may change and their permitted values. |

The supported constraint attributes are `meteringPoint.id`, `type`, and
`validFrom`. Contract-type values are `EnergySupplier` and `EndUser`.

An `allowedChanges.validFrom` value restricts the effective date of the
contract change. It is not the grant's own start date.

## Permission shapes produced by approval

| Business type | Metering-point restriction | Allowed contract types | Optional date restriction |
|---------------|----------------------------|------------------------|---------------------------|
| Supplier change | One point per grant | `EnergySupplier` | None. |
| Move-in and supplier change | One point per grant | `EndUser`, `EnergySupplier` | Specified move-in date at midnight in Oslo. |
| Framework agreement | No fixed point list | `EndUser`, `EnergySupplier` | None; agreement validity is recorded on the grant instead. |

Creation inputs are not an arbitrary policy language. The service validates
the [authorization type's input shape](authorization-types.md)
and derives its allowed contract types. A framework agreement does not accept
caller-supplied scopes, even though its response contains the derived scope.

Scopes remain tied to the grant's principal and recipient. No fixed point list
on a framework-agreement scope is not a permission to act for any organization.

## Implementation references

* [Scope model](../../src/main/kotlin/no/elhub/auth/v1/domain/Scope.kt)
* [Request scope generation](../../src/main/kotlin/no/elhub/auth/v1/domain/AuthorizationRequest.kt)
* [Document scope generation](../../src/main/kotlin/no/elhub/auth/v1/domain/documents/DocumentSpecification.kt)
