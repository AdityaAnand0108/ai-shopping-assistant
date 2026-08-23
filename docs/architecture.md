# Architecture

How the assistant reaches data, why that path is safe, and the decisions behind
the chat layer.

[← back to README](../README.md)

---

## The tool layer

The assistant reaches data only through nine tools. Each calls the same services
the REST API calls, so a tool can never see more than an HTTP client can.

| Tool | What it does |
|------|--------------|
| `searchProducts` | Filtered catalog search, capped at 8 results |
| `getProductDetails` | One product by exact SKU |
| `checkStock` | Whether a quantity can be bought — yes or no, never a count |
| `listMyOrders` | The signed-in shopper's orders. **Takes no arguments** |
| `getOrderStatus` | One order in full, with its tracking timeline |
| `getDeliveryEstimate` | Recorded ETA and latest tracking update |
| `createOrderDraft` | Prices a proposal. **Buys nothing** |
| `confirmOrder` | The only path to a real order |
| `cancelOrder` | Cancels, if the status still allows it |

### No tool takes a customer argument

The shopper is resolved from the authenticated session inside the service layer,
so the model has no parameter through which it could name a different account —
not because it is told not to, but because the vocabulary to express it does not
exist. A test asserts this reflectively, so adding such a parameter later fails
the build.

This is a guardrail made of structure rather than instruction. The prompt asks
the model to behave; the type signatures are what happens when it does not.

### Buying takes two calls that cannot be combined

`createOrderDraft` prices a proposal and stops. `confirmOrder` is the only thing
that creates an order, and it re-reads prices and stock rather than trusting what
the draft recorded — a quote taken minutes ago is not a promise the shop can
still keep.

A model that is confused, over-eager, or argued into a purchase has no single
call that completes one.

`confirmOrder` deliberately takes **no arguments**. It used to take the draft
reference, and that could not work: conversation history replays only the text of
earlier turns, so a reference handed to the model in one turn is gone by the
next. Asked to confirm, the model either invented a reference or re-priced the
purchase forever, and a shopper saying "yes, place the order" could loop without
ever buying anything.

Resolving the draft server-side removes that whole class of failure, and follows
the same principle as `listMyOrders`: the model should never have to carry an
identifier it can get wrong.

### Scoped to one conversation

Resolving "the shopper's most recent draft" is the right answer inside one thread
and the wrong one across two. A draft left unconfirmed in an earlier conversation
was the newest draft everywhere, so agreeing to a purchase in a later
conversation could confirm the earlier one instead — a different product, at a
different price, never mentioned in the thread the shopper was reading.

Seen in a real log: a $1,099 draft pending in one thread was the target of a
`confirmOrder` in another thread about $129 shoes. It failed for an unrelated
reason, not by design.

Drafts now carry the conversation they were proposed in
(`V6__order_draft_conversation.sql`), and confirmation is filtered by it. Drafts
created from the checkout page carry none, and are confirmed by reference.

---

## Chat design decisions

- **`AssistantModel` is an interface.** Exactly one class knows Spring AI exists.
  That makes the chat surface testable with no model server running, keeps the
  provider swappable, and gives tool calling one place to attach.
- **History is windowed** to the last 12 turns, fetched newest-first with a SQL
  limit and reversed. An unbounded thread would grow the prompt until it
  overflowed the context window, and that failure arrives as a quietly worse
  answer rather than an error.
- **Conversations are owner-scoped** the same way orders are, and the repository
  has no lookup that omits the owner. Another shopper cannot read a thread or
  post into one even with a valid conversation id.
- **Temperature is 0.1.** This assistant reports facts fetched from a database; a
  creative rephrasing of an order status is simply a wrong answer.
- **A model outage returns 503**, not 500 — a dependency being down is not the
  shop being broken, and the distinction tells a client whether retrying helps.
  The response names no host, port or library.
- **`/actuator/health` includes the model**, so an operator can tell a model
  outage from an application fault without reading logs.

### The model call happens outside any transaction

`ChatService` is not `@Transactional`; `ConversationStore` commits the question
before the call and the answer after.

One transaction around the whole turn would hold a pooled connection for the
several seconds a local model takes — and worse, an ordinary "no such order" from
a tool marks that shared transaction rollback-only, so the request fails at
commit time after the assistant has already composed a good reply. This was a
real bug, found by asking one shopper about another's order.

A consequence worth knowing: if the model fails, the shopper's question is
already committed and no assistant turn joins it. That is the right way round.
What someone asked is worth keeping; a reply that never existed is not.

### What is recorded

Every turn is persisted — both sides — with the model that produced each reply
and how long it took:

```
role      | model      | latency_ms | content
USER      | null       | null       | Do you have Nike t-shirts in stock…
ASSISTANT | qwen2.5:7b | 1066       | I can't check stock or prices right now…
```

