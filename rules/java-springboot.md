# Java 21 / Spring Boot Conventions

Aligned with constitution Principle I and the team Java coding standards.
**Detected Java version:** 21 (constitution; no `pom.xml` / Gradle file in repo yet).
**Base package:** not present under `src/main/java` yet — use
`com.ticketmanagement` until a longer common prefix exists; nest by domain
(e.g. `com.ticketmanagement.ticket`, `com.ticketmanagement.rag`).

Principles: SOLID, DRY, KISS, YAGNI, OWASP, data-oriented design, DDD where it
clarifies boundaries. Prefer Effective Java idioms (immutability, composition,
`Optional`/empty collections over `null`).

## Stack (this project)

| Concern | Choice |
|---------|--------|
| Language / runtime | Java 21 |
| Framework | Spring Boot |
| Build | Maven (preferred) |
| Persistence | MySQL (prod); H2 (local & tests) |
| Vector store | Elasticsearch via Spring AI `ElasticsearchVectorStore` |
| API | REST (`rules/api-standards.md`) |
| Logging | Logback |
| Tests | JUnit + Spring Boot Test (`rules/testing.md`) |

Do not introduce MySQL, Memcached, or Kafka unless the constitution is amended.
Bind all tunables (including RAG top-K / similarity) via
`@ConfigurationProperties` — never hardcode.

## Package structure

Layout under `{basePackage}.{domain}/` (create only packages you need):

```text
config/        # @Configuration, @ConfigurationProperties, bean wiring
controller/    # @RestController — HTTP only
service/       # Business interfaces + implementations
repository/    # Spring Data JPA repositories
entity/        # JPA @Entity types
dto/           # Request/response records or Lombok DTOs
client/        # RestClient / WebClient wrappers (external APIs)
advisors/      # Spring AI advisors
filter/        # Servlet / WebFlux filters (auth, CORS)
exception/     # Custom *Exception types + ErrorCode
```

Optional when needed: `tool/` for Spring AI `@Tool` methods.

```text
com.ticketmanagement
├── TicketManagementApplication.java
├── ticket/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   └── exception/          # or shared under com.ticketmanagement.exception
├── rag/
│   ├── controller/
│   ├── service/
│   ├── config/
│   └── advisors/
└── common/
    ├── config/
    ├── exception/
    └── dto/                # shared error payload types
```

## Layering

| Layer | Responsibility | Must not |
|-------|----------------|----------|
| **Controller** | Map HTTP ↔ DTOs, `@Valid`, status codes, call service | Contain business rules, talk to repositories, or embed RAG prompts |
| **Service** | Domain logic, transactions, orchestration | Depend on HTTP types (`HttpServletRequest`, raw `ResponseEntity` construction beyond return) |
| **Repository** | Persistence access | Contain business rules or map to API DTOs |

Flow: `Controller → Service → Repository` (and clients/vector store from services only).

```java
// ✅ Controller delegates
@PostMapping
public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
  TicketResponse created = ticketService.create(request);
  return ResponseEntity.status(HttpStatus.CREATED).body(created);
}

// ❌ Business logic in controller
@PostMapping("/{id}/transitions")
public TicketResponse transition(@PathVariable UUID id, @RequestBody TransitionRequest req) {
  Ticket t = ticketRepository.findById(id).orElseThrow(...);
  if (t.getStatus() == Status.CLOSED) { /* ... */ }  // belongs in service
}
```

Entities MUST NOT be used as `@RequestBody` / response bodies (expose DTOs).
Keep methods ≤ 50 lines; extract private helpers.

## Dependency injection

- Prefer **constructor injection**; make dependencies `private final`.
- Do **not** use field `@Autowired` on production beans.
- Prefer injecting by **interface** type when a service has an interface
  (`TicketService` → `TicketServiceImpl` in the same `service/` package).
- Mark concrete services `@Service`; avoid `@Component` for domain services
  unless there is no clearer stereotype.
- Configuration beans live in `config/` with `@Configuration` /
  `@ConfigurationProperties` (prefix documented; values from env /
  `application.yml` only).

```java
@Service
public class TicketServiceImpl implements TicketService {

  private final TicketRepository ticketRepository;

  public TicketServiceImpl(TicketRepository ticketRepository) {
    this.ticketRepository = ticketRepository;
  }
}
```

## Exception handling

- Throw custom `*Exception` types from `exception/` with a stable `ErrorCode`
  (`SCREAMING_SNAKE`), e.g. `throw new ApiException(ErrorCode.INVALID_TRANSITION)`.
- Map exceptions to the **shared error JSON** in `rules/api-standards.md` via a
  single `@ControllerAdvice` / `@RestControllerAdvice` (`@ExceptionHandler`).
- Validation failures (`MethodArgumentNotValidException`, constraint violations)
  → `400` + `details[]`.
- Not found → `404`; illegal state transitions → `409` + specific `code`.
- Do not leak stack traces or internal messages in the response body.
- Controllers do not catch-and-swallow domain exceptions; let advice translate them.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
    // map ex.getErrorCode() → status + ErrorResponse shape
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    // populate details[] per field
  }
}
```

## Naming

| Kind | Convention | Examples |
|------|------------|----------|
| Packages | all lowercase, no underscores | `com.ticketmanagement.ticket` |
| Classes / interfaces | `UpperCamelCase` | `TicketService`, `TicketServiceImpl` |
| Methods / fields | `lowerCamelCase` | `applyTransition` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` |
| Controllers | `*Controller` | `TicketController` |
| Services | `*Service` / `*ServiceImpl` | `TicketService` |
| Repositories | `*Repository` | `TicketRepository` |
| Entities | domain noun | `Ticket` |
| DTOs | `*Request` / `*Response` | `TicketCreateRequest` |
| Exceptions | `*Exception` | `ApiException`, `TicketNotFoundException` |
| Properties classes | `*Properties` | `RagRetrievalProperties` |
| Error codes | `SCREAMING_SNAKE` | `VALIDATION_FAILED` |

REST path naming follows `rules/api-standards.md`, not Java type names.

## Style and quality bar

- Google Java Style: 2-space indent, 100-column limit, no wildcard imports,
  braces always, one top-level class per file.
- Lombok **only** on DTOs (prefer Java records for immutable request/response
  when practical).
- Javadoc on every public controller, service, and repository method:
  summary, `@param`, `@return`, `@throws` as applicable (third-person:
  "Returns…", "Creates…").
- Read configuration from `application.yml` / env; never commit secrets
  (constitution Security & Secrets).
- Java language level: use only features available in **Java 21** (records,
  pattern matching for `switch`, sequenced collections, virtual threads if
  adopted deliberately — document why).

## Quick checklist

- [ ] Class lives in the correct package for its role
- [ ] Controller has no business logic; service owns domain rules
- [ ] Constructor injection; `final` dependencies
- [ ] Exceptions use `*Exception` + `ErrorCode`; advice emits API error shape
- [ ] Config via `@ConfigurationProperties`; RAG/tuning not hardcoded
- [ ] Naming matches table; DTOs ≠ entities on the wire
- [ ] Methods ≤ 50 lines; Google style + Javadoc on public API surface
- [ ] Sonar Java rules: BLOCKER / CRITICAL Bugs, Vulnerabilities, and
      Security Hotspots are must-fix; Code Smells are should-fix (see below)

# Sonar Rules (Java)

All code MUST comply with the following SonarQube rules for Java. This project
enforces 700 Java rules. Rules are grouped by type and ordered by severity
(BLOCKER first). Treat BLOCKER, CRITICAL, and all Vulnerability/Bug rules as
must-fix; Code Smells as should-fix.

Source of truth: `sonar_rules.json` (full export, all languages). This file is
the Java-only, human-readable subset for quick reference.

## Must-Fix Priority Rules (Blocker / Critical — Bugs, Vulnerabilities, Security Hotspots)

