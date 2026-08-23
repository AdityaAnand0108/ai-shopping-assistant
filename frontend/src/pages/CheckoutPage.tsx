import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api, money, shortDate } from '../api/client'
import type { Draft, OrderDetail } from '../api/types'
import { useCart } from '../cart/CartContext'
import { ErrorNote } from '../components/ErrorNote'
import { Spinner } from '../components/Spinner'

type Stage = 'pricing' | 'review' | 'placing' | 'placed' | 'unavailable'

interface BuyNowState {
  buyNow?: { sku: string; quantity: number }
}

/** Minutes and seconds left on a quote, or null once it has lapsed. */
function remainingOn(expiresAt: string): string | null {
  const ms = new Date(expiresAt).getTime() - Date.now()
  if (ms <= 0) return null
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.floor((ms % 60000) / 1000)
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/**
 * The second half of the two-step purchase.
 *
 * The page arrives with a basket, asks the server to price it, and shows what
 * came back. Nothing is ordered until Place order is pressed, and the figures
 * on screen are the server's rather than the cart's — which is the whole point
 * of drafting first.
 */
export function CheckoutPage() {
  const { lines, clear } = useCart()
  const location = useLocation()
  const navigate = useNavigate()

  const buyNow = (location.state as BuyNowState | null)?.buyNow

  // Captured once so that clearing the cart after a successful order does not
  // pull the basket out from under the page mid-render.
  const [source] = useState(() =>
    buyNow ? [buyNow] : lines.map((line) => ({ sku: line.sku, quantity: line.quantity })),
  )
  const expectedTotal = buyNow
    ? null
    : lines.reduce((total, line) => total + line.price * line.quantity, 0)

  const [stage, setStage] = useState<Stage>('pricing')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [remaining, setRemaining] = useState<string | null>(null)

  const price = useCallback(async () => {
    setStage('pricing')
    setError(null)
    try {
      const priced = await api.createDraft(source)
      setDraft(priced)
      setStage('review')
    } catch (e) {
      setDraft(null)
      // A 400 or 404 here means the basket itself cannot be bought — sold out,
      // or a product that no longer exists — so it is a dead end rather than
      // something to retry.
      setError(e instanceof ApiError ? e.message : 'Could not price your basket.')
      setStage('unavailable')
    }
  }, [source])

  // StrictMode mounts effects twice in development. Without this the page would
  // create two drafts every time, and confirm only one of them.
  const priced = useRef(false)
  useEffect(() => {
    if (priced.current || source.length === 0) return
    priced.current = true
    void price()
  }, [price, source.length])

  // A quote is only good for fifteen minutes, so the page says so rather than
  // letting the shopper find out when the button fails.
  useEffect(() => {
    if (stage !== 'review' || !draft) return
    const tick = () => setRemaining(remainingOn(draft.expiresAt))
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [stage, draft])

  const placeOrder = async () => {
    if (!draft) return
    setStage('placing')
    setError(null)
    try {
      const placed = await api.confirmDraft(draft.reference)
      setOrder(placed)
      setStage('placed')
      // Only a cart checkout empties the cart; buying one item outright leaves
      // whatever else the shopper was collecting alone.
      if (!buyNow) clear()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not place your order.')
      setStage('review')
    }
  }

  const abandon = async () => {
    // Best effort: the draft expires on its own, so a failure here costs
    // nothing and must not block going back.
    if (draft) await api.cancelDraft(draft.reference).catch(() => undefined)
    navigate(buyNow ? '/catalog' : '/cart')
  }

  if (source.length === 0) return <Navigate to="/cart" replace />

  if (stage === 'placed' && order) {
    return (
      <div className="panel narrow">
        <div className="order-placed">
          <span className="tick" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M20 6 9 17l-5-5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          <h1>Order placed</h1>
          <p className="muted">
            <strong>{order.orderNumber}</strong> · {money(order.totalAmount, order.currency)}
          </p>
          <p className="muted">
            Expected {shortDate(order.expectedDeliveryDate)}. You can track it any time, or just
            ask the assistant where it is.
          </p>
          <div className="cta-row centered-row">
            <Link to="/orders" className="button large">
              Track this order
            </Link>
            <Link to="/catalog" className="button large ghost">
              Keep shopping
            </Link>
          </div>
        </div>
      </div>
    )
  }

  if (stage === 'pricing') {
    return (
      <div className="panel narrow">
        <h1>Checkout</h1>
        <Spinner label="Checking prices and stock…" />
      </div>
    )
  }

  if (stage === 'unavailable') {
    return (
      <div className="panel narrow">
        <h1>Checkout</h1>
        <ErrorNote message={error} />
        <p className="muted">
          Nothing has been ordered and nothing has been charged. Adjust the basket and try again.
        </p>
        <div className="cta-row">
          <Link to={buyNow ? '/catalog' : '/cart'} className="button large ghost">
            {buyNow ? 'Back to the catalog' : 'Back to cart'}
          </Link>
        </div>
      </div>
    )
  }

  if (!draft) return null

  const expired = remaining === null
  const repriced = expectedTotal !== null && Math.abs(expectedTotal - draft.totalAmount) > 0.005

  return (
    <div className="panel narrow">
      <h1>Checkout</h1>
      <p className="muted">
        Priced by the shop just now. Nothing is ordered until you place it.
      </p>

      <ul className="draft-lines">
        {draft.items.map((item) => (
          <li key={item.sku}>
            <span className="draft-line-name">
              {item.name}
              {item.quantity > 1 && <span className="muted"> × {item.quantity}</span>}
            </span>
            <span className="draft-line-total">{money(item.lineTotal, draft.currency)}</span>
          </li>
        ))}
      </ul>

      <p className="draft-total">
        <span>Total</span>
        <strong>{money(draft.totalAmount, draft.currency)}</strong>
      </p>

      {repriced && (
        <p className="insight-warning" role="status">
          The shop's price differs from what your cart showed. The total above is the one that
          applies.
        </p>
      )}

      {expired ? (
        <p className="insight-warning" role="status">
          This quote has expired, so prices and stock need checking again.
        </p>
      ) : (
        <p className="muted small">Held for {remaining}.</p>
      )}

      <ErrorNote message={error} />

      <div className="cta-row">
        {expired ? (
          <button type="button" className="button large" onClick={() => void price()}>
            Check again
          </button>
        ) : (
          <button
            type="button"
            className="button large"
            onClick={() => void placeOrder()}
            disabled={stage === 'placing'}
          >
            {stage === 'placing' ? 'Placing…' : 'Place order'}
          </button>
        )}
        <button
          type="button"
          className="button large ghost"
          onClick={() => void abandon()}
          disabled={stage === 'placing'}
        >
          {buyNow ? 'Cancel' : 'Back to cart'}
        </button>
      </div>
    </div>
  )
}
