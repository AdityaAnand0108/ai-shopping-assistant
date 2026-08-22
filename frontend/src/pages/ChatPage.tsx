import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { ChatMessage, TurnInsight } from '../api/types'
import { ErrorNote } from '../components/ErrorNote'
import { InsightPanel } from '../components/InsightPanel'

/** A message plus, for assistant turns, where the answer came from. */
interface Turn {
  message: ChatMessage
  insight?: TurnInsight
}

const SUGGESTIONS = [
  'What Nike t-shirts do you have?',
  'I need something to keep me warm',
  'What are my orders?',
  'When will my order arrive?',
]

export function ChatPage() {
  const [turns, setTurns] = useState<Turn[]>([])
  const [conversationId, setConversationId] = useState<string | undefined>()
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const endRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns, sending])

  const send = async (text: string) => {
    const question = text.trim()
    if (!question || sending) return

    setError(null)
    setDraft('')
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

    try {
      const response = await api.chat(question, conversationId)
      setConversationId(response.conversationId)
      setTurns((current) => [
        ...current,
        { message: response.reply, insight: response.insight },
      ])
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
    } finally {
      setSending(false)
      inputRef.current?.focus()
    }
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    void send(draft)
  }

  return (
    <div className="panel chat-panel">
      <h1>Assistant</h1>

      <div className="chat-log" role="log" aria-live="polite" aria-label="Conversation">
        {turns.length === 0 && (
          <div className="chat-empty">
            <p className="muted">
              Ask about products or your orders. Every factual answer comes from a
              real backend call — and each reply shows which ones.
            </p>
            <ul className="suggestions">
              {SUGGESTIONS.map((suggestion) => (
                <li key={suggestion}>
                  <button type="button" className="chip" onClick={() => void send(suggestion)}>
                    {suggestion}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {turns.map((turn) => (
          <article
            key={turn.message.id}
            className={turn.message.role === 'USER' ? 'bubble user' : 'bubble assistant'}
          >
            <p className="bubble-role">
              {turn.message.role === 'USER' ? 'You' : 'Assistant'}
            </p>
            <div className="bubble-text">{turn.message.content}</div>
            {turn.insight && <InsightPanel insight={turn.insight} />}
          </article>
        ))}

        {sending && (
          <article className="bubble assistant pending">
            <p className="bubble-role">Assistant</p>
            <p className="muted">
              Thinking… a local model takes a few seconds, longer when it looks
              something up.
            </p>
          </article>
        )}

        <div ref={endRef} />
      </div>

      <ErrorNote message={error} />

      <form className="composer" onSubmit={handleSubmit}>
        <label htmlFor="message" className="visually-hidden">
          Your message
        </label>
        <input
          id="message"
          ref={inputRef}
          value={draft}
          maxLength={1000}
          placeholder="Ask about a product or an order…"
          onChange={(e) => setDraft(e.target.value)}
          disabled={sending}
          autoComplete="off"
        />
        <button type="submit" className="button" disabled={sending || !draft.trim()}>
          Send
        </button>
      </form>
    </div>
  )
}
