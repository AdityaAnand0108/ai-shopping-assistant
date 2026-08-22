import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ErrorNote } from '../components/ErrorNote'

export function SignupPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', fullName: '' })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const update = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: e.target.value }))

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setBusy(true)
    try {
      await register(form.username, form.email, form.password, form.fullName)
      navigate('/chat', { replace: true })
    } catch (e) {
      if (e instanceof ApiError) {
        // The backend returns per-field messages for a 400, which belong next to
        // the input that caused them rather than lumped into one banner.
        setFieldErrors(e.fieldErrors)
        setError(Object.keys(e.fieldErrors).length ? null : e.message)
      } else {
        setError('Could not create your account.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel narrow">
      <h1>Create an account</h1>

      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          autoComplete="username"
          value={form.username}
          onChange={update('username')}
          aria-invalid={Boolean(fieldErrors.username)}
          aria-describedby={fieldErrors.username ? 'username-error' : undefined}
          required
        />
        {fieldErrors.username && (
          <p id="username-error" className="field-error">
            {fieldErrors.username}
          </p>
        )}

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          value={form.email}
          onChange={update('email')}
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? 'email-error' : undefined}
          required
        />
        {fieldErrors.email && (
          <p id="email-error" className="field-error">
            {fieldErrors.email}
          </p>
        )}

        <label htmlFor="fullName">Full name (optional)</label>
        <input id="fullName" autoComplete="name" value={form.fullName} onChange={update('fullName')} />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={update('password')}
          aria-invalid={Boolean(fieldErrors.password)}
          aria-describedby={fieldErrors.password ? 'password-error' : 'password-hint'}
          required
        />
        {fieldErrors.password ? (
          <p id="password-error" className="field-error">
            {fieldErrors.password}
          </p>
        ) : (
          <p id="password-hint" className="muted small">
            At least 8 characters, with a letter and a number.
          </p>
        )}

        <ErrorNote message={error} />

        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </button>
      </form>

      <p className="muted">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </div>
  )
}
