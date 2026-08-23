# Frontend

React 19 with Vite and TypeScript. One hand-written stylesheet, no component
library — the UI is small enough that a framework would be more bytes and more
indirection than it is worth, and hand-written CSS keeps the accessibility
decisions visible rather than inherited.

[← back to README](../README.md)

---

## Running it

```bash
npm install --prefix frontend
npm run dev --prefix frontend
```

Vite serves on http://localhost:5173 and proxies `/api` to the backend, so run
`./mvnw spring-boot:run` alongside it. Proxying rather than relying on CORS
avoids a preflight on every request and keeps the frontend free of absolute URLs
— the same relative paths work when the built bundle is served by the backend.

## Routes

| Route | Page | Access |
|-------|------|--------|
| `/` | Landing page | Public |
| `/login`, `/signup` | Auth forms | Public |
| `/catalog` | Product grid with brand, category and price filters | Signed in |
| `/cart` | The basket: quantities, removals, running total | Signed in |
| `/checkout` | The server-priced draft, and the button that places the order | Signed in |
| `/chat` | The assistant, with conversation history | Signed in |
| `/orders` | Order list, detail and status timeline | Signed in |

Only the landing page and the two auth forms are reachable without a session;
everything else redirects to sign-in and returns you where you were headed.

`/catalog` is gated **in the UI only**. `GET /api/products` is still public, so
the catalog page is hidden from signed-out visitors but the catalog data is not.
The routes that matter — chat, orders, purchases — are enforced by the backend,
which checks the token on every request and never trusts the client's redirect.

## State that outlives the router

Four providers sit above the router: auth, theme, cart and chat.

The chat one matters most. The active thread used to live in `ChatPage`'s state,
and a page component is unmounted the moment the shopper opens the catalog — so
browsing mid-conversation destroyed the conversation. Holding it above the router
means browsing costs nothing, and because the request lives there too, a reply
that lands while the shopper is on another page is still waiting when they come
back.

Past conversations are listed beside the thread from `GET /api/chat/conversations`
and replayed through `GET /api/chat/conversations/{id}`. The open thread's id is
remembered in `localStorage` under `shopassist.chat.<userId>`, so a reload returns
to it.

A failed send removes its optimistic bubble and puts the text back in the
composer. Leaving the bubble implies a question was asked that never arrived, and
a later replay of that thread would not contain it.

## Buying from the chat

A turn that prices or places a purchase carries an `action` object beside the
reply text, holding the tool's own result: the priced draft, or the order with
the number the shop assigned. The page renders it as a card with **Confirm and
order** / **Not now**, and confirming posts to
`POST /api/purchases/{reference}/confirm` — the same endpoint the checkout page
uses. Agreeing to a total therefore never has to be typed, and never goes back
through the model.

This exists because the prose is where the assistant goes wrong. It has been
observed pricing one pair of shoes and then describing two, and announcing an
order without ever quoting its number — leaving a shopper unable to find in their
history the thing they had just been told was placed. Every figure on the card,
and the order number, comes from the tool result. Where card and prose disagree,
the card is the one that is true.

The card only exists when `createOrderDraft` actually ran, and a 7B model
regularly asks "would you like to proceed?" one turn before it prices anything —
at which point there is genuinely nothing to confirm, and inventing a button
would mean the UI guessing at a purchase. So when a reply asks a purchase
question and no draft came with it, the page offers a **Yes, price it up** chip.
That only sends the answer for them, which reliably produces the draft and
therefore the real Confirm button on the next turn: two clicks instead of typing,
with the deterministic step still deterministic.

The model may still call `confirmOrder` itself when a shopper types "yes". Both
paths end in the same card, so either way the order number is shown and matches
what lands on the orders page.

**One asymmetry worth knowing:** a replayed thread shows the messages but not the
insight panels or the purchase cards. Which tools ran, whether the answer was
grounded, and what was bought are computed per turn and returned by
`POST /api/chat` — none of it is stored against the message. So insights and
cards appear on turns the current session sent, and not on history loaded back
from the server.

## Cart and checkout

The cart lives in the browser, under `localStorage` key
`shopassist.cart.<userId>` so two accounts sharing a machine do not share a
basket. That is deliberate: a cart is a UI concept, and the server already has
the right model for a committed basket in `OrderDraft`.

Nothing about the cart is authoritative. Its prices are a snapshot taken when
items were added, and the figures the shopper actually agrees to come from the
draft the server prices at checkout. Where the two disagree, the checkout page
says so and the server's total wins.

**Buy now** on a product card skips the cart, drafting that one item on its own,
and leaves whatever else was collected alone.

A trade-off to note: because the cart is client-side, it does not sync across
devices and clearing browser storage empties it.

## Theme

Light, dark, or match-the-system, chosen from the control in the header and
remembered in `localStorage` under `shopassist-theme`. **Light is the default**
when nothing has been chosen.

A small inline script in `index.html` resolves the saved choice onto a
`data-theme` attribute before the first paint, so a reload never flashes the
wrong palette; `src/theme/ThemeContext.tsx` owns the same key and default from
then on. Resolving the three-way preference in JavaScript means the dark palette
is written once in CSS rather than duplicated across a media query and an
attribute selector.

Both palettes are checked at WCAG AA for body text.

**One thing not to re-add:** the page background is deliberately not transitioned
on a theme change. Animating a `background-color` whose value comes from a custom
property leaves it stuck on the old colour in Chrome when only that property
changes — dark mode kept a light page background. The same applies to the large
buttons, where a lagging fill left white text on a pale blue background for about
a second.

## Accessibility

- A visible focus ring on every interactive element. Removing outlines is the
  single most common way a keyboard user loses their place.
- A skip link past the nav, visible on focus.
- Errors carry `role="alert"` so a failed sign-in is announced, not just shown.
- The chat log is a `role="log"` live region.
- `prefers-reduced-motion` disables every transition and animation, including the
  landing page's scroll reveals.
- Scroll-triggered animation fails open: anything already on screen reveals
  immediately, a scroll listener backs up the observer, and a timer backs up
  both. All three are frame-driven, and content meant to be read must never be
  stranded invisible because no frames were produced.
