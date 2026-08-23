import { Link, useNavigate } from 'react-router-dom'
import { money } from '../api/client'
import { useCart } from '../cart/CartContext'

export function CartPage() {
  const { lines, count, subtotal, currency, setQuantity, remove, clear, maxPerLine } = useCart()
  const navigate = useNavigate()

  if (lines.length === 0) {
    return (
      <div className="panel">
        <h1>Your cart</h1>
        <p className="muted centered">
          Nothing in here yet. <Link to="/catalog">Browse the catalog</Link>, or just{' '}
          <Link to="/chat">ask the assistant</Link> for what you are after.
        </p>
      </div>
    )
  }

  return (
    <div className="panel">
      <h1>Your cart</h1>
      <p className="muted">
        {count} item{count === 1 ? '' : 's'}. Prices are confirmed at checkout.
      </p>

      <ul className="cart-lines">
        {lines.map((line) => (
          <li key={line.sku} className="cart-line">
            {line.imageUrl ? (
              <img src={line.imageUrl} alt="" loading="lazy" width={72} height={72} />
            ) : (
              <div className="cart-thumb-empty" aria-hidden="true" />
            )}

            <div className="cart-line-body">
              <p className="product-brand">{line.brand}</p>
              <h2 className="cart-line-name">{line.name}</h2>
              <p className="muted small">
                <code>{line.sku}</code> · {money(line.price, line.currency)} each
              </p>
            </div>

            <div className="cart-line-actions">
              <div className="stepper">
                <button
                  type="button"
                  onClick={() => setQuantity(line.sku, line.quantity - 1)}
                  aria-label={`Reduce quantity of ${line.name}`}
                >
                  −
                </button>
                <span aria-live="polite" aria-label={`Quantity of ${line.name}`}>
                  {line.quantity}
                </span>
                <button
                  type="button"
                  onClick={() => setQuantity(line.sku, line.quantity + 1)}
                  disabled={line.quantity >= maxPerLine}
                  aria-label={`Increase quantity of ${line.name}`}
                >
                  +
                </button>
              </div>

              <p className="cart-line-total">{money(line.price * line.quantity, line.currency)}</p>

              <button type="button" className="link" onClick={() => remove(line.sku)}>
                Remove
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="cart-summary">
        <div>
          <p className="muted small">Estimated total</p>
          <p className="cart-total">{money(subtotal, currency)}</p>
          {/* Stated plainly rather than in fine print: the cart is a local
              snapshot, and the server prices the basket again at checkout. */}
          <p className="muted small">
            Checked against the catalog at checkout, before anything is ordered.
          </p>
        </div>

        <div className="cart-summary-actions">
          <button type="button" className="button large" onClick={() => navigate('/checkout')}>
            Checkout
          </button>
          <button type="button" className="link" onClick={clear}>
            Empty cart
          </button>
        </div>
      </div>
    </div>
  )
}
