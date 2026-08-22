import type { TurnInsight } from '../api/types'

/** Tool names as the backend reports them, phrased for a shopper. */
const TOOL_LABELS: Record<string, string> = {
  searchProducts: 'searched the catalog',
  getProductDetails: 'looked up a product',
  checkStock: 'checked availability',
  listMyOrders: 'read your orders',
  getOrderStatus: 'read an order',
  getDeliveryEstimate: 'checked delivery',
  createOrderDraft: 'priced a purchase',
  confirmOrder: 'placed an order',
  cancelOrder: 'cancelled an order',
}

/**
 * Shows where an answer came from.
 *
 * This is the explainability the brief asks for, made visible rather than left
 * in a log. Two things are worth surfacing to a shopper:
 *
 * - which backend calls produced the answer, so a confident sentence can be
 *   distinguished from a looked-up fact;
 * - whether any part of it had no source, because the model does occasionally
 *   state a price or date that no tool returned.
 *
 * The ungrounded warning is deliberately not hidden behind a details toggle. A
 * wrong number a shopper acts on costs more than a warning they ignore.
 */
export function InsightPanel({ insight }: { insight: TurnInsight }) {
  const hasTools = insight.toolsUsed.length > 0
  if (!hasTools && insight.grounded && !insight.redacted) return null

  return (
    <div className="insight">
      {hasTools && (
        <p className="insight-sources">
          <span aria-hidden="true">🔎</span> Based on:{' '}
          {insight.toolsUsed.map((tool) => TOOL_LABELS[tool] ?? tool).join(', ')}
        </p>
      )}

      {!insight.grounded && (
        <p className="insight-warning" role="note">
          <span aria-hidden="true">⚠️</span> Some of this could not be verified
          against our records
          {insight.unsupported.length > 0 && (
            <>
              {' ('}
              {insight.unsupported.map((value, index) => (
                <span key={value}>
                  {index > 0 && ', '}
                  <code>{value}</code>
                </span>
              ))}
              {')'}
            </>
          )}
          . Please double-check before relying on it.
        </p>
      )}

      {insight.redacted && (
        <p className="insight-warning" role="note">
          <span aria-hidden="true">⚠️</span> Part of this reply was withheld.
        </p>
      )}
    </div>
  )
}
