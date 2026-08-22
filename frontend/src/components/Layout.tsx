import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          Shop<span>Assist</span>
        </NavLink>

        <nav aria-label="Main">
          <NavLink to="/">Catalog</NavLink>
          {user && <NavLink to="/chat">Assistant</NavLink>}
          {user && <NavLink to="/orders">Orders</NavLink>}
        </nav>

        <div className="account">
          {user ? (
            <>
              <span className="muted">{user.username}</span>
              <button type="button" className="link" onClick={handleSignOut}>
                Sign out
              </button>
            </>
          ) : (
            <NavLink to="/login" className="button small">
              Sign in
            </NavLink>
          )}
        </div>
      </header>

      <main id="main">
        <Outlet />
      </main>
    </div>
  )
}
