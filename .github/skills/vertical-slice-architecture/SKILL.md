---
name: vertical-slice-architecture
description: >
  Use when creating or modifying any feature code.
  Defines the feature/action directory layout, Route/Handler/Error/DTO responsibilities,
  Module.kt wiring, and shared kernel structure.
  Load before generating any new feature, action slice, or module.
---
# Vertical Slice Architecture

Code is organised by **feature/action**, not by layer. Each action slice contains Route, Handler, DTOs, and errors — colocated.

## Directory layout

```text
features/
├── common/                      # Cross-feature kernel
├── requests/                    # feature name, e.g. documents, common,
│   ├── Module.kt                # DI bindings + route mounting
│   ├── AuthorizationRequest.kt  # Domain model
│   ├── common/                  # Repository interface + Exposed Table objects
│   ├── create/                  # Action slice: Route, Handler, CreateError, dto/
│   ├── get/
│   ├── update/
│   └── query/
```

## Layer responsibilities

### Route.kt — HTTP only

Authenticates → deserialises → delegates to Handler → maps `Either` to HTTP. No business logic.

#### Auth method selection

| Method | Use when |
| ------ | -------- |
| `authorizeMaskinporten(call)` | Machine-to-machine only (orgs via Maskinporten) |
| `authorizeEndUser(call)` | End-users only (persons, e.g. BankID) |
| `authorizeEndUserOrMaskinporten(call)` | Both allowed |

Examples: `POST /authorization-documents` → `authorizeMaskinporten`.
`GET /authorization-documents/{id}` → `authorizeEndUserOrMaskinporten`.
`PATCH /authorization-requests/{id}` → `authorizeEndUser`.

```kotlin
fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    post {
        val actor = authProvider.authorizeMaskinporten(call)
            .getOrElse { return@post call.respond(HttpStatusCode.Unauthorized, it.toApiErrorResponse()) }
        val body = call.receive<JsonApiCreateRequest>()
        handler(body.toModel(actor))
            .fold(
                ifLeft  = { call.respond(it.toApiErrorResponse().first, it.toApiErrorResponse().second) },
                ifRight = { call.respond(HttpStatusCode.Created, it.toCreateResponse()) }
            )
    }
}
```

### Handler.kt — business logic

Orchestrates services/repos with `either { }`. Never imports Ktor types. Returns `Either<FeatureError, Result>`. All IO `suspend`.

```kotlin
class Handler(private val partyService: PartyService, private val repo: RequestRepository) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
        val party = partyService.resolve(model.requestedBy).mapLeft { CreateError.PartyResolutionFailed }.bind()
        ensure(model.authorizedParty == party) { CreateError.AuthorizationError }
        repo.insert(model.toRequest()).mapLeft { CreateError.PersistenceError }.bind()
    }
}
```

### CreateError.kt — sealed error type

One sealed interface per action slice with `toApiErrorResponse()`:

```kotlin
sealed interface CreateError {
    data object AuthorizationError : CreateError
    data object PersistenceError : CreateError
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = when (this) {
    CreateError.AuthorizationError -> buildApiErrorResponse(HttpStatusCode.Forbidden, "Forbidden", "...")
    CreateError.PersistenceError   -> toInternalServerApiErrorResponse()
}
```

### DTOs

Live in `dto/` within the action slice. Provide `toModel()` / `toResponse()` extensions. Not referenced outside their slice.

## Module.kt — DI + routing

```kotlin
fun Application.requestsModule() {
    dependencies {
        provide<RequestRepository> { ExposedRequestRepository(resolve()) }
        provide<CreateHandler> { CreateHandler(resolve(), resolve()) }
        provide<GetHandler> { GetHandler(resolve()) }
    }
    val createHandler: CreateHandler by dependencies
    val getHandler: GetHandler by dependencies
    val authProvider: AuthorizationProvider by dependencies
    routing {
        route(REQUESTS_PATH) {
            createRoute(createHandler, authProvider)
            getRoute(getHandler, authProvider)
        }
    }
}
```

Use aliased imports to avoid name collisions: `import ...create.Handler as CreateHandler`.

## Shared kernel (`features/common/`)

| File / Package | Contents |
| -------------- | -------- |
| `Errors.kt` | `RepositoryReadError`, `RepositoryWriteError`, JSON:API error builders |
| `auth/` | `AuthorizationProvider`, auth types |
| `party/` | `PartyService`, `AuthorizationParty`, `PartyRepository` |
| `person/` | External person-resolution client |

## Adding a new action slice

1. Create `features/{domain}/{action}/`
2. Add `Handler.kt` → `Either<ActionError, Result>`
3. Add `ActionError.kt` sealed interface with `toApiErrorResponse()`
4. Add `Route.kt` — HTTP wiring only
5. Add DTOs under `dto/` if needed
6. Register handler in `features/{domain}/Module.kt` and mount route
7. **Update `src/main/resources/static/openapi.yaml`**
8. If new domain: register in `src/main/resources/application.yaml` under `ktor.application.modules`, before `no.elhub.auth.features.common.ModuleKt.module`

Each action slice is self-contained. Never share a Handler across slices.

## OpenAPI spec (non-negotiable)

`src/main/resources/static/openapi.yaml` **must** be updated for every route change (add, modify, remove).
