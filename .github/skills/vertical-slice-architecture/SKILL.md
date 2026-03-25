---
name: vertical-slice-architecture
description: >
  Use when creating or modifying any feature code.
  Defines the feature/action directory layout, Route/Handler/Error/DTO responsibilities,
  Module.kt wiring, and shared kernel structure.
  Load before generating any new feature, action slice, or module.
---

# Vertical Slice Architecture

Code is organised by **feature**, not by technical layer. Each action slice contains everything needed to implement one use case — Route, Handler, DTOs, errors,
and models — colocated in a single directory.

## Directory layout

```text
src/main/kotlin/no/elhub/auth/
├── Application.kt
├── config/                         # Cross-cutting: Database, Serialization, ErrorHandling, Logging
└── features/
    ├── common/                     # Cross-feature kernel (see Shared Code below)
    ├── openapi/
    ├── requests/                   # Feature domain
    │   ├── Module.kt               # Koin bindings + route mounting for this domain
    │   ├── AuthorizationRequest.kt # Domain model
    │   ├── common/                 # Repository interface + Exposed Table objects
    │   ├── create/                 # Action slice
    │   ├── get/
    │   ├── update/
    │   └── query/
    ├── documents/
    ├── grants/
    ├── businessprocesses/
    └── filegenerator/
```

## Action slice layout

```text
features/requests/create/
├── Route.kt
├── Handler.kt
├── CreateError.kt
├── dto/
│   ├── JsonApiCreateRequest.kt
│   └── JsonApiCreateResponse.kt
└── model/
    └── CreateRequestModel.kt
```

## Layer responsibilities

### Route.kt — HTTP only

- Authenticates, deserialises, delegates, maps the `Either` result to HTTP. Contains no business logic.
- Handler methods only accept business objects, and not care about ktor implementation details.
- All routes must have an authProvider and a handlerInterface as input to the route.

```kotlin
fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    post {
        val actor = authProvider.authorizeMaskinporten(call)
            .getOrElse { return@post call.respond(HttpStatusCode.Unauthorized, it.toApiErrorResponse()) }

        val body = call.receive<JsonApiCreateRequest>()

        handler(body.toModel(actor))
            .fold(
                ifLeft = { error ->
                    val (status, apiError) = error.toApiErrorResponse()
                    call.respond(status, apiError)
                },
                ifRight = { result ->
                    call.respond(HttpStatusCode.Created, result.toCreateResponse())
                }
            )
    }
}
```

- **Rules:** No business logic. No database access. Always responds with JSON:API-compliant bodies, including errors.

### Handler.kt — business logic

- Orchestrates services and repositories using `either { }`. Never imports Ktor types.
- Core logic is always in the Handler
- Service can be seen as a repository or a client which collects data from external apps.


```kotlin
class Handler(
    private val partyService: PartyService,
    private val repo: RequestRepository,
) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
        val party = partyService.resolve(model.requestedBy)
            .mapLeft { CreateError.PartyResolutionFailed }
            .bind()

        ensure(model.authorizedParty == party) { CreateError.AuthorizationError }

        repo.insert(model.toRequest())
            .mapLeft { CreateError.PersistenceError }
            .bind()
    }
}
```

**Rules:** Returns `Either<FeatureError, Result>`. All IO is `suspend`. No `runBlocking`.

### CreateError.kt — sealed error type

One sealed interface per action. Provides mapping to JSON:API error responses.

```kotlin
sealed interface CreateError {
    data object AuthorizationError : CreateError
    data object PersistenceError : CreateError
    data class BusinessError(val cause: BusinessProcessError) : CreateError
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> = when (this) {
    CreateError.AuthorizationError -> buildApiErrorResponse(HttpStatusCode.Forbidden, "Forbidden", "...")
    CreateError.PersistenceError -> toInternalServerApiErrorResponse()
    is CreateError.BusinessError -> buildApiErrorResponse(HttpStatusCode.UnprocessableEntity, "...", "...")
}
```

### DTOs — JSON:API serialisation only

Live in `dto/` within the action slice. Provide `toModel()` and `toResponse()` extension functions. Never referenced outside their slice.

## Module.kt — DI + routing

- Each feature domain has one `Module.kt` that registers bindings and mounts routes.
- Each module needs to be in application.yml under ktor.application.modules list.

```kotlin
fun Application.requestsModule() {
    dependencies {
        provide<ExposedRequestRepository> {
            ExposedRequestRepository(resolve(), resolve()) // resolve resolves from Ktor Native Dependency registry
        }
        provide<CreateHandler> {
            CreateHandler(resolve(), resolve(), resolve(), resolve())
        }
        provide<GetHandler> {
            GetHandler(resolve())
        }
    }

    val createHandler: CreateHandler by dependencies
    val getHandler: GetHandler by dependencies
    val authorizationProvider: AuthorizationProvider by dependencies

    routing {
        route(REQUESTS_PATH) {
            createRoute(createHandler, authorizationProvider)
            getRoute(getHandler, authorizationProvider)
        }
    }
}
```

When multiple action handlers exist, alias imports avoid name collisions:

```kotlin
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.create.route  as createRoute
```

## Shared code

### Feature-level (`features/{domain}/common/`)

Repository interfaces and Exposed Table objects for that domain.

### Cross-feature (`features/common/`)

| File / Package | Contents                                                                                     |
|----------------|----------------------------------------------------------------------------------------------|
| `Errors.kt`    | Base error types (`RepositoryReadError`, `RepositoryWriteError`) and JSON:API error builders |
| `auth/`        | `AuthorizationProvider`, auth-related types                                                  |
| `party/`       | `PartyService`, `AuthorizationParty`, `PartyRepository`                                      |
| `person/`      | External person-resolution client                                                            |

## Adding a new action

Whenever a new feature is getting implemented

Example: `DELETE /authorization-requests/{id}`

1. Create `features/requests/delete/`
2. Add `Handler.kt` returning `Either<DeleteError, Unit>`
3. Add `DeleteError.kt` sealed interface with `toApiErrorResponse()`
4. Add `Route.kt` — HTTP wiring only
5. Add DTOs if the endpoint has a request/response body
6. Register `DeleteHandler` in `features/requests/Module.kt`
7. Mount `deleteRoute(get(), get())` in the routing block

**Do not** add the handler to an existing file. **Do not** create a shared `RequestService` to hold logic from multiple slices. Each action slice is
self-contained.
