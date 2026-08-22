/**
 * Types mirroring the backend's response records.
 *
 * Hand-written rather than generated: the backend deliberately returns
 * whitelisted DTOs rather than entities, and writing these by hand keeps that
 * contract visible on this side too. If a field appears here that the API does
 * not send, it is a mistake — not something to paper over with `any`.
 */

export type Availability = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export type OrderStatus =
  | 'PLACED'
  | 'CONFIRMED'
  | 'PACKED'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED'

export interface UserProfile {
  id: string
  username: string
  email: string
  fullName: string | null
  role: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: UserProfile
}

export interface ProductSummary {
  sku: string
  name: string
  brand: string
  category: string
  subcategory: string | null
  price: number
  currency: string
  rating: number | null
  availability: Availability
  imageUrl: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface CatalogFilters {
  brands: string[]
  categories: string[]
}

export interface OrderSummary {
  orderNumber: string
  status: OrderStatus
  placedAt: string
  expectedDeliveryDate: string | null
  totalAmount: number
  currency: string
  itemCount: number
}

export interface OrderItem {
  sku: string
  name: string
  brand: string
  quantity: number
  unitPrice: number
  lineTotal: number
  imageUrl: string | null
}

export interface TrackingStep {
  status: OrderStatus
  occurredAt: string
  note: string | null
}

export interface OrderDetail extends OrderSummary {
  deliveredAt: string | null
  cancelledAt: string | null
  shippingAddress: string | null
  cancellable: boolean
  items: OrderItem[]
  timeline: TrackingStep[]
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

/**
 * Why an answer says what it says.
 *
 * `grounded: false` means the reply stated a price, SKU, order number or date
 * that no backend call returned. The UI surfaces that rather than hiding it —
 * a shopper is better served by a visible warning than by a confident wrong
 * number.
 */
export interface TurnInsight {
  toolsUsed: string[]
  grounded: boolean
  unsupported: string[]
  redacted: boolean
}

export interface ChatResponse {
  conversationId: string
  reply: ChatMessage
  insight: TurnInsight
}

export interface ConversationSummary {
  id: string
  title: string | null
  createdAt: string
  updatedAt: string
  messageCount: number
}

export interface ConversationDetail {
  id: string
  title: string | null
  createdAt: string
  messages: ChatMessage[]
}
