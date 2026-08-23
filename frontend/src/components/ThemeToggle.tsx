import type { ReactNode } from 'react'
import { useTheme } from '../theme/ThemeContext'
import type { ThemePreference } from '../theme/ThemeContext'

const OPTIONS: { value: ThemePreference; label: string; icon: ReactNode }[] = [
  {
    value: 'light',
    label: 'Light',
    icon: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </>
    ),
  },
  {
    value: 'dark',
    label: 'Dark',
    icon: <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />,
  },
  {
    value: 'system',
    label: 'Match system',
    icon: (
      <>
        <rect x="3" y="4" width="18" height="12" rx="2" />
        <path d="M9 20h6M12 16v4" />
      </>
    ),
  },
]

/**
 * Three-way theme control.
 *
 * A group of toggle buttons rather than a single cycling one, so the current
 * setting is visible instead of something you have to click to discover — and
 * so "match system" is reachable at all.
 */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme()

  return (
    <div className="theme-toggle" role="group" aria-label="Colour theme">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          className="theme-option"
          aria-label={option.label}
          aria-pressed={preference === option.value}
          title={option.label}
          onClick={() => setPreference(option.value)}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            {option.icon}
          </svg>
        </button>
      ))}
    </div>
  )
}
