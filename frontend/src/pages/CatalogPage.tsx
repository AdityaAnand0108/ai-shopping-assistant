import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, money } from '../api/client'
import type { CatalogFilters, Page, ProductSummary } from '../api/types'
import { useCart } from '../cart/CartContext'
import { ErrorNote } from '../components/ErrorNote'
import { Spinner } from '../components/Spinner'

const AVAILABILITY_LABEL = {
  IN_STOCK: 'In stock',
  LOW_STOCK: 'Only a few left',
  OUT_OF_STOCK: 'Out of stock',
} as const

export function CatalogPage() {
  const [query, setQuery] = useState({ q: '', brand: '', category: '', maxPrice: '' })
  const [inStockOnly, setInStockOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [results, setResults] = useState<Page<ProductSummary> | null>(null)
  const [filters, setFilters] = useState<CatalogFilters | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const { add, quantityOf } = useCart()
  const navigate = useNavigate()
  // Which SKU just went in, so the button can confirm it without a toast
  // library or a layout shift.
  const [justAdded, setJustAdded] = useState<string | null>(null)
  const [cartError, setCartError] = useState<string | null>(null)

  const addToCart = (product: ProductSummary) => {
    const problem = add(product)
    setCartError(problem)
    if (problem) return
    setJustAdded(product.sku)
    window.setTimeout(() => setJustAdded((sku) => (sku === product.sku ? null : sku)), 1600)
  }

  useEffect(() => {
    api.filters().then(setFilters).catch(() => setFilters(null))
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setResults(await api.products({ ...query, inStockOnly, page, size: 12 }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load products.')
      setResults(null)
    } finally {
      setLoading(false)
    }
  }, [query, inStockOnly, page])

  useEffect(() => {
    void load()
  }, [load])

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    // Any change to the filters invalidates the current page number.
    setPage(0)
    void load()
  }

  return (
    <div className="panel">
      <h1>Catalog</h1>
      <p className="muted">
        Every product in the demo catalog. The assistant searches the same data.
      </p>

      <form className="filters" onSubmit={handleSearch} role="search">
        <div className="field">
          <label htmlFor="q">Search</label>
          <input
            id="q"
            value={query.q}
            placeholder="running shoes, something warm…"
            onChange={(e) => setQuery({ ...query, q: e.target.value })}
          />
        </div>

        <div className="field">
          <label htmlFor="brand">Brand</label>
          <select
            id="brand"
            value={query.brand}
            onChange={(e) => setQuery({ ...query, brand: e.target.value })}
          >
            <option value="">Any</option>
            {filters?.brands.map((brand) => (
              <option key={brand} value={brand}>
                {brand}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="category">Category</label>
          <select
            id="category"
            value={query.category}
            onChange={(e) => setQuery({ ...query, category: e.target.value })}
          >
            <option value="">Any</option>
            {filters?.categories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
        </div>

        <div className="field narrow-field">
          <label htmlFor="maxPrice">Max price</label>
          <input
            id="maxPrice"
            inputMode="decimal"
            value={query.maxPrice}
            placeholder="50"
            onChange={(e) => setQuery({ ...query, maxPrice: e.target.value })}
          />
        </div>

        <div className="field checkbox">
          <input
            id="inStockOnly"
            type="checkbox"
            checked={inStockOnly}
            onChange={(e) => setInStockOnly(e.target.checked)}
          />
          <label htmlFor="inStockOnly">In stock only</label>
        </div>

        <button type="submit" className="button">
          Search
        </button>
      </form>

      <ErrorNote message={error} />
      <ErrorNote message={cartError} />

      {loading && <Spinner label="Loading products…" />}

      {!loading && results && results.content.length === 0 && (
        <p className="muted centered">
          Nothing matched. Try fewer filters, or describe what you want in your
          own words — search understands intent, not just keywords.
        </p>
      )}

      {!loading && results && results.content.length > 0 && (
        <>
          <p className="muted" aria-live="polite">
            {results.totalElements} product{results.totalElements === 1 ? '' : 's'}
          </p>

          <ul className="product-grid">
            {results.content.map((product) => (
              <li key={product.sku} className="product-card">
                {product.imageUrl && (
                  <img src={product.imageUrl} alt="" loading="lazy" width={200} height={200} />
                )}
                <div className="product-body">
                  <p className="product-brand">{product.brand}</p>
                  <h2 className="product-name">{product.name}</h2>
                  <p className="product-price">{money(product.price, product.currency)}</p>
                  <p
                    className={
                      product.availability === 'OUT_OF_STOCK' ? 'stock out' : 'stock in'
                    }
                  >
                    {AVAILABILITY_LABEL[product.availability]}
                  </p>
                  <p className="muted small">
                    <code>{product.sku}</code>
                    {product.rating != null && <> · {product.rating.toFixed(1)}★</>}
                  </p>

                  {product.availability === 'OUT_OF_STOCK' ? (
                    <button type="button" className="button small block" disabled>
                      Out of stock
                    </button>
                  ) : (
                    <div className="product-actions">
                      <button
                        type="button"
                        className="button small block"
                        onClick={() => addToCart(product)}
                      >
                        {justAdded === product.sku ? 'Added ✓' : 'Add to cart'}
                      </button>
                      <button
                        type="button"
                        className="button small block ghost"
                        onClick={() =>
                          navigate('/checkout', {
                            state: { buyNow: { sku: product.sku, quantity: 1 } },
                          })
                        }
                      >
                        Buy now
                      </button>
                    </div>
                  )}

                  {quantityOf(product.sku) > 0 && (
                    <p className="muted small in-cart">{quantityOf(product.sku)} in your cart</p>
                  )}
                </div>
              </li>
            ))}
          </ul>

          <div className="pager">
            <button
              type="button"
              className="button small"
              disabled={results.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <span className="muted">
              Page {results.page + 1} of {Math.max(1, results.totalPages)}
            </span>
            <button
              type="button"
              className="button small"
              disabled={results.last}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  )
}
