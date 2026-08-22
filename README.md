# AI-Powered Shopping Assistant

A conversational shopping assistant for an e-commerce catalog, built as a
**Spring Boot monolith** with a **React** frontend.

Customers can search products, check order status, and place purchases through
chat. Every transactional answer is produced by an explicit backend **tool call**
against the database — never by the language model guessing. Product discovery is
grounded with **RAG** over the catalog.

Runs entirely on free, local tooling. No paid APIs, no cloud infrastructure.

---

## Status

Built phase by phase. Current: **Phase 0 — scaffold complete.**

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Project scaffold, build, config profiles | ✅ Done |
| 1 | Domain model, migrations, CSV seed data | ⬜ Next |
| 2 | Deterministic REST API (no AI) | ⬜ |
| 3 | Ollama + Spring AI wiring, plain chat | ⬜ |
| 4 | Tool calling (search, orders, draft→confirm purchase) | ⬜ |
| 5 | RAG over the product catalog | ⬜ |
| 6 | Guardrails and scope enforcement | ⬜ |
| 7 | Governance: audit, explainability, feedback, eval | ⬜ |
| 8 | Streaming and hardening | ⬜ |
| 9 | React frontend | ⬜ |
| 10 | Docs, architecture, limitations note | ⬜ |

## Tech stack

| Layer | Choice |
|-------|--------|
| Runtime | Java 21, Spring Boot 3.5.15 |
| AI | Spring AI 1.1.8 |
| LLM | Ollama, `qwen2.5:7b` (local) |
| Embeddings | Ollama, `nomic-embed-text` |
| Vector store | Spring AI `SimpleVectorStore`, file-persisted |
| Database | H2 file (default) or MySQL 8 (`mysql` profile) |
| Migrations | Flyway |
| Frontend | React + Vite + TypeScript (Phase 9) |

The default `dev` profile runs H2 in **MySQL compatibility mode**, so a single set
of Flyway migrations works against both H2 and a real MySQL server. This keeps a
clean-environment run to one command with nothing to install.

## Prerequisites

- **JDK 21** — required now.
- **Ollama** — required from Phase 3 onward. https://ollama.com/download
- **Node.js 20+** — required from Phase 9 onward.

Maven is not required; the project uses the Maven wrapper.

## Running

```bash
./mvnw spring-boot:run
```

Then:

- Health: http://localhost:8080/actuator/health
- Info: http://localhost:8080/api/info
- API docs: http://localhost:8080/swagger-ui.html

Run the tests:

```bash
./mvnw test
```

Tests run fully in-memory and require neither Ollama nor MySQL.

### Against a real MySQL

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Override `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` as needed.

### Models (from Phase 3)

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

## Project layout

```
src/main/java/com/shopassist/
├── catalog/      Products: entity, repository, service, REST API
├── order/        Orders, order items, status timeline
├── customer/     Customer identity and session scoping
├── chat/         Conversations and messages — the only AI entry point
├── ai/
│   ├── client/   ChatClient configuration
│   ├── tools/    @Tool methods — the only way the model reaches data
│   ├── rag/      Embedding pipeline and retriever
│   ├── prompt/   System prompts and templates
│   └── guard/    Input and output guardrails
├── governance/   Tool-call audit, feedback, accuracy evaluation
└── common/       Shared DTOs, error handling, configuration
```

## Design note

The model never sees SQL, entity classes, or internal identifiers. It sees a small
set of typed tool signatures. Customer identity is injected server-side from the
session and is never a tool argument, so one customer cannot ask about another's
order. Purchases are split into `createOrderDraft` and `confirmOrder`, so the
assistant can never commit an order without an explicit user confirmation.

Documented in full in Phase 10.