Message ids are public references precisely so an audit trail and shopper
feedback can hang off them. Neither is built — see the deferred work in the
[README](../README.md#status-and-limitations).

---

## Authentication

Stateless JWT in an `Authorization: Bearer <token>` header, valid 60 minutes.
There is no refresh token, so a shopper signs in again when one expires.

- Passwords are BCrypt hashed and never appear in a response or a log line. The
  signup DTO caps them at 72 characters because BCrypt ignores anything beyond
  that, and silently truncating would weaken the hash without saying so.
- The token's `sub` is the account's random `public_ref`, never the database
  primary key, so a leaked token reveals nothing about how many accounts exist.
- The user row is re-read on every request rather than trusted from the token
  body, so disabling an account takes effect at once instead of waiting out the
  token's remaining lifetime.
- Five failed sign-ins lock an account for 15 minutes.
- Unknown username and wrong password return a byte-identical response, and a
  miss still runs one BCrypt comparison so response timing does not separate the
  two either.
- Every error leaves through one handler that emits RFC 7807 problem documents,
  so no stack trace, SQL fragment or constraint name can reach a client.

Signing uses a development key checked into `application.yml`, which the
application warns about on every startup. Override it anywhere real:

```bash
SHOPASSIST_JWT_SECRET="a-long-random-secret-of-at-least-32-bytes"
```

### Known limitations

- **Revocation is in memory.** Logout denylists the token id until it would have
  expired anyway, so revocations are lost on restart and are not shared between
  instances. Fine for a single node; a scaled deployment would move the denylist
  to Redis with a TTL equal to the token lifetime — the same interface, a
  different backing store.
- **Signup and lockout confirm that an account exists.** A taken username has to
  be reported so the user can pick another, and a locked-out user has to be told
  why. Login itself gives nothing away; these two paths trade a little
  enumeration exposure for a usable product.

---

## Package layout

Layer-first, then by business domain: the top level names a technical
responsibility, and the domain appears beneath it. Every package carries a
`package-info.java` giving architectural orientation — what it is for, and which
invariants hold across it.

```
src/main/java/com/shopassist/
├── ShopAssistantApplication.java
├── advice/           GlobalExceptionHandler - the single exit for every error
├── config/           @Configuration and @ConfigurationProperties
│   ├── ai/           model + retrieval settings
│   ├── chat/         chat turn bounds
│   └── security/     filter chain, JWT settings, password hashing
├── controllers/      HTTP entry points
│   ├── auth/  catalog/  chat/  order/
├── dto/              request, response and cross-layer records
│   ├── ai/  auth/  catalog/  chat/  order/
├── entity/           JPA entities
│   ├── catalog/  chat/  order/  user/
├── enums/            closed value sets
│   ├── catalog/  chat/  order/  user/
├── exception/        application exceptions
│   ├── ai/  auth/
├── repository/       Spring Data repositories
│   ├── catalog/  chat/  order/  user/
├── scheduler/        startup runners: demo data, then the semantic index
├── security/         principal, token issuing/verification, filter, denylist
├── services/         business logic
│   ├── ai/           model client + retriever (the only Spring AI importers)
│   │   ├── guard/    the checks around the model call
│   │   └── tools/    @Tool methods - the only way the model reaches data
│   ├── auth/  catalog/  chat/  order/
└── util/             stateless helpers
    ├── ai/           system prompts
    └── catalog/      CSV loading
```

There is no `validation/` package: validation is expressed with Jakarta
constraints on the DTOs themselves, and `ProductSearchCriteria` normalises and
bounds its own values in its constructor. No class would belong there.

Test packages mirror this layout.

---

## Documentation conventions

Comments explain **why**, never **what**. The code already says what it does; a
comment that restates it is a second thing to keep in sync and the first thing to
rot.

A comment earns its place when it records one of:

- a decision and the alternative rejected (`OrderRepository` has no
  `findByOrderNumber(String)`, and the Javadoc says why);
- a constraint that is not visible locally (BCrypt ignoring input past 72 bytes,
  `USER` being reserved in MySQL and H2);
- a measurement (the 0.55 similarity threshold, with the scores behind it);
- a failure mode being defended against (holding a transaction across a model
  call, a stale index surfacing an old price).

Deliberately **not** documented: accessors, `from()` mappers, and other members
whose signature is the whole story. Many public methods carry no Javadoc, and
that is the intended state — `ProductSummaryResponse.from(Product)` gains nothing
from a sentence repeating its own name.

### Enforced by the build

```bash
./mvnw clean verify
```

`maven-javadoc-plugin` runs with `doclint=all,-missing` and
`failOnWarnings=true`, so the build fails on malformed HTML, a `@param` that does
not match the signature, or an `{@link}` pointing at something that no longer
exists — the ways documentation quietly turns into lies. `-missing` is excluded
on purpose: requiring a comment on every element is what produces
`@param sku the sku`.

The `clean` matters. `javadoc:javadoc` on its own skips regeneration when its
output looks newer than the sources, and will report success without having
checked anything.
