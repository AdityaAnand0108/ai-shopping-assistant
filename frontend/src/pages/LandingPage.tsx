import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Reveal } from '../components/Reveal'

/** Stroke icons, sized by font-size and coloured by `currentColor`. */
function Icon({ name }: { name: keyof typeof PATHS }) {
  return (
    <svg
      className="feature-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {PATHS[name]}
    </svg>
  )
}

const PATHS = {
  chat: <path d="M21 11.5a8.5 8.5 0 0 1-11.9 7.8L3 21l1.7-6.1A8.5 8.5 0 1 1 21 11.5Z" />,
  database: (
    <>
      <ellipse cx="12" cy="6" rx="8" ry="3" />
      <path d="M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6" />
      <path d="M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" />
    </>
  ),
  truck: (
    <>
      <path d="M3 7h11v9H3z" />
      <path d="M14 10h4l3 3v3h-7z" />
      <circle cx="7" cy="18" r="1.8" />
      <circle cx="17.5" cy="18" r="1.8" />
    </>
  ),
  cart: (
    <>
      <path d="M3 4h2l2.4 10.4A2 2 0 0 0 9.3 16h7.6a2 2 0 0 0 2-1.6L20.5 7H6" />
      <circle cx="10" cy="20" r="1.4" />
      <circle cx="17" cy="20" r="1.4" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3l7 3v6c0 4.2-2.9 7.9-7 9-4.1-1.1-7-4.8-7-9V6z" />
      <path d="M9 12l2 2 4-4" />
    </>
  ),
  chip: (
    <>
      <rect x="7" y="7" width="10" height="10" rx="2" />
      <path d="M10 3v3M14 3v3M10 18v3M14 18v3M3 10h3M3 14h3M18 10h3M18 14h3" />
    </>
  ),
} as const

const FEATURES = [
  {
    icon: 'chat',
    title: 'Ask the way you would ask a person',
    body: 'No filter grid to fight. "Something warm for a cold trip, under $80" is a valid search — the catalog is embedded, so the assistant matches on meaning rather than only on keywords.',
  },
  {
    icon: 'database',
    title: 'Every fact comes from the database',
    body: 'Prices, stock levels and order statuses are fetched by an explicit backend tool call before the answer is written. The model narrates the result; it never invents it.',
  },
  {
    icon: 'truck',
    title: 'Order tracking, in the same thread',
    body: 'Ask where an order is and get the real status with its full timeline — placed, packed, shipped, delivered — without leaving the conversation.',
  },
  {
    icon: 'cart',
    title: 'Buying takes two steps, on purpose',
    body: 'A purchase is drafted first, with the item, quantity and total spelled out. Nothing is ordered until you confirm it.',
  },
  {
    icon: 'shield',
    title: 'Scoped and guarded',
    body: 'The assistant stays on shopping and declines the rest. Order tools are bound to your session, so a question about another shopper simply has no answer to give.',
  },
  {
    icon: 'chip',
    title: 'Runs on your machine',
    body: 'A local Ollama model does the talking and the embedding. No paid APIs, no third-party inference, and the catalog and orders never leave the host.',
  },
] as const

const STEPS = [
  {
    title: 'Create an account',
    body: 'Username, email, password. It takes a few seconds, opens the catalog, and is what scopes orders and purchases to you.',
  },
  {
    title: 'Ask for what you want',
    body: 'Describe the thing, ask about an order, or start a purchase. The assistant picks the right tool and runs it against the catalog.',
  },
  {
    title: 'Check it, then confirm',
    body: 'Answers show what they were drawn from, and a purchase waits for a yes. You stay the one making the decision.',
  },
] as const

