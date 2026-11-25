# Authorization types in v1

An authorization type defines the inputs, validation, and grants for a
particular purpose. These v1 types are available through both the
[request and document processes](../processes/index.md).

A market entity seeks authorization to change metering-point contracts for an
organization. The principal is the organization whose contracts are affected;
`requestedOf` is the organization asked to authorize. A person must have
authority to approve for these organizations.

For these types, the service derives the permitted contract types; callers
cannot request arbitrary capabilities.

| Authorization type | Accepted type-specific inputs | Grants issued on approval |
|--------------------|-------------------------------|---------------------------|
| `ChangeOfEnergySupplierForOrganization` | Selected metering points. | One consumable `Write` grant per point for `EnergySupplier` contract changes. |
| `MoveInAndChangeOfEnergySupplierForOrganization` | Selected metering points and an optional effective date. | One consumable `Write` grant per point for `EndUser` and `EnergySupplier` contract changes, subject to the date constraint if supplied. |
| `EnergySupplierFrameworkAgreement` | Optional requested grant start and end; no caller-supplied scopes. | One persistent `Write` grant for `EndUser` and `EnergySupplier` contract changes, without a fixed metering-point list. |

## Change energy supplier

**Type:** `ChangeOfEnergySupplierForOrganization`

This authorizes energy-supplier contract changes on selected metering points.
The proposal contains exactly one `MeteringPointContract` scope with one
`meteringPoint.id` constraint containing between 1 and 50 IDs. IDs must be
valid 18-digit metering-point identifiers. Duplicate IDs collapse to distinct
points. Caller-supplied `allowedChanges` and requested grant dates are not
supported.

The service looks up the principal in Elhub structure data, obtains the
selected metering points for that organization, and resolves organization
and supplier names for the proposal.

**Outcome:** one consumable `Write` grant per distinct metering point,
allowing only `EnergySupplier` contract changes.

## Move in and change energy supplier

**Type:** `MoveInAndChangeOfEnergySupplierForOrganization`

This authorizes both `EndUser` and `EnergySupplier` contract changes on
selected metering points. The same scope shape and 1-to-50 ID limit apply.

The caller may supply one `allowedChanges` constraint for `validFrom`, with
one ISO date or timestamp. A timestamp is interpreted as an Oslo calendar date.
The date must be today or in the past. Omit it for a future move-in; a
future date is not accepted in this proposal.

When present, the move-in date becomes a contract-date restriction at midnight
in Oslo. Requested grant dates are not supported.

**Current implementation:** preparation uses the same organization-specific
metering-point lookup as supplier change. It does not implement the v0
person-flow rule described in the older FAQ that checks that the incoming
person is not the current end user.

**Outcome:** one consumable `Write` grant per distinct metering point,
allowing `EndUser` and `EnergySupplier` changes and carrying the optional
contract-date restriction.

## Establish an energy-supplier framework agreement

**Type:** `EnergySupplierFrameworkAgreement`

This provides continuing authorization for end-user and energy-supplier
contract changes on the principal's metering points, without selecting a fixed
list of points in the proposal.

Caller-supplied `requestedScopes` are not supported. Optional requested grant
start and end instants determine the agreement's
[validity](index.md#validity-and-time). Organization and supplier names are
resolved for the proposal; no per-point lookup is performed for this type.

**Outcome:** exactly one persistent `Write` grant for `MeteringPointContract`,
allowing `EndUser` and `EnergySupplier` changes without a `meteringPoint.id`
constraint.

## Common outcomes and exceptions

Creating a proposal does not grant permission. Approval or successful document
confirmation is required before any grants are created. Unsupported scope
shapes, invalid identifiers or dates, invalid actor types, or failed structure
data lookups prevent creation.

Both channels produce the same permission shape for a given business type.
They do not perform the downstream market process; other services must
evaluate and apply the resulting permission.

## Implementation references

* [Request specification validation](../../src/main/kotlin/no/elhub/auth/v1/api/requests/request/AuthorizationRequestSpecificationMapper.kt)
* [Document specification validation](../../src/main/kotlin/no/elhub/auth/v1/api/documents/request/DocumentSpecificationMapper.kt)
* [Request preparation](../../src/main/kotlin/no/elhub/auth/v1/requesthandlers)
* [Document preparation](../../src/main/kotlin/no/elhub/auth/v1/documenthandlers)
* [Request grant generation](../../src/main/kotlin/no/elhub/auth/v1/features/requests/RequestToGrants.kt)
* [Document grant generation](../../src/main/kotlin/no/elhub/auth/v1/features/documents/DocumentToGrants.kt)
