---
language: java
version: "17"
---

# Language Idioms: Java

## Error Handling

Checked exceptions are avoided in application code; use unchecked `AppException` / `ParamException` for business and validation failures. Propagate by throwing; do not swallow without logging. Log with `log.error("message", exception)` and SLF4J `{}` placeholders.

## Type System & Object Model

Classes with Lombok (`@Data`, `@Builder` where appropriate). Interfaces for services (`I{Feature}Service`) with `{Feature}ServiceImpl` implementations. Prefer composition over inheritance. Use `Optional` sparingly at API boundaries; avoid null returns in public SDK contracts without documentation.

## Naming Conventions

| Kind | Pattern | Example |
|------|---------|---------|
| Service interface | `I{Name}Service` | `IProcessService` |
| Service impl | `{Name}ServiceImpl` | `ProcessServiceImpl` |
| REST | `{Feature}Rest` | `ProcessRest` |
| Request DTO | `{Feature}Req` | `CreateProcessReq` |
| Response DTO | `{Feature}Resp` | `ProcessResp` |
| Test class | `{ClassName}Test` | `ProcessServiceImplTest` |
| Test method | `test{Method}_{Scenario}` | `testCreateProcess_Success` |
| Packages | lowercase segments | `com.airedgesoft.gf.biz.process` |

## Parameter Design

Prefer small parameter objects (Req DTOs) for REST; use `@Valid` on controller inputs. Avoid long parameter lists in internal APIs; use dedicated command/query types when >3 related parameters.

## Dependency Management

Constructor injection only via `@RequiredArgsConstructor` on `@Service` / `@Component` classes. No field `@Autowired`. No static bean lookup in biz layer.

## Testing Idioms

JUnit 5 (`@Test`, `@DisplayName` optional). Mockito for unit tests; prefer integration tests without mocks when feasible. Test methods named `test{Method}_{Scenario}`. Maven: module-scoped `mvn test` from component or root POM.

## Module & Packaging

Maven multi-module; one deployable from `apps/`. Source under `src/main/java` mirroring package tree. Resources in `src/main/resources`; Flyway scripts under `upgrade/`.

---
*Java 17 + Spring Boot 3.3 idioms for AirEdge Backend. Refine via `/language-idioms-refiner`.*
