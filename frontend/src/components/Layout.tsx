import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'
import { ThemeToggle } from './ThemeToggle'

/** Spoken label for the cart button, since the badge itself is decorative. */
function cartLabel(count: number): string {
  if (count === 0) return 'Cart, empty'
  return `Cart, ${count} item${count === 1 ? '' : 's'}`
}

export function Layout() {
  const { user, signOut } = useAuth()
  const { count } = useCart()
  const navigate = useNavigate()
  const location = useLocation()

  // The landing page runs edge to edge and brings its own spacing, so it opts
  // out of the centred column the rest of the app renders into.
  const fullBleed = location.pathname === '/'

  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

  return (
    <div className="app">
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <header className="topbar">
        <NavLink to="/" className="brand">
          Shop<span>Assist</span>
        </NavLink>

        {/* Every destination here needs a session, so the nav is empty until
            there is one rather than offering links that bounce to sign-in. */}
        <nav aria-label="Main">
          {user && <NavLink to="/catalog">Catalog</NavLink>}
          {user && <NavLink to="/chat">Assistant</NavLink>}
          {user && <NavLink to="/orders">Orders</NavLink>}
        </nav>

        <div className="account">
          {user && (
            <NavLink to="/cart" className="cart-button" aria-label={cartLabel(count)}>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M3 4h2l2.4 10.4A2 2 0 0 0 9.3 16h7.6a2 2 0 0 0 2-1.6L20.5 7H6" />
                <circle cx="10" cy="20" r="1.4" />
                <circle cx="17" cy="20" r="1.4" />
              </svg>
              {count > 0 && (
                <span className="cart-badge" aria-hidden="true">
                  {count > 99 ? '99+' : count}
                </span>
              )}
            </NavLink>
          )}

          <ThemeToggle />

          {user ? (
            <>
              <span className="muted">{user.username}</span>
              <button type="button" className="link" onClick={handleSignOut}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login" className="link">
                Sign in
              </NavLink>
              <NavLink to="/signup" className="button small">
                Get started
              </NavLink>
            </>
          )}
        </div>
      </header>

      <main id="main" className={fullBleed ? 'full-bleed' : undefined}>
        <Outlet />
      </main>
    </div>
  )
}
