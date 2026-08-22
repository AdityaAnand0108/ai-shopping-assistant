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

Built phase by phase. Current: **Phase 1 — domain model and demo dataset complete.**

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Project scaffold, build, config profiles | ✅ Done |
| 1 | Domain model, migrations, CSV seed data | ✅ Done |
| 2 | Auth: signup, login, BCrypt, JWT security chain | ⬜ Next |
| 3 | Deterministic REST API, scoped to the signed-in user | ⬜ |
| 4 | Ollama + Spring AI wiring, plain chat | ⬜ |
| 5 | Tool calling (search, orders, draft→confirm purchase) | ⬜ |
| 6 | RAG over the product catalog | ⬜ |
| 7 | Guardrails and scope enforcement | ⬜ |
| 8 | Governance: audit, explainability, feedback, eval | ⬜ |
| 9 | Streaming and hardening | ⬜ |
| 10 | React frontend | ⬜ |
| 11 | Docs, architecture, limitations note | ⬜ |

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
| Frontend | React + Vite + TypeScript (Phase 10) |

## Demo dataset

The catalog is loaded from `src/main/resources/data/products.csv` — 60 products
across apparel, footwear, electronics, home, sports and books. One item
(`NIK-TS-004`) is deliberately out of stock so availability handling can be
tested.

Four accounts are seeded on first startup. Passwords are hashed with BCrypt; the
plaintext below exists only so the demo is runnable.

| Username | Password | Orders |
|----------|----------|--------|
| `aditya` | `Password123` | 5, spanning delivered, out for delivery, shipped, placed and returned |
| `priya` | `Password123` | 3, including one out for delivery |
| `rahul` | `Password123` | 1, cancelled |
| `demo` | `Demo1234` | none — exercises the empty state |

Two accounts hold real orders on purpose: signing in as one and asking about the
other's order number is how the cross-account guardrail gets demonstrated.

Seeding is idempotent — each section is skipped when its table already has rows,
so restarting against the file database does not duplicate anything. To start
over, stop the app and delete the `data/` directory.

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
