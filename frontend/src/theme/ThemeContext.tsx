import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

/** What the shopper picked. "system" tracks the operating system setting. */
export type ThemePreference = 'system' | 'light' | 'dark'

/** What that preference resolves to, and what `data-theme` is ever set to. */
export type ResolvedTheme = 'light' | 'dark'

// Shared with the inline script in index.html, which applies the same default
// before the first paint. Change one and you must change the other.
const STORAGE_KEY = 'shopassist-theme'
const DEFAULT_PREFERENCE: ThemePreference = 'light'

const DARK_QUERY = '(prefers-color-scheme: dark)'

function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
}

function resolve(preference: ThemePreference): ResolvedTheme {
  if (preference === 'system') return prefersDark() ? 'dark' : 'light'
  return preference
}

function readStoredPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'system' || saved === 'light' || saved === 'dark') return saved
  } catch {
    // Storage is unavailable in some private-browsing modes. A theme is not
    // worth failing a page load over, so fall through to the default.
  }
  return DEFAULT_PREFERENCE
}

interface ThemeState {
  preference: ThemePreference
  resolved: ResolvedTheme
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeState | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference)
  const [resolved, setResolved] = useState<ResolvedTheme>(() => resolve(readStoredPreference()))

  // The attribute is the single thing the stylesheet reads, so every change to
  // the preference ends here.
  useEffect(() => {
    document.documentElement.dataset.theme = resolved
  }, [resolved])

  // Only while following the system does an OS-level change matter; a shopper
  // who picked light or dark explicitly should not have it moved under them.
  useEffect(() => {
    if (preference !== 'system' || typeof window.matchMedia !== 'function') {
      setResolved(resolve(preference))
      return
    }

    const query = window.matchMedia(DARK_QUERY)
    const sync = () => setResolved(query.matches ? 'dark' : 'light')
    sync()
    query.addEventListener('change', sync)
    return () => query.removeEventListener('change', sync)
  }, [preference])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Same as on read: the theme still applies for this session.
    }
  }, [])

  const value = useMemo(
    () => ({ preference, resolved, setPreference }),
    [preference, resolved, setPreference],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeState {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}
