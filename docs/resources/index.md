# Resources

Resources are the business records used to propose, evidence, and apply
authorization. These pages describe their meaning and lifecycle, not every
wire-format field.

| Resource | Role |
|----------|------|
| [Authorization request](authorization-request.md) | Electronic proposal and decision. |
| [Authorization document](authorization-document.md) | Generated PDF and verified signature evidence. |
| [Authorization grant](authorization-grant.md) | Permission produced by an approved source. |
| [Authorization scope](authorization-scope.md) | Capability and constraints embedded in proposals and grants. |

Requests, documents, and grants have system-generated UUID identifiers.
Agent relationships use the identities described in the
[conceptual model](../concepts/conceptual-model.md).

See [Authorization types in v1](authorization-types.md) for type-specific
inputs and the grants each type produces.

## Access and collections

Knowing a record's ID does not authorize access. Read rules and update rules
are distinct and are described per resource. Collection filters narrow an
actor's visible records; they do not expand authorization.

Collections support pagination and status filtering, with newest records
first. Grant collections also support grant-type and agent-role filters.
Current system actors can retrieve an individual grant by ID, but their
collection visibility still uses the principal/recipient relationship filter;
it is not an unrestricted system-wide list.

See the [OpenAPI specification](../../src/main/resources/static/openapi.yaml)
and [v1 schemas](../../src/main/resources/static/schemas/v1) for exact
API contracts.
