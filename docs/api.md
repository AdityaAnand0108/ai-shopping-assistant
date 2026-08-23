# API reference

Everything the assistant does through tool calls is available here as plain REST,
and works with no AI running. That ordering is deliberate: when the assistant
says something wrong, these endpoints tell you immediately whether the data layer
or the model is at fault.

[← back to README](../README.md)

Interactive docs are at http://localhost:8080/swagger-ui.html while the app is
running.

---

## Authentication

Stateless JWT in an `Authorization: Bearer <token>` header. Tokens last 60
minutes; there is no refresh token.

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/auth/signup` | — | Register and sign in immediately |
| `POST` | `/api/auth/login` | — | Exchange credentials for a token |
| `GET` | `/api/auth/me` | ✅ | The signed-in account |
| `POST` | `/api/auth/logout` | ✅ | Revoke the token used for the request |

```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"satvik\",\"password\":\"Password123\"}"
```

```bash
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

Catalog reads, health and the API docs stay open. Everything tied to a person —
chat, orders, profile, purchases — requires a token.

---

## Catalog — public

No token required. The frontend's `/catalog` page is behind sign-in, but that is
a product decision applied in the UI; it does not change what these endpoints
will answer.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/products` | Search with filters, sorting and paging |
| `GET` | `/api/products/filters` | Brands and categories available to filter on |
| `GET` | `/api/products/{sku}` | One product in full |

Search parameters: `q`, `brand`, `category`, `minPrice`, `maxPrice`,
`inStockOnly`, `sort` (`RELEVANCE`, `PRICE`, `RATING`, `NAME`), `direction`
(`ASC`/`DESC`), `page`, `size`.

```bash
curl -s "http://localhost:8080/api/products?q=t-shirt&brand=Nike&inStockOnly=true"
```

---

## Orders — requires a token

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/orders` | Your orders, newest first |
| `GET` | `/api/orders/{orderNumber}` | One of your orders, with lines and timeline |
| `GET` | `/api/orders/{orderNumber}/timeline` | Just the tracking timeline |

No path carries a user identifier. Whose orders these are comes from the token,
so there is no URL to edit to reach somebody else's.

---

## Purchases — requires a token

Checkout is two calls, and that is the point. The first prices a basket and
creates nothing; the second is the only one that places an order.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/purchases/draft` | Price a basket. Creates no order |
| `POST` | `/api/purchases/{reference}/confirm` | Place the order for a priced basket |
| `DELETE` | `/api/purchases/{reference}` | Abandon a priced basket |

The draft request carries SKUs and quantities and **no prices** — the server
prices the basket from the catalog, and the response is what the shopper is shown
before confirming. A checkout that accepted a total from the browser would be one
edited request away from a free order.

```bash
curl -s -X POST http://localhost:8080/api/purchases/draft -H "Authorization: Bearer PASTE_TOKEN_HERE" -H "Content-Type: application/json" -d "{\"items\":[{\"sku\":\"SNY-HP-001\",\"quantity\":2}]}"
```

```bash
curl -s -X POST http://localhost:8080/api/purchases/PASTE_REFERENCE_HERE/confirm -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

Behaviour worth knowing:

- Confirming **re-reads prices and stock** rather than trusting the draft.
- A draft holds for **15 minutes**; confirming a lapsed one is refused rather
  than silently repriced.
- Confirming the same draft **twice returns the order that already exists**, so a
  double-clicked button cannot buy twice.
- Quantities are bounded at 10 per line and 10 lines per order.

This is the same `PurchaseService` the assistant's tools call, so the browser and
the model cannot end up with different definitions of what a purchase is. The
only difference is that the page passes the draft reference it was given, where
the assistant resolves the newest draft in its conversation server-side. See
[Architecture](architecture.md#buying-takes-two-calls-that-cannot-be-combined).

---

## Chat — requires a token

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/chat` | Send a message, get a reply |
| `GET` | `/api/chat/conversations` | Your threads, most recently active first |
| `GET` | `/api/chat/conversations/{id}` | Replay one thread |

Omit `conversationId` to start a thread; pass it to continue one.

```bash
curl -s -X POST http://localhost:8080/api/chat -H "Authorization: Bearer PASTE_TOKEN_HERE" -H "Content-Type: application/json" -d "{\"message\":\"What can you help me with?\"}"
```

A reply carries three things beside the message:

- **`insight`** — which tools ran, whether every figure in the reply is supported
  by what they returned, and which are not. See
  [Guardrails](guardrails.md#grounding-check).
- **`action`** — a purchase this turn priced or placed, holding the tool's own
  result rather than the model's description of it. This is what the frontend
  renders as a confirmable card. See [Frontend](frontend.md#buying-from-the-chat).

---

## What these endpoints will not tell you

- **Stock levels.** Availability is published as `IN_STOCK`, `LOW_STOCK` or
  `OUT_OF_STOCK`, never the count. Inventory is commercially sensitive and no
  shopper needs the figure, so the API cannot be scraped to reconstruct it. The
  `checkStock` tool likewise answers yes or no.
- **Whether an order number is real.** An order belonging to another shopper and
  an order that was never issued return byte-identical 404s. If they differed,
  the order-number space could be walked to find live numbers.
- **Anything about the schema.** Responses are whitelisted records, not
  serialised entities, so primary keys, foreign keys, audit timestamps and stock
  counts cannot start appearing because a column was added later. Errors leave
  through one handler as RFC 7807 documents, so no stack trace, SQL fragment or
  Java type name reaches a client.
- **More than 50 products at a time.** Page size is capped server-side.
- **Results ordered by an arbitrary column.** `sort` is an allowlist of four
  named orderings. A free-text sort parameter would let a caller order by
  `stockQuantity` and read inventory back out of the ordering. Each ordering
  appends SKU as a tiebreak, so paging cannot repeat or skip a row.
