# Semantic search

Catalog search is hybrid: retrieval decides which products a phrase is *about*,
and SQL decides what may actually be shown.

[← back to README](../README.md)

---

## What it buys you

```
"gift for someone who runs"                → running shoes
"noise cancelling headphones for a flight" → headphones, Sony XM5 first
"something to keep me warm"                → down jacket, fleece hoodie (plus noise)
"I need to make tea and coffee"            → nothing
```

None of these contain a word that appears in the product descriptions, so the
keyword path returns nothing for all four.

## Two rules that keep it honest

**Retrieval proposes, SQL disposes.** An embedding has no idea what anything
costs or whether it is in stock, so price, brand, category and stock filters run
in SQL *after* retrieval, against candidate SKUs. Everything shown is re-read
from the database, so a stale index can never surface an out-of-date price — the
index holds only SKUs and embedded text.

**Retrieval never has the last word.** If the index is not ready, returns
nothing, or has everything filtered away, search falls through to the keyword
path. An unreachable embedding model degrades search; it does not break it.

The catalog is embedded once with `nomic-embed-text` (3.7s for 60 products) and
persisted to `data/vector-store.json`, rebuilt when the file is missing or the
product count no longer matches.

## The threshold was measured, not guessed

Similarity scores from `nomic-embed-text` over short product descriptions sit in
a narrow band. Measured directly against Ollama:

```
query: "something to keep me warm"        query: "I need to make tea and coffee"
  down jacket   0.6050                      chinos       0.4645
  hoodie        0.5619                      flask        0.4564
  flask         0.5541                      down jacket  0.3817
  chinos        0.4939                      hoodie       0.3533
```

A strong match reaches ~0.60 and an unrelated item still scores ~0.45, so ranking
in the middle is close to arbitrary — note that chinos out-score a flask for "tea
and coffee". Cutting at **0.55** keeps the confident head of the ranking and
discards the rest, which then falls through to keyword search. Returning nothing
beats returning chinos.

I also tested `nomic-embed-text`'s documented `search_query:` / `search_document:`
task prefixes. They made results **worse** on this data — with prefixes, a vacuum
flask out-ranks a down jacket for "keep me warm" — so they are not used. That was
measured before implementing rather than assumed from the model card.

## Known limitation

**Semantic ranking is noisy in the middle.** "Something to keep me warm" returns a
down jacket and a fleece hoodie, but also a training tee and a vacuum flask,
because those genuinely score above the threshold. A larger embedding model, or
richer product text, would separate them; tuning the threshold further only
trades this noise for empty results on valid queries.