- **java:S2068** [Vulnerability / BLOCKER] — Credentials should not be hard-coded
- **java:S2115** [Vulnerability / BLOCKER] — A secure password should be used when connecting to a database
- **java:S2755** [Vulnerability / BLOCKER] — XML parsers should not be vulnerable to XXE attacks
- **java:S6373** [Vulnerability / BLOCKER] — XML parsers should not allow inclusion of arbitrary files
- **java:S6418** [Vulnerability / BLOCKER] — Secrets should not be hard-coded
- **java:S6437** [Vulnerability / BLOCKER] — Credentials should not be hard-coded
- **java:S2053** [Vulnerability / CRITICAL] — Password hashing functions should use an unpredictable salt
- **java:S2254** [Vulnerability / CRITICAL] — "HttpServletRequest.getRequestedSessionId()" should not be used
- **java:S2647** [Vulnerability / CRITICAL] — Basic authentication should not be used
- **java:S2658** [Vulnerability / CRITICAL] — Classes should not be loaded dynamically
- **java:S3329** [Vulnerability / CRITICAL] — Cipher Block Chaining IVs should be unpredictable
- **java:S4347** [Vulnerability / CRITICAL] — Secure random number generators should not output predictable values
- **java:S4423** [Vulnerability / CRITICAL] — Weak SSL/TLS protocols should not be used
- **java:S4426** [Vulnerability / CRITICAL] — Cryptographic keys should be robust
- **java:S4433** [Vulnerability / CRITICAL] — LDAP connections should be authenticated
- **java:S4601** [Vulnerability / CRITICAL] — "HttpSecurity" URL patterns should be correctly ordered
- **java:S4684** [Vulnerability / CRITICAL] — Persistent entities should not be used as arguments of "@RequestMapping" methods
- **java:S4830** [Vulnerability / CRITICAL] — Server certificates should be verified during SSL/TLS connections
- **java:S5344** [Vulnerability / CRITICAL] — Passwords should not be stored in plaintext or with a fast hashing algorithm
- **java:S5445** [Vulnerability / CRITICAL] — Insecure temporary file creation methods should not be used
- **java:S5527** [Vulnerability / CRITICAL] — Server hostnames should be verified during SSL/TLS connections
- **java:S5542** [Vulnerability / CRITICAL] — Encryption algorithms should be used with secure mode and padding scheme
- **java:S5547** [Vulnerability / CRITICAL] — Cipher algorithms should be robust
- **java:S5659** [Vulnerability / CRITICAL] — JWT should be signed and verified with strong cipher algorithms
- **java:S5876** [Vulnerability / CRITICAL] — A new session should be created during user authentication
- **java:S6432** [Vulnerability / CRITICAL] — Counter Mode initialization vectors should not be reused
- **java:S2095** [Bug / BLOCKER] — Resources should be closed
- **java:S2168** [Bug / BLOCKER] — Double-checked locking should not be used
- **java:S2189** [Bug / BLOCKER] — Loops should not be infinite
- **java:S2229** [Bug / BLOCKER] — Methods should not call same-class methods with incompatible "@Transactional" values
- **java:S2236** [Bug / BLOCKER] — Methods "wait(...)", "notify()" and "notifyAll()" should not be called on Thread instances
- **java:S2275** [Bug / BLOCKER] — Printf-style format strings should not lead to unexpected behavior at runtime
- **java:S2276** [Bug / BLOCKER] — "wait(...)" should be used instead of "Thread.sleep(...)" when a lock is held
- **java:S2689** [Bug / BLOCKER] — Files opened in append mode should not be used with "ObjectOutputStream"
- **java:S2695** [Bug / BLOCKER] — "PreparedStatement" and "ResultSet" methods should be called with valid indices
- **java:S3046** [Bug / BLOCKER] — "wait" should not be called when multiple locks are held
- **java:S3546** [Bug / BLOCKER] — Custom resources should be closed
- **java:S3753** [Bug / BLOCKER] — "@Controller" classes that use "@SessionAttributes" must call "setComplete" on their "SessionStatus" objects
- **java:S4602** [Bug / BLOCKER] — "@SpringBootApplication" and "@ComponentScan" should not be used in the default package
- **java:S5979** [Bug / BLOCKER] — Annotated Mockito objects should be initialized
- **java:S1114** [Bug / CRITICAL] — "super.finalize()" should be called at the end of "Object.finalize()" implementations
- **java:S1143** [Bug / CRITICAL] — Jump statements should not occur in "finally" blocks
- **java:S1175** [Bug / CRITICAL] — The signature of "finalize()" should match that of "Object.finalize()"
- **java:S2119** [Bug / CRITICAL] — "Random" objects should be reused
- **java:S2122** [Bug / CRITICAL] — "ScheduledThreadPoolExecutor" should not have 0 core threads
- **java:S2151** [Bug / CRITICAL] — "runFinalizersOnExit" should not be called
- **java:S2222** [Bug / CRITICAL] — Locks should be released on all paths
- **java:S2390** [Bug / CRITICAL] — Classes should not access their own subclasses during class initialization
- **java:S3518** [Bug / CRITICAL] — Zero should not be a possible denominator
- **java:S4275** [Bug / CRITICAL] — Getters and setters should access the expected fields
- **java:S5779** [Bug / CRITICAL] — Assertion methods should not be used within the try block of a try-catch catching an Error
- **java:S5783** [Bug / CRITICAL] — Only one method invocation is expected when testing checked exceptions
- **java:S5790** [Bug / CRITICAL] — JUnit5 inner test classes should be annotated with @Nested
- **java:S5845** [Bug / CRITICAL] — Assertions comparing incompatible types should not be made
- **java:S5856** [Bug / CRITICAL] — Regular expressions should be syntactically valid
- **java:S5994** [Bug / CRITICAL] — Regex patterns following a possessive quantifier should not always fail
- **java:S5996** [Bug / CRITICAL] — Regex boundaries should not be used in a way that can never be matched
- **java:S6001** [Bug / CRITICAL] — Back references in regular expressions should only refer to capturing groups that are matched before the reference
- **java:S6002** [Bug / CRITICAL] — Regex lookahead assertions should not be contradictory
- **java:S6104** [Bug / CRITICAL] — Map "computeIfAbsent()" and "computeIfPresent()" should not be used to add "null" values.
- **java:S6209** [Bug / CRITICAL] — Members ignored during record serialization should not be used
- **java:S6816** [Bug / CRITICAL] — Nullable injected fields and parameters should provide a default value
- **java:S6817** [Bug / CRITICAL] — Use of the "@Async" annotation on methods declared within a "@Configuration" class in Spring Boot
- **java:S6818** [Bug / CRITICAL] — "@Autowired" should only be used on a single constructor
- **java:S6857** [Bug / CRITICAL] — SpEL expression should have a valid syntax
- **java:S6881** [Bug / CRITICAL] — Virtual threads should be used for tasks that include heavy blocking operations
- **java:S7184** [Bug / CRITICAL] — "@Scheduled" annotation should only be applied to no-arg methods
- **java:S7185** [Bug / CRITICAL] — @EventListener methods should have one parameter at most
- **java:S2245** [Security Hotspot / CRITICAL] — Using pseudorandom number generators (PRNGs) is security-sensitive
- **java:S2257** [Security Hotspot / CRITICAL] — Using non-standard cryptographic algorithms is security-sensitive
- **java:S4502** [Security Hotspot / CRITICAL] — Disabling CSRF protections is security-sensitive
- **java:S4512** [Security Hotspot / CRITICAL] — Setting JavaBean properties is security-sensitive
- **java:S4544** [Security Hotspot / CRITICAL] — Using unsafe Jackson deserialization configuration is security-sensitive
- **java:S4790** [Security Hotspot / CRITICAL] — Using weak hashing algorithms is security-sensitive
- **java:S4792** [Security Hotspot / CRITICAL] — Configuring loggers is security-sensitive
- **java:S5042** [Security Hotspot / CRITICAL] — Expanding archive files without controlling resource consumption is security-sensitive
- **java:S5320** [Security Hotspot / CRITICAL] — Broadcasting intents is security-sensitive
- **java:S5322** [Security Hotspot / CRITICAL] — Receiving intents is security-sensitive
- **java:S5324** [Security Hotspot / CRITICAL] — Accessing Android external storage is security-sensitive
- **java:S5332** [Security Hotspot / CRITICAL] — Using clear-text protocols is security-sensitive
- **java:S5443** [Security Hotspot / CRITICAL] — Using publicly writable directories is security-sensitive
- **java:S5852** [Security Hotspot / CRITICAL] — Using slow regular expressions is security-sensitive

## All Java Rules by Type

### Vulnerability (34)

- `java:S2068` [BLOCKER] — Credentials should not be hard-coded
- `java:S2115` [BLOCKER] — A secure password should be used when connecting to a database
- `java:S2755` [BLOCKER] — XML parsers should not be vulnerable to XXE attacks
- `java:S6373` [BLOCKER] — XML parsers should not allow inclusion of arbitrary files
- `java:S6418` [BLOCKER] — Secrets should not be hard-coded
- `java:S6437` [BLOCKER] — Credentials should not be hard-coded
- `java:S2053` [CRITICAL] — Password hashing functions should use an unpredictable salt
- `java:S2254` [CRITICAL] — "HttpServletRequest.getRequestedSessionId()" should not be used
- `java:S2647` [CRITICAL] — Basic authentication should not be used
- `java:S2658` [CRITICAL] — Classes should not be loaded dynamically
- `java:S3329` [CRITICAL] — Cipher Block Chaining IVs should be unpredictable
- `java:S4347` [CRITICAL] — Secure random number generators should not output predictable values
- `java:S4423` [CRITICAL] — Weak SSL/TLS protocols should not be used
- `java:S4426` [CRITICAL] — Cryptographic keys should be robust
- `java:S4433` [CRITICAL] — LDAP connections should be authenticated
- `java:S4601` [CRITICAL] — "HttpSecurity" URL patterns should be correctly ordered
- `java:S4684` [CRITICAL] — Persistent entities should not be used as arguments of "@RequestMapping" methods
- `java:S4830` [CRITICAL] — Server certificates should be verified during SSL/TLS connections
- `java:S5344` [CRITICAL] — Passwords should not be stored in plaintext or with a fast hashing algorithm
- `java:S5445` [CRITICAL] — Insecure temporary file creation methods should not be used
- `java:S5527` [CRITICAL] — Server hostnames should be verified during SSL/TLS connections
- `java:S5542` [CRITICAL] — Encryption algorithms should be used with secure mode and padding scheme
- `java:S5547` [CRITICAL] — Cipher algorithms should be robust
- `java:S5659` [CRITICAL] — JWT should be signed and verified with strong cipher algorithms
- `java:S5876` [CRITICAL] — A new session should be created during user authentication
- `java:S6432` [CRITICAL] — Counter Mode initialization vectors should not be reused
- `java:S5679` [MAJOR] — OpenSAML2 should be configured to prevent authentication bypass
- `java:S5808` [MAJOR] — Authorizations should be based on strong decisions
- `java:S6301` [MAJOR] — Mobile database encryption keys should not be disclosed
- `java:S6374` [MAJOR] — XML parsers should not load external schemas
- `java:S6376` [MAJOR] — XML parsers should not be vulnerable to Denial of Service attacks
- `java:S6377` [MAJOR] — XML signatures should be validated securely
- `java:S1989` [MINOR] — Exceptions should not be thrown from servlet methods
- `java:S5301` [MINOR] — "ActiveMQConnectionFactory" should not be vulnerable to malicious code deserialization

