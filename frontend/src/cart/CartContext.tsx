import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { ProductSummary } from '../api/types'

/**
 * A line in the shopper's basket.
 *
 * The price is a snapshot from the catalog listing, kept so the cart can show a
 * running total without a round trip. It is not what anyone gets charged: the
 * server re-prices the whole basket at checkout, and that draft is what the
 * shopper confirms.
 */
export interface CartLine {
  sku: string
  name: string
  brand: string
  price: number
  currency: string
  imageUrl: string | null
  quantity: number
}

// Mirrors PurchaseService.MAX_QUANTITY_PER_LINE and MAX_LINES. Enforcing them
// here only saves a doomed round trip — the server enforces them for real, and
// rejects a basket that gets past this.
const MAX_PER_LINE = 10
const MAX_LINES = 10

/** Baskets are per account, so two people sharing a browser do not share one. */
const storageKey = (userId: string) => `shopassist.cart.${userId}`

function readStored(userId: string): CartLine[] {
  try {
    const raw = localStorage.getItem(storageKey(userId))
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    // Storage is user-writable and survives deploys, so anything malformed is
    // dropped rather than trusted into the UI.
    return parsed.filter(
      (line): line is CartLine =>
        typeof line === 'object' &&
        line !== null &&
        typeof (line as CartLine).sku === 'string' &&
        typeof (line as CartLine).quantity === 'number' &&
        (line as CartLine).quantity > 0,
    )
  } catch {
    return []
  }
}

function writeStored(userId: string, lines: CartLine[]) {
  try {
    localStorage.setItem(storageKey(userId), JSON.stringify(lines))
  } catch {
    // A full or unavailable storage should not break checkout; the basket just
    // will not survive a reload.
  }
}

interface CartState {
  lines: CartLine[]
  /** Total units, for the header badge. */
  count: number
  /** Local estimate only. The draft total is the one that counts. */
  subtotal: number
  currency: string
  /** Returns a reason the item could not be added, or null on success. */
  add: (product: ProductSummary, quantity?: number) => string | null
  setQuantity: (sku: string, quantity: number) => void
  remove: (sku: string) => void
  clear: () => void
  quantityOf: (sku: string) => number
  maxPerLine: number
}

const CartContext = createContext<CartState | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  // The owner travels with the lines so a sign-out cannot flush one shopper's
  // basket into another's storage key between renders.
  const [basket, setBasket] = useState<{ ownerId: string | null; lines: CartLine[] }>({
    ownerId: null,
    lines: [],
  })

  useEffect(() => {
    const ownerId = user?.id ?? null
    setBasket({ ownerId, lines: ownerId ? readStored(ownerId) : [] })
  }, [user?.id])

  useEffect(() => {
    // Signed out, the basket stays in storage untouched, ready for the next
    // sign-in rather than discarded.
    if (basket.ownerId) writeStored(basket.ownerId, basket.lines)
  }, [basket])

  const update = useCallback(
    (change: (lines: CartLine[]) => CartLine[]) =>
      setBasket((current) => ({ ...current, lines: change(current.lines) })),
    [],
  )

  const add = useCallback(
    (product: ProductSummary, quantity = 1): string | null => {
      if (product.availability === 'OUT_OF_STOCK') {
        return `${product.name} is out of stock.`
      }

      const existing = basket.lines.find((line) => line.sku === product.sku)
      if (!existing && basket.lines.length >= MAX_LINES) {
        return `A single order can hold ${MAX_LINES} different products.`
      }
      if (existing && existing.quantity >= MAX_PER_LINE) {
        return `You can order at most ${MAX_PER_LINE} of any one item.`
      }

      update((lines) =>
        existing
          ? lines.map((line) =>
              line.sku === product.sku
                ? { ...line, quantity: Math.min(MAX_PER_LINE, line.quantity + quantity) }
                : line,
            )
          : [
              ...lines,
              {
                sku: product.sku,
                name: product.name,
                brand: product.brand,
                price: product.price,
                currency: product.currency,
                imageUrl: product.imageUrl,
                quantity: Math.min(MAX_PER_LINE, quantity),
              },
            ],
      )
      return null
    },
    [basket.lines, update],
  )

  const setQuantity = useCallback(
    (sku: string, quantity: number) => {
      if (quantity < 1) {
        update((lines) => lines.filter((line) => line.sku !== sku))
        return
      }
      update((lines) =>
        lines.map((line) =>
          line.sku === sku ? { ...line, quantity: Math.min(MAX_PER_LINE, quantity) } : line,
        ),
      )
    },
    [update],
  )

  const remove = useCallback(
    (sku: string) => update((lines) => lines.filter((line) => line.sku !== sku)),
    [update],
  )

  const clear = useCallback(() => update(() => []), [update])

  const value = useMemo<CartState>(() => {
    const { lines } = basket
    return {
      lines,
      count: lines.reduce((total, line) => total + line.quantity, 0),
      subtotal: lines.reduce((total, line) => total + line.price * line.quantity, 0),
      currency: lines[0]?.currency ?? 'USD',
      add,
      setQuantity,
      remove,
      clear,
      quantityOf: (sku: string) => lines.find((line) => line.sku === sku)?.quantity ?? 0,
      maxPerLine: MAX_PER_LINE,
    }
  }, [basket, add, setQuantity, remove, clear])

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart(): CartState {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
