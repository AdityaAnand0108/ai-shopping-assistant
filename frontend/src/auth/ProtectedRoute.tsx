import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

/**
 * Gates a route behind a signed-in session.
 *
 * Purely a convenience for the shopper: the backend does not trust the client
 * for any of this. Every protected endpoint checks the token itself, so a
 * bypass here would produce a page full of 401s rather than anyone's data.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <p className="muted centered">Checking your session…</p>
  }

  if (!user) {
    // Remember where they were headed so sign-in can return them there.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
