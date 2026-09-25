# Architecture: Ticket Management REST API

## Module / package layout

Single Spring Boot module, package-by-domain, per `rules/java-springboot.md`:

```text
com.ticketmanagement
├── TicketManagementApplication.java
├── ticket/
│   ├── controller/
│   │   ├── TicketController.java        # /api/v1/tickets, /api/v1/tickets/{id}, /transitions
│   │   └── CommentController.java       # /api/v1/tickets/{id}/comments
│   ├── service/
│   │   ├── TicketService.java           # interface
│   │   ├── TicketServiceImpl.java       # owns state machine, orchestrates repository calls
│   │   ├── CommentService.java          # interface
│   │   └── CommentServiceImpl.java
│   ├── repository/
│   │   ├── TicketRepository.java        # Spring Data JPA, extends JpaRepository<Ticket, UUID> + JpaSpecificationExecutor for search/filter
│   │   └── CommentRepository.java
│   ├── entity/
│   │   ├── Ticket.java
│   │   ├── Comment.java
│   │   ├── Priority.java                # enum
│   │   └── TicketStatus.java            # enum
│   ├── dto/
│   │   ├── TicketCreateRequest.java     # record
│   │   ├── TicketUpdateRequest.java     # record, all fields Optional/nullable
│   │   ├── TicketTransitionRequest.java # record { targetStatus }
│   │   ├── CommentCreateRequest.java    # record
│   │   ├── TicketResponse.java
│   │   └── CommentResponse.java
│   └── exception/
│       ├── TicketNotFoundException.java
│       └── InvalidTransitionException.java
└── common/
    ├── config/
    │   └── PaginationProperties.java    # @ConfigurationProperties(prefix = "app.pagination") — default/max page size
    ├── exception/
    │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice — single source of ErrorResponse mapping
    │   ├── ErrorCode.java               # enum: VALIDATION_FAILED, TICKET_NOT_FOUND, INVALID_TRANSITION, UNKNOWN_FILTER
    │   └── ErrorResponse.java           # shared error DTO (record)
    └── dto/
        └── PageResponse.java            # generic { content, page, size, totalElements, totalPages }
```

## Request flow

```text
HTTP request
   │
   ▼
Controller (ticket/controller)
   - @Valid on request DTO → Bean Validation runs first; failures short-circuit to
     GlobalExceptionHandler before the controller body executes
   - maps path/query params to service call
   - no business logic, no repository access
   │
   ▼
Service (ticket/service)
   - TicketServiceImpl: business rules live here
       • create(): sets status = OPEN, id, timestamps
       • update(): applies only present fields from TicketUpdateRequest
       • transition(): looks up current status, checks the static
         transition table (state-machine.md), throws
         InvalidTransitionException on rejection, else updates status
       • search/filter: builds a JPA Specification from q/status params
   - throws domain exceptions (TicketNotFoundException, InvalidTransitionException)
     rather than returning nulls/booleans
   │
   ▼
Repository (ticket/repository)
   - TicketRepository / CommentRepository — Spring Data JPA, no business rules
   - JpaSpecificationExecutor<Ticket> backs the combined search+filter query
   │
   ▼
Database (H2 dev/test, MySQL prod/docker — same schema via Flyway)
```

Errors flow back up as thrown exceptions; `GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place that turns `MethodArgumentNotValidException`, `TicketNotFoundException`, and `InvalidTransitionException` into the shared `ErrorResponse` shape with the correct HTTP status (400/404/409 respectively) — controllers never build error bodies themselves.

## State machine implementation

`TicketServiceImpl` holds a static, immutable transition map built once (e.g. an `EnumMap<TicketStatus, Set<TicketStatus>>`):

```java
private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
    TicketStatus.OPEN,        Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
    TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED),
    TicketStatus.RESOLVED,    Set.of(TicketStatus.CLOSED),
    TicketStatus.CLOSED,      Set.of(),
    TicketStatus.CANCELLED,   Set.of()
);
```

`transition(id, targetStatus)`:
1. Load the ticket (or throw `TicketNotFoundException`).
2. Look up `ALLOWED_TRANSITIONS.get(current)`; if it doesn't contain `targetStatus`, throw `InvalidTransitionException(current, targetStatus)` — no write happens.
3. Otherwise set `status = targetStatus`, update `updatedAt`, save.

This is the literal reference implementation the tasks phase should follow, and it directly mirrors state-machine.md so the table and the code cannot silently drift apart.

## Cross-cutting concerns

- **Validation**: Bean Validation annotations on request DTOs (`@NotBlank`, etc.) run before the controller method body; `priority`/`status`/`targetStatus` enum values are validated by Jackson enum deserialization (unrecognized value → 400) plus explicit checks where needed.
- **Pagination defaults**: `app.pagination.default-size` / `app.pagination.max-size` bound via `PaginationProperties`, never hardcoded, per constitution Principle IV and `rules/java-springboot.md`.
- **Timestamps**: `createdAt`/`updatedAt` set via JPA `@PrePersist`/`@PreUpdate` on the entity, using `Instant.now()` — no manual timestamp handling in services.