### Bug (168)

- `java:S2095` [BLOCKER] — Resources should be closed
- `java:S2168` [BLOCKER] — Double-checked locking should not be used
- `java:S2189` [BLOCKER] — Loops should not be infinite
- `java:S2229` [BLOCKER] — Methods should not call same-class methods with incompatible "@Transactional" values
- `java:S2236` [BLOCKER] — Methods "wait(...)", "notify()" and "notifyAll()" should not be called on Thread instances
- `java:S2275` [BLOCKER] — Printf-style format strings should not lead to unexpected behavior at runtime
- `java:S2276` [BLOCKER] — "wait(...)" should be used instead of "Thread.sleep(...)" when a lock is held
- `java:S2689` [BLOCKER] — Files opened in append mode should not be used with "ObjectOutputStream"
- `java:S2695` [BLOCKER] — "PreparedStatement" and "ResultSet" methods should be called with valid indices
- `java:S3046` [BLOCKER] — "wait" should not be called when multiple locks are held
- `java:S3546` [BLOCKER] — Custom resources should be closed
- `java:S3753` [BLOCKER] — "@Controller" classes that use "@SessionAttributes" must call "setComplete" on their "SessionStatus" objects
- `java:S4602` [BLOCKER] — "@SpringBootApplication" and "@ComponentScan" should not be used in the default package
- `java:S5979` [BLOCKER] — Annotated Mockito objects should be initialized
- `java:S1114` [CRITICAL] — "super.finalize()" should be called at the end of "Object.finalize()" implementations
- `java:S1143` [CRITICAL] — Jump statements should not occur in "finally" blocks
- `java:S1175` [CRITICAL] — The signature of "finalize()" should match that of "Object.finalize()"
- `java:S2119` [CRITICAL] — "Random" objects should be reused
- `java:S2122` [CRITICAL] — "ScheduledThreadPoolExecutor" should not have 0 core threads
- `java:S2151` [CRITICAL] — "runFinalizersOnExit" should not be called
- `java:S2222` [CRITICAL] — Locks should be released on all paths
- `java:S2390` [CRITICAL] — Classes should not access their own subclasses during class initialization
- `java:S3518` [CRITICAL] — Zero should not be a possible denominator
- `java:S4275` [CRITICAL] — Getters and setters should access the expected fields
- `java:S5779` [CRITICAL] — Assertion methods should not be used within the try block of a try-catch catching an Error
- `java:S5783` [CRITICAL] — Only one method invocation is expected when testing checked exceptions
- `java:S5790` [CRITICAL] — JUnit5 inner test classes should be annotated with @Nested
- `java:S5845` [CRITICAL] — Assertions comparing incompatible types should not be made
- `java:S5856` [CRITICAL] — Regular expressions should be syntactically valid
- `java:S5994` [CRITICAL] — Regex patterns following a possessive quantifier should not always fail
- `java:S5996` [CRITICAL] — Regex boundaries should not be used in a way that can never be matched
- `java:S6001` [CRITICAL] — Back references in regular expressions should only refer to capturing groups that are matched before the reference
- `java:S6002` [CRITICAL] — Regex lookahead assertions should not be contradictory
- `java:S6104` [CRITICAL] — Map "computeIfAbsent()" and "computeIfPresent()" should not be used to add "null" values.
- `java:S6209` [CRITICAL] — Members ignored during record serialization should not be used
- `java:S6816` [CRITICAL] — Nullable injected fields and parameters should provide a default value
- `java:S6817` [CRITICAL] — Use of the "@Async" annotation on methods declared within a "@Configuration" class in Spring Boot
- `java:S6818` [CRITICAL] — "@Autowired" should only be used on a single constructor
- `java:S6857` [CRITICAL] — SpEL expression should have a valid syntax
- `java:S6881` [CRITICAL] — Virtual threads should be used for tasks that include heavy blocking operations
- `java:S7184` [CRITICAL] — "@Scheduled" annotation should only be applied to no-arg methods
- `java:S7185` [CRITICAL] — @EventListener methods should have one parameter at most
- `java:S1111` [MAJOR] — The "Object.finalize()" method should not be called
- `java:S1201` [MAJOR] — "equals" method overrides should accept "Object" parameters
- `java:S1217` [MAJOR] — "Thread.run()" should not be called directly
- `java:S1221` [MAJOR] — Methods should not be named "tostring", "hashcode" or "equal"
- `java:S1244` [MAJOR] — Floating point numbers should not be tested for equality
- `java:S1317` [MAJOR] — "StringBuilder" and "StringBuffer" should not be instantiated with a character
- `java:S1656` [MAJOR] — Variables should not be self-assigned
- `java:S1751` [MAJOR] — Loops with at most one iteration should be refactored
- `java:S1764` [MAJOR] — Identical expressions should not be used on both sides of a binary operator
- `java:S1849` [MAJOR] — "Iterator.hasNext()" should not call "Iterator.next()"
- `java:S1860` [MAJOR] — Synchronization should not be done on instances of value-based classes
- `java:S1862` [MAJOR] — Related "if/else if" statements should not have the same condition
- `java:S1872` [MAJOR] — Classes should not be compared by name
- `java:S2060` [MAJOR] — "Externalizable" classes should have no-arguments constructors
- `java:S2061` [MAJOR] — Custom serialization methods should have required signatures
- `java:S2109` [MAJOR] — Reflection should not be used to check non-runtime annotations
- `java:S2110` [MAJOR] — Invalid "Date" values should not be used
- `java:S2111` [MAJOR] — "BigDecimal(double)" should not be used
- `java:S2114` [MAJOR] — Collections should not be passed as arguments to their own methods
- `java:S2116` [MAJOR] — "hashCode" and "toString" should not be called on array instances
- `java:S2118` [MAJOR] — "writeObject" argument must implement "Serializable"
- `java:S2121` [MAJOR] — String operations with predictable outcomes should be avoided
- `java:S2123` [MAJOR] — Values should not be uselessly incremented
- `java:S2127` [MAJOR] — "Double.longBitsToDouble" should take "long" as argument
- `java:S2134` [MAJOR] — Classes extending java.lang.Thread should provide a specific "run" behavior
- `java:S2141` [MAJOR] — Classes that don't define "hashCode()" should not be used in hashes
- `java:S2142` [MAJOR] — "InterruptedException" and "ThreadDeath" should not be ignored
- `java:S2154` [MAJOR] — Dissimilar primitive wrappers should not be used with the ternary operator without explicit casting
- `java:S2159` [MAJOR] — Unnecessary equality checks should not be made
- `java:S2175` [MAJOR] — Inappropriate "Collection" calls should not be made
- `java:S2177` [MAJOR] — Child class methods named for parent class methods should be overrides
- `java:S2201` [MAJOR] — Return values from functions without side effects should not be ignored
- `java:S2204` [MAJOR] — ".equals()" should not be used to test the values of "Atomic" classes
- `java:S2225` [MAJOR] — "toString()" and "clone()" methods should not return null
- `java:S2226` [MAJOR] — Servlets should not have mutable instance fields
- `java:S2230` [MAJOR] — Methods with Spring proxying annotations should be public
- `java:S2251` [MAJOR] — A "for" loop update clause should move the counter in the right direction
- `java:S2252` [MAJOR] — Loop conditions should be true at least once
- `java:S2259` [MAJOR] — Null pointers should not be dereferenced
- `java:S2273` [MAJOR] — "Object.wait()", "Object.notify()" and "Object.notifyAll()" should only be called from synchronized code
- `java:S2441` [MAJOR] — Non-serializable objects should not be stored in "javax.servlet.http.HttpSession" instances
- `java:S2445` [MAJOR] — Blocks should be synchronized on "private final" fields
- `java:S2446` [MAJOR] — "notifyAll()" should be preferred over "notify()"
- `java:S2583` [MAJOR] — Conditionally executed code should be reachable
- `java:S2639` [MAJOR] — Inappropriate regular expressions should not be used
- `java:S2677` [MAJOR] — "read" and "readLine" return values should be used
- `java:S2757` [MAJOR] — Non-existent operators like "=+" should not be used
- `java:S2761` [MAJOR] — Unary prefix operators should not be repeated
- `java:S2789` [MAJOR] — "null" should not be used with "Optional"
- `java:S2885` [MAJOR] — Non-thread-safe fields should not be static
- `java:S2886` [MAJOR] — Getters and setters should be synchronized in pairs
- `java:S3034` [MAJOR] — Raw byte values should not be used in bitwise operations in combination with shifts
- `java:S3039` [MAJOR] — Indexes to passed to "String" operations should be within the string's bounds
- `java:S3064` [MAJOR] — Assignment of lazy-initialized members should be the last step with double-checked locking
- `java:S3065` [MAJOR] — Min and max used in combination should not always return the same value
- `java:S3067` [MAJOR] — "getClass" should not be used for synchronization
- `java:S3078` [MAJOR] — "volatile" variables should not be used with compound operators
- `java:S3306` [MAJOR] — Constructor injection should be used instead of field injection
- `java:S3346` [MAJOR] — Expressions used in "assert" should not produce side effects
- `java:S3436` [MAJOR] — Value-based classes should not be used for locking
- `java:S3551` [MAJOR] — Overrides should match their parent class methods in synchronization
- `java:S3655` [MAJOR] — Optional value should only be accessed after calling isPresent()
- `java:S3750` [MAJOR] — Spring "@Controller" classes should not use "@Scope"
- `java:S3923` [MAJOR] — All branches in a conditional structure should not have exactly the same implementation
- `java:S3958` [MAJOR] — Intermediate Stream methods should not be left unused
- `java:S3959` [MAJOR] — Consumed Stream pipelines should not be reused
- `java:S3981` [MAJOR] — Collection sizes and array length comparisons should make sense
- `java:S3984` [MAJOR] — Exceptions should not be created without being thrown
- `java:S3986` [MAJOR] — Week Year ("YYYY") should not be used for date formatting
- `java:S4143` [MAJOR] — Map values should not be replaced unconditionally
- `java:S4348` [MAJOR] — "iterator" should not return "this"
- `java:S4351` [MAJOR] — "compareTo" should not be overloaded
- `java:S4517` [MAJOR] — InputSteam.read() implementation should not return a signed byte
- `java:S4973` [MAJOR] — Strings and Boxed types should be compared using "equals()"
- `java:S5164` [MAJOR] — "ThreadLocal" variables should be cleaned up when no longer used
- `java:S5810` [MAJOR] — JUnit5 test classes and methods should not be silently ignored
- `java:S5831` [MAJOR] — AssertJ configuration should be applied
- `java:S5833` [MAJOR] — AssertJ methods setting the assertion context should come before an assertion
- `java:S5850` [MAJOR] — Alternatives in regular expressions should be grouped when used with anchors
- `java:S5855` [MAJOR] — Regex alternatives should not be redundant
- `java:S5863` [MAJOR] — Assertions should not compare an object to itself
- `java:S5866` [MAJOR] — Case insensitive Unicode regular expressions should enable the "UNICODE_CASE" flag
- `java:S5868` [MAJOR] — Unicode Grapheme Clusters should be avoided inside regex character classes
- `java:S5917` [MAJOR] — DateTimeFormatters should not use mismatched year and week numbers
- `java:S5960` [MAJOR] — Assertions should not be used in production code
- `java:S5967` [MAJOR] — Tests method should not be annotated with competing annotations
- `java:S5998` [MAJOR] — Regular expressions should not overflow the stack
- `java:S6070` [MAJOR] — The regex escape sequence \cX should only be used with characters in the @-_ range
- `java:S6073` [MAJOR] — Mockito argument matchers should be used on all parameters
- `java:S6103` [MAJOR] — AssertJ assertions with "Consumer" arguments should contain assertion inside consumers
- `java:S6216` [MAJOR] — Reflection should not be used to increase accessibility of records' fields
- `java:S6218` [MAJOR] — Equals method should be overridden in records containing array fields
- `java:S6806` [MAJOR] — Model attributes should follow the Java identifier naming convention
- `java:S6810` [MAJOR] — Async methods should return void or Future
- `java:S6831` [MAJOR] — "@Qualifier" should not be used on "@Bean" methods
- `java:S6838` [MAJOR] — "@Bean" methods for Singleton should not be invoked in "@Configuration" when proxyBeanMethods is false
- `java:S6856` [MAJOR] — "@PathVariable" annotation should be present if a path variable is used
- `java:S6862` [MAJOR] — Beans in "@Configuration" class should have different names
- `java:S6863` [MAJOR] — Set appropriate Status Codes on HTTP responses
- `java:S6901` [MAJOR] — "setDaemon", "setPriority" and "getThreadGroup" should not be invoked on virtual threads
- `java:S6906` [MAJOR] — Virtual threads should not run tasks that include synchronized code
- `java:S6913` [MAJOR] — "Math.clamp" should be used with correct ranges
- `java:S6915` [MAJOR] — "String.indexOf" should be used with correct ranges
- `java:S1206` [MINOR] — "equals(Object obj)" and "hashCode()" should be overridden in pairs
- `java:S1226` [MINOR] — Method parameters, caught exceptions and foreach variables' initial values should not be ignored
- `java:S2055` [MINOR] — The non-serializable super class of a "Serializable" class should have a no-argument constructor
- `java:S2066` [MINOR] — "Serializable" inner classes of non-serializable outer classes should be "static"
- `java:S2097` [MINOR] — "equals(Object obj)" should test the argument's type
- `java:S2153` [MINOR] — Unnecessary boxing and unboxing should be avoided
- `java:S2162` [MINOR] — "equals" methods should be symmetric and work for subclasses
- `java:S2164` [MINOR] — Math should not be performed on floats
- `java:S2167` [MINOR] — "compareTo" should not return "Integer.MIN_VALUE"
- `java:S2183` [MINOR] — Ints and longs should not be shifted by zero or more than their number of bits-1
- `java:S2184` [MINOR] — Math operands should be cast before assignment
- `java:S2200` [MINOR] — "compareTo" results should not be checked for specific values
- `java:S2272` [MINOR] — "Iterator.next()" methods should throw "NoSuchElementException"
- `java:S2637` [MINOR] — "@NonNull" values should not be set to null
- `java:S2674` [MINOR] — The value returned from a stream read should be checked
- `java:S2676` [MINOR] — "Math.abs" and negation should not be used on numbers that could be "MIN_VALUE"
- `java:S3020` [MINOR] — "Collection.toArray()" should be passed an array of the proper type
- `java:S3032` [MINOR] — JEE applications should not "getClassLoader"
- `java:S3077` [MINOR] — Non-primitive fields should not be "volatile"
- `java:S3599` [MINOR] — Double Brace Initialization should not be used
- `java:S5841` [MINOR] — AssertJ assertions "allMatch" and "doesNotContain" should also test for emptiness
- `java:S5842` [MINOR] — Repeated patterns in regular expressions should not match the empty string
- `java:S899` [MINOR] — Return values should not be ignored when they contain the operation status code

