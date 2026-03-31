# java-spring-best-practices

Enterprise-grade Spring Boot development guide for AI coding agents. Updated for **Spring Boot 3.5.x / 4.0.x** and **Java 17+ (optimized for 21+)**.

## What This Skill Does

When an AI agent loads this skill, it gains deep knowledge of modern Spring Boot best practices, enabling it to:

- Generate production-ready REST APIs with proper layered architecture
- Use **Java Records** as immutable DTOs instead of mutable `@Data` classes
- Implement **ProblemDetail (RFC 7807)** error responses with sealed exception hierarchies
- Configure **Virtual Threads** for high-concurrency I/O without reactive programming
- Replace `RestTemplate` with the modern **RestClient** fluent API
- Write integration tests with **Testcontainers** (real databases, not H2)
- Set up **structured logging**, observability, and proper configuration patterns
- Avoid common anti-patterns (field injection, `synchronized` with virtual threads, etc.)

## Skill Structure

```
java-spring-best-practices/
├── SKILL.md                              # Main instructions (~400 lines)
├── references/
│   ├── architecture.md                   # Layer vs feature-based structure
│   ├── config-reference.md               # Profiles, Docker Compose, externalization
│   ├── exception-handling.md             # Full sealed hierarchy, validation
│   ├── performance-guide.md              # Virtual threads, caching, observability
│   ├── streams-guide.md                  # Functional patterns, modern Java idioms
│   └── testing-guide.md                  # Mockito, Testcontainers, slice tests
└── scripts/
    ├── GlobalExceptionHandler.java       # ProblemDetail handler example
    ├── ProductController.java            # REST controller with records
    ├── ProductService.java               # Service with business logic
    ├── ProductServiceTest.java           # Unit + integration test patterns
    ├── logback-config.xml                # Structured logging config
    └── pom-template.xml                  # Complete Maven setup
```

The skill uses **progressive disclosure**: the agent loads `SKILL.md` first (~400 lines), then fetches reference docs and code examples only when needed for the specific task.

## Installation

```bash
npx skills add cosbort/agent-skills/java-spring-best-practices
```

Or manually:

```bash
# Claude Code
cp -r java-spring-best-practices ~/.claude/skills/

# Cross-agent (Copilot, Codex, Cursor, etc.)
cp -r java-spring-best-practices ~/.agents/skills/
```

## Key Topics Covered

| Topic | What's Inside |
|-------|--------------|
| Architecture | Layered vs feature-based, DI patterns |
| DTOs | Java Records with Bean Validation |
| Error Handling | ProblemDetail + sealed class hierarchy |
| Virtual Threads | Configuration, pinning avoidance, pool sizing |
| REST Clients | RestClient, `@HttpExchange` declarative interfaces |
| Testing | Mockito unit tests, Testcontainers integration tests |
| Logging | SLF4J placeholders, structured logging (ECS/GELF/Logstash) |
| Configuration | Record-based `@ConfigurationProperties`, YAML profiles |
| Performance | Caching (Caffeine), observability (Micrometer), GraalVM native |

## Version History

- **v2.0.0** (March 2026) — Major update: Records as DTOs, Virtual Threads, ProblemDetail, RestClient, Testcontainers, structured logging, sealed classes, GraalVM native awareness
- **v1.0.0** — Initial release with core Spring Boot patterns

## License

MIT
