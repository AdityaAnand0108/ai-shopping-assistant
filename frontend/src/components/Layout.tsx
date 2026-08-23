import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ThemeToggle } from './ThemeToggle'

export function Layout() {
  const { user, signOut } = useAuth()
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
