# ShopAssist — AI Shopping Assistant

A conversational storefront where shoppers search products, track orders and buy
things by talking to an assistant. Built as a **Spring Boot** monolith with a
**React** frontend, running entirely on free local tooling — no paid APIs, no
cloud.

> **The one idea worth knowing.** Every transactional answer comes from an
> explicit backend tool call against the database — never from the model
> guessing. The model chooses *which* question to ask the shop; it never invents
> the answer. Where it does invent something anyway, a grounding check says so.

---

## Features

- **Chat that does things.** Search the catalog, check an order, place one — in
  plain language, through nine typed tools that reach the same services the REST
  API does.
- **Semantic search.** "Something to keep me warm" finds a down jacket, with no
  shared keyword. Retrieval proposes candidates; SQL applies price, brand and
  stock filters and re-reads every figure from the database.
- **Purchases in two steps.** Pricing a basket and placing the order are separate
  calls that cannot be combined, so no single request — from a model or a button
  — both decides on a purchase and completes it.
- **Answers that show their working.** Each reply reports which tools ran, and
  flags any price, SKU or date that no tool returned.
- **A real storefront too.** Product grid, cart, checkout, order history and
  tracking timeline — all of it usable without saying a word to the assistant.
- **Light and dark themes**, both checked at WCAG AA for body text.

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
| Frontend | React 19, Vite, TypeScript |

## Quick start

**Prerequisites:** JDK 21, Node.js 20+, and [Ollama](https://ollama.com/download).
Maven is not needed — the project uses the wrapper.

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

Start the backend on port 8080:

```bash
./mvnw spring-boot:run
```

Start the frontend on port 5173, in a second terminal:

```bash
npm install --prefix frontend && npm run dev --prefix frontend
```

Open **http://localhost:5173** and sign in with a demo account below. Vite
proxies `/api` to the backend, so both need to be running.

| | |
|---|---|
| App | http://localhost:5173 |
| API docs | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

A step-by-step walkthrough for a first-time clone is in
[`docs/SETUP-GUIDE.pdf`](docs/SETUP-GUIDE.pdf).

### Demo accounts

Seeded on first startup. Passwords are BCrypt hashed; the plaintext exists only
so the demo is runnable.

| Username | Password | Orders |
|----------|----------|--------|
| `satvik` | `Password123` | 5, spanning delivered, shipped, placed and returned |
| `sarah` | `Password123` | 3, including one out for delivery |
| `rahul` | `Password123` | 1, cancelled |
| `demo` | `Demo1234` | none — exercises the empty state |

Two accounts hold real orders on purpose: signing in as one and asking about the
other's order is how the cross-account guardrail gets demonstrated.

The catalog is 60 products across six categories, loaded from
`src/main/resources/data/products.csv`. One item (`NIK-TS-004`) is deliberately
out of stock. Seeding is idempotent — to start over, stop the app and delete the
`data/` directory.

### Running against MySQL

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Override `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` as needed. The
default `dev` profile runs H2 in MySQL compatibility mode, so one set of Flyway
migrations works against both.

## Testing

```bash
./mvnw test
```

192 tests, fully in-memory — neither Ollama nor MySQL is required. `./mvnw clean
verify` additionally fails the build on malformed Javadoc.

## Pages

| Route | Page | Access |
|-------|------|--------|
| `/` | Landing page | Public |
| `/login`, `/signup` | Auth forms | Public |
| `/catalog` | Product grid with brand, category and price filters | Signed in |
| `/cart`, `/checkout` | Basket, then the server-priced draft and Place order | Signed in |
| `/chat` | The assistant, with conversation history | Signed in |
| `/orders` | Order list, detail and status timeline | Signed in |

## API

Full reference with request shapes and examples: **[docs/api.md](docs/api.md)**.

| Area | Endpoints |
|------|-----------|
| Auth | `POST /api/auth/signup`, `/login`, `/logout` · `GET /api/auth/me` |
| Catalog | `GET /api/products`, `/products/{sku}`, `/products/filters` |
| Orders | `GET /api/orders`, `/orders/{orderNumber}`, `/{orderNumber}/timeline` |
| Purchases | `POST /api/purchases/draft`, `/{ref}/confirm` · `DELETE /{ref}` |
| Chat | `POST /api/chat` · `GET /api/chat/conversations`, `/conversations/{id}` |

Authentication is a stateless JWT in an `Authorization: Bearer <token>` header,
valid for 60 minutes. Catalog reads, health and the API docs are open; everything
tied to a person requires a token.

## Project layout

Packages are organised **layer-first, then by business domain**, and every one
carries a `package-info.java` describing its role and invariants.

```
src/main/java/com/shopassist/
├── advice/       one exit for every error, as RFC 7807 documents
├── config/       @Configuration and @ConfigurationProperties
├── controllers/  HTTP entry points          (auth, catalog, chat, order)
├── dto/          request and response records
├── entity/       JPA entities
├── repository/   Spring Data repositories
├── security/     principal, JWT issuing and verification, filter, denylist
├── services/     business logic
│   └── ai/       model client, retriever, guards
│       └── tools/  @Tool methods — the only way the model reaches data
└── util/         stateless helpers
frontend/src/
├── api/          typed client and response types
├── auth/  cart/  chat/  theme/     React context providers
├── components/   shared UI
└── pages/        one per route
```

Test packages mirror the backend layout.

## Documentation

The design decisions behind this project, and the evidence for them, live in
`docs/`:

| Document | What is in it |
|----------|---------------|
| [Architecture](docs/architecture.md) | The tool layer, what makes it safe, chat design decisions, and the commenting conventions the build enforces |
| [API reference](docs/api.md) | Every endpoint, what it deliberately will not tell you, and curl examples |
| [Guardrails and accuracy](docs/guardrails.md) | The four checks around the model call, what the model got wrong in live testing, and what the backend guarantees regardless |
| [Semantic search](docs/semantic-search.md) | How hybrid retrieval works, and the measurements behind the 0.55 threshold |
| [Frontend](docs/frontend.md) | State that outlives the router, the cart, buying from chat, and theming |

## Status and limitations

Built and working: auth, the deterministic REST API, tool-calling chat, RAG over
the catalog, guardrails, the storefront, cart and checkout.

Deferred: governance (audit trail, feedback endpoint, scored evaluation) and
response streaming.

The limitations worth knowing before reading the code:

- **The model is a 7B local model, and it shows.** It has invented SKUs, quoted
  totals no tool returned, and asked for confirmation instead of calling the
  tool that confirms. Every one of those was caught or contained by the backend
  — that containment is the point of the design, and the full log of what was
  observed is in [Guardrails and accuracy](docs/guardrails.md).
- **Prompt rules are requests, not constraints.** The guarantees hold because of
  code, not because of the prompt.
- **Accuracy is described, not measured.** Failures were observed by hand rather
  than scored against a fixed question set.
- **`/catalog` is gated in the UI only.** `GET /api/products` remains public, so
  the page is hidden from signed-out visitors and the data is not.
- **Latency is 1–11 seconds** per reply on CPU, longer when tools run.