export function LandingPage() {
  const { user } = useAuth()

  return (
    <div className="landing">
      {/* --- hero ---------------------------------------------------------- */}
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-glow" aria-hidden="true">
          <span className="blob blob-a" />
          <span className="blob blob-b" />
          <span className="blob blob-c" />
        </div>

        <div className="hero-inner">
          <div className="hero-copy">
            <p className="eyebrow">
              <span className="dot" aria-hidden="true" />
              Local AI, grounded in a real catalog
            </p>

            <h1 id="hero-title">
              Shopping that answers back —{' '}
              <span className="gradient-text">and checks its facts</span>
            </h1>

            <p className="lede">
              ShopAssist is a conversational storefront. Search the catalog, track an order, or
              place one, all in plain language. Every number you see was read out of the database
              first, so the assistant is never guessing on your behalf.
            </p>

            <div className="cta-row">
              {user ? (
                <>
                  <Link to="/chat" className="button large">
                    Open the assistant
                  </Link>
                  <Link to="/orders" className="button large ghost">
                    View your orders
                  </Link>
                </>
              ) : (
                <>
                  <Link to="/signup" className="button large">
                    Create an account
                  </Link>
                  <Link to="/login" className="button large ghost">
                    Sign in
                  </Link>
                </>
              )}
            </div>

            <p className="muted small cta-note">
              {user ? (
                <>
                  Or <Link to="/catalog">browse the catalog</Link> on your own.
                </>
              ) : (
                'Free, and it runs on this machine — no card, no cloud, no waiting list.'
              )}
            </p>
          </div>

          {/* Illustrative only: a still of a typical exchange, not a live chat. */}
          <div
            className="hero-demo"
            role="img"
            aria-label="Example exchange: a shopper asks for Nike t-shirts under forty dollars, and the assistant answers with two in-stock products and their prices, noting that the answer came from a catalog search."
          >
            <div className="demo-card">
              <div className="demo-head">
                <span className="demo-dots" aria-hidden="true">
                  <i />
                  <i />
                  <i />
                </span>
                <span className="muted small">ShopAssist</span>
              </div>

              <div className="demo-body" aria-hidden="true">
                <p className="demo-msg from-user">Do you have Nike t-shirts under $40?</p>

                <div className="demo-msg from-bot">
                  <p>Two in stock right now:</p>
                  <ul className="demo-products">
                    <li>
                      <span>Nike Sportswear Club Tee</span>
                      <strong>$32.00</strong>
                    </li>
                    <li>
                      <span>Nike Dri-FIT Training Tee</span>
                      <strong>$38.00</strong>
                    </li>
                  </ul>
                  <p className="demo-source">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.4"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M20 6 9 17l-5-5" />
                    </svg>
                    from catalog search · 2 products
                  </p>
                </div>

                <p className="demo-msg from-user short">Order the Club Tee, size medium.</p>

                <div className="demo-msg from-bot typing">
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <ul className="stat-strip">
          <li>
            <strong>60</strong>
            <span>products seeded</span>
          </li>
          <li>
            <strong>6</strong>
            <span>categories</span>
          </li>
          <li>
            <strong>0</strong>
            <span>paid APIs</span>
          </li>
          <li>
            <strong>100%</strong>
            <span>facts from tool calls</span>
          </li>
        </ul>
      </section>

      {/* --- features ------------------------------------------------------ */}
      <section className="band" aria-labelledby="features-title">
        <Reveal className="band-head">
          <h2 id="features-title">What it actually does</h2>
          <p className="muted">
            A chat box on a storefront is easy. Building one that cannot quietly make things up is
            the part worth doing.
          </p>
        </Reveal>

        <ul className="feature-grid">
          {FEATURES.map((feature, index) => (
            <Reveal as="li" key={feature.title} delay={index * 60} className="feature-card">
              <span className="icon-well">
                <Icon name={feature.icon} />
              </span>
              <h3>{feature.title}</h3>
              <p className="muted">{feature.body}</p>
            </Reveal>
          ))}
        </ul>
      </section>

      {/* --- how it works -------------------------------------------------- */}
      <section className="band alt" aria-labelledby="steps-title">
        <Reveal className="band-head">
          <h2 id="steps-title">Three steps, start to finish</h2>
        </Reveal>

        <ol className="step-list">
          {STEPS.map((step, index) => (
            <Reveal as="li" key={step.title} delay={index * 80} className="step">
              <span className="step-number" aria-hidden="true">
                {index + 1}
              </span>
              <h3>{step.title}</h3>
              <p className="muted">{step.body}</p>
            </Reveal>
          ))}
        </ol>
      </section>

      {/* --- stack --------------------------------------------------------- */}
      <section className="band" aria-labelledby="stack-title">
        <Reveal className="band-head">
          <h2 id="stack-title">Built on</h2>
          <p className="muted">Free, local tooling end to end — clone it and it runs.</p>
        </Reveal>

        <Reveal className="stack-row">
          {STACK.map((item) => (
            <span className="tech-pill" key={item}>
              {item}
            </span>
          ))}
        </Reveal>
      </section>

      {/* --- closing CTA --------------------------------------------------- */}
      <section className="band closing" aria-labelledby="cta-title">
        <Reveal className="closing-card">
          <h2 id="cta-title">{user ? 'Pick up where you left off' : 'Start a conversation'}</h2>
          <p className="muted">
            {user
              ? 'Your session is still active — the assistant is one click away.'
              : 'Create an account to chat, track orders and buy. Everything stays on this machine.'}
          </p>

          <div className="cta-row centered-row">
            {user ? (
              <Link to="/chat" className="button large">
                Open the assistant
              </Link>
            ) : (
              <>
                <Link to="/signup" className="button large">
                  Create an account
                </Link>
                <Link to="/login" className="button large ghost">
                  Sign in
                </Link>
              </>
            )}
          </div>

          {!user && (
            <p className="demo-accounts">
              Just exploring? Sign in as <code>demo</code> / <code>Demo1234</code>, or as{' '}
              <code>satvik</code> / <code>Password123</code> for an account that already has order
              history.
            </p>
          )}
        </Reveal>
      </section>

      <footer className="landing-footer">
        <p className="brand">
          Shop<span>Assist</span>
        </p>
        <nav aria-label="Footer">
          {user && <Link to="/catalog">Catalog</Link>}
          {user ? (
            <Link to="/chat">Assistant</Link>
          ) : (
            <>
              <Link to="/login">Sign in</Link>
              <Link to="/signup">Create an account</Link>
            </>
          )}
        </nav>
        <p className="muted small">
          A demo project. The catalog and the orders in it are fictional, and no payment is ever
          taken.
        </p>
      </footer>
    </div>
  )
}

const STACK = [
  'Java 21',
  'Spring Boot',
  'Spring AI',
  'Ollama · qwen2.5',
  'nomic-embed-text',
  'React + Vite',
  'Flyway',
  'H2 / MySQL',
] as const
