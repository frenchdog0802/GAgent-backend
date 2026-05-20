# GAgent — AI Agent for Workspace Automation

A backend service where an AI agent interprets natural language requests and executes real actions via Google Workspace APIs (Calendar, Docs, Gmail). The agent plans structured tool calls, and the backend validates and executes them.

## Tech Stack

- **Java 17** / **Spring Boot 4.0.5**
- **PostgreSQL** — persistent storage
- **OpenAI** — tool-calling agent
- **Google Workspace APIs** — Calendar, Docs, Gmail
- **OAuth2** — secure API access
- **Docker** — containerisation

## Project Structure

```
com.gagent
├── GagentApplication.java      # Entry point
├── controller/                  # REST controllers
├── service/                     # Business logic
├── repository/                  # JPA repositories
├── model/                       # JPA entities
├── dto/                         # Request/response DTOs
├── config/                      # Spring configuration
└── exception/                   # Global error handling
```

## Prerequisites

- **JDK 17+**
- **Gradle** (wrapper included)

## Getting Started

### Build

```bash
./gradlew build -x test
```

### Run

```bash
# Option 1 — Gradle
./gradlew bootRun

# Option 2 — JAR
java -jar build/libs/gagent-0.0.1-SNAPSHOT.jar
```

The server starts on **http://localhost:8081**.

### Verify

```bash
curl http://localhost:8081/api/hello
```

Expected response:

```json
{
  "message": "GAgent is running!",
  "timestamp": "2026-04-20T02:25:21.211Z"
}
```

## Configuration

Config lives in `src/main/resources/application.yml`.

PostgreSQL is commented out by default so the server can start without a database. When ready, uncomment the datasource block in `application.yml` and remove the `exclude` from `@SpringBootApplication` in `GagentApplication.java`.
