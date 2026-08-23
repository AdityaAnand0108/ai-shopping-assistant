import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { ChatMessage, ConversationSummary, TurnAction, TurnInsight } from '../api/types'
import { useAuth } from '../auth/AuthContext'

/** A message plus, for assistant turns, where the answer came from. */
export interface Turn {
  message: ChatMessage
  insight?: TurnInsight
  /** A purchase this turn priced or placed, rendered as a card. */
  action?: TurnAction | null
  /** True while this turn's Confirm button is working. */
  busy?: boolean
  /** Why this turn's confirm or decline failed, shown on the card. */
  actionError?: string
}

/** Which thread the shopper was last in, so a reload returns to it. */
const activeKey = (userId: string) => `shopassist.chat.${userId}`

interface ChatState {
  turns: Turn[]
  conversationId: string | undefined
  sending: boolean
  /** True while an older thread is being replayed from the server. */
  loading: boolean
  error: string | null
  conversations: ConversationSummary[]
  /** Resolves false when the message never reached the assistant. */
  send: (text: string) => Promise<boolean>
  startNew: () => void
  openConversation: (id: string) => Promise<void>
  dismissError: () => void
  /** Places the purchase a turn priced, from its Confirm button. */
  confirmPurchase: (messageId: string, reference: string) => Promise<void>
  /** Abandons it, so a stale card cannot be clicked later. */
  declinePurchase: (messageId: string, reference: string) => Promise<void>
}

const ChatContext = createContext<ChatState | null>(null)

/**
 * The conversation, held above the router.
 *
 * It lives here rather than in ChatPage because a page component is unmounted
 * the moment the shopper looks at the catalog, and with it went the thread they
 * were in the middle of. Keeping it here means browsing mid-conversation costs
 * nothing — and a reply that arrives while they are on another page is still
 * waiting when they come back.
 *
 * Insights only exist on turns this session actually sent: replaying a thread
 * from the server returns the messages, not the tool calls behind them.
 */
