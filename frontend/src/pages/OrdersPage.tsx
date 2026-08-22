import { useEffect, useState } from 'react'
import { ApiError, api, money, shortDate, shortDateTime } from '../api/client'
import type { OrderDetail, OrderSummary } from '../api/types'
import { ErrorNote } from '../components/ErrorNote'
import { Spinner } from '../components/Spinner'

const STATUS_LABEL: Record<string, string> = {
  PLACED: 'Placed',
  CONFIRMED: 'Confirmed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RETURNED: 'Returned',
}

export function OrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null)
  const [selected, setSelected] = useState<OrderDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .orders()
      .then(setOrders)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load your orders.'))
      .finally(() => setLoading(false))
  }, [])

  const open = async (orderNumber: string) => {
    setError(null)
    try {
      setSelected(await api.order(orderNumber))
    } catch (e) {
      // An order that is not yours returns the same not-found as one that never
      // existed, so there is nothing more specific to say here either.
      setError(e instanceof ApiError ? e.message : 'Could not open that order.')
    }
  }

  if (loading) return <Spinner label="Loading your orders…" />

  return (
    <div className="panel">
      <h1>Your orders</h1>
      <ErrorNote message={error} />

      {orders && orders.length === 0 && (
        <p className="muted centered">
          You have no orders yet. Ask the assistant to help you find something.
        </p>
      )}

      <div className="orders-layout">
        <ul className="order-list">
          {orders?.map((order) => (
            <li key={order.orderNumber}>
              <button
                type="button"
                className={
                  selected?.orderNumber === order.orderNumber
                    ? 'order-row selected'
                    : 'order-row'
                }
                onClick={() => void open(order.orderNumber)}
                aria-expanded={selected?.orderNumber === order.orderNumber}
              >
                <span className="order-number">{order.orderNumber}</span>
                <span className={`status ${order.status.toLowerCase()}`}>
                  {STATUS_LABEL[order.status] ?? order.status}
                </span>
                <span className="muted small">
                  {order.itemCount} item{order.itemCount === 1 ? '' : 's'} ·{' '}
                  {money(order.totalAmount, order.currency)}
                </span>
              </button>
            </li>
          ))}
        </ul>

        {selected && (
          <section className="order-detail" aria-label={`Order ${selected.orderNumber}`}>
            <h2>{selected.orderNumber}</h2>
            <p className="muted">
              Placed {shortDate(selected.placedAt)}
              {selected.expectedDeliveryDate && (
                <> · expected {shortDate(selected.expectedDeliveryDate)}</>
              )}
            </p>

            <ul className="order-items">
              {selected.items.map((item) => (
                <li key={item.sku}>
                  <span>
                    {item.quantity} × {item.name}
                  </span>
                  <span className="muted">{money(item.lineTotal, selected.currency)}</span>
                </li>
              ))}
            </ul>

            <p className="order-total">
              Total <strong>{money(selected.totalAmount, selected.currency)}</strong>
            </p>

            <h3>Tracking</h3>
            <ol className="timeline">
              {selected.timeline.map((step) => (
                <li key={`${step.status}-${step.occurredAt}`}>
                  <span className="timeline-status">
                    {STATUS_LABEL[step.status] ?? step.status}
                  </span>
                  <span className="muted small">{shortDateTime(step.occurredAt)}</span>
                  {step.note && <span className="muted small">{step.note}</span>}
                </li>
              ))}
            </ol>

            {selected.shippingAddress && (
              <>
                <h3>Shipping to</h3>
                <p className="muted">{selected.shippingAddress}</p>
              </>
            )}
          </section>
        )}
      </div>
    </div>
  )
}
