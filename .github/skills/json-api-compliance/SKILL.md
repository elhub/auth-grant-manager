---
name: json-api-compliance
description: Use when writing any Route, DTO, or error response. Defines JSON:API document shapes, field rules, Kotlin DTO patterns using elhub-jsonapi, error helper functions, and JSON Schema layout. Load before generating any API response or request DTO.
---

# JSON:API Compliance

All responses use `application/vnd.api+json` and the JSON:API v1.1 document structure.

## Document shapes

### Single resource (GET by id, POST 201)

```json
{
  "data": {
    "id": "uuid",
    "type": "AuthorizationRequest",
    "attributes": {
      "status": "Pending",
      "requestType": "...",
      "createdAt": "2025-12-17T12:51:57+01:00"
    },
    "relationships": {
      "requestedBy": {
        "data": {
          "type": "OrganizationEntity",
          "id": "gln"
        }
      }
    },
    "meta": {
      "requestedByName": "Supplier AS"
    },
    "links": {
      "self": "/access/v0/authorization-requests/uuid"
    }
  },
  "links": {
    "self": "/access/v0/authorization-requests/uuid"
  },
  "meta": {
    "createdAt": "2025-12-17T12:51:57+01:00"
  }
}
```

### Collection (GET list)

```json
{
  "data": [
    {
      "id": "uuid-1",
      "type": "AuthorizationRequest",
      ...
    },
    ...
  ],
  "links": {
    "self": "/access/v0/authorization-requests"
  },
  "meta": {
    "createdAt": "2025-12-17T12:51:57+01:00"
  }
}
```

### Error (all error responses)

```json
{
  "errors": [
    { "status": "403", "title": "Forbidden", "detail": "Actor does not match authorized party." }
  ],
  "meta": { "createdAt": "2025-12-17T12:51:57+01:00" }
}
```

Every endpoint returns a JSON:API document — including 4xx and 5xx responses.

## Field rules

| Field               | Rule                                                                                                |
|---------------------|-----------------------------------------------------------------------------------------------------|
| `type`              | PascalCase constant string, e.g. `"AuthorizationRequest"`. Must match between request and response. |
| `id`                | UUID string. Present on responses; omitted from POST request bodies.                                |
| `attributes`        | Domain state fields. Timestamps in ISO 8601 with offset: `"2025-12-17T12:51:57+01:00"`.             |
| `relationships`     | References to other resources. Each has `data: { type, id }`.                                       |
| `meta` (resource)   | Read-only contextual data that is not domain state, e.g. display names.                             |
| `links` (resource)  | `self` URI for this resource. Documents add `file` for PDF download.                                |
| `links` (top-level) | Required on all resource and collection responses. Must include `self`.                             |
| `meta` (top-level)  | Required on all responses. Must include `createdAt`.                                                |

**`meta` is not for domain state.** If a field can change and affects business logic, it belongs in `attributes`.

## Kotlin DTOs

DTOs use types from `no.elhub.elhub-jsonapi` (on classpath via Gradle). Import path: `no.elhub.jsonapi.*`.

### Response DTO

```kotlin
@Serializable
data class CreateResponseAttributes(
    val status: String,
    val requestType: String,
    val createdAt: String,
    val updatedAt: String,
) : JsonApiAttributes

@Serializable
data class CreateResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias SingleCreateResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
        CreateResponseAttributes,
        CreateResponseRelationships,
        CreateResponseMeta,
        CreateResponseLinks
        >

fun AuthorizationRequest.toCreateResponse(): SingleCreateResponse = SingleCreateResponse(
    data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "AuthorizationRequest",
        id = this.id.toString(),
        attributes = CreateResponseAttributes(
            status = this.status.name,
            requestType = this.type.name,
            createdAt = this.createdAt.toTimeZoneOffsetString(),
            updatedAt = this.updatedAt.toTimeZoneOffsetString(),
        ),
        relationships = CreateResponseRelationships(
            requestedBy = JsonApiRelationshipToOne(JsonApiRelationshipData("OrganizationEntity", this.requestedBy.id)),
            requestedFrom = JsonApiRelationshipToOne(JsonApiRelationshipData("OrganizationEntity", this.requestedFrom.id)),
        ),
        meta = CreateResponseMeta(requestedByName = this.requestedByName),
        links = CreateResponseLinks(self = "$REQUESTS_PATH/${this.id}"),
    ),
    links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
    meta = JsonApiMeta(buildJsonObject { put("createdAt", this@toCreateResponse.createdAt.toTimeZoneOffsetString()) }),
)
```

Note: `this@toCreateResponse` is needed inside the `buildJsonObject` lambda to reference the outer receiver.

### Request DTO

```kotlin
@Serializable
data class CreateRequestAttributes(
    val requestType: AuthorizationRequest.Type,
) : JsonApiAttributes

@Serializable
data class CreateRequestMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
) : JsonApiResourceMeta

typealias JsonApiCreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>

fun JsonApiCreateRequest.toModel(actor: AuthorizedActor): CreateRequestModel = CreateRequestModel(
    requestType = this.data.attributes.requestType,
    requestedBy = this.data.meta.requestedBy,
    requestedFrom = this.data.meta.requestedFrom,
    actor = actor,
)
```

## Error helpers (from `features/common/Errors.kt`)

| Function                                           | When to use                       |
|----------------------------------------------------|-----------------------------------|
| `buildApiErrorResponse(status, title, detail)`     | Any custom error                  |
| `toInternalServerApiErrorResponse()`               | Unexpected infrastructure failure |
| `toNotFoundApiErrorResponse()`                     | Resource not found                |
| `toValidationApiErrorResponse(detail)`             | Input failed validation           |
| `toTypeMismatchApiErrorResponse(expected, actual)` | JSON:API `type` field wrong       |

All helpers include `meta.createdAt` automatically.

## JSON Schema layout

Schemas live in `src/main/resources/static/schemas/` and are referenced by the OpenAPI spec.

```
schemas/
├── elhub-common.schema.json              # UUID, timestamps, partyIdentifier
├── authorization-common.schema.json      # Shared domain fields
├── json-api-framework.schema.json        # Structural types
├── json-api-error.schema.json
├── requests/
│   ├── authorization-request-common.schema.json      # $defs: attributes, relationships, meta, links
│   ├── authorization-request-resource.schema.json    # Single resource (assembles from common)
│   ├── authorization-request-collection.schema.json  # Array of resources
│   └── authorization-request-submission.schema.json  # POST request body
└── ...
```

`*-common.schema.json` defines `$defs`. The other three files assemble documents by `$ref`-ing into it and declaring which fields are `required`. Do not
duplicate field definitions across these files.