### Security Hotspot (37)

- `java:S2245` [CRITICAL] — Using pseudorandom number generators (PRNGs) is security-sensitive
- `java:S2257` [CRITICAL] — Using non-standard cryptographic algorithms is security-sensitive
- `java:S4502` [CRITICAL] — Disabling CSRF protections is security-sensitive
- `java:S4512` [CRITICAL] — Setting JavaBean properties is security-sensitive
- `java:S4544` [CRITICAL] — Using unsafe Jackson deserialization configuration is security-sensitive
- `java:S4790` [CRITICAL] — Using weak hashing algorithms is security-sensitive
- `java:S4792` [CRITICAL] — Configuring loggers is security-sensitive
- `java:S5042` [CRITICAL] — Expanding archive files without controlling resource consumption is security-sensitive
- `java:S5320` [CRITICAL] — Broadcasting intents is security-sensitive
- `java:S5322` [CRITICAL] — Receiving intents is security-sensitive
- `java:S5324` [CRITICAL] — Accessing Android external storage is security-sensitive
- `java:S5332` [CRITICAL] — Using clear-text protocols is security-sensitive
- `java:S5443` [CRITICAL] — Using publicly writable directories is security-sensitive
- `java:S5852` [CRITICAL] — Using slow regular expressions is security-sensitive
- `java:S2077` [MAJOR] — Formatting SQL queries is security-sensitive
- `java:S2612` [MAJOR] — Setting loose POSIX file permissions is security-sensitive
- `java:S4434` [MAJOR] — Allowing deserialization of LDAP objects is security-sensitive
- `java:S5247` [MAJOR] — Disabling auto-escaping in template engines is security-sensitive
- `java:S5693` [MAJOR] — Allowing requests with excessive content length is security-sensitive
- `java:S5804` [MAJOR] — Allowing user enumeration is security-sensitive
- `java:S6263` [MAJOR] — Using long-term access keys is security-sensitive
- `java:S6288` [MAJOR] — Authorizing non-authenticated users to use keys in the Android KeyStore is security-sensitive
- `java:S6291` [MAJOR] — Using unencrypted databases in mobile applications is security-sensitive
- `java:S6293` [MAJOR] — Using biometric authentication without a cryptographic solution is security-sensitive
- `java:S6300` [MAJOR] — Using unencrypted files in mobile applications is security-sensitive
- `java:S6362` [MAJOR] — Enabling JavaScript support for WebViews is security-sensitive
- `java:S6363` [MAJOR] — Enabling file access for WebViews is security-sensitive
- `java:S7409` [MAJOR] — Exposing native code through JavaScript interfaces is security-sensitive
- `java:S1313` [MINOR] — Using hardcoded IP addresses is security-sensitive
- `java:S2092` [MINOR] — Creating cookies without the "secure" flag is security-sensitive
- `java:S3330` [MINOR] — Creating cookies without the "HttpOnly" flag is security-sensitive
- `java:S3752` [MINOR] — Allowing both safe and unsafe HTTP methods is security-sensitive
- `java:S4036` [MINOR] — Searching OS commands in PATH is security-sensitive
- `java:S4507` [MINOR] — Delivering code in production with debug features activated is security-sensitive
- `java:S5122` [MINOR] — Having a permissive Cross-Origin Resource Sharing policy is security-sensitive
- `java:S5689` [MINOR] — Disclosing fingerprints from web application technologies is security-sensitive
- `java:S7435` [MINOR] — Processing persistent unique identifiers is security-sensitive

### Code Smell (461)

