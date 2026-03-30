---
name: json-api-compliance
description: >
  Use when writing any Route, DTO, or error response.
  Defines JSON:API document shapes, field rules, Kotlin DTO patterns using elhub-jsonapi,
  error helper functions, and JSON Schema layout. Load before generating any API response or request DTO.
---
# JSON:API Compliance

All responses use `Content-Type: application/vnd.api+json`.

## Field rules

| Field | Rule |
| ----- | ---- |
| `type` | PascalCase constant, e.g. `"AuthorizationRequest"`. Must match between request and response. |
| `id` | UUID string. Present on responses; omitted from POST request bodies. |
| `attributes` | Domain state. Timestamps as ISO 8601 with offset: `"2025-12-17T12:51:57+01:00"`. |
| `relationships` | References: `data: { type, id }`. |
| `meta` (resource) | Read-only contextual data (display names, etc.) — not domain state. |
| `links` (resource) | `self` URI. Documents also add `file` for PDF. |
| `links` (top-level) | **Required** on all resource/collection responses. Must include `self`. |
| `meta` (top-level) | **Required** on all responses. Must include `createdAt`. |

## Kotlin response DTOs

### With relationships (standard)

```kotlin
@Serializable data class FooAttributes(val status: String) : JsonApiAttributes
@Serializable data class FooRelationships(val party: JsonApiRelationshipToOne) : JsonApiRelationships
@Serializable data class FooMeta(val displayName: String) : JsonApiResourceMeta
@Serializable data class FooLinks(val self: String) : JsonApiResourceLinks

typealias FooResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    FooAttributes, FooRelationships, FooMeta, FooLinks>

fun Foo.toResponse() = FooResponse(
    data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "Foo", id = this.id.toString(),
        attributes = FooAttributes(status = this.status.name),
        relationships = FooRelationships(party = JsonApiRelationshipToOne(JsonApiRelationshipData("OrgEntity", this.partyId))),
        meta = FooMeta(displayName = this.name),
        links = FooLinks(self = "$FOOS_PATH/${this.id}"),
    ),
    links = JsonApiLinks.ResourceObjectLink(FOOS_PATH),
    meta = JsonApiMeta(buildJsonObject { put("createdAt", this@toResponse.createdAt.toTimeZoneOffsetString()) }),
    // Note: use this@toResponse inside buildJsonObject to reference the outer receiver
)
```

### No relationships

Use empty implementations:

```kotlin
@Serializable class FooRelationships : JsonApiRelationships  // empty
@Serializable class FooMeta : JsonApiResourceMeta            // empty
```

### Available response variants

| Variant | Use when |
| ------- | -------- |
| `SingleDocumentWithRelationshipsAndMetaAndLinks<A,R,M,L>` | Preferred — fully compliant |
| `SingleDocumentWithRelationships<A,R>` | Has relationships, top-level meta/links not needed |
| `CollectionDocumentWithRelationshipsAndMetaAndLinks<A,R,M,L>` | Fully compliant collection |
| `CollectionDocumentWithRelationships<A,R>` | Collection with relationships |

**`JsonApiResponse.SingleDocument<A>` is only for deserialising external API responses. Never use it to generate app responses.**

## Request DTOs

```kotlin
@Serializable data class CreateRequestAttributes(val requestType: AuthorizationRequest.Type) : JsonApiAttributes
@Serializable data class CreateRequestMeta(val requestedBy: PartyIdentifier) : JsonApiResourceMeta

typealias JsonApiCreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>
// Without meta: JsonApiRequest.SingleDocument<Attributes>

fun JsonApiCreateRequest.toModel(actor: AuthorizedActor) = CreateRequestModel(
    requestType = this.data.attributes.requestType,
    requestedBy = this.data.meta.requestedBy,
    actor = actor,
)
```

## Error helpers (`features/common/Errors.kt`)

| Function | Status |
| -------- | ------ |
| `buildApiErrorResponse(status, title, detail)` | Any |
| `toInternalServerApiErrorResponse()` | 500 |
| `toNotFoundApiErrorResponse(detail?)` | 404 |
| `toValidationApiErrorResponse(detail?)` | 422 |
| `toUnsupportedErrorResponse(detail?)` | 415 |
| `toTypeMismatchApiErrorResponse(expected, actual)` | 409 |

All helpers include `meta.createdAt` automatically.

## OpenAPI spec (non-negotiable)

Every route addition, modification, or removal **must** update `src/main/resources/static/openapi.yaml`. Never leave spec out of sync.

## JSON Schema layout

```text
src/main/resources/static/schemas/
├── elhub-common.schema.json           # UUID, timestamps, partyIdentifier
├── json-api-framework.schema.json     # Structural types
├── {domain}/
│   ├── *-common.schema.json           # $defs only
│   ├── *-resource.schema.json         # Single resource
│   ├── *-collection.schema.json       # Array
│   └── *-submission.schema.json       # POST body
```

`*-common.schema.json` defines `$defs`. Others `$ref` into it. Do not duplicate field definitions.
