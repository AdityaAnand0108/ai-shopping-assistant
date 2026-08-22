import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, tokenStore } from '../api/client'
import type { UserProfile } from '../api/types'

interface AuthState {
  user: UserProfile | null
  /** True until the stored token has been checked, so routes do not flash. */
  loading: boolean
  signIn: (username: string, password: string) => Promise<void>
  register: (username: string, email: string, password: string, fullName?: string) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)

  // A token in storage is not proof of a valid session: it may have expired, or
  // been revoked by signing out elsewhere. Verify it against the server once on
  // load rather than trusting its presence.
  useEffect(() => {
    if (!tokenStore.get()) {
      setLoading(false)
      return
    }
    api
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const response = await api.login(username, password)
    tokenStore.set(response.accessToken)
    setUser(response.user)
  }, [])

  const register = useCallback(
    async (username: string, email: string, password: string, fullName?: string) => {
      const response = await api.signup(username, email, password, fullName)
      tokenStore.set(response.accessToken)
      setUser(response.user)
    },
    [],
  )

  const signOut = useCallback(async () => {
    // Ask the server to revoke the token, but clear locally whatever happens —
    // a failed logout must not leave someone stuck in a signed-in shell.
    try {
      await api.logout()
    } finally {
      tokenStore.clear()
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, signIn, register, signOut }),
    [user, loading, signIn, register, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