- `java:S1147` [BLOCKER] — Exit methods should not be called
- `java:S1190` [BLOCKER] — Future keywords should not be used as names
- `java:S1219` [BLOCKER] — "switch" statements should not contain non-case labels
- `java:S128` [BLOCKER] — Switch cases should end with an unconditional "break" statement
- `java:S1314` [BLOCKER] — Octal values should not be used
- `java:S1451` [BLOCKER] — Track lack of copyright and license headers
- `java:S1845` [BLOCKER] — Methods and field names should not be the same or differ only by capitalization
- `java:S2096` [BLOCKER] — "main" should not "throw" anything
- `java:S2178` [BLOCKER] — Short-circuit logic should be used in boolean contexts
- `java:S2187` [BLOCKER] — TestCases should contain tests
- `java:S2188` [BLOCKER] — JUnit test cases should call super methods
- `java:S2387` [BLOCKER] — Child class fields should not shadow parent class fields
- `java:S2437` [BLOCKER] — Unnecessary bit operations should not be performed
- `java:S2693` [BLOCKER] — Threads should not be started in constructors
- `java:S2699` [BLOCKER] — Tests should include assertions
- `java:S2970` [BLOCKER] — Assertions should be complete
- `java:S2975` [BLOCKER] — "clone" should not be overridden
- `java:S3014` [BLOCKER] — "ThreadGroup" should not be used
- `java:S3516` [BLOCKER] — Methods returns should not be invariant
- `java:S1067` [CRITICAL] — Expressions should not be too complex
- `java:S1113` [CRITICAL] — The "Object.finalize()" method should not be overridden
- `java:S115` [CRITICAL] — Constant names should comply with a naming convention
- `java:S1163` [CRITICAL] — Exceptions should not be thrown in finally blocks
- `java:S1174` [CRITICAL] — "Object.finalize()" should remain protected (versus public) when overriding
- `java:S1186` [CRITICAL] — Methods should not be empty
- `java:S1192` [CRITICAL] — String literals should not be duplicated
- `java:S121` [CRITICAL] — Control structures should use curly braces
- `java:S1214` [CRITICAL] — Interfaces should not solely consist of constants
- `java:S1215` [CRITICAL] — Execution of the Garbage Collector should be triggered only by the JVM
- `java:S126` [CRITICAL] — "if ... else if" constructs should end with "else" clauses
- `java:S131` [CRITICAL] — "switch" statements should have "default" clauses
- `java:S134` [CRITICAL] — Control flow statements "if", "for", "while", "switch" and "try" should not be nested too deeply
- `java:S1452` [CRITICAL] — Generic wildcard types should not be used in return types
- `java:S1541` [CRITICAL] — Methods should not be too complex
- `java:S1598` [CRITICAL] — Package declaration should match source file directory
- `java:S1699` [CRITICAL] — Constructors should only call non-overridable methods
- `java:S1821` [CRITICAL] — "switch" statements and expressions should not be nested
- `java:S1948` [CRITICAL] — Fields in a "Serializable" class should either be transient or serializable
- `java:S1994` [CRITICAL] — "for" loop increment clauses should modify the loops' counters
- `java:S2057` [CRITICAL] — "Serializable" classes should have a "serialVersionUID"
- `java:S2062` [CRITICAL] — "readResolve" methods should be inheritable
- `java:S2063` [CRITICAL] — Comparators should be "Serializable"
- `java:S2093` [CRITICAL] — Try-with-resources should be used
- `java:S2157` [CRITICAL] — "Cloneables" should implement "clone"
- `java:S2176` [CRITICAL] — Class names should not shadow interfaces or superclasses
- `java:S2186` [CRITICAL] — JUnit assertions should not be used in "run" methods
- `java:S2197` [CRITICAL] — Modulus results should not be checked for direct equality
- `java:S2208` [CRITICAL] — Wildcard imports should not be used
- `java:S2235` [CRITICAL] — "IllegalMonitorStateException" should not be caught
- `java:S2274` [CRITICAL] — "Object.wait(...)" and "Condition.await(...)" should be called inside a "while" loop
- `java:S2444` [CRITICAL] — Lazy initialization of "static" fields should be "synchronized"
- `java:S2447` [CRITICAL] — "null" should not be returned from a "Boolean" method
- `java:S2479` [CRITICAL] — Whitespace and control characters in literals should be explicit
- `java:S2638` [CRITICAL] — Method overrides should not change contracts
- `java:S2692` [CRITICAL] — "indexOf" checks should not be for positive numbers
- `java:S2696` [CRITICAL] — Instance methods should not write to "static" fields
- `java:S3252` [CRITICAL] — "static" base class members should not be accessed via derived types
- `java:S3305` [CRITICAL] — Factory method injection should be used in "@Configuration" classes
- `java:S3749` [CRITICAL] — Members of Spring components should be injected
- `java:S3776` [CRITICAL] — Cognitive Complexity of methods should not be too high
- `java:S3937` [CRITICAL] — Number patterns should be regular
- `java:S3972` [CRITICAL] — Conditionals should start on new lines
- `java:S3973` [CRITICAL] — A conditionally executed single line should be denoted by indentation
- `java:S4454` [CRITICAL] — "equals" method parameters should not be marked "@Nonnull"
- `java:S4524` [CRITICAL] — "default" clauses should be last
- `java:S4605` [CRITICAL] — Spring beans should be considered by "@ComponentScan"
- `java:S4635` [CRITICAL] — String offset-based methods should be preferred for finding substrings from offsets
- `java:S4970` [CRITICAL] — Derived exceptions should not hide their parents' catch blocks
- `java:S5128` [CRITICAL] — "Bean Validation" (JSR 380) should be properly configured
- `java:S5361` [CRITICAL] — "String#replace" should be preferred to "String#replaceAll"
- `java:S5803` [CRITICAL] — Class members annotated with "@VisibleForTesting" should not be accessed from production code
- `java:S5826` [CRITICAL] — Methods setUp() and tearDown() should be correctly annotated starting with JUnit4
- `java:S5846` [CRITICAL] — Empty lines should not be tested with regex MULTILINE flag
- `java:S5969` [CRITICAL] — Mocking all non-private methods of a class should be avoided
- `java:S6809` [CRITICAL] — Methods with Spring proxy should not be called via "this"
- `java:S6814` [CRITICAL] — Optional REST parameters should have an object type
- `java:S6876` [CRITICAL] — Reverse iteration should utilize reversed view
- `java:S6877` [CRITICAL] — Reverse view should be used instead of reverse copy in read-only cases
- `java:S7178` [CRITICAL] — Injecting data into static fields is not supported by Spring
- `java:S7186` [CRITICAL] — Methods returning "Page" or "Slice" must take "Pageable" as an input parameter
- `java:S7190` [CRITICAL] — Methods annotated with "@BeforeTransaction" or "@AfterTransaction" must respect the contract
- `java:S888` [CRITICAL] — Equality operators should not be used in "for" loop termination conditions
- `java:NoSonar` [MAJOR] — Track uses of "NOSONAR" comments
- `java:S103` [MAJOR] — Lines should not be too long
- `java:S104` [MAJOR] — Files should not have too many lines of code
- `java:S106` [MAJOR] — Standard outputs should not be used directly to log anything
- `java:S1065` [MAJOR] — Unused labels should be removed
- `java:S1066` [MAJOR] — Mergeable "if" statements should be combined
- `java:S1068` [MAJOR] — Unused "private" fields should be removed
- `java:S107` [MAJOR] — Methods should not have too many parameters
- `java:S108` [MAJOR] — Nested blocks of code should not be left empty
- `java:S109` [MAJOR] — Magic numbers should not be used
- `java:S110` [MAJOR] — Inheritance tree of classes should not be too deep
- `java:S1110` [MAJOR] — Redundant pairs of parentheses should be removed
- `java:S1117` [MAJOR] — Local variables should not shadow class fields
- `java:S1118` [MAJOR] — Utility classes should not have public constructors
- `java:S1119` [MAJOR] — Labels should not be used
- `java:S112` [MAJOR] — Generic exceptions should never be thrown
- `java:S1121` [MAJOR] — Assignments should not be made from within sub-expressions
- `java:S1123` [MAJOR] — Deprecated elements should have both the annotation and the Javadoc tag
- `java:S1134` [MAJOR] — Track uses of "FIXME" tags
- `java:S1141` [MAJOR] — Try-catch blocks should not be nested
- `java:S1142` [MAJOR] — Methods should not have too many return statements
- `java:S1144` [MAJOR] — Unused "private" methods should be removed
- `java:S1149` [MAJOR] — Synchronized classes "Vector", "Hashtable", "Stack" and "StringBuffer" should not be used
- `java:S1150` [MAJOR] — "Enumeration" should not be implemented
- `java:S1151` [MAJOR] — "switch case" clauses should not have too many lines of code
- `java:S1160` [MAJOR] — Public methods should throw at most one checked exception
- `java:S1161` [MAJOR] — "@Override" should be used on overriding and implementing methods
- `java:S1162` [MAJOR] — Checked exceptions should not be thrown
- `java:S1166` [MAJOR] — Exception handlers should preserve the original exceptions
- `java:S1168` [MAJOR] — Empty arrays and collections should be returned instead of null
- `java:S1171` [MAJOR] — Only static class initializers should be used
- `java:S1172` [MAJOR] — Unused method parameters should be removed
- `java:S1176` [MAJOR] — Public types, methods and fields (API) should be documented with Javadoc
- `java:S1181` [MAJOR] — Throwable and Error should not be caught
- `java:S1188` [MAJOR] — Anonymous classes should not have too many lines
- `java:S1191` [MAJOR] — Classes from "sun.*" packages should not be used
- `java:S1193` [MAJOR] — Exception types should not be tested using "instanceof" in catch blocks
- `java:S1194` [MAJOR] — "java.lang.Error" should not be extended
- `java:S1200` [MAJOR] — Classes should not be coupled to too many other classes
- `java:S122` [MAJOR] — Statements should be on separate lines
- `java:S1223` [MAJOR] — Non-constructor methods should not have the same name as the enclosing class
- `java:S124` [MAJOR] — Track comments matching a regular expression
- `java:S125` [MAJOR] — Sections of code should not be commented out
- `java:S1258` [MAJOR] — Classes and enums with private members should have a constructor
- `java:S127` [MAJOR] — "for" loop stop conditions should be invariant
- `java:S138` [MAJOR] — Methods should not have too many lines
- `java:S1448` [MAJOR] — Classes should not have too many methods
- `java:S1479` [MAJOR] — "switch" statements should not have too many "case" clauses
- `java:S1604` [MAJOR] — Anonymous inner classes containing only one method should become lambdas
- `java:S1607` [MAJOR] — JUnit4 @Ignored and JUnit5 @Disabled annotations should be used to disable tests and should provide a rationale
- `java:S1695` [MAJOR] — "NullPointerException" should not be explicitly thrown
- `java:S1696` [MAJOR] — "NullPointerException" should not be caught
- `java:S1700` [MAJOR] — A field should not duplicate the name of its containing class
- `java:S1711` [MAJOR] — Standard functional interfaces should not be redefined
- `java:S1774` [MAJOR] — The ternary operator should not be used
- `java:S1820` [MAJOR] — Classes should not have too many fields
- `java:S1844` [MAJOR] — "Object.wait" should not be called on objects that implement "java.util.concurrent.locks.Condition"
- `java:S1854` [MAJOR] — Unused assignments should be removed
- `java:S1871` [MAJOR] — Two branches in a conditional structure should not have exactly the same implementation
- `java:S1996` [MAJOR] — Files should contain only one top-level class or interface each
- `java:S2047` [MAJOR] — The names of methods with boolean return values should start with "is" or "has"
- `java:S2112` [MAJOR] — "URL.hashCode" and "URL.equals" should be avoided
- `java:S2129` [MAJOR] — Constructors should not be used to instantiate "String", "BigInteger", "BigDecimal" and primitive-wrapper classes
- `java:S2131` [MAJOR] — Primitives should not be boxed just for "String" conversion
- `java:S2133` [MAJOR] — Objects should not be created only to invoke "getClass"
- `java:S2139` [MAJOR] — Exceptions should be either logged or rethrown but not both
- `java:S2143` [MAJOR] — "java.time" classes should be used for dates and times
- `java:S2166` [MAJOR] — Classes named like "Exception" should extend "Exception" or a subclass
- `java:S2185` [MAJOR] — Do not perform unnecessary mathematical operations
- `java:S2209` [MAJOR] — "static" members should be accessed statically
- `java:S2211` [MAJOR] — Types should be used in lambdas
- `java:S2232` [MAJOR] — "ResultSet.isLast()" should not be used
- `java:S2234` [MAJOR] — Parameters should be passed in the correct order
- `java:S2253` [MAJOR] — Track uses of disallowed methods
- `java:S2260` [MAJOR] — Java parser failure
- `java:S2301` [MAJOR] — Public methods should not contain selector arguments
- `java:S2308` [MAJOR] — "deleteOnExit" should not be used
- `java:S2326` [MAJOR] — Unused type parameters should be removed
- `java:S2388` [MAJOR] — Inner class calls to super class methods should be unambiguous
- `java:S2438` [MAJOR] — "Thread" should not be used where a "Runnable" argument is expected
- `java:S2440` [MAJOR] — Classes with only "static" methods should not be instantiated
- `java:S2442` [MAJOR] — Synchronizing on a "Lock" object should be avoided
- `java:S2589` [MAJOR] — Boolean expressions should not be gratuitous
- `java:S2629` [MAJOR] — "Preconditions" and logging arguments should not require evaluation
- `java:S2675` [MAJOR] — "readObject" should not be "synchronized"
- `java:S2681` [MAJOR] — Multiline blocks should be enclosed in curly braces
- `java:S2694` [MAJOR] — Inner classes which do not reference their owning classes should be "static"
- `java:S2718` [MAJOR] — "DateUtils.truncate" from Apache Commons Lang library should not be used
- `java:S2864` [MAJOR] — "entrySet()" should be iterated when both the key and value are needed
- `java:S2925` [MAJOR] — "Thread.sleep" should not be used in tests
- `java:S2972` [MAJOR] — Inner classes should not have too many lines of code
- `java:S2973` [MAJOR] — Escaped Unicode characters should not be used
- `java:S3010` [MAJOR] — Static fields should not be updated in constructors
- `java:S3011` [MAJOR] — Reflection should not be used to increase accessibility of classes, methods, or fields
- `java:S3030` [MAJOR] — Classes should not have too many "static" imports
- `java:S3042` [MAJOR] — "writeObject" should not be the only "synchronized" code in a class
- `java:S3063` [MAJOR] — "StringBuilder" data should be used
- `java:S3358` [MAJOR] — Ternary operators should not be nested
- `java:S3366` [MAJOR] — "this" should not be exposed from constructors
- `java:S3414` [MAJOR] — Tests should be kept in a dedicated source directory
- `java:S3415` [MAJOR] — Assertion arguments should be passed in the correct order
- `java:S3457` [MAJOR] — Format strings should be used correctly
- `java:S3553` [MAJOR] — "Optional" should not be used for parameters
- `java:S3631` [MAJOR] — "Arrays.stream" should be used for primitive arrays
- `java:S3725` [MAJOR] — Java 8's "Files.exists" should not be used
- `java:S3740` [MAJOR] — Raw types should not be used
- `java:S3751` [MAJOR] — "@RequestMapping" methods should not be "private"
- `java:S3824` [MAJOR] — "Map.get" and value test should be replaced with single method call
- `java:S3864` [MAJOR] — "Stream.peek" should be used with caution
- `java:S3985` [MAJOR] — Unused "private" classes should be removed
- `java:S4011` [MAJOR] — Track uses of disallowed constructors
- `java:S4042` [MAJOR] — "java.nio.Files#delete" should be preferred
- `java:S4144` [MAJOR] — Methods should not have identical implementations
- `java:S4165` [MAJOR] — Assignments should not be redundant
- `java:S4248` [MAJOR] — Regex patterns should not be created needlessly
- `java:S4274` [MAJOR] — Asserts should not be used to check the parameters of a public method
- `java:S4288` [MAJOR] — Spring components should use constructor injection
- `java:S4425` [MAJOR] — "Integer.toHexString" should not be used to build hexadecimal strings
- `java:S4449` [MAJOR] — Nullness of parameters should be guaranteed
- `java:S4551` [MAJOR] — Enum values should be compared with "=="
- `java:S4604` [MAJOR] — "@EnableAutoConfiguration" should be fine-tuned
- `java:S4738` [MAJOR] — Java features should be preferred to Guava
- `java:S4925` [MAJOR] — "Class.forName()" should not load JDBC 4.0+ drivers
- `java:S5261` [MAJOR] — "else" statements should be clearly matched with an "if"
- `java:S5329` [MAJOR] — Collection constructors should not be used as java.util.function.Function
- `java:S5413` [MAJOR] — 'List.remove()' should not be used in ascending 'for' loops
- `java:S5612` [MAJOR] — Lambdas should not have too many lines
- `java:S5664` [MAJOR] — Whitespace for text block indent should be consistent
- `java:S5669` [MAJOR] — Vararg method arguments should not be confusing
- `java:S5738` [MAJOR] — "@Deprecated" code marked for removal should never be used
- `java:S5776` [MAJOR] — Exception testing via JUnit ExpectedException rule should not be mixed with other assertions
- `java:S5778` [MAJOR] — Only one method invocation is expected when testing runtime exceptions
- `java:S5785` [MAJOR] — JUnit assertTrue/assertFalse should be simplified to the corresponding dedicated assertion
- `java:S5843` [MAJOR] — Regular expressions should not be too complicated
- `java:S5854` [MAJOR] — Regexes containing characters subject to normalization should use the CANON_EQ flag
- `java:S5860` [MAJOR] — Names of regular expressions named groups should be used
- `java:S5869` [MAJOR] — Character classes in regular expressions should not contain the same character twice
- `java:S5958` [MAJOR] — AssertJ "assertThatThrownBy" should not be used alone
- `java:S5961` [MAJOR] — Test methods should not contain too many assertions
- `java:S5970` [MAJOR] — Spring's ModelAndViewAssert assertions should be used instead of other assertions
- `java:S5973` [MAJOR] — Tests should be stable
- `java:S5976` [MAJOR] — Similar tests should be grouped in a single Parameterized test
- `java:S5977` [MAJOR] — Tests should use fixed data instead of randomized data
- `java:S5993` [MAJOR] — Constructors of an "abstract" class should not be declared "public"
- `java:S6019` [MAJOR] — Reluctant quantifiers in regular expressions should be followed by an expression that can't match the empty string
- `java:S6035` [MAJOR] — Single-character alternations in regular expressions should be replaced with character classes
- `java:S6126` [MAJOR] — String multiline concatenation should be replaced with Text Blocks
- `java:S6202` [MAJOR] — Operator "instanceof" should be used instead of "A.class.isInstance()"
- `java:S6204` [MAJOR] — "Stream.toList()" method should be used instead of "collectors" when unmodifiable list needed
- `java:S6206` [MAJOR] — Records should be used instead of ordinary classes when representing immutable data structure
- `java:S6207` [MAJOR] — Redundant constructors/methods should be avoided in records
- `java:S6211` [MAJOR] — Custom getter method should not be used to override record's getter behavior
- `java:S6213` [MAJOR] — Restricted Identifiers should not be used as Identifiers
- `java:S6241` [MAJOR] — Region should be set explicitly when creating a new "AwsClient"
- `java:S6242` [MAJOR] — Credentials Provider should be set explicitly when creating a new "AwsClient"
- `java:S6243` [MAJOR] — Reusable resources should be initialized at construction time of Lambda functions
- `java:S6326` [MAJOR] — Regular expressions should not contain multiple spaces
- `java:S6331` [MAJOR] — Regular expressions should not contain empty groups
- `java:S6355` [MAJOR] — Deprecated annotations should include explanations
- `java:S6395` [MAJOR] — Non-capturing groups without quantifier should not be used
- `java:S6396` [MAJOR] — Superfluous curly brace quantifiers should be avoided
- `java:S6397` [MAJOR] — Character classes in regular expressions should not contain only one character
- `java:S6411` [MAJOR] — Types used as keys in Maps should implement Comparable
- `java:S6485` [MAJOR] — Hash-based collections with known capacity should be initialized with the proper related static method.
- `java:S6665` [MAJOR] — Redundant nullability annotations should be removed
- `java:S6804` [MAJOR] — "@Value" annotation should inject property or SpEL expression
- `java:S6813` [MAJOR] — Field dependency injection should be avoided
- `java:S6829` [MAJOR] — "@Autowired" should be used when multiple constructors are provided
- `java:S6830` [MAJOR] — Bean names should adhere to the naming conventions
- `java:S6832` [MAJOR] — Non-singleton Spring beans should not be injected into singleton beans
- `java:S6833` [MAJOR] — "@Controller" should be replaced with "@RestController"
- `java:S6837` [MAJOR] — Superfluous "@ResponseBody" annotations should be removed
- `java:S6878` [MAJOR] — Use record pattern instead of explicit field access
- `java:S6880` [MAJOR] — Use switch instead of if-else chain to compare a variable against multiple cases
- `java:S6885` [MAJOR] — Use built-in "Math.clamp" methods
- `java:S6889` [MAJOR] — Proper Sensor Resource Management
- `java:S6891` [MAJOR] — Exact alarms should not be abused
- `java:S6898` [MAJOR] — High frame rates should not be used
- `java:S6904` [MAJOR] — Avoid using "FetchType.EAGER"
- `java:S6905` [MAJOR] — SQL queries should retrieve only necessary fields
- `java:S6909` [MAJOR] — Constant parameters in a "PreparedStatement" should not be set more than once
- `java:S6912` [MAJOR] — Use batch Processing in JDBC
- `java:S6914` [MAJOR] — Use Fused Location to optimize battery power
- `java:S6916` [MAJOR] — Use when instead of a single if inside a pattern match body
- `java:S6923` [MAJOR] — Motion Sensor should not use gyroscope
- `java:S6926` [MAJOR] — Bluetooth should be configured to use low power
- `java:S7177` [MAJOR] — Use appropriate @DirtiesContext modes
- `java:S7179` [MAJOR] — @Cacheable and @CachePut should not be combined
- `java:S7180` [MAJOR] — "@Cache*" annotations should only be applied on concrete classes
- `java:S7183` [MAJOR] — @InitBinder methods should have void return type
- `java:S7482` [MAJOR] — Don't provide an initializer for a stateless stream gatherer
- `java:S8346` [MAJOR] — Increment and decrement operators (++/--) should not be used with floating point variables
- `java:S8432` [MAJOR] — "ScopedValue.where" results should not be ignored
- `java:S8433` [MAJOR] — Validation logic should be placed in constructor prologue when possible
- `java:S8444` [MAJOR] — Excessive logic before super() should not bloat constructor
- `java:S864` [MAJOR] — Limited dependence should be placed on operator precedence
- `java:S881` [MAJOR] — Increment (++) and decrement (--) operators should not be used in a method call or mixed with other operators in an expression
- `java:S100` [MINOR] — Method names should comply with a naming convention
- `java:S101` [MINOR] — Class names should comply with a naming convention
- `java:S105` [MINOR] — Tabulation characters should not be used
- `java:S1075` [MINOR] — URIs should not be hardcoded
- `java:S1104` [MINOR] — Class variable fields should not have public accessibility
- `java:S1105` [MINOR] — An open curly brace should be located at the end of a line
- `java:S1106` [MINOR] — An open curly brace should be located at the beginning of a line
- `java:S1107` [MINOR] — Close curly brace and the next "else", "catch" and "finally" keywords should be located on the same line
- `java:S1108` [MINOR] — Close curly brace and the next "else", "catch" and "finally" keywords should be on two different lines
- `java:S1109` [MINOR] — A close curly brace should be located at the beginning of a line
- `java:S1116` [MINOR] — Empty statements should be removed
- `java:S1120` [MINOR] — Source code should be indented consistently
- `java:S1124` [MINOR] — Modifiers should be declared in the correct order
- `java:S1125` [MINOR] — Boolean literals should not be redundant
- `java:S1126` [MINOR] — Return of boolean expressions should not be wrapped into an "if-then-else" statement
- `java:S1128` [MINOR] — Unnecessary imports should be removed
- `java:S113` [MINOR] — Files should end with a newline
- `java:S1130` [MINOR] — Exceptions in "throws" clauses should not be superfluous
- `java:S1132` [MINOR] — Strings literals should be placed on the left side when checking for equality
- `java:S114` [MINOR] — Interface names should comply with a naming convention
- `java:S1153` [MINOR] — "String.valueOf()" should not be appended to a "String"
- `java:S1155` [MINOR] — "Collection.isEmpty()" should be used to test for emptiness
- `java:S1157` [MINOR] — Case insensitive string comparisons should be made without intermediate upper or lower casing
- `java:S1158` [MINOR] — Primitive wrappers should not be instantiated only for "toString" or "compareTo" calls
- `java:S116` [MINOR] — Field names should comply with a naming convention
- `java:S1165` [MINOR] — Exception classes should have final fields
- `java:S117` [MINOR] — Local variable and method parameter names should comply with a naming convention
- `java:S1170` [MINOR] — Public constants and fields initialized at declaration should be "static final" rather than merely "final"
- `java:S118` [MINOR] — Abstract class names should comply with a naming convention
- `java:S1182` [MINOR] — Classes that override "clone" should be "Cloneable" and call "super.clone()"
- `java:S1185` [MINOR] — Overriding methods should do more than simply call the same method in the super class
- `java:S119` [MINOR] — Type parameter names should comply with a naming convention
- `java:S1195` [MINOR] — Array designators "[]" should be located after the type in method signatures
- `java:S1197` [MINOR] — Array designators "[]" should be on the type, not the variable
- `java:S1199` [MINOR] — Nested code blocks should not be used
- `java:S120` [MINOR] — Package names should comply with a naming convention
- `java:S1210` [MINOR] — "equals(Object obj)" should be overridden along with the "compareTo(T obj)" method
- `java:S1213` [MINOR] — The members of an interface or class declaration should appear in a pre-defined order
- `java:S1220` [MINOR] — The default unnamed package should not be used
- `java:S1228` [MINOR] — Packages should have a javadoc file 'package-info.java'
- `java:S1264` [MINOR] — A "while" loop should be used instead of a "for" loop
- `java:S1301` [MINOR] — "switch" statements should have at least 3 "case" clauses
- `java:S1310` [MINOR] — Track uses of "NOPMD" suppression comments
- `java:S1312` [MINOR] — Loggers should be "private static final" and should share a naming convention
- `java:S1315` [MINOR] — Track uses of "CHECKSTYLE:OFF" suppression comments
- `java:S1319` [MINOR] — Declarations should use Java collection interfaces such as "List" rather than specific implementation classes such as "LinkedList"
- `java:S135` [MINOR] — Loops should not contain more than a single "break" or "continue" statement
- `java:S139` [MINOR] — Comments should not be located at the end of lines of code
- `java:S1444` [MINOR] — "public static" fields should be constant
- `java:S1449` [MINOR] — String operations should not rely on the default system locale
- `java:S1450` [MINOR] — Private fields only used as local variables in methods should become local variables
- `java:S1481` [MINOR] — Unused local variables should be removed
- `java:S1488` [MINOR] — Local variables should not be declared and then immediately returned or thrown
- `java:S1596` [MINOR] — "Collections.EMPTY_LIST", "EMPTY_MAP", and "EMPTY_SET" should not be used
- `java:S1602` [MINOR] — Lambdas containing only one statement should not nest this statement in a block
- `java:S1610` [MINOR] — Abstract classes without fields should be converted to interfaces
- `java:S1611` [MINOR] — Parentheses should be removed from a single lambda parameter when its type is inferred
- `java:S1612` [MINOR] — Lambdas should be replaced with method references
- `java:S1640` [MINOR] — Maps with keys that are enum values should use the EnumMap implementation
- `java:S1641` [MINOR] — Sets with elements that are enum values should be replaced with EnumSet
- `java:S1643` [MINOR] — Strings should not be concatenated using '+' in a loop
- `java:S1659` [MINOR] — Multiple variables should not be declared on the same line
- `java:S1694` [MINOR] — An abstract class should have both abstract and concrete methods
- `java:S1698` [MINOR] — "==" and "!=" should not be used when "equals" is overridden
- `java:S1710` [MINOR] — Annotation repetitions should not be wrapped
- `java:S1858` [MINOR] — "toString()" should never be called on a String object
- `java:S1874` [MINOR] — "@Deprecated" code should not be used
- `java:S1905` [MINOR] — Redundant casts should not be used
- `java:S1939` [MINOR] — Extensions and implementations should not be redundant
- `java:S1940` [MINOR] — Boolean checks should not be inverted
- `java:S1941` [MINOR] — Variables should not be declared before they are relevant
- `java:S1942` [MINOR] — Simple class names should be used
- `java:S1943` [MINOR] — Classes and methods that rely on the default system encoding should not be used
- `java:S2039` [MINOR] — Member variable visibility should be specified
- `java:S2059` [MINOR] — "Serializable" inner classes of "Serializable" classes should be static
- `java:S2065` [MINOR] — Fields in non-serializable classes should not be "transient"
- `java:S2094` [MINOR] — Classes should not be empty
- `java:S2130` [MINOR] — Parsing should be used to convert "Strings" to primitives
- `java:S2140` [MINOR] — Methods of "Random" that return floating point values should not be used in random integer generation
- `java:S2147` [MINOR] — Catches should be combined
- `java:S2148` [MINOR] — Underscores should be used to make large numbers readable
- `java:S2156` [MINOR] — "final" classes should not have "protected" members
- `java:S2160` [MINOR] — Subclasses that add fields to classes that override "equals" should also override "equals"
- `java:S2165` [MINOR] — "finalize" should not set fields to "null"
- `java:S2196` [MINOR] — Switches should be used for sequences of simple "String" tests
- `java:S2203` [MINOR] — "collect" should be used with "Streams" instead of "list::add"
- `java:S2221` [MINOR] — "Exception" should not be caught when not required by called methods
- `java:S2250` [MINOR] — Collection methods with O(n) performance should be used carefully
- `java:S2293` [MINOR] — The diamond operator ("<>") should be used
- `java:S2309` [MINOR] — Files should not be empty
- `java:S2325` [MINOR] — "private" and "final" methods that don't access instance data should be "static"
- `java:S2333` [MINOR] — Redundant modifiers should not be used
- `java:S2384` [MINOR] — Private mutable members should not be stored or returned directly
- `java:S2386` [MINOR] — Mutable fields should not be "public static"
- `java:S2698` [MINOR] — Test assertions should include messages
- `java:S2701` [MINOR] — Literal boolean values and nulls should not be used in assertions
- `java:S2737` [MINOR] — "catch" clauses should do more than rethrow
- `java:S2786` [MINOR] — Nested "enum"s should not be declared static
- `java:S2924` [MINOR] — JUnit rules should be used
- `java:S2959` [MINOR] — Unnecessary semicolons should be omitted
- `java:S2974` [MINOR] — Classes without "public" constructors should be "final"
- `java:S3008` [MINOR] — Static non-final field names should comply with a naming convention
- `java:S3012` [MINOR] — Arrays and lists should not be copied using loops
- `java:S3024` [MINOR] — Arguments to "append" should not be concatenated
- `java:S3033` [MINOR] — ".isEmpty" should be used to test for the emptiness of StringBuffers/Builders
- `java:S3038` [MINOR] — Abstract methods should not be redundant
- `java:S3047` [MINOR] — Multiple loops over the same set should be combined
- `java:S3052` [MINOR] — Fields should not be initialized to default values
- `java:S3066` [MINOR] — "enum" fields should not be publicly mutable
- `java:S3242` [MINOR] — Method parameters should be declared with base types
- `java:S3254` [MINOR] — Default annotation parameter values should not be passed as arguments
- `java:S3398` [MINOR] — "private" methods called only by inner classes should be moved to those classes
- `java:S3400` [MINOR] — Methods should not return constants
- `java:S3416` [MINOR] — Loggers should be named for their enclosing classes
- `java:S3437` [MINOR] — Value-based objects should not be serialized
- `java:S3577` [MINOR] — Test classes should comply with a naming convention
- `java:S3578` [MINOR] — Test methods should comply with a naming convention
- `java:S3626` [MINOR] — Jump statements should not be redundant
- `java:S3658` [MINOR] — Unit tests should throw exceptions
- `java:S3878` [MINOR] — Arrays should not be created for varargs parameters
- `java:S4030` [MINOR] — Collection contents should be used
- `java:S4032` [MINOR] — Packages containing only "package-info.java" should be removed
- `java:S4034` [MINOR] — "Stream" call chains should be simplified when possible
- `java:S4065` [MINOR] — "ThreadLocal.withInitial" should be preferred
- `java:S4087` [MINOR] — "close()" calls should not be redundant
- `java:S4174` [MINOR] — Local constants should follow naming conventions for constants
- `java:S4201` [MINOR] — Null checks should not be used with "instanceof"
- `java:S4266` [MINOR] — "Stream.collect()" calls should not be redundant
- `java:S4276` [MINOR] — Functional Interfaces should be as specialised as possible
- `java:S4349` [MINOR] — "write(byte[],int,int)" should be overridden
- `java:S4488` [MINOR] — Composed "@RequestMapping" variants should be preferred
- `java:S4682` [MINOR] — "@CheckForNull" or "@Nullable" should not be used on primitive types
- `java:S4719` [MINOR] — "StandardCharsets" constants should be preferred
- `java:S4838` [MINOR] — An iteration on a Collection should be performed on the type handled by the Collection
- `java:S4926` [MINOR] — "serialVersionUID" should not be declared blindly
- `java:S4929` [MINOR] — "read(byte[],int,int)" should be overridden
- `java:S4968` [MINOR] — The upper bound of type variables and wildcards should not be "final"
- `java:S4977` [MINOR] — Type parameters should not shadow other type parameters
- `java:S5194` [MINOR] — Use Java 14 "switch" expression
- `java:S5411` [MINOR] — Avoid using boxed "Boolean" types directly in boolean expressions
- `java:S5663` [MINOR] — Simple string literal should be used for single line strings
- `java:S5665` [MINOR] — Escape sequences should not be used in text blocks
- `java:S5777` [MINOR] — Exception testing via JUnit @Test annotation should be avoided
- `java:S5838` [MINOR] — Chained AssertJ assertions should be simplified to the corresponding dedicated assertion
- `java:S5853` [MINOR] — Consecutive AssertJ "assertThat" statements should be chained
- `java:S5857` [MINOR] — Character classes should be preferred over reluctant quantifiers in regular expressions
- `java:S5867` [MINOR] — Unicode-aware versions of character classes should be preferred
- `java:S6068` [MINOR] — Call to Mockito method "verify", "when" or "given" should be simplified
- `java:S6201` [MINOR] — Pattern Matching for "instanceof" operator should be used instead of simple "instanceof" + cast
- `java:S6203` [MINOR] — Text blocks should not be used in complex expressions
- `java:S6205` [MINOR] — Switch arrow labels should not use redundant keywords
- `java:S6217` [MINOR] — Permitted types of a sealed class should be omitted if they are declared in the same file
- `java:S6219` [MINOR] — 'serialVersionUID' field should not be set to '0L' in records
- `java:S6244` [MINOR] — Consumer Builders should be used
- `java:S6246` [MINOR] — Lambdas should not invoke other lambdas synchronously
- `java:S6262` [MINOR] — AWS region should not be set with a hardcoded String
- `java:S6353` [MINOR] — Regular expression quantifiers and character classes should be used concisely
- `java:S7158` [MINOR] — "String.isEmpty()" should be used to test for emptiness
- `java:S7466` [MINOR] — Unnamed variable declarations should use the "var" identifier
- `java:S7467` [MINOR] — Unused exception parameter should use the unnamed variable pattern
- `java:S7474` [MINOR] — Markdown, HTML and Javadoc tags should be consistent
- `java:S7476` [MINOR] — Comments should start with the appropriate number of slashes
- `java:S7477` [MINOR] — Class name should be omitted when unchanged by class transform
- `java:S7478` [MINOR] — "transformClass" method should be used instead of "build" when transforming a class
- `java:S7479` [MINOR] — "ClassBuilder.withMethodBody" should be preferred to "ClassBuilder.withMethod"
- `java:S7481` [MINOR] — Gatherer.ofSequential() should be used to build sequential gathers
- `java:S7629` [MINOR] — When a defaultFinisher is passed to a Gatherer factory, use the overload that does not take a finisher
- `java:S818` [MINOR] — Literal suffixes should be upper case
- `java:S8445` [MINOR] — Import declarations should be grouped by specificity
- `java:S1133` [INFO] — Deprecated code should be removed
- `java:S1135` [INFO] — Track uses of "TODO" tags
- `java:S1309` [INFO] — Track uses of "@SuppressWarnings" annotations
- `java:S3688` [INFO] — Track uses of disallowed classes
- `java:S5786` [INFO] — JUnit5 test classes and methods should have default package visibility
- `java:S5793` [INFO] — Migrate your tests from JUnit4 to the new JUnit5 annotations
- `java:S6208` [INFO] — Comma-separated labels should be used in Switch with colon case
- `java:S6212` [INFO] — Local-Variable Type Inference should be used
- `java:S6539` [INFO] — Classes should not depend on an excessive number of classes (aka Monster Class)
- `java:S6541` [INFO] — Methods should not perform too many tasks (aka Brain method)
- `java:S6548` [INFO] — The Singleton design pattern should be used with care
- `java:S7475` [INFO] — Types of unused record components should be removed from pattern matching
- `java:S923` [INFO] — Functions should not be defined with a variable number of arguments