export function ChatProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const userId = user?.id ?? null

  const [turns, setTurns] = useState<Turn[]>([])
  const [conversationId, setConversationId] = useState<string | undefined>()
  const [sending, setSending] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [conversations, setConversations] = useState<ConversationSummary[]>([])

  const refreshConversations = useCallback(async () => {
    try {
      setConversations(await api.conversations())
    } catch {
      // History is a convenience. Failing to list it must not stop anyone from
      // starting a new conversation.
    }
  }, [])

  const replay = useCallback(async (id: string) => {
    const detail = await api.conversation(id)
    setTurns(detail.messages.map((message) => ({ message })))
    setConversationId(detail.id)
  }, [])

  // Signing in loads the thread list and reopens whatever was last open;
  // signing out clears everything so the next person sees none of it.
  useEffect(() => {
    setTurns([])
    setConversationId(undefined)
    setConversations([])
    setError(null)

    if (!userId) return

    let cancelled = false
    void (async () => {
      await refreshConversations()
      const lastOpen = localStorage.getItem(activeKey(userId))
      if (!lastOpen || cancelled) return
      setLoading(true)
      try {
        if (!cancelled) await replay(lastOpen)
      } catch {
        // The thread may have been removed, or belong to someone else now.
        // Starting fresh is a better answer than an error the shopper cannot act on.
        localStorage.removeItem(activeKey(userId))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [userId, refreshConversations, replay])

  const rememberActive = useCallback(
    (id: string | undefined) => {
      if (!userId) return
      if (id) localStorage.setItem(activeKey(userId), id)
      else localStorage.removeItem(activeKey(userId))
    },
    [userId],
  )

  const send = useCallback(
    async (text: string): Promise<boolean> => {
      const question = text.trim()
      if (!question || sending) return false

      setError(null)
      // Show the question immediately. The id is a placeholder until the server
      // responds; it is never sent anywhere, only used as a React key.
      setTurns((current) => [
        ...current,
        {
          message: {
            id: `local-${Date.now()}`,
            role: 'USER',
            content: question,
            createdAt: new Date().toISOString(),
          },
        },
      ])
      setSending(true)

      const wasNewThread = conversationId === undefined

      try {
        const response = await api.chat(question, conversationId)
        setConversationId(response.conversationId)
        rememberActive(response.conversationId)
        setTurns((current) => [
          ...current,
          { message: response.reply, insight: response.insight, action: response.action },
        ])

        // A new thread needs to appear in the history; an existing one needs
        // its position and count updated. Either way the list is now stale.
        void refreshConversations()
        return true
      } catch (e) {
        if (e instanceof ApiError && e.status === 429) {
          setError(
            `You are sending messages faster than the assistant can answer. Try again in ${
              e.retryAfterSeconds ?? 60
            } seconds.`,
          )
        } else if (e instanceof ApiError && e.status === 503) {
          setError('The assistant is unavailable. Is Ollama running?')
        } else {
          setError(e instanceof ApiError ? e.message : 'Could not send that message.')
        }
        // Drop the optimistic bubble: leaving it implies the question was asked
        // when it never reached the assistant, and a replay of this thread
        // later would not contain it. The caller puts the text back in the
        // composer so nothing the shopper typed is lost.
        setTurns((current) => current.slice(0, -1))
        if (wasNewThread) setConversationId(undefined)
        return false
      } finally {
        setSending(false)
      }
    },
    [conversationId, sending, rememberActive, refreshConversations],
  )

  const startNew = useCallback(() => {
    setTurns([])
    setConversationId(undefined)
    setError(null)
    rememberActive(undefined)
  }, [rememberActive])

  const openConversation = useCallback(
    async (id: string) => {
      if (id === conversationId) return
      setLoading(true)
      setError(null)
      try {
        await replay(id)
        rememberActive(id)
      } catch (e) {
        setError(e instanceof ApiError ? e.message : 'Could not open that conversation.')
      } finally {
        setLoading(false)
      }
    },
    [conversationId, replay, rememberActive],
  )

  const dismissError = useCallback(() => setError(null), [])

  /** Applies a change to one turn, found by the message it belongs to. */
  const patchTurn = useCallback((messageId: string, change: Partial<Turn>) => {
    setTurns((current) =>
      current.map((turn) => (turn.message.id === messageId ? { ...turn, ...change } : turn)),
    )
  }, [])

  const confirmPurchase = useCallback(
    async (messageId: string, reference: string) => {
      patchTurn(messageId, { busy: true, actionError: undefined })
      try {
        const order = await api.confirmDraft(reference)
        // The card becomes the receipt. The order number here is the shop's,
        // not one the model wrote into a sentence.
        patchTurn(messageId, { busy: false, action: { draft: null, order } })
      } catch (e) {
        patchTurn(messageId, {
          busy: false,
          actionError:
            e instanceof ApiError ? e.message : 'Could not place that order. Nothing was charged.',
        })
      }
    },
    [patchTurn],
  )

  const declinePurchase = useCallback(
    async (messageId: string, reference: string) => {
      patchTurn(messageId, { busy: true, actionError: undefined })
      try {
        await api.cancelDraft(reference)
        patchTurn(messageId, { busy: false, action: null })
      } catch {
        // It expires on its own, so treat a failure here as done rather than
        // leaving a button the shopper has already decided against.
        patchTurn(messageId, { busy: false, action: null })
      }
    },
    [patchTurn],
  )

  const value = useMemo(
    () => ({
      turns,
      conversationId,
      sending,
      loading,
      error,
      conversations,
      send,
      startNew,
      openConversation,
      dismissError,
      confirmPurchase,
      declinePurchase,
    }),
    [
      turns,
      conversationId,
      sending,
      loading,
      error,
      conversations,
      send,
      startNew,
      openConversation,
      dismissError,
      confirmPurchase,
      declinePurchase,
    ],
  )

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat(): ChatState {
  const context = useContext(ChatContext)
  if (!context) throw new Error('useChat must be used inside ChatProvider')
  return context
}
