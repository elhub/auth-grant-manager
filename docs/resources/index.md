# Resources

This section describes the business resources in `auth-grant-manager` and
their relationships.

```mermaid
classDiagram
    class AuthorizationSource {
        <<abstract>>
        Basis for issuing a grant
    }
    class AuthorizationRequest {
        Records a requested approval or rejection
    }
    class AuthorizationDocument {
        Records approval through a verified signature
    }
    class AuthorizationGrant {
        Records who may act on whose behalf
    }
    class RequestedScope {
        Describes the permission sought
    }
    class GrantedScope {
        Defines what the grant permits
    }

    AuthorizationSource <|-- AuthorizationRequest
    AuthorizationSource <|-- AuthorizationDocument
    AuthorizationSource "1" -- "0..*" AuthorizationGrant: source
    AuthorizationSource "1" *-- "1..*" RequestedScope: requested scopes
    AuthorizationGrant "1" *-- "1..*" GrantedScope: granted scopes
```

`AuthorizationSource` is the superclass of `AuthorizationRequest` and `AuthorizationDocument`; it is not a separate
API resource. [Requested and granted scopes](../concepts/authorization-scopes.md)
are embedded objects, not standalone resources.

| Resource                                            | Role                                                         |
|-----------------------------------------------------|--------------------------------------------------------------|
| [Authorization request](authorization-request.md)   | Electronic proposal and decision.                            |
| [Authorization document](authorization-document.md) | Generated PDF and verified signature evidence.               |
| [Authorization grant](authorization-grant.md)       | Permission produced by an approved source.                   |
