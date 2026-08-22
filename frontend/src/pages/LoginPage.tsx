import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ErrorNote } from '../components/ErrorNote'

export function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signIn(username, password)
      // Return them wherever they were headed before the redirect to sign-in.
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/chat', { replace: true })
    } catch (e) {
      // The backend answers an unknown username and a wrong password
      // identically on purpose, so there is nothing more specific to show.
      setError(e instanceof ApiError ? e.message : 'Could not sign in.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel narrow">
      <h1>Sign in</h1>

      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <ErrorNote message={error} />

        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="muted">
        No account? <Link to="/signup">Create one</Link>
      </p>

      <div className="demo-hint">
        <p className="muted">
          Demo accounts — <code>satvik</code>, <code>sarah</code> or{' '}
          <code>rahul</code> with <code>Password123</code>, or <code>demo</code>{' '}
          with <code>Demo1234</code> for an account with no orders.
        </p>
      </div>
    </div>
  )
}
