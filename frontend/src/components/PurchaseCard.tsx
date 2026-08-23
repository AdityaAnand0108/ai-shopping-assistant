import { Link } from 'react-router-dom'
import { money, shortDate } from '../api/client'
import type { Turn } from '../chat/ChatContext'
import { useChat } from '../chat/ChatContext'

/**
 * The purchase a turn priced or placed, rendered from the tool result.
 *
 * <p>Everything here comes from the server: the totals, the line items, and the
 * order number. The reply text beside it is the model's account of the same
 * events, and the two have been observed disagreeing — a draft for one pair of
 * shoes described as two, an order announced without its number. When they
 * differ, this is the one that is true.
 *
 * Confirming goes straight to the purchase endpoint rather than back through
 * the model, so agreeing to a total cannot turn into another round of prose.
 */
export function PurchaseCard({ turn }: { turn: Turn }) {
  const { confirmPurchase, declinePurchase } = useChat()
  const action = turn.action
  if (!action) return null

  if (action.order) {
    const order = action.order
    return (
      <div className="purchase-card placed">
        <p className="purchase-card-head">
          <span className="tick-small" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
              <path d="M20 6 9 17l-5-5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          Order placed
        </p>

        <p className="purchase-order-number">{order.orderNumber}</p>

        <ul className="purchase-lines">
          {order.items.map((item) => (
            <li key={item.sku}>
              <span>
                {item.name}
                {item.quantity > 1 && <span className="muted"> × {item.quantity}</span>}
              </span>
              <strong>{money(item.lineTotal, order.currency)}</strong>
            </li>
          ))}
        </ul>

        <p className="purchase-total">
          <span>Total</span>
          <strong>{money(order.totalAmount, order.currency)}</strong>
        </p>

        <p className="muted small">
          Expected {shortDate(order.expectedDeliveryDate)} ·{' '}
          <Link to="/orders">Track this order</Link>
        </p>
      </div>
    )
  }

  const draft = action.draft
  if (!draft) return null

  return (
    <div className="purchase-card">
      <p className="purchase-card-head">Ready to order</p>

      <ul className="purchase-lines">
        {draft.items.map((item) => (
          <li key={item.sku}>
            <span>
              {item.name}
              {item.quantity > 1 && <span className="muted"> × {item.quantity}</span>}
            </span>
            <strong>{money(item.lineTotal, draft.currency)}</strong>
          </li>
        ))}
      </ul>

      <p className="purchase-total">
        <span>Total</span>
        <strong>{money(draft.totalAmount, draft.currency)}</strong>
      </p>

      {turn.actionError && (
        <p className="field-error" role="alert">
          {turn.actionError}
        </p>
      )}

      <div className="purchase-actions">
        <button
          type="button"
          className="button small"
          disabled={turn.busy}
          onClick={() => void confirmPurchase(turn.message.id, draft.reference)}
        >
          {turn.busy ? 'Placing…' : 'Confirm and order'}
        </button>
        <button
          type="button"
          className="button small ghost"
          disabled={turn.busy}
          onClick={() => void declinePurchase(turn.message.id, draft.reference)}
        >
          Not now
        </button>
      </div>

      <p className="muted small">Nothing is charged until you confirm.</p>
    </div>
  )
}
