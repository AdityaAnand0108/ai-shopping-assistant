import type {
  AuthResponse,
  CatalogFilters,
  ChatResponse,
  ConversationDetail,
  ConversationSummary,
  OrderDetail,
  OrderSummary,
  Page,
  ProductSummary,
  UserProfile,
} from './types'

const TOKEN_KEY = 'shopassist.token'

/**
 * The access token.
 *
 * Held in localStorage because the backend issues a bearer token rather than a
 * cookie, so JavaScript has to be able to read it. That is a deliberate,
 * documented trade: it means an XSS bug would expose the token. The safer shape
 * is an HttpOnly cookie, which would move this responsibility out of the browser
 * entirely.
 */
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/**
 * An error carrying what the backend's RFC 7807 problem document said.
 *
 * The backend deliberately keeps detail out of error bodies, so this surfaces
 * the human-readable `detail` and nothing more. Field-level validation errors
 * arrive under `errors` and are worth showing next to the inputs.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>
  readonly retryAfterSeconds?: number

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> = {},
    retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = tokenStore.get()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    // fetch only rejects on a transport failure, so this is genuinely "the
    // server is not reachable" rather than any HTTP error status.
    throw new ApiError(0, 'Could not reach the server. Is the backend running?')
  }

  if (response.status === 204) return undefined as T

  const body = await response.json().catch(() => null)

  if (!response.ok) {
    // An expired or revoked token should not leave the app in a half-signed-in
    // state, so clear it here rather than at every call site.
    if (response.status === 401) tokenStore.clear()

    throw new ApiError(
      response.status,
      body?.detail ?? body?.title ?? `Request failed (${response.status})`,
      body?.errors ?? {},
      body?.retryAfterSeconds,
    )
  }

  return body as T
}

export interface ProductQuery {
  q?: string
  brand?: string
  category?: string
  minPrice?: string
  maxPrice?: string
  inStockOnly?: boolean
  page?: number
  size?: number
}

function queryString(query: ProductQuery): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    // Empty strings are dropped so a cleared filter box means "no filter"
    // rather than "match the empty string".
    if (value !== undefined && value !== '' && value !== false) {
      params.set(key, String(value))
    }
  })
  const encoded = params.toString()
  return encoded ? `?${encoded}` : ''
}

export const api = {
  login: (username: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  signup: (username: string, email: string, password: string, fullName?: string) =>
    request<AuthResponse>('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ username, email, password, fullName: fullName || null }),
    }),

  me: () => request<UserProfile>('/api/auth/me'),

  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),

  products: (query: ProductQuery) =>
    request<Page<ProductSummary>>(`/api/products${queryString(query)}`),

  filters: () => request<CatalogFilters>('/api/products/filters'),

  orders: () => request<OrderSummary[]>('/api/orders'),

  order: (orderNumber: string) =>
    request<OrderDetail>(`/api/orders/${encodeURIComponent(orderNumber)}`),

  chat: (message: string, conversationId?: string) =>
    request<ChatResponse>('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ message, conversationId: conversationId ?? null }),
    }),

  conversations: () => request<ConversationSummary[]>('/api/chat/conversations'),

  conversation: (id: string) =>
    request<ConversationDetail>(`/api/chat/conversations/${encodeURIComponent(id)}`),
}

/** Formats a price the way the backend and the assistant both write it. */
export const money = (amount: number, currency = 'USD') =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount)

export const shortDate = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString('en-US', { dateStyle: 'medium' }) : '—'

export const shortDateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })
