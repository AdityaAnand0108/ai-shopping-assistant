import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { shortDateTime } from '../api/client'
import { useChat } from '../chat/ChatContext'
import { ErrorNote } from '../components/ErrorNote'
import { InsightPanel } from '../components/InsightPanel'
import { PurchaseCard } from '../components/PurchaseCard'
import { Spinner } from '../components/Spinner'

const SUGGESTIONS = [
  'What Nike t-shirts do you have?',
  'I need something to keep me warm',
  'What are my orders?',
  'When will my order arrive?',
]

export function ChatPage() {
  const {
    turns,
    conversationId,
    sending,
    loading,
    error,
    conversations,
    send,
    startNew,
    openConversation,
  } = useChat()

  const [draft, setDraft] = useState('')
  const [historyOpen, setHistoryOpen] = useState(false)
  const endRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns, sending])

  const submit = async (text: string) => {
    const question = text.trim()
    if (!question) return
    setDraft('')
    const delivered = await send(question)
    // Nothing typed is thrown away because the network failed.
    if (!delivered) setDraft(question)
    inputRef.current?.focus()
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    void submit(draft)
  }

  // The assistant frequently asks "would you like to proceed?" before it has
  // called createOrderDraft, so there is no priced purchase yet and nothing for
  // a Confirm button to act on. Answering it produces the draft, and the card
  // with the real Confirm button appears on the next turn. This chip is only a
  // shortcut for typing that answer — nothing is derived from the matched text
  // beyond whether to offer it, so a false positive costs a chip nobody clicks.
  const last = turns[turns.length - 1]
  const offerYesNo =
    !sending &&
    !loading &&
    last?.message.role === 'ASSISTANT' &&
    !last.action &&
    /\?\s*$/.test(last.message.content.trim()) &&
    /proceed|purchase|confirm|place the order|buy it/i.test(last.message.content)

  const pick = (id: string) => {
    void openConversation(id)
    setHistoryOpen(false)
  }

  return (
    <div className="chat-layout">
      <button
        type="button"
        className="button small history-toggle"
        aria-expanded={historyOpen}
        onClick={() => setHistoryOpen((open) => !open)}
      >
        {historyOpen ? 'Hide history' : `History (${conversations.length})`}
      </button>

      <aside
        className={`chat-history ${historyOpen ? 'open' : ''}`}
        aria-label="Past conversations"
      >
        <button type="button" className="button small block" onClick={startNew}>
          New chat
        </button>

        {conversations.length === 0 ? (
          <p className="muted small history-empty">
            Past conversations show up here once you have had one.
          </p>
        ) : (
          <ul className="history-list">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <button
                  type="button"
                  className={`history-row ${conversation.id === conversationId ? 'selected' : ''}`}
                  aria-current={conversation.id === conversationId}
                  onClick={() => pick(conversation.id)}
                >
                  <span className="history-title">
                    {conversation.title ?? 'Untitled conversation'}
                  </span>
                  <span className="muted small">
                    {shortDateTime(conversation.updatedAt)} · {conversation.messageCount} message
                    {conversation.messageCount === 1 ? '' : 's'}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>

      <div className="panel chat-panel">
        <h1>Assistant</h1>

        <div className="chat-log" role="log" aria-live="polite" aria-label="Conversation">
          {loading && <Spinner label="Opening that conversation…" />}

          {!loading && turns.length === 0 && (
            <div className="chat-empty">
              <p className="muted">
                Ask about products or your orders. Every factual answer comes from a real backend
                call — and each reply shows which ones.
              </p>
              <ul className="suggestions">
                {SUGGESTIONS.map((suggestion) => (
                  <li key={suggestion}>
                    <button type="button" className="chip" onClick={() => void submit(suggestion)}>
                      {suggestion}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {!loading &&
            turns.map((turn) => (
              <article
                key={turn.message.id}
                className={turn.message.role === 'USER' ? 'bubble user' : 'bubble assistant'}
              >
                <p className="bubble-role">{turn.message.role === 'USER' ? 'You' : 'Assistant'}</p>
                <div className="bubble-text">{turn.message.content}</div>
                {turn.action && <PurchaseCard turn={turn} />}
                {turn.insight && <InsightPanel insight={turn.insight} />}
              </article>
            ))}

          {sending && (
            <article className="bubble assistant pending">
              <p className="bubble-role">Assistant</p>
              <p className="muted">
                Thinking… a local model takes a few seconds, longer when it looks something up.
              </p>
            </article>
          )}

          <div ref={endRef} />
        </div>

        {offerYesNo && (
          <ul className="quick-replies">
            <li>
              <button type="button" className="chip primary" onClick={() => void submit('Yes')}>
                Yes, price it up
              </button>
            </li>
            <li>
              <button type="button" className="chip" onClick={() => void submit('No thanks')}>
                No thanks
              </button>
            </li>
          </ul>
        )}

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
    </div>
  )
}
